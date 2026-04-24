package com.diabeto.data.model

import com.google.firebase.Timestamp

/**
 * Avis d'un patient sur un medecin.
 * Stocke dans Firestore : /doctor_reviews/{reviewId}
 * Un patient ne peut laisser qu'un seul avis par medecin (reviewId = "${doctorUid}_${patientUid}").
 */
data class DoctorReview(
    val id: String = "",             // "${doctorUid}_${patientUid}"
    val doctorUid: String = "",
    val patientUid: String = "",
    val patientNom: String = "",     // denormalise pour eviter un second read
    val rating: Int = 5,             // 1..5
    val comment: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "doctorUid" to doctorUid,
        "patientUid" to patientUid,
        "patientNom" to patientNom,
        "rating" to rating,
        "comment" to comment,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun reviewId(doctorUid: String, patientUid: String) = "${doctorUid}_${patientUid}"

        fun fromMap(map: Map<String, Any?>): DoctorReview = DoctorReview(
            id = map["id"] as? String ?: "",
            doctorUid = map["doctorUid"] as? String ?: "",
            patientUid = map["patientUid"] as? String ?: "",
            patientNom = map["patientNom"] as? String ?: "",
            rating = (map["rating"] as? Number)?.toInt() ?: 5,
            comment = map["comment"] as? String ?: "",
            createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now(),
            updatedAt = map["updatedAt"] as? Timestamp ?: Timestamp.now()
        )
    }
}
