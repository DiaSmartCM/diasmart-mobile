package com.diabeto.data.repository

import android.util.Log
import com.diabeto.data.api.NotificationApi
import com.diabeto.data.model.Conversation
import com.diabeto.data.model.Message
import com.diabeto.data.model.UserProfile
import com.diabeto.data.model.UserRole
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
 * Repository pour la messagerie patient-médecin via Firestore
 */
@Singleton
class MessagerieRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val notificationApi: NotificationApi
) {
    companion object {
        private const val COL_CONVERSATIONS = "conversations"
        private const val COL_MESSAGES = "messages"
    }

    /**
     * Flow en temps réel des conversations de l'utilisateur courant
     */
    fun getConversationsFlow(): Flow<List<Conversation>> = callbackFlow {
        val uid = authRepository.currentUserId ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val profile = authRepository.getCurrentUserProfile()
        val field = if (profile?.role == UserRole.MEDECIN) "medecinId" else "patientId"

        val listener = firestore.collection(COL_CONVERSATIONS)
            .whereEqualTo(field, uid)
            .orderBy("dernierMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val conversations = snap?.documents?.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    doc.data?.let { Conversation.fromMap(doc.id, it as Map<String, Any?>) }
                } ?: emptyList()
                trySend(conversations)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Flow en temps réel des messages d'une conversation
     */
    fun getMessagesFlow(conversationId: String): Flow<List<Message>> = callbackFlow {
        val listener = firestore.collection(COL_CONVERSATIONS)
            .document(conversationId)
            .collection(COL_MESSAGES)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val messages = snap?.documents?.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    doc.data?.let { Message.fromMap(doc.id, it as Map<String, Any?>) }
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Envoyer un message dans une conversation
     */
    suspend fun envoyerMessage(conversationId: String, contenu: String): Result<Unit> {
        return try {
            val profile = authRepository.getCurrentUserProfile()
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            val message = Message(
                envoyeurId = profile.uid,
                envoyeurNom = profile.nomComplet,
                contenu = contenu,
                timestamp = Timestamp.now()
            )

            firestore.collection(COL_CONVERSATIONS)
                .document(conversationId)
                .collection(COL_MESSAGES)
                .add(message.toMap())
                .await()

            // Mettre à jour le dernier message de la conversation
            val updateField = if (profile.role == UserRole.MEDECIN) "nonLusPatient" else "nonLusMedecin"
            firestore.collection(COL_CONVERSATIONS)
                .document(conversationId)
                .update(
                    mapOf(
                        "dernierMessage" to contenu,
                        "dernierMessageAt" to Timestamp.now(),
                        updateField to com.google.firebase.firestore.FieldValue.increment(1)
                    )
                )
                .await()

            // Push FCM au destinataire (best-effort, n'echoue pas la conv).
            try {
                notificationApi.notifyMessage(conversationId = conversationId, preview = contenu)
            } catch (e: Exception) {
                Log.w("MessagerieRepository", "notifyMessage failed: ${e.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Envoyer un message avec piece jointe (rapport PDF stocke dans Firebase Storage).
     */
    suspend fun envoyerMessageAvecPieceJointe(
        conversationId: String,
        contenu: String,
        attachmentUrl: String,
        attachmentName: String,
        attachmentType: String = "application/pdf"
    ): Result<Unit> {
        return try {
            val profile = authRepository.getCurrentUserProfile()
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            val message = Message(
                envoyeurId = profile.uid,
                envoyeurNom = profile.nomComplet,
                contenu = contenu,
                timestamp = Timestamp.now(),
                attachmentUrl = attachmentUrl,
                attachmentName = attachmentName,
                attachmentType = attachmentType
            )

            firestore.collection(COL_CONVERSATIONS)
                .document(conversationId)
                .collection(COL_MESSAGES)
                .add(message.toMap())
                .await()

            val updateField = if (profile.role == UserRole.MEDECIN) "nonLusPatient" else "nonLusMedecin"
            val resume = if (contenu.isBlank()) "📎 $attachmentName" else contenu
            firestore.collection(COL_CONVERSATIONS)
                .document(conversationId)
                .update(
                    mapOf(
                        "dernierMessage" to resume,
                        "dernierMessageAt" to Timestamp.now(),
                        updateField to com.google.firebase.firestore.FieldValue.increment(1)
                    )
                )
                .await()

            // Push FCM au destinataire (best-effort).
            try {
                notificationApi.notifyMessage(
                    conversationId = conversationId,
                    preview = contenu,
                    attachmentName = attachmentName
                )
            } catch (e: Exception) {
                Log.w("MessagerieRepository", "notifyMessage (attachment) failed: ${e.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Créer une nouvelle conversation entre un patient et un médecin
     * (appel historique cote patient).
     */
    suspend fun creerConversation(medecinProfile: UserProfile): Result<String> {
        val current = authRepository.getCurrentUserProfile()
            ?: return Result.failure(Exception("Utilisateur non connecté"))
        return findOrCreateConversationWith(current, medecinProfile)
    }

    /**
     * Cree ou retrouve une conversation entre `currentUser` et `otherUser`,
     * en placant CHACUN dans le bon champ (patientId / medecinId) selon son
     * role. Utilise par les flows ou l'appelant peut etre patient OU medecin
     * (envoi de rapport, ordonnance, compte-rendu...).
     */
    suspend fun findOrCreateConversationWith(
        currentUser: UserProfile,
        otherUser: UserProfile
    ): Result<String> {
        return try {
            val (patient, medecin) = when {
                currentUser.role == UserRole.PATIENT && otherUser.role == UserRole.MEDECIN ->
                    currentUser to otherUser
                currentUser.role == UserRole.MEDECIN && otherUser.role == UserRole.PATIENT ->
                    otherUser to currentUser
                else -> return Result.failure(
                    Exception("Conversation impossible : roles invalides (${currentUser.role} ↔ ${otherUser.role})")
                )
            }

            val existing = firestore.collection(COL_CONVERSATIONS)
                .whereEqualTo("patientId", patient.uid)
                .whereEqualTo("medecinId", medecin.uid)
                .get()
                .await()
            if (!existing.isEmpty) return Result.success(existing.documents.first().id)

            val conversation = Conversation(
                patientId = patient.uid,
                medecinId = medecin.uid,
                patientNom = patient.nomComplet,
                medecinNom = medecin.nomComplet
            )

            val doc = firestore.collection(COL_CONVERSATIONS)
                .add(conversation.toMap()).await()
            Result.success(doc.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Marquer tous les messages d'une conversation comme lus
     */
    suspend fun marquerCommeLus(conversationId: String) {
        val uid = authRepository.currentUserId ?: return
        val profile = authRepository.getCurrentUserProfile() ?: return

        try {
            val nonLusField = if (profile.role == UserRole.MEDECIN) "nonLusMedecin" else "nonLusPatient"
            firestore.collection(COL_CONVERSATIONS)
                .document(conversationId)
                .update(nonLusField, 0)
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Nombre total de messages non lus pour l'utilisateur courant
     */
    suspend fun getTotalNonLus(): Int {
        val uid = authRepository.currentUserId ?: return 0
        val profile = authRepository.getCurrentUserProfile() ?: return 0
        val field = if (profile.role == UserRole.MEDECIN) "medecinId" else "patientId"
        val nonLusField = if (profile.role == UserRole.MEDECIN) "nonLusMedecin" else "nonLusPatient"

        return try {
            val snap = firestore.collection(COL_CONVERSATIONS)
                .whereEqualTo(field, uid)
                .get()
                .await()
            snap.documents.sumOf { doc ->
                (doc.getLong(nonLusField) ?: 0L).toInt()
            }
        } catch (e: Exception) {
            0
        }
    }
}
