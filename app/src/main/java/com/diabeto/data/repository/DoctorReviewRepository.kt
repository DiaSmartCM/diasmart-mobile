package com.diabeto.data.repository

import android.util.Log
import com.diabeto.data.model.DoctorReview
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
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

    companion object {
        private const val TAG = "DoctorReviewRepo"
        private const val NOTIFY_REVIEW_URL =
            "https://website-omega-umber-20.vercel.app/api/notify-review"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // Scope detache pour le fire-and-forget de la notification (ne bloque pas le submit)
    private val notifyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        // Notification FCM au medecin (fire-and-forget, non bloquant)
        val wasUpdate = runCatching {
            val snap = reviewRef.get().await()
            (snap.getTimestamp("createdAt")?.toDate()?.time ?: 0L) <
                (snap.getTimestamp("updatedAt")?.toDate()?.time ?: 0L)
        }.getOrDefault(false)
        notifyReviewAsync(
            doctorUid = doctorUid,
            rating = rating,
            comment = comment.take(200),
            patientNom = patientNom,
            isUpdate = wasUpdate
        )
    }

    /**
     * Poste un PUSH FCM via l'endpoint Vercel /api/notify-review.
     * Non bloquant et silencieux : si ca echoue, l'avis est deja ecrit, pas grave.
     */
    private fun notifyReviewAsync(
        doctorUid: String,
        rating: Int,
        comment: String,
        patientNom: String,
        isUpdate: Boolean
    ) {
        notifyScope.launch {
            try {
                val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
                    ?: return@launch
                val body = JSONObject().apply {
                    put("doctorUid", doctorUid)
                    put("rating", rating)
                    put("comment", comment)
                    put("patientNom", patientNom)
                    put("isUpdate", isUpdate)
                }.toString()
                val req = Request.Builder()
                    .url(NOTIFY_REVIEW_URL)
                    .addHeader("Authorization", "Bearer $idToken")
                    .post(body.toRequestBody(JSON_MEDIA))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    Log.d(TAG, "notify-review HTTP ${resp.code}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "notify-review failed (non-blocking): ${e.message}")
            }
        }
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

    /**
     * Increment du compteur de consultations pour le medecin implique dans un appel donne.
     * Lit calls/{callId}, identifie celui des deux UIDs (caller/callee) qui est MEDECIN
     * et incremente son `consultationCount`. Silencieux en cas d'erreur.
     */
    suspend fun incrementConsultationForCall(callId: String): Result<Unit> = runCatching {
        if (callId.isBlank()) return@runCatching
        val callDoc = firestore.collection("calls").document(callId).get().await()
        val callerUid = callDoc.getString("callerUid").orEmpty()
        val calleeUid = callDoc.getString("calleeUid").orEmpty()
        val candidates = listOf(callerUid, calleeUid).filter { it.isNotBlank() }
        for (uid in candidates) {
            val userSnap = users.document(uid).get().await()
            val role = userSnap.getString("role")
            if (role == "MEDECIN") {
                users.document(uid)
                    .update("consultationCount", FieldValue.increment(1L))
                    .await()
                Log.d(TAG, "consultationCount++ for doctor $uid (call $callId)")
                break
            }
        }
    }
}
