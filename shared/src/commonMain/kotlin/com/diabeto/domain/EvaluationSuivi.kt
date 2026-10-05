package com.diabeto.domain

import com.diabeto.data.model.PrioriteSuivi
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** Une HbA1c partagee par le patient. */
data class MesureHbA1c(val date: LocalDate, val valeur: Double, val estimee: Boolean)

/** Resultat de l'evaluation d'un patient inscrit dans un etablissement. */
data class ResultatSuivi(
    val derniereMesure: LocalDateTime?,
    val nbMesures30j: Int,
    val moyenne30j: Double?,
    val nbHypos30j: Int,
    val nbHyposSeveres30j: Int,
    val hba1c: MesureHbA1c?,
    val priorite: PrioriteSuivi,
    val raisons: List<String>,
    val perduDeVue: Boolean,
    val derniereTension: MesureTension? = null,
    val tensionMoyenne30j: Pair<Int, Int>? = null
)

/**
 * Regles de priorite du tableau de bord etablissement (aide au suivi, pas
 * un diagnostic). Communes a l'app Android et a la version PC.
 *
 *  - HAUTE : HbA1c >= 9 %, ou une glycemie < 54 mg/dL sur 30 jours,
 *            ou 3 glycemies < 70 mg/dL ou plus, ou moyenne 30 j >= 250 mg/dL
 *  - MOYENNE : HbA1c >= 7 %, moyenne 30 j >= 180 mg/dL, 1 ou 2 glycemies
 *              < 70 mg/dL, ou aucune HbA1c depuis 6 mois
 *  - BASSE : rien de tout cela, avec des donnees recentes
 *  - Tension (automesure, reperes ADA 2025 / ESC 2024) : HAUTE si une
 *    mesure >= 180/110 sur 30 jours ; MOYENNE si moyenne 30 j >= 135/85,
 *    PAS < 90, hypotension orthostatique ou tachycardie de repos persistante
 *  - Perdu de vue : aucune mesure depuis [JOURS_PERDU_DE_VUE] jours
 *    (ou aucune mesure du tout, inscrit depuis plus de 30 jours)
 */
object EvaluationSuivi {
    const val JOURS_PERDU_DE_VUE = 60L

    fun evaluer(
        inscritAt: Long,
        mesures: List<Pair<LocalDateTime, Double>>,
        hba1c: List<MesureHbA1c>,
        maintenant: LocalDateTime,
        fuseau: TimeZone,
        tensions: List<MesureTension> = emptyList()
    ): ResultatSuivi {
        val depuis30 = moinsJours(maintenant, 30)
        val recentes = mesures.filter { it.first > depuis30 }.map { it.second }
        val moyenne = recentes.takeIf { it.isNotEmpty() }?.average()
        val hypos = recentes.count { it < 70 }
        val hyposSeveres = recentes.count { it < 54 }
        val derniere = mesures.maxOfOrNull { it.first }
        // Une vraie HbA1c de labo passe avant une estimation
        val hReelle = hba1c.filter { !it.estimee }.maxByOrNull { it.date }
        val h = hReelle ?: hba1c.maxByOrNull { it.date }
        val inscritDepuisJours = if (inscritAt > 0)
            joursEntre(Instant.fromEpochMilliseconds(inscritAt).toLocalDateTime(fuseau), maintenant)
        else 0L
        // Une mesure de tension compte aussi comme signe de suivi
        val derniereActivite = listOfNotNull(derniere, tensions.maxOfOrNull { it.date }).maxOrNull()
        val perdu = if (derniereActivite != null) joursEntre(derniereActivite, maintenant) >= JOURS_PERDU_DE_VUE
        else inscritDepuisJours >= 30

        val haute = mutableListOf<String>()
        val moyenneR = mutableListOf<String>()
        if (h != null && h.valeur >= 9.0) haute += "HbA1c ${unChiffre(h.valeur)} %"
        else if (h != null && h.valeur >= 7.0) moyenneR += "HbA1c ${unChiffre(h.valeur)} %"
        if (hyposSeveres > 0) haute += "$hyposSeveres glycemie(s) < 54 mg/dL en 30 j"
        if (hypos >= 3) haute += "$hypos glycemies < 70 mg/dL en 30 j"
        else if (hypos > 0 && hyposSeveres == 0) moyenneR += "$hypos glycemie(s) < 70 mg/dL en 30 j"
        if (moyenne != null && moyenne >= 250) haute += "moyenne 30 j ${moyenne.toInt()} mg/dL"
        else if (moyenne != null && moyenne >= 180) moyenneR += "moyenne 30 j ${moyenne.toInt()} mg/dL"
        if (h == null || h.date < maintenant.date.minus(6, DateTimeUnit.MONTH)) moyenneR += "pas d'HbA1c depuis 6 mois"

        val tensions30 = tensions.filter { it.date > depuis30 }
        val tMoy = ReglesTension.moyenne(tensions30)
        val tMax = tensions30.filter { it.systolique >= 180 || it.diastolique >= 110 }
        if (tMax.isNotEmpty()) haute += "${tMax.size} tension(s) >= 180/110 en 30 j"
        if (tMoy != null && (tMoy.first >= 135 || tMoy.second >= 85))
            moyenneR += "tension moyenne 30 j ${tMoy.first}/${tMoy.second}"
        val basses = tensions30.count { it.systolique < 90 }
        if (basses > 0) moyenneR += "$basses tension(s) PAS < 90 en 30 j"
        if (ReglesTension.testsOrthostatiques(tensions30).any { it.positif }) moyenneR += "hypotension orthostatique"
        if (ReglesTension.tachycardiePersistante(tensions30)) moyenneR += "FC de repos >= 100 répétée"


        val priorite = when {
            haute.isNotEmpty() -> PrioriteSuivi.HAUTE
            recentes.isEmpty() && h == null && tensions30.isEmpty() -> PrioriteSuivi.INCONNUE
            moyenneR.isNotEmpty() -> PrioriteSuivi.MOYENNE
            else -> PrioriteSuivi.BASSE
        }
        val raisons = buildList {
            if (perdu) add(if (derniereActivite == null) "aucune mesure partagee" else "aucune mesure depuis ${joursEntre(derniereActivite, maintenant)} jours")
            addAll(haute)
            if (priorite != PrioriteSuivi.INCONNUE) addAll(moyenneR)
        }
        return ResultatSuivi(
            derniereMesure = derniere,
            nbMesures30j = recentes.size,
            moyenne30j = moyenne,
            nbHypos30j = hypos,
            nbHyposSeveres30j = hyposSeveres,
            hba1c = h,
            priorite = priorite,
            raisons = raisons,
            perduDeVue = perdu,
            derniereTension = tensions.maxByOrNull { it.date },
            tensionMoyenne30j = tMoy
        )
    }

    // Arithmetique "heure locale" (comme java.time.LocalDateTime) : on passe
    // par UTC pour ne pas etre gene par les changements d'heure.
    private fun joursEntre(a: LocalDateTime, b: LocalDateTime): Long =
        a.toInstant(TimeZone.UTC).daysUntil(b.toInstant(TimeZone.UTC), TimeZone.UTC).toLong()

    private fun moinsJours(t: LocalDateTime, jours: Int): LocalDateTime =
        t.toInstant(TimeZone.UTC).minus(jours, DateTimeUnit.DAY, TimeZone.UTC).toLocalDateTime(TimeZone.UTC)

    /** 7.25 -> "7,3" (format francais, un chiffre apres la virgule). */
    fun unChiffre(v: Double): String {
        val d = (v * 10).roundToInt()
        val signe = if (d < 0) "-" else ""
        return "$signe${abs(d) / 10},${abs(d) % 10}"
    }
}
