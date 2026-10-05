package com.diabeto.domain

import kotlinx.datetime.LocalDateTime
import kotlin.math.roundToInt

/** Une mesure de tension arterielle (mmHg) et le pouls (battements/min). */
data class MesureTension(
    val date: LocalDateTime,
    val systolique: Int,
    val diastolique: Int,
    val pouls: Int? = null
)

/**
 * Categories de tension (d'apres l'ISH 2020). Indication pour le patient
 * et aide au suivi pour le soignant : ce n'est pas un diagnostic.
 */
enum class CategorieTension(val libelle: String, val conseil: String) {
    BASSE("Tension basse", "Si vous avez des vertiges ou un malaise, asseyez-vous et parlez-en à votre soignant."),
    NORMALE("Normale", "Continuez ainsi."),
    NORMALE_HAUTE("Normale haute", "À surveiller : moins de sel, activité physique, et reprenez la mesure."),
    HTA_1("Hypertension (grade 1)", "Reprenez la mesure au repos. Si elle reste élevée, parlez-en à votre soignant."),
    HTA_2("Hypertension (grade 2)", "Parlez-en rapidement à votre soignant."),
    TRES_ELEVEE("Très élevée", "Reposez-vous 5 minutes et reprenez la mesure. Si elle reste aussi haute, ou en cas de mal de tête, douleur dans la poitrine ou trouble de la vue : consultez en urgence.")
}

object ReglesTension {
    const val SYS_MIN = 50
    const val SYS_MAX = 300
    const val DIA_MIN = 30
    const val DIA_MAX = 200

    fun valide(systolique: Int, diastolique: Int): Boolean =
        systolique in SYS_MIN..SYS_MAX && diastolique in DIA_MIN..DIA_MAX && systolique > diastolique

    /** La categorie la plus haute entre la systolique et la diastolique. */
    fun categorie(systolique: Int, diastolique: Int): CategorieTension = when {
        systolique >= 180 || diastolique >= 110 -> CategorieTension.TRES_ELEVEE
        systolique >= 160 || diastolique >= 100 -> CategorieTension.HTA_2
        systolique >= 140 || diastolique >= 90 -> CategorieTension.HTA_1
        systolique >= 130 || diastolique >= 85 -> CategorieTension.NORMALE_HAUTE
        systolique < 90 || diastolique < 60 -> CategorieTension.BASSE
        else -> CategorieTension.NORMALE
    }

    /** Moyenne arrondie (systolique, diastolique), ou null sans mesure. */
    fun moyenne(mesures: List<MesureTension>): Pair<Int, Int>? =
        if (mesures.isEmpty()) null
        else mesures.map { it.systolique }.average().roundToInt() to mesures.map { it.diastolique }.average().roundToInt()

    fun texte(systolique: Int, diastolique: Int) = "$systolique/$diastolique mmHg"
}
