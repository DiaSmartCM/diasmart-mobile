package com.diabeto.data.repository

import com.diabeto.domain.ObjectifTension
import com.diabeto.domain.ReglesTension
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Objectif de tension personnel d'un patient (objectifs_tension/{patientUid}).
 * Fixe par le medecin lie ou un soignant de l'etablissement, lu par le patient.
 */
@Singleton
class ObjectifTensionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {
    private fun doc(patientUid: String) = firestore.collection("objectifs_tension").document(patientUid)

    suspend fun lire(patientUid: String): ObjectifTension? = runCatching {
        val d = doc(patientUid).get().await()
        val s = (d.get("systolique") as? Number)?.toInt()
        val p = (d.get("diastolique") as? Number)?.toInt()
        if (s != null && p != null) ObjectifTension(s, p, d.getString("auteurNom").orEmpty()) else null
    }.getOrNull()

    /** Objectif du patient connecte. */
    suspend fun lireMien(): ObjectifTension? = authRepository.currentUserId?.let { lire(it) }

    suspend fun fixer(patientUid: String, systolique: Int, diastolique: Int): Result<Unit> = runCatching {
        require(ReglesTension.objectifValide(systolique, diastolique)) {
            "Objectif invalide : PAS entre 100 et 170, PAD entre 60 et 110."
        }
        val uid = authRepository.currentUserId ?: error("Non connecté")
        val nom = runCatching { authRepository.getCurrentUserProfile()?.nomComplet }.getOrNull().orEmpty().take(100)
        doc(patientUid).set(mapOf(
            "systolique" to systolique,
            "diastolique" to diastolique,
            "auteurUid" to uid,
            "auteurNom" to nom,
            "majAt" to System.currentTimeMillis()
        )).await()
    }

    suspend fun retirer(patientUid: String): Result<Unit> = runCatching { doc(patientUid).delete().await() }
}
