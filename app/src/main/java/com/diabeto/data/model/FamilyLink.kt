package com.diabeto.data.model

import com.google.firebase.Timestamp

/**
 * Lien familial : un patient ("owner") invite un proche ("aidant") a suivre
 * ses donnees medicales. L'aidant a un acces LECTURE SEULE (jamais
 * d'ecriture, jamais de modification). Si une urgence est detectee
 * (glycemie < 54 ou > 300 mg/dL, mots-cles urgence), l'aidant est notifie
 * par FCM.
 *
 * Stocke dans Firestore : `/family_links/{ownerUid}_{aidantUid}`
 *
 * Modele freemium (v2.1.48+) :
 * - Gratuit : 1 aidant actif maximum par owner
 * - Premium : 3+ aidants (a implementer apres seed)
 *
 * v2.1.48 — V1 : ne gere que le lien + invitation. Pas encore d'affichage
 * du dashboard owner cote aidant (V2). Pas encore de notifications urgences
 * propagees (V2).
 */
data class FamilyLink(
    /** UID du patient proprietaire des donnees (celui qui invite). */
    val ownerUid: String = "",
    /** UID de l'aidant (le proche : fils, conjoint, parent...). */
    val aidantUid: String = "",
    val ownerNom: String = "",
    val aidantNom: String = "",
    val aidantEmail: String = "",  // Email saisi a l'invitation, peut etre vide si invitation par autre canal
    /** "fils", "conjoint", "parent", "fratrie", "ami" — libre */
    val relation: String = "",
    val isActive: Boolean = false,
    val status: FamilyLinkStatus = FamilyLinkStatus.PENDING,
    val invitedAt: Timestamp = Timestamp.now(),
    val acceptedAt: Timestamp? = null,
    val revokedAt: Timestamp? = null,
    /** Permissions de l'aidant (V1 : full read seulement). */
    val canSeeGlucose: Boolean = true,
    val canSeeMeals: Boolean = true,
    val canSeeMedications: Boolean = true,
    val canReceiveEmergencyAlerts: Boolean = true,
    /** "owner" (owner a revoque) ou "aidant" (aidant s'est desabonne). */
    val revokedBy: String? = null
) {
    val documentId: String get() = "${ownerUid}_${aidantUid}"

    fun toMap(): Map<String, Any?> = mapOf(
        "ownerUid" to ownerUid,
        "aidantUid" to aidantUid,
        "ownerNom" to ownerNom,
        "aidantNom" to aidantNom,
        "aidantEmail" to aidantEmail,
        "relation" to relation,
        "isActive" to isActive,
        "status" to status.name,
        "invitedAt" to invitedAt,
        "acceptedAt" to acceptedAt,
        "revokedAt" to revokedAt,
        "canSeeGlucose" to canSeeGlucose,
        "canSeeMeals" to canSeeMeals,
        "canSeeMedications" to canSeeMedications,
        "canReceiveEmergencyAlerts" to canReceiveEmergencyAlerts,
        "revokedBy" to revokedBy
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): FamilyLink = FamilyLink(
            ownerUid = map["ownerUid"] as? String ?: "",
            aidantUid = map["aidantUid"] as? String ?: "",
            ownerNom = map["ownerNom"] as? String ?: "",
            aidantNom = map["aidantNom"] as? String ?: "",
            aidantEmail = map["aidantEmail"] as? String ?: "",
            relation = map["relation"] as? String ?: "",
            isActive = map["isActive"] as? Boolean ?: false,
            status = try {
                FamilyLinkStatus.valueOf(map["status"] as? String ?: "PENDING")
            } catch (_: Exception) { FamilyLinkStatus.PENDING },
            invitedAt = map["invitedAt"] as? Timestamp ?: Timestamp.now(),
            acceptedAt = map["acceptedAt"] as? Timestamp,
            revokedAt = map["revokedAt"] as? Timestamp,
            canSeeGlucose = map["canSeeGlucose"] as? Boolean ?: true,
            canSeeMeals = map["canSeeMeals"] as? Boolean ?: true,
            canSeeMedications = map["canSeeMedications"] as? Boolean ?: true,
            canReceiveEmergencyAlerts = map["canReceiveEmergencyAlerts"] as? Boolean ?: true,
            revokedBy = map["revokedBy"] as? String
        )

        fun docId(ownerUid: String, aidantUid: String) = "${ownerUid}_${aidantUid}"
    }
}

enum class FamilyLinkStatus {
    PENDING,   // Owner a envoye l'invitation, en attente de l'aidant
    ACCEPTED,  // Aidant a accepte
    REJECTED   // Aidant a refuse OU lien revoque
}
