package com.diabeto.data.repository

import com.diabeto.data.model.DoctorReview
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestion des avis patients sur les medecins.
 *
 * Strategie d'aggregation :
 *  - /doctor_reviews/{reviewId} : 1 document par (doctor, patient)
 *  - /users/{doctorUid} : compteurs denormalises `ratingSum`, `reviewCount`
 *    mis a jour dans la meme transaction que l'ecriture de l'avis.
 *  - Cela permet de trier les medecins sans read-fanout (1 seule lecture users + stats deja la).
 *
 * Note : on utilise un ID deterministe (`${doctorUid}_${patientUid}`) pour que
 * chaque patient puisse editer/remplacer son avis a un medecin sans dupliquer.
 */
@Singleton
class DoctorReviewRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val reviews = firestore.collection("doctor_reviews")
    private val users = firestore.collection("users")

    /**
     * Ecrit ou remplace l'avis du patient courant sur un medecin.
     * Recalcule `ratingSum` et `reviewCount` atomiquement sur le doc users/{doctorUid}.
     */
    suspend fun submitReview(
        doctorUid: String,
        rating: Int,
        comment: String,
        patientNom: String = ""
    ): Result<Unit> = runCatching {
        require(rating in 1..5) { "rating doit etre entre 1 et 5" }
        val patientUid = auth.currentUser?.uid
            ?: throw IllegalStateException("Utilisateur non connecte")
        require(doctorUid.isNotBlank() && doctorUid != patientUid) { "doctorUid invalide" }

        val reviewId = DoctorReview.reviewId(doctorUid, patientUid)
        val reviewRef = reviews.document(reviewId)
        val doctorRef = users.document(doctorUid)
        val now = Timestamp.now()

        firestore.runTransaction { tx ->
            val prev = tx.get(reviewRef)
            val prevRating = if (prev.exists()) (prev.getLong("rating") ?: 0L).toInt() else 0
            val isNew = !prev.exists()

            // Delta a appliquer sur les compteurs du medecin
            val ratingDelta = (rating - prevRating).toDouble()
            val countDelta = if (isNew) 1L else 0L

            val review = DoctorReview(
                id = reviewId,
                doctorUid = doctorUid,
                patientUid = patientUid,
                patientNom = patientNom,
                rating = rating,
                comment = comment.take(500),
                createdAt = if (isNew) now else (prev.getTimestamp("createdAt") ?: now),
                updatedAt = now
            )
            tx.set(reviewRef, review.toMap())

            // Incrementer les compteurs coté medecin
            tx.update(doctorRef, mapOf(
                "ratingSum" to FieldValue.increment(ratingDelta),
                "reviewCount" to FieldValue.increment(countDelta)
            ))
            null
        }.await()
    }

    /** Retourne l'avis du patient courant sur ce medecin, ou null. */
    suspend fun getMyReviewFor(doctorUid: String): DoctorReview? {
        val patientUid = auth.currentUser?.uid ?: return null
        val reviewId = DoctorReview.reviewId(doctorUid, patientUid)
        val snap = reviews.document(reviewId).get().await()
        if (!snap.exists()) return null
        @Suppress("UNCHECKED_CAST")
        return DoctorReview.fromMap(snap.data as Map<String, Any?>)
    }

    /** Liste les avis recents sur un medecin (max 50). */
    suspend fun getReviewsForDoctor(doctorUid: String, limit: Long = 50): List<DoctorReview> {
        val q = reviews
            .whereEqualTo("doctorUid", doctorUid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
        val snap = q.get().await()
        return snap.documents.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
            DoctorReview.fromMap(data)
        }
    }

    /**
     * Increment du compteur de consultations (a appeler apres une teleconsultation reussie
     * ou la validation d'un RDV). Pas d'ecriture si le medecin n'existe pas.
     */
    suspend fun incrementConsultationCount(doctorUid: String): Result<Unit> = runCatching {
        if (doctorUid.isBlank()) return@runCatching
        users.document(doctorUid)
            .update("consultationCount", FieldValue.increment(1L))
            .await()
    }
}
