package com.diabeto.data.model

import com.google.firebase.Timestamp

/**
 * Historique d'un rapport PDF genere par l'utilisateur.
 *
 * Chemin Firestore : /reports/{ownerUid}/items/{reportId}
 *
 * - `ownerUid`     : auteur du rapport (le patient qui a exporte ses donnees,
 *                    ou le medecin qui a redige une ordonnance / compte-rendu)
 * - `recipientUid` : destinataire (medecin lie pour un rapport patient ; patient
 *                    pour un rapport medecin) — facultatif si email seul
 * - `recipientEmail`: si l'envoi s'est fait par email, on memorise l'adresse
 * - `channels`     : canaux d'envoi reussis : "messagerie", "email"
 */
data class ReportRecord(
    val id: String = "",
    val ownerUid: String = "",
    val ownerNom: String = "",
    val recipientUid: String = "",
    val recipientNom: String = "",
    val recipientEmail: String = "",
    val type: String = TYPE_PATIENT,        // TYPE_PATIENT | TYPE_MEDECIN
    val periodLabel: String = "",
    val title: String = "",
    val fileUrl: String = "",
    val fileName: String = "",
    val sizeBytes: Long = 0,
    val channels: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "ownerUid" to ownerUid,
        "ownerNom" to ownerNom,
        "recipientUid" to recipientUid,
        "recipientNom" to recipientNom,
        "recipientEmail" to recipientEmail,
        "type" to type,
        "periodLabel" to periodLabel,
        "title" to title,
        "fileUrl" to fileUrl,
        "fileName" to fileName,
        "sizeBytes" to sizeBytes,
        "channels" to channels,
        "createdAt" to createdAt
    )

    companion object {
        const val TYPE_PATIENT = "PATIENT"
        const val TYPE_MEDECIN = "MEDECIN"
        const val CHANNEL_MESSAGERIE = "messagerie"
        const val CHANNEL_EMAIL = "email"

        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, map: Map<String, Any?>): ReportRecord = ReportRecord(
            id = id,
            ownerUid = map["ownerUid"] as? String ?: "",
            ownerNom = map["ownerNom"] as? String ?: "",
            recipientUid = map["recipientUid"] as? String ?: "",
            recipientNom = map["recipientNom"] as? String ?: "",
            recipientEmail = map["recipientEmail"] as? String ?: "",
            type = map["type"] as? String ?: TYPE_PATIENT,
            periodLabel = map["periodLabel"] as? String ?: "",
            title = map["title"] as? String ?: "",
            fileUrl = map["fileUrl"] as? String ?: "",
            fileName = map["fileName"] as? String ?: "",
            sizeBytes = (map["sizeBytes"] as? Number)?.toLong() ?: 0L,
            channels = (map["channels"] as? List<String>) ?: emptyList(),
            createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now()
        )
    }
}
