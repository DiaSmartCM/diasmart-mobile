package com.diabeto.data.repository

import android.util.Log
import com.diabeto.data.model.FamilyLink
import com.diabeto.data.model.FamilyLinkStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.1.48 : repository pour le Mode Famille.
 *
 * Workflow :
 * 1. Owner invite un aidant par email (`inviteAidant`) → cree un doc
 *    family_links/{ownerUid}_{aidantUid} avec status=PENDING.
 *    Si l'aidantUid n'est pas encore connu (l'aidant n'a pas de compte
 *    DiaSmart), on stocke l'email et le doc devient "active a la
 *    creation du compte de l'aidant" (mecanisme V2).
 * 2. Aidant accepte (`acceptInvitation`) → status=ACCEPTED, isActive=true.
 * 3. Owner ou aidant peut revoquer (`revoke...`) → isActive=false.
 * 4. Reactivation possible (`reactivate`) — meme pattern que data_sharing.
 *
 * Modele freemium V1 :
 * - Limite Spark : 1 aidant actif gratuit par owner (verifie cote client).
 *   Quand premium V2 arrive, on enleve la limite cote client + verification
 *   serveur sur la cle premium dans users/{uid}.
 */
@Singleton
class FamilyRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "FamilyRepo"
        private const val COL_FAMILY = "family_links"
        const val FREE_AIDANT_LIMIT = 1
    }

    /**
     * Owner cree une invitation pour un aidant. L'aidant peut deja avoir
     * un compte (aidantUid non vide) ou pas (aidantUid="", on stocke
     * juste l'email pour matching futur — implementation V2).
     *
     * Retourne Failure si :
     * - L'utilisateur n'est pas connecte
     * - Il a deja FREE_AIDANT_LIMIT aidants actifs (modele freemium V1)
     * - L'invitation existe deja
     */
    suspend fun inviteAidant(
        aidantUid: String,
        aidantEmail: String,
        aidantNom: String,
        relation: String
    ): Result<Unit> = runCatching {
        val ownerUid = authRepository.currentUserId
            ?: throw IllegalStateException("Non connecte")
        val ownerProfile = authRepository.getCurrentUserProfile()
        val ownerNom = ownerProfile?.nomComplet?.ifBlank { ownerProfile.email } ?: "Patient"

        // Verifie la limite freemium V1
        val existingActive = getMyAidantsList().count { it.isActive }
        if (existingActive >= FREE_AIDANT_LIMIT && aidantUid.isNotBlank()) {
            throw IllegalStateException(
                "Limite atteinte : version gratuite = $FREE_AIDANT_LIMIT aidant. " +
                "Passez en premium pour inviter d'autres proches."
            )
        }

        // Pour V1, on exige que l'aidantUid soit connu (l'aidant doit
        // deja avoir un compte DiaSmart). L'invitation par email pure
        // sera la V2.
        require(aidantUid.isNotBlank()) {
            "L'aidant doit deja avoir un compte DiaSmart. Demande-lui de s'inscrire."
        }
        require(ownerUid != aidantUid) { "Vous ne pouvez pas vous inviter vous-meme." }

        val docId = FamilyLink.docId(ownerUid, aidantUid)
        val existing = firestore.collection(COL_FAMILY).document(docId).get().await()
        if (existing.exists()) {
            val data = existing.data ?: emptyMap<String, Any?>()
            val activeAlready = data["isActive"] as? Boolean == true
            if (activeAlready) {
                throw IllegalStateException("Lien deja actif avec cet aidant.")
            }
            // Sinon on reactive : status PENDING → l'aidant doit ré-accepter
            firestore.collection(COL_FAMILY).document(docId)
                .update(mapOf(
                    "status" to FamilyLinkStatus.PENDING.name,
                    "isActive" to false,
                    "invitedAt" to Timestamp.now(),
                    "revokedAt" to null,
                    "relation" to relation,
                    "aidantNom" to aidantNom
                )).await()
            return@runCatching
        }

        val link = FamilyLink(
            ownerUid = ownerUid,
            aidantUid = aidantUid,
            ownerNom = ownerNom,
            aidantNom = aidantNom,
            aidantEmail = aidantEmail,
            relation = relation,
            status = FamilyLinkStatus.PENDING,
            isActive = false
        )
        firestore.collection(COL_FAMILY).document(docId).set(link.toMap()).await()
    }

    /**
     * Aidant accepte l'invitation. status → ACCEPTED, isActive → true.
     */
    suspend fun acceptInvitation(ownerUid: String): Result<Unit> = runCatching {
        val aidantUid = authRepository.currentUserId
            ?: throw IllegalStateException("Non connecte")
        val docId = FamilyLink.docId(ownerUid, aidantUid)
        firestore.collection(COL_FAMILY).document(docId)
            .update(mapOf(
                "status" to FamilyLinkStatus.ACCEPTED.name,
                "isActive" to true,
                "acceptedAt" to Timestamp.now(),
                "revokedBy" to null
            )).await()
    }

    /**
     * Aidant refuse l'invitation.
     */
    suspend fun rejectInvitation(ownerUid: String): Result<Unit> = runCatching {
        val aidantUid = authRepository.currentUserId
            ?: throw IllegalStateException("Non connecte")
        val docId = FamilyLink.docId(ownerUid, aidantUid)
        firestore.collection(COL_FAMILY).document(docId)
            .update(mapOf(
                "status" to FamilyLinkStatus.REJECTED.name,
                "isActive" to false,
                "revokedAt" to Timestamp.now(),
                "revokedBy" to "aidant"
            )).await()
    }

    /**
     * Owner revoque le lien avec un aidant.
     */
    suspend fun revokeAsOwner(aidantUid: String): Result<Unit> = runCatching {
        val ownerUid = authRepository.currentUserId
            ?: throw IllegalStateException("Non connecte")
        val docId = FamilyLink.docId(ownerUid, aidantUid)
        firestore.collection(COL_FAMILY).document(docId)
            .update(mapOf(
                "isActive" to false,
                "status" to FamilyLinkStatus.REJECTED.name,
                "revokedAt" to Timestamp.now(),
                "revokedBy" to "owner"
            )).await()
    }

    /**
     * Aidant se desabonne d'un owner.
     */
    suspend fun unlinkAsAidant(ownerUid: String): Result<Unit> = runCatching {
        val aidantUid = authRepository.currentUserId
            ?: throw IllegalStateException("Non connecte")
        val docId = FamilyLink.docId(ownerUid, aidantUid)
        firestore.collection(COL_FAMILY).document(docId)
            .update(mapOf(
                "isActive" to false,
                "status" to FamilyLinkStatus.REJECTED.name,
                "revokedAt" to Timestamp.now(),
                "revokedBy" to "aidant"
            )).await()
    }

    /**
     * Reactive un lien precedemment revoque (les 2 parties peuvent le faire).
     */
    suspend fun reactivateLink(otherUid: String): Result<Unit> = runCatching {
        val myUid = authRepository.currentUserId
            ?: throw IllegalStateException("Non connecte")
        val asOwner = FamilyLink.docId(myUid, otherUid)
        val asAidant = FamilyLink.docId(otherUid, myUid)
        val docRef = listOf(asOwner, asAidant).firstNotNullOfOrNull { id ->
            val snap = firestore.collection(COL_FAMILY).document(id).get().await()
            if (snap.exists()) firestore.collection(COL_FAMILY).document(id) else null
        } ?: throw IllegalStateException("Aucun lien existant a reactiver.")
        docRef.update(mapOf(
            "isActive" to true,
            "status" to FamilyLinkStatus.ACCEPTED.name,
            "acceptedAt" to Timestamp.now(),
            "revokedAt" to null,
            "revokedBy" to null
        )).await()
    }

    // ═══════════════════════════════════════════════════════════════
    //  Lectures (one-shot et flow)
    // ═══════════════════════════════════════════════════════════════

    /** Liste mes aidants (vue OWNER). */
    suspend fun getMyAidantsList(): List<FamilyLink> {
        val ownerUid = authRepository.currentUserId ?: return emptyList()
        return try {
            firestore.collection(COL_FAMILY)
                .whereEqualTo("ownerUid", ownerUid)
                .get().await()
                .documents.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    doc.data?.let { FamilyLink.fromMap(it as Map<String, Any?>) }
                }
        } catch (e: Exception) {
            Log.w(TAG, "getMyAidantsList failed: ${e.message}")
            emptyList()
        }
    }

    /** Flow temps reel des aidants (vue OWNER). */
    fun getMyAidantsFlow(): Flow<List<FamilyLink>> = callbackFlow {
        val ownerUid = authRepository.currentUserId
        if (ownerUid.isNullOrBlank()) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = firestore.collection(COL_FAMILY)
            .whereEqualTo("ownerUid", ownerUid)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    doc.data?.let { FamilyLink.fromMap(it as Map<String, Any?>) }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Flow temps reel des owners pour qui je suis aidant. */
    fun getMyOwnersFlow(): Flow<List<FamilyLink>> = callbackFlow {
        val aidantUid = authRepository.currentUserId
        if (aidantUid.isNullOrBlank()) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = firestore.collection(COL_FAMILY)
            .whereEqualTo("aidantUid", aidantUid)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    doc.data?.let { FamilyLink.fromMap(it as Map<String, Any?>) }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Recherche d'un utilisateur par email pour l'inviter en tant qu'aidant.
     * V1 : exige que la personne ait deja un compte DiaSmart.
     */
    suspend fun findUserByEmail(email: String): com.diabeto.data.model.UserProfile? {
        val clean = email.trim().lowercase()
        if (clean.isBlank() || !clean.contains("@")) return null
        return try {
            val snap = firestore.collection("users")
                .whereEqualTo("email", clean)
                .limit(1).get().await()
            snap.documents.firstOrNull()?.let { doc ->
                @Suppress("UNCHECKED_CAST")
                doc.data?.let { com.diabeto.data.model.UserProfile.fromMap(it as Map<String, Any?>) }?.copy(uid = doc.id)
            }
        } catch (e: Exception) {
            Log.w(TAG, "findUserByEmail failed: ${e.message}")
            null
        }
    }
}
