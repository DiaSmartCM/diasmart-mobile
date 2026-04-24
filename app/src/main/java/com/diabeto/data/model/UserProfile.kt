package com.diabeto.data.model

import com.google.firebase.Timestamp
import kotlin.math.ln
import kotlin.math.max

/**
 * Profil utilisateur stocké dans Firestore (/users/{uid})
 */
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val nom: String = "",
    val prenom: String = "",
    val role: UserRole = UserRole.PATIENT,
    val telephone: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    // Donnees morphometriques (PATIENT uniquement — synchro avec Room PatientEntity)
    val poids: Double? = null,
    val taille: Double? = null,
    val tourDeTaille: Double? = null,
    val masseGrasse: Double? = null,
    // Donnees professionnelles (MEDECIN uniquement)
    val specialite: String = "",
    val numeroOrdre: String = "",
    val structureSante: String = "",
    val anneesExperience: Int? = null,
    val modeConsultation: String = "", // TELECONSULTATION | CABINET | LES_DEUX
    val disponibilite: String = "",    // EN_LIGNE | INDISPONIBLE | SUR_RDV
    val joursGarde: String = "",       // ex: "Lun-Ven 8h-18h"
    val languesParlees: String = "",   // ex: "Francais, Anglais"
    // Notation (MEDECIN uniquement — agregees coté users pour lecture rapide)
    val ratingSum: Double = 0.0,       // Somme des notes recues (1..5 chacune)
    val reviewCount: Int = 0,          // Nombre d'avis recus
    val consultationCount: Int = 0,    // Nombre de consultations completees
    // Localisation (MEDECIN principalement, disponible aussi pour patient si besoin)
    val latitude: Double? = null,
    val longitude: Double? = null,
    val ville: String = "",            // ex: "Douala"
    val adresse: String = ""           // adresse lisible, ex: "Bonapriso, en face de la pharmacie X"
) {
    val nomComplet: String get() = "$prenom $nom".trim()

    /** Note moyenne sur 5, 0.0 si aucun avis. */
    val averageRating: Double
        get() = if (reviewCount > 0) ratingSum / reviewCount else 0.0

    /**
     * Score composite pour trier les medecins du meilleur au moins bon.
     * Ponderation bayesienne legere : une note 5/5 avec 1 avis < une note 4.5/5 avec 20 avis.
     * Formule : averageRating * ln(reviewCount+1) + 0.05 * consultationCount
     * - ln(1+N) attenue la domination des medecins avec peu d'avis
     * - consultationCount ajoute un petit bonus d'activite
     */
    val doctorScore: Double
        get() = averageRating * ln((reviewCount + 1).toDouble()) +
                0.05 * consultationCount.toDouble()

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "uid" to uid,
            "email" to email,
            "nom" to nom,
            "prenom" to prenom,
            "role" to role.name,
            "telephone" to telephone,
            "createdAt" to createdAt
        )
        // Patient
        poids?.let { map["poids"] = it }
        taille?.let { map["taille"] = it }
        tourDeTaille?.let { map["tourDeTaille"] = it }
        masseGrasse?.let { map["masseGrasse"] = it }
        // Medecin
        if (specialite.isNotBlank()) map["specialite"] = specialite
        if (numeroOrdre.isNotBlank()) map["numeroOrdre"] = numeroOrdre
        if (structureSante.isNotBlank()) map["structureSante"] = structureSante
        anneesExperience?.let { map["anneesExperience"] = it }
        if (modeConsultation.isNotBlank()) map["modeConsultation"] = modeConsultation
        if (disponibilite.isNotBlank()) map["disponibilite"] = disponibilite
        if (joursGarde.isNotBlank()) map["joursGarde"] = joursGarde
        if (languesParlees.isNotBlank()) map["languesParlees"] = languesParlees
        // Notation
        map["ratingSum"] = ratingSum
        map["reviewCount"] = reviewCount
        map["consultationCount"] = consultationCount
        // Localisation
        latitude?.let { map["latitude"] = it }
        longitude?.let { map["longitude"] = it }
        if (ville.isNotBlank()) map["ville"] = ville
        if (adresse.isNotBlank()) map["adresse"] = adresse
        return map
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): UserProfile = UserProfile(
            uid = map["uid"] as? String ?: "",
            email = map["email"] as? String ?: "",
            nom = map["nom"] as? String ?: "",
            prenom = map["prenom"] as? String ?: "",
            role = try {
                UserRole.valueOf(map["role"] as? String ?: "PATIENT")
            } catch (e: Exception) {
                UserRole.PATIENT
            },
            telephone = map["telephone"] as? String ?: "",
            createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now(),
            poids = (map["poids"] as? Number)?.toDouble(),
            taille = (map["taille"] as? Number)?.toDouble(),
            tourDeTaille = (map["tourDeTaille"] as? Number)?.toDouble(),
            masseGrasse = (map["masseGrasse"] as? Number)?.toDouble(),
            specialite = map["specialite"] as? String ?: "",
            numeroOrdre = map["numeroOrdre"] as? String ?: "",
            structureSante = map["structureSante"] as? String ?: "",
            anneesExperience = (map["anneesExperience"] as? Number)?.toInt(),
            modeConsultation = map["modeConsultation"] as? String ?: "",
            disponibilite = map["disponibilite"] as? String ?: "",
            joursGarde = map["joursGarde"] as? String ?: "",
            languesParlees = map["languesParlees"] as? String ?: "",
            ratingSum = (map["ratingSum"] as? Number)?.toDouble() ?: 0.0,
            reviewCount = (map["reviewCount"] as? Number)?.toInt() ?: 0,
            consultationCount = (map["consultationCount"] as? Number)?.toInt() ?: 0,
            latitude = (map["latitude"] as? Number)?.toDouble(),
            longitude = (map["longitude"] as? Number)?.toDouble(),
            ville = map["ville"] as? String ?: "",
            adresse = map["adresse"] as? String ?: ""
        )
    }
}

enum class UserRole {
    PATIENT, MEDECIN
}

/** Helper Haversine pour calculer la distance entre 2 points en km. */
object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(lat1)) *
                kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).let { it * it }
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /** Formate une distance en km pour affichage ("320 m", "2.5 km", "45 km"). */
    fun formatDistance(km: Double): String {
        val safe = max(0.0, km)
        return when {
            safe < 1.0 -> "${(safe * 1000).toInt()} m"
            safe < 10.0 -> "%.1f km".format(safe)
            else -> "${safe.toInt()} km"
        }
    }
}
