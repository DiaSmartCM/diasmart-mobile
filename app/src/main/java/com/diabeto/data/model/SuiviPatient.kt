package com.diabeto.data.model

import java.time.LocalDate
import java.time.LocalDateTime

// Reste dans le module Android tant que les dates utilisent java.time ;
// les autres modeles de l'etablissement sont dans le module commun (shared).

/** Ligne du tableau de bord : un patient inscrit et ses indicateurs. */
data class SuiviPatient(
    val uid: String,
    val nom: String,
    val inscritAt: Long,
    val derniereMesure: LocalDateTime?,
    val nbMesures30j: Int,
    val moyenne30j: Double?,
    val nbHypos30j: Int,          // < 70 mg/dL
    val nbHyposSeveres30j: Int,   // < 54 mg/dL
    val derniereHbA1c: Double?,
    val dateHbA1c: LocalDate?,
    val hba1cEstimee: Boolean,
    val priorite: PrioriteSuivi,
    val raisons: List<String>,
    val perduDeVue: Boolean,
    val tensionMoyenne30j: String? = null,   // ex. "145/92"
    val derniereTension: String? = null
)
