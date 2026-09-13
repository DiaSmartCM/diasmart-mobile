package com.diabeto.data.repository

import com.diabeto.util.tickerFlow
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Presence "en ligne" via Firestore (pas de Realtime Database : on reste sur
 * Spark/Firestore uniquement). Un seul document par utilisateur, mis a jour
 * par un heartbeat peu frequent (cf. MainActivity) : pas de vraie detection
 * de deconnexion (pas d'equivalent onDisconnect), on considere "en ligne"
 * tant que le dernier battement date de moins de [ONLINE_THRESHOLD_MS].
 */
@Singleton
class PresenceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val COL_PRESENCE = "presence"
        const val ONLINE_THRESHOLD_MS = 45_000L
        private const val RECHECK_INTERVAL_MS = 15_000L
    }

    /** Battement de presence pour l'utilisateur courant. Best-effort, jamais bloquant. */
    suspend fun ping() {
        val uid = authRepository.currentUserId ?: return
        try {
            firestore.collection(COL_PRESENCE).document(uid)
                .set(mapOf("lastSeen" to Timestamp.now()))
                .await()
        } catch (e: Exception) {
            // best-effort
        }
    }

    /** Flow temps reel : true si [uid] a envoye un battement recent. */
    fun observeOnline(uid: String): Flow<Boolean> {
        if (uid.isBlank()) return flowOf(false)

        val lastSeenFlow = callbackFlow {
            val listener = firestore.collection(COL_PRESENCE).document(uid)
                .addSnapshotListener { snap, _ -> trySend(snap?.getTimestamp("lastSeen")) }
            awaitClose { listener.remove() }
        }

        return lastSeenFlow.combine(tickerFlow(RECHECK_INTERVAL_MS)) { lastSeen, _ -> lastSeen }
            .map { lastSeen ->
                lastSeen != null &&
                    (Timestamp.now().toDate().time - lastSeen.toDate().time) < ONLINE_THRESHOLD_MS
            }
    }
}
