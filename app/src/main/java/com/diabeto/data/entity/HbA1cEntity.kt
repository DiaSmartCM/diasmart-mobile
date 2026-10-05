package com.diabeto.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diabeto.domain.ReglesGlycemie
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Entité HbA1c (Hémoglobine Glyquée) pour Room Database
 *
 * L'HbA1c reflète la glycémie moyenne des 2-3 derniers mois.
 * C'est l'indicateur clé du contrôle du diabète.
 *
 * Cibles recommandées (ADA 2024) :
 *   - Adultes : < 7.0%
 *   - Personnes âgées / comorbidités : < 8.0%
 *   - Grossesse : < 6.0%
 *   - Sans hypoglycémies fréquentes : < 6.5%
 */
@Entity(
    tableName = "hba1c_lectures",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["dateMesure"])
    ]
)
data class HbA1cEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patientId: Long,
    val valeur: Double, // Pourcentage (ex: 6.8)
    val dateMesure: LocalDate,
    val laboratoire: String = "",
    val notes: String = "",
    val estEstimation: Boolean = false, // true si calculée à partir de la glycémie moyenne
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastModified: Long = System.currentTimeMillis()
) {
    /**
     * Interprétation clinique de la valeur HbA1c
     */
    fun getInterpretation(): HbA1cInterpretation = ReglesGlycemie.interpreterHbA1c(valeur)

    /**
     * Glycémie moyenne estimée (eAG) à partir de l'HbA1c
     * Formule ADAG : eAG (mg/dL) = 28.7 × HbA1c − 46.7
     */
    fun getGlycemieMoyenneEstimee(): Double = ReglesGlycemie.glycemieMoyenneDepuisHbA1c(valeur)

    companion object {
        /**
         * Estimer l'HbA1c à partir de la glycémie moyenne (mg/dL)
         * Formule inverse ADAG : HbA1c = (eAG + 46.7) / 28.7
         */
        fun estimerDepuisGlycemieMoyenne(glycemieMoyenne: Double): Double {
            return ReglesGlycemie.hba1cDepuisGlycemieMoyenne(glycemieMoyenne)
        }
    }
}
