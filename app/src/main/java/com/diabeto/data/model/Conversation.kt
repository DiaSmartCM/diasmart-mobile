package com.diabeto.data.model

import com.google.firebase.Timestamp

/**
 * Conversation entre un patient et un médecin
 * Chemin Firestore : /conversations/{conversationId}
 */
data class Conversation(
    val id: String = "",
    val patientId: String = "",
    val medecinId: String = "",
    val patientNom: String = "",
    val medecinNom: String = "",
    val dernierMessage: String = "",
    val dernierMessageAt: Timestamp = Timestamp.now(),
    val nonLusPatient: Int = 0,
    val nonLusMedecin: Int = 0,
    // v2.1.97 : indicateur de frappe (debounce cote client, cf. ConversationDetailViewModel).
    // Le *At est utilise comme garde-fou pour eviter un "en train d'ecrire..." bloque
    // si le dernier ecrivain a quitte l'app sans que le debounce n'ait pu ecrire false.
    val typingPatient: Boolean = false,
    val typingPatientAt: Timestamp = Timestamp(0, 0),
    val typingMedecin: Boolean = false,
    val typingMedecinAt: Timestamp = Timestamp(0, 0)
) {
    fun toMap(): Map<String, Any> = mapOf(
        "patientId" to patientId,
        "medecinId" to medecinId,
        "patientNom" to patientNom,
        "medecinNom" to medecinNom,
        "dernierMessage" to dernierMessage,
        "dernierMessageAt" to dernierMessageAt,
        "nonLusPatient" to nonLusPatient,
        "nonLusMedecin" to nonLusMedecin,
        "typingPatient" to typingPatient,
        "typingPatientAt" to typingPatientAt,
        "typingMedecin" to typingMedecin,
        "typingMedecinAt" to typingMedecinAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Conversation = Conversation(
            id = id,
            patientId = map["patientId"] as? String ?: "",
            medecinId = map["medecinId"] as? String ?: "",
            patientNom = map["patientNom"] as? String ?: "",
            medecinNom = map["medecinNom"] as? String ?: "",
            dernierMessage = map["dernierMessage"] as? String ?: "",
            dernierMessageAt = map["dernierMessageAt"] as? Timestamp ?: Timestamp.now(),
            nonLusPatient = (map["nonLusPatient"] as? Long)?.toInt() ?: 0,
            nonLusMedecin = (map["nonLusMedecin"] as? Long)?.toInt() ?: 0,
            typingPatient = map["typingPatient"] as? Boolean ?: false,
            typingPatientAt = map["typingPatientAt"] as? Timestamp ?: Timestamp(0, 0),
            typingMedecin = map["typingMedecin"] as? Boolean ?: false,
            typingMedecinAt = map["typingMedecinAt"] as? Timestamp ?: Timestamp(0, 0)
        )
    }
}
