package com.diabeto.data.repository

import com.diabeto.ui.viewmodel.CommunityMessage
import com.diabeto.util.tickerFlow
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
        private const val COL_TYPING = "community_typing"
        private const val MESSAGE_LIMIT = 200L
        // v2.1.98 : coherent avec MessagerieViewModel (meme mecanisme de
        // rafraichissement periodique pendant une frappe longue).
        private const val TYPING_STALE_MS = 12_000L
        private const val TYPING_RECHECK_MS = 4_000L
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

    /**
     * v2.1.98 : signale que `uid` est (ou n'est plus) en train d'ecrire dans
     * la communaute. Un document par utilisateur qui tape actuellement —
     * supprime a l'arret plutot que marque false, pour ne pas laisser
     * grossir la collection avec des entrees perimees.
     */
    suspend fun setTyping(uid: String, userName: String, isTyping: Boolean) {
        try {
            val ref = firestore.collection(COL_TYPING).document(uid)
            if (isTyping) {
                ref.set(mapOf("userName" to userName, "updatedAt" to Timestamp.now())).await()
            } else {
                ref.delete().await()
            }
        } catch (e: Exception) {
            // best-effort : ne doit jamais faire echouer la saisie
        }
    }

    /**
     * Flow temps reel des noms des membres actuellement en train d'ecrire
     * (hors soi-meme), avec re-evaluation periodique de la fraicheur.
     */
    fun observeTypers(excludeUid: String): Flow<List<String>> {
        val docsFlow = callbackFlow {
            val listener = firestore.collection(COL_TYPING)
                .addSnapshotListener { snap, _ ->
                    val entries = snap?.documents
                        ?.filter { it.id != excludeUid }
                        ?.mapNotNull { doc ->
                            val name = doc.getString("userName") ?: return@mapNotNull null
                            val updatedAt = doc.getTimestamp("updatedAt") ?: return@mapNotNull null
                            name to updatedAt
                        } ?: emptyList()
                    trySend(entries)
                }
            awaitClose { listener.remove() }
        }

        return docsFlow.combine(tickerFlow(TYPING_RECHECK_MS)) { entries, _ -> entries }
            .map { entries ->
                val now = Timestamp.now().toDate().time
                entries.filter { (_, updatedAt) -> (now - updatedAt.toDate().time) < TYPING_STALE_MS }
                    .map { (name, _) -> name }
            }
    }
}
