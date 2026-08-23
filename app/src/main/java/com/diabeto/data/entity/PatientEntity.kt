package com.diabeto.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

/**
 * Entité Patient pour Room Database
 */
@Entity(
    tableName = "patients",
    indices = [
        Index(value = ["nom", "prenom"]),
        Index(value = ["email"])
    ]
)
data class PatientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /**
     * v2.1.82 : compte proprietaire du dossier.
     *
     * La base locale appartenait a l'appareil et non au compte connecte : sur
     * un telephone ou l'application avait servi a un patient, un medecin qui
     * s'y connectait ensuite retrouvait ses dossiers et sa glycemie. Chaque
     * dossier porte desormais l'identifiant de son createur, et toutes les
     * lectures filtrent dessus.
     *
     * Chaine vide = dossier orphelin, cree avant le cloisonnement. Il reste
     * stocke mais n'apparait dans aucune requete tant qu'il n'a pas ete
     * reattribue — on ne devine pas a qui appartenait une donnee de sante.
     */
    val ownerUid: String = "",
    val nom: String,
    val prenom: String,
    val dateNaissance: LocalDate,
    val sexe: Sexe,
    val telephone: String = "",
    val email: String = "",
    val adresse: String = "",
    val typeDiabete: TypeDiabete,
    val dateDiagnostic: LocalDate? = null,
    val notes: String = "",
    // ── Données corporelles ──
    val poids: Double? = null,         // kg
    val taille: Double? = null,        // cm
    val tourDeTaille: Double? = null,   // cm
    val masseGrasse: Double? = null,    // % de masse grasse
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val lastModified: Long = System.currentTimeMillis()
) {
    val age: Int
        get() = Period.between(dateNaissance, LocalDate.now()).years

    val nomComplet: String
        get() = "$prenom $nom"

    val initiales: String
        get() = "${prenom.firstOrNull() ?: ' '}${nom.firstOrNull() ?: ' '}"

    /** IMC calculé (kg/m²) — null si poids ou taille manquant */
    val imc: Double?
        get() {
            val p = poids ?: return null
            val t = taille ?: return null
            if (t <= 0) return null
            return p / ((t / 100.0) * (t / 100.0))
        }

    /** Catégorie IMC selon OMS */
    val categorieImc: String?
        get() = imc?.let { v ->
            when {
                v < 18.5 -> "Insuffisance pondérale"
                v < 25.0 -> "Poids normal"
                v < 30.0 -> "Surpoids"
                v < 35.0 -> "Obésité classe I"
                v < 40.0 -> "Obésité classe II"
                else -> "Obésité classe III"
            }
        }

    /** Risque métabolique basé sur le tour de taille (IDF) */
    val risqueTourDeTaille: String?
        get() {
            val tdt = tourDeTaille ?: return null
            return when (sexe) {
                Sexe.HOMME -> when {
                    tdt < 94 -> "Normal"
                    tdt < 102 -> "Risque accru"
                    else -> "Risque élevé"
                }
                Sexe.FEMME -> when {
                    tdt < 80 -> "Normal"
                    tdt < 88 -> "Risque accru"
                    else -> "Risque élevé"
                }
                Sexe.AUTRE -> when {
                    tdt < 88 -> "Normal"
                    tdt < 102 -> "Risque accru"
                    else -> "Risque élevé"
                }
            }
        }
}

enum class Sexe {
    HOMME, FEMME, AUTRE
}

enum class TypeDiabete {
    TYPE_1, TYPE_2, GESTATIONNEL, PRE_DIABETE
}
