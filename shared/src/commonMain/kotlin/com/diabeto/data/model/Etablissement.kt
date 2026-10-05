package com.diabeto.data.model

/**
 * Espace etablissement (offre B2B2C) : un centre de sante, un administrateur,
 * plusieurs soignants, et les patients que le centre suit.
 *
 * Firestore :
 *  - etablissements/{etabId}                      fiche du centre
 *  - etablissements/{etabId}/membres/{uid}        administrateur + soignants
 *  - etablissements/{etabId}/patients/{uid}       patients inscrits (par eux-memes, avec le code)
 *  - codes_invitation/{code}                      code -> etablissement (lecture par code seulement)
 *  - affiliations/{uid}                           l'etablissement de chaque compte (un seul a la fois)
 *
 * Le patient s'inscrit lui-meme avec le code : c'est son accord pour que les
 * soignants du centre lisent ses glycemies et ses HbA1c. Il peut quitter le
 * centre a tout moment depuis la meme page.
 */
data class Etablissement(
    val id: String = "",
    val nom: String = "",
    val ville: String = "",
    val adminUid: String = "",
    val codePatient: String = "",
    val codeSoignant: String = "",
    val createdAt: Long = 0L
) {
    companion object {
        fun fromMap(id: String, m: Map<String, Any?>) = Etablissement(
            id = id,
            nom = m["nom"] as? String ?: "",
            ville = m["ville"] as? String ?: "",
            adminUid = m["adminUid"] as? String ?: "",
            codePatient = m["codePatient"] as? String ?: "",
            codeSoignant = m["codeSoignant"] as? String ?: "",
            createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

enum class RoleEtablissement { ADMIN, SOIGNANT, PATIENT }

/** Le type d'un code d'invitation : pour un patient ou pour un soignant. */
enum class TypeCode { PATIENT, SOIGNANT }

data class MembreEtablissement(
    val uid: String = "",
    val nom: String = "",
    val role: RoleEtablissement = RoleEtablissement.SOIGNANT,
    val joinedAt: Long = 0L
) {
    companion object {
        fun fromMap(m: Map<String, Any?>) = MembreEtablissement(
            uid = m["uid"] as? String ?: "",
            nom = m["nom"] as? String ?: "",
            role = runCatching { RoleEtablissement.valueOf(m["role"] as? String ?: "") }
                .getOrDefault(RoleEtablissement.SOIGNANT),
            joinedAt = (m["joinedAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

data class PatientInscrit(
    val uid: String = "",
    val nom: String = "",
    val inscritAt: Long = 0L
) {
    companion object {
        fun fromMap(m: Map<String, Any?>) = PatientInscrit(
            uid = m["uid"] as? String ?: "",
            nom = m["nom"] as? String ?: "",
            inscritAt = (m["inscritAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

/** L'etablissement du compte connecte (document affiliations/{uid}). */
data class Affiliation(
    val etablissementId: String = "",
    val etablissementNom: String = "",
    val role: RoleEtablissement = RoleEtablissement.PATIENT
) {
    companion object {
        fun fromMap(m: Map<String, Any?>) = Affiliation(
            etablissementId = m["etablissementId"] as? String ?: "",
            etablissementNom = m["etablissementNom"] as? String ?: "",
            role = runCatching { RoleEtablissement.valueOf(m["role"] as? String ?: "") }
                .getOrDefault(RoleEtablissement.PATIENT)
        )
    }
}

/**
 * Niveau de priorite de suivi, calcule a partir des mesures partagees.
 * Ce n'est PAS un diagnostic : c'est une aide pour savoir quel patient
 * recontacter en premier. La decision reste au soignant.
 */
enum class PrioriteSuivi(val libelle: String) {
    HAUTE("A revoir en priorite"),
    MOYENNE("A surveiller"),
    BASSE("Stable"),
    INCONNUE("Pas assez de donnees")
}
