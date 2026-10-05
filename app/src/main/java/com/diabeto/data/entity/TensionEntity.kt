package com.diabeto.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diabeto.domain.CategorieTension
import com.diabeto.domain.ReglesTension
import java.time.LocalDateTime

/**
 * Mesure de tension arterielle (mmHg) et pouls, pour le suivi de
 * l'hypertension. Regles d'interpretation : ReglesTension (module commun).
 */
@Entity(
    tableName = "tension_lectures",
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
        Index(value = ["dateHeure"])
    ]
)
data class TensionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patientId: Long,
    val systolique: Int,
    val diastolique: Int,
    val pouls: Int? = null,
    val dateHeure: LocalDateTime,
    val notes: String = "",
    val lastModified: Long = System.currentTimeMillis()
) {
    fun categorie(): CategorieTension = ReglesTension.categorie(systolique, diastolique)
}
