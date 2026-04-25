package com.diabeto.data.model

import com.google.firebase.Timestamp

/**
 * Message dans une conversation
 * Chemin Firestore : /conversations/{conversationId}/messages/{messageId}
 */
data class Message(
    val id: String = "",
    val envoyeurId: String = "",
    val envoyeurNom: String = "",
    val contenu: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val lu: Boolean = false,
    val estIA: Boolean = false,
    // ── Pieces jointes (rapports PDF) ──
    val attachmentUrl: String = "",
    val attachmentName: String = "",
    val attachmentType: String = "" // ex: "application/pdf"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "envoyeurId" to envoyeurId,
        "envoyeurNom" to envoyeurNom,
        "contenu" to contenu,
        "timestamp" to timestamp,
        "lu" to lu,
        "estIA" to estIA,
        "attachmentUrl" to attachmentUrl,
        "attachmentName" to attachmentName,
        "attachmentType" to attachmentType
    )

    val hasAttachment: Boolean
        get() = attachmentUrl.isNotBlank()

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Message = Message(
            id = id,
            envoyeurId = map["envoyeurId"] as? String ?: "",
            envoyeurNom = map["envoyeurNom"] as? String ?: "",
            contenu = map["contenu"] as? String ?: "",
            timestamp = map["timestamp"] as? Timestamp ?: Timestamp.now(),
            lu = map["lu"] as? Boolean ?: false,
            estIA = map["estIA"] as? Boolean ?: false,
            attachmentUrl = map["attachmentUrl"] as? String ?: "",
            attachmentName = map["attachmentName"] as? String ?: "",
            attachmentType = map["attachmentType"] as? String ?: ""
        )
    }
}

/**
 * Message dans le chatbot IA
 * Persisté dans Firestore : /rolly_history/{uid}/messages/{id}
 */
data class ChatbotMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val contenu: String = "",
    val estUtilisateur: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val enChargement: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "contenu" to contenu,
        "estUtilisateur" to estUtilisateur,
        "timestamp" to timestamp
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): ChatbotMessage = ChatbotMessage(
            id = id,
            contenu = map["contenu"] as? String ?: "",
            estUtilisateur = map["estUtilisateur"] as? Boolean ?: true,
            timestamp = (map["timestamp"] as? Long)
                ?: (map["timestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time
                ?: System.currentTimeMillis(),
            enChargement = false
        )
    }
}
