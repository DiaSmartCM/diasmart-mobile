package com.diabeto.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.roundToInt

/**
 * Une mesure de tension arterielle : PAS/PAD (mmHg), FC (battements/min),
 * position (voir [ReglesTension.POSITIONS]), bras ("GAUCHE"/"DROIT"),
 * traitement antihypertenseur en cours au moment de la mesure.
 */
data class MesureTension(
    val date: LocalDateTime,
    val systolique: Int,
    val diastolique: Int,
    val pouls: Int? = null,
    val position: String = "",
    val bras: String = "",
    val traitement: Boolean? = null
) {
    val pressionPulsee: Int get() = ReglesTension.pressionPulsee(systolique, diastolique)
    val pam: Int get() = ReglesTension.pam(systolique, diastolique)
}

/**
 * Categories d'une mesure en automesure a domicile, pour un patient
 * diabetique (reperes ADA 2025 / ESC 2024). Ce n'est pas un diagnostic :
 * les objectifs individuels sont fixes par le medecin traitant.
 */
enum class CategorieTension(val libelle: String, val conseil: String) {
    BASSE("Tension basse (PAS < 90)",
        "En cas de malaise, vertiges ou évanouissement : allongez-vous et appelez les secours. Sinon, parlez-en à votre médecin."),
    OBJECTIF("Dans l'objectif (< 130/80)", "Continuez ainsi."),
    OBJECTIF_AGE("Dans l'objectif assoupli (sujet âgé)", "Objectif assoupli (PAS 130-139) : continuez ainsi, selon l'avis de votre médecin."),
    AU_DESSUS_OBJECTIF("Au-dessus de l'objectif (≥ 130/80)",
        "À surveiller : moins de sel, activité physique, et reprenez la mesure au repos."),
    HTA_DOMICILE("Hypertension en automesure (≥ 135/85)",
        "Si c'est confirmé sur plusieurs jours, parlez-en à votre médecin."),
    URGENCE("Alerte : très élevée (≥ 180/110)",
        "Reposez-vous 5 minutes et reprenez la mesure. Si elle reste aussi haute, ou en cas de mal de tête, douleur dans la poitrine, trouble de la vue ou faiblesse d'un côté : urgence.")
}

object ReglesTension {
    const val SYS_MIN = 50
    const val SYS_MAX = 300
    const val DIA_MIN = 30
    const val DIA_MAX = 200

    // Positions
    const val ASSIS = "ASSIS"
    const val COUCHE = "COUCHE"
    const val DEBOUT_1MIN = "DEBOUT_1MIN"
    const val DEBOUT_3MIN = "DEBOUT_3MIN"
    const val DEBOUT = "DEBOUT"
    val POSITIONS = listOf(ASSIS, COUCHE, DEBOUT_1MIN, DEBOUT_3MIN)

    // Bras
    const val GAUCHE = "GAUCHE"
    const val DROIT = "DROIT"

    /** Age a partir duquel l'objectif est assoupli (PAS 130-139). */
    const val AGE_OBJECTIF_ASSOUPLI = 65

    /** Rappel affiche avec chaque interpretation. */
    const val AVERTISSEMENT = "Ces seuils sont des repères généraux (ADA 2025 / ESC 2024). " +
        "Vos objectifs personnels sont fixés par votre médecin traitant."

    fun valide(systolique: Int, diastolique: Int): Boolean =
        systolique in SYS_MIN..SYS_MAX && diastolique in DIA_MIN..DIA_MAX && systolique > diastolique

    /**
     * Categorie d'une mesure. [age] (annees) assouplit l'objectif a partir
     * de [AGE_OBJECTIF_ASSOUPLI] ans : PAS 130-139 reste dans l'objectif.
     */
    fun categorie(systolique: Int, diastolique: Int, age: Int? = null): CategorieTension = when {
        systolique >= 180 || diastolique >= 110 -> CategorieTension.URGENCE
        systolique < 90 -> CategorieTension.BASSE
        age != null && age >= AGE_OBJECTIF_ASSOUPLI && systolique in 130..139 && diastolique < 80 ->
            CategorieTension.OBJECTIF_AGE
        systolique >= 135 || diastolique >= 85 -> CategorieTension.HTA_DOMICILE
        systolique >= 130 || diastolique >= 80 -> CategorieTension.AU_DESSUS_OBJECTIF
        else -> CategorieTension.OBJECTIF
    }

    /** Pression pulsee = PAS - PAD. Au-dela de 60 mmHg : rigidite arterielle possible. */
    fun pressionPulsee(systolique: Int, diastolique: Int) = systolique - diastolique
    fun pressionPulseeElevee(systolique: Int, diastolique: Int) = pressionPulsee(systolique, diastolique) > 60

    /** Pression arterielle moyenne PAM = PAD + (PAS - PAD) / 3. */
    fun pam(systolique: Int, diastolique: Int): Int = (diastolique + (systolique - diastolique) / 3.0).roundToInt()

    /** FC >= 100 au repos (assis ou couche). */
    fun tachycardieRepos(m: MesureTension): Boolean =
        (m.pouls ?: 0) >= 100 && (m.position == ASSIS || m.position == COUCHE || m.position.isBlank())

    /**
     * Tachycardie de repos persistante : au moins 3 mesures au repos avec
     * FC >= 100, et la majorite des mesures au repos avec FC renseignee.
     */
    fun tachycardiePersistante(mesures: List<MesureTension>): Boolean {
        val repos = mesures.filter { it.pouls != null && (it.position == ASSIS || it.position == COUCHE || it.position.isBlank()) }
        val rapides = repos.count { tachycardieRepos(it) }
        return rapides >= 3 && rapides * 2 > repos.size
    }

    /** Resultat d'un test couche puis debout. */
    data class TestOrthostatique(
        val couche: MesureTension,
        val debout: MesureTension,
        val baissePas: Int,
        val baissePad: Int
    ) {
        /** Baisse de PAS >= 20 mmHg ou de PAD >= 10 mmHg. */
        val positif: Boolean get() = baissePas >= 20 || baissePad >= 10
    }

    /**
     * Retrouve les tests couche -> debout : une mesure couchee suivie, dans
     * les 15 minutes, de mesures debout (1 et/ou 3 min). On garde la plus
     * forte baisse. Le plus recent en premier.
     */
    fun testsOrthostatiques(mesures: List<MesureTension>): List<TestOrthostatique> {
        val triees = mesures.sortedBy { it.date }
        return triees.filter { it.position == COUCHE }.mapNotNull { c ->
            val t0 = c.date.toInstant(TimeZone.UTC)
            val debouts = triees.filter {
                (it.position == DEBOUT_1MIN || it.position == DEBOUT_3MIN || it.position == DEBOUT) &&
                    it.date >= c.date && (it.date.toInstant(TimeZone.UTC) - t0).inWholeMinutes <= 15
            }
            debouts.map { d -> TestOrthostatique(c, d, c.systolique - d.systolique, c.diastolique - d.diastolique) }
                .maxByOrNull { maxOf(it.baissePas - 20, (it.baissePad - 10) * 2) }
        }.sortedByDescending { it.couche.date }
    }

    /** Moyenne arrondie (systolique, diastolique), ou null sans mesure. */
    fun moyenne(mesures: List<MesureTension>): Pair<Int, Int>? =
        if (mesures.isEmpty()) null
        else mesures.map { it.systolique }.average().roundToInt() to mesures.map { it.diastolique }.average().roundToInt()

    fun texte(systolique: Int, diastolique: Int) = "$systolique/$diastolique mmHg"

    fun libellePosition(code: String) = when (code) {
        ASSIS -> "assis"
        COUCHE -> "couché"
        DEBOUT_1MIN -> "debout 1 min"
        DEBOUT_3MIN -> "debout 3 min"
        DEBOUT -> "debout"
        else -> "position non précisée"
    }

    fun libelleBras(code: String) = when (code) { GAUCHE -> "gauche"; DROIT -> "droit"; else -> "non précisé" }
}
