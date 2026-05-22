package com.diabeto.data.repository

import android.util.Log
import com.diabeto.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.1.47 : repository extrait de `RendezVousViewModel` qui bypassait
 * FirebaseFirestore directement pour la collection `rdv_shared/{patientUid}
 * /rendezvous/{rdvId}`.
 *
 * Encapsule egalement la recherche des medecins disponibles (`users` where
 * role = MEDECIN) historiquement faite par le VM.
 */
@Singleton
class RdvSharedRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "RdvSharedRepo"
        private const val COL_RDV_SHARED = "rdv_shared"
        private const val SUB_RDV = "rendezvous"
        private const val COL_USERS = "users"
    }

    /**
     * Lit tous les RDV partages avec un patient donne (vue patient).
     * Renvoie chaque doc en Map (le mapping en RendezVousPatientItem est
     * fait cote VM pour eviter de coupler le model UI au repo).
     */
    suspend fun getPatientRendezVous(patientUid: String): List<Pair<String, Map<String, Any?>>> {
        return try {
            val docs = firestore.collection(COL_RDV_SHARED)
                .document(patientUid)
                .collection(SUB_RDV)
                .get().await()
            docs.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                doc.id to data
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load patient RDV", e)
            emptyList()
        }
    }

    /**
     * Cree ou remplace un RDV partage avec un patient (cote medecin).
     */
    suspend fun setSharedRdv(
        patientUid: String,
        rdvId: String,
        data: Map<String, Any?>
    ): Result<Unit> = runCatching {
        firestore.collection(COL_RDV_SHARED)
            .document(patientUid)
            .collection(SUB_RDV)
            .document(rdvId)
            .set(data).await()
    }

    /**
     * Met a jour un champ specifique d'un RDV partage (ex : `estConfirme`).
     */
    suspend fun updateSharedRdvField(
        patientUid: String,
        rdvId: String,
        field: String,
        value: Any?
    ): Result<Unit> = runCatching {
        firestore.collection(COL_RDV_SHARED)
            .document(patientUid)
            .collection(SUB_RDV)
            .document(rdvId)
            .update(field, value).await()
    }

    /**
     * Supprime un RDV partage.
     */
    suspend fun deleteSharedRdv(patientUid: String, rdvId: String): Result<Unit> = runCatching {
        firestore.collection(COL_RDV_SHARED)
            .document(patientUid)
            .collection(SUB_RDV)
            .document(rdvId)
            .delete().await()
    }

    /**
     * Liste les medecins disponibles sur la plateforme (cote patient pour
     * book a appointment).
     */
    suspend fun getAvailableMedecins(): List<UserProfile> {
        return try {
            val snap = firestore.collection(COL_USERS)
                .whereEqualTo("role", "MEDECIN")
                .get().await()
            snap.documents.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                doc.data?.let { UserProfile.fromMap(it as Map<String, Any?>) }?.copy(uid = doc.id)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load available medecins", e)
            emptyList()
        }
    }
}
