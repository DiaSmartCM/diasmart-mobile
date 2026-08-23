package com.diabeto.data.repository

import com.diabeto.ui.viewmodel.CommunityMessage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.1.47 : repository extrait de `CommunityViewModel` qui bypassait
 * directement FirebaseFirestore. Encapsule les acces a la collection
 * `community_messages` + le count des membres patients.
 */
@Singleton
class CommunityRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val COL_COMMUNITY = "community_messages"
        private const val COL_USERS = "users"
        private const val MESSAGE_LIMIT = 200L
    }

    /**
     * Flow temps reel des 200 derniers messages de la communaute.
     */
    fun observeMessages(): Flow<List<CommunityMessage>> = callbackFlow {
        val listener = firestore.collection(COL_COMMUNITY)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(MESSAGE_LIMIT)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val messages = snap?.documents?.mapNotNull { doc ->
                    try {
                        CommunityMessage(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            userName = doc.getString("userName") ?: "Anonyme",
                            content = doc.getString("content") ?: "",
                            timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now()
                        )
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Compte les utilisateurs avec role=PATIENT (pour l'affichage "membres").
     */
    suspend fun countPatientMembers(): Int = runCatching {
        firestore.collection(COL_USERS)
            .whereIn("role", listOf("PATIENT", "patient"))
            .get().await()
            .size()
    }.getOrDefault(0)

    /**
     * Publie un nouveau message dans la communaute.
     */
    suspend fun postMessage(userId: String, userName: String, content: String): Result<String> = runCatching {
        val doc = firestore.collection(COL_COMMUNITY).add(
            mapOf(
                "userId" to userId,
                "userName" to userName,
                "content" to content,
                "timestamp" to Timestamp.now()
            )
        ).await()
        doc.id
    }
}
