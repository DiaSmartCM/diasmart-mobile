package com.diabeto.data.repository

import com.diabeto.data.model.RollyValidation
import com.diabeto.data.model.UserProfile
import com.diabeto.data.model.UserRole
import com.diabeto.data.model.ValidationStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val COL_VALIDATIONS = "validations"
    }

    /**
     * Crée une validation en attente
     */
    suspend fun createValidation(
        question: String,
        rollyResponse: String,
        medecin: UserProfile
    ): String {
        val uid = authRepository.currentUserId ?: throw IllegalStateException("Non connecté")
        val profile = authRepository.getCurrentUserProfile()
        val validation = RollyValidation(
            patientUid = uid,
            patientNom = profile?.nomComplet ?: "Patient",
            medecinUid = medecin.uid,
            medecinNom = medecin.nomComplet,
            question = question,
            rollyResponse = rollyResponse,
            status = ValidationStatus.PENDING,
            createdAt = Timestamp.now()
        )
        val ref = firestore.collection(COL_VALIDATIONS).add(validation.toMap()).await()
        return ref.id
    }

    /**
     * Flow des validations pour l'utilisateur courant.
     *
     * IMPORTANT : on n'utilise PAS .orderBy("createdAt") au niveau Firestore pour
     * eviter de dependre d'un index composite (medecinUid/patientUid + createdAt).
     * Le tri est fait cote client. En cas d'erreur Firestore (permissions,
     * reseau...), on emet une liste vide au lieu de fermer le flow avec
     * exception, ce qui faisait crasher l'ecran cote medecin.
     */
    fun getValidationsFlow(): Flow<List<RollyValidation>> = callbackFlow {
        val uid = authRepository.currentUserId ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val profile = try {
            authRepository.getCurrentUserProfile()
        } catch (e: Exception) {
            null
        }
        val field = if (profile?.role == UserRole.MEDECIN) "medecinUid" else "patientUid"

        val listener = firestore.collection(COL_VALIDATIONS)
            .whereEqualTo(field, uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    // Ne pas fermer le flow : emettre une liste vide pour eviter le crash UI
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val validations = snap?.documents?.mapNotNull { doc ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        doc.data?.let { RollyValidation.fromMap(doc.id, it as Map<String, Any?>) }
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.createdAt.seconds } ?: emptyList()
                trySend(validations)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Valider ou rejeter une réponse ROLLY (médecin)
     */
    suspend fun validateResponse(validationId: String, approved: Boolean, comment: String) {
        firestore.collection(COL_VALIDATIONS).document(validationId).update(
            mapOf(
                "status" to if (approved) "validated" else "rejected",
                "medecinComment" to comment,
                "validatedAt" to Timestamp.now()
            )
        ).await()
    }

    /**
     * Récupère la liste des médecins disponibles
     */
    suspend fun getAvailableDoctors(): List<UserProfile> {
        return try {
            val snap = firestore.collection("users")
                .whereIn("role", listOf("MEDECIN", "medecin"))
                .get().await()
            snap.documents.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                doc.data?.let { UserProfile.fromMap(it as Map<String, Any?>) }?.copy(uid = doc.id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
