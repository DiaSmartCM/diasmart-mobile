package com.diabeto.data.repository

import com.diabeto.data.model.AppointmentRequestStatus
import com.diabeto.data.model.RendezVousRequest
import com.diabeto.data.model.UserRole
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour les demandes de rendez-vous patient -> medecin.
 * Collection : /rdv_requests/{id}
 */
@Singleton
class AppointmentRequestRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val doctorReviewRepository: DoctorReviewRepository
) {
    companion object {
        private const val COL_REQUESTS = "rdv_requests"
    }

    /**
     * Patient cree une demande de RDV pour un medecin specifique.
     */
    suspend fun createRequest(
        medecinUid: String,
        medecinNom: String,
        dateHeureIso: String,
        dureeMinutes: Int,
        motif: String,
        type: String = "CONSULTATION"
    ): String {
        val patientUid = authRepository.currentUserId
            ?: throw IllegalStateException("Non connecte")
        val profile = authRepository.getCurrentUserProfile()
        val request = RendezVousRequest(
            patientUid = patientUid,
            patientNom = profile?.nomComplet ?: "Patient",
            medecinUid = medecinUid,
            medecinNom = medecinNom,
            dateHeureSouhaitee = dateHeureIso,
            dureeMinutes = dureeMinutes,
            motif = motif,
            type = type,
            status = AppointmentRequestStatus.PENDING,
            createdAt = Timestamp.now()
        )
        val ref = firestore.collection(COL_REQUESTS).add(request.toMap()).await()
        return ref.id
    }

    /**
     * Flux des demandes pour l'utilisateur courant.
     * - PATIENT : ses propres demandes
     * - MEDECIN : les demandes qui lui sont adressees
     *
     * Tri client pour eviter la dependance a un index composite.
     * En cas d'erreur, emet une liste vide (ne crashe pas l'UI).
     */
    fun getRequestsFlow(): Flow<List<RendezVousRequest>> = callbackFlow {
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

        val listener = firestore.collection(COL_REQUESTS)
            .whereEqualTo(field, uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        doc.data?.let { RendezVousRequest.fromMap(doc.id, it as Map<String, Any?>) }
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.createdAt.seconds } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Medecin accepte une demande. Met a jour le status et ecrit
     * un RDV dans rdv_shared/{patientUid}/rendezvous/{requestId}.
     */
    suspend fun acceptRequest(requestId: String, reponse: String = "") {
        val doc = firestore.collection(COL_REQUESTS).document(requestId).get().await()
        val data = doc.data ?: throw IllegalStateException("Demande introuvable")
        val request = RendezVousRequest.fromMap(requestId, data)

        firestore.collection(COL_REQUESTS).document(requestId).update(
            mapOf(
                "status" to AppointmentRequestStatus.ACCEPTED.name,
                "medecinReponse" to reponse,
                "updatedAt" to Timestamp.now()
            )
        ).await()

        // Ecrire le RDV dans rdv_shared/{patientUid}/rendezvous/
        val rdvData = mapOf(
            "titre" to request.motif.ifBlank { "Consultation" },
            "dateHeure" to request.dateHeureSouhaitee,
            "dureeMinutes" to request.dureeMinutes,
            "type" to request.type,
            "lieu" to "",
            "notes" to request.motif,
            "estConfirme" to true,
            "medecinNom" to request.medecinNom,
            "medecinUid" to request.medecinUid,
            "createdAt" to Timestamp.now()
        )
        firestore.collection("rdv_shared")
            .document(request.patientUid)
            .collection("rendezvous")
            .document(requestId)
            .set(rdvData).await()

        // Compteur de consultations cote medecin (best-effort, ne bloque pas l'acceptation).
        runCatching { doctorReviewRepository.incrementConsultationCount(request.medecinUid) }
    }

    /**
     * Medecin refuse une demande.
     */
    suspend fun rejectRequest(requestId: String, reponse: String = "") {
        firestore.collection(COL_REQUESTS).document(requestId).update(
            mapOf(
                "status" to AppointmentRequestStatus.REJECTED.name,
                "medecinReponse" to reponse,
                "updatedAt" to Timestamp.now()
            )
        ).await()
    }

    /**
     * Patient annule sa demande (suppression).
     */
    suspend fun cancelRequest(requestId: String) {
        firestore.collection(COL_REQUESTS).document(requestId).delete().await()
    }
}
