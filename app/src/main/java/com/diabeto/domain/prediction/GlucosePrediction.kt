package com.diabeto.domain.prediction

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Modele d'excursion glycemique post-prandiale.
 *
 * Pourquoi ce fichier existe
 * --------------------------
 * Jusqu'a la v2.1.78, la "prediction" etait une droite de regression tracee sur
 * les dernieres mesures puis ramenee vers la moyenne. Aucune variable ne
 * representait le repas : un patient qui venait de manger 68 g de glucides
 * voyait la meme courbe plate qu'a jeun. Ce n'etait pas une prediction, c'etait
 * le prolongement d'une tendance.
 *
 * Ce modele part de ce qui fait reellement monter la glycemie apres un repas :
 * la quantite de glucides et leur vitesse d'absorption.
 *
 * La forme de la courbe
 * ---------------------
 * L'absorption d'un repas ne monte pas en ligne droite : elle croit, culmine,
 * puis redescend. On utilise une courbe gamma normalisee
 *
 *     montee(t) = A · (t/tau)^n · exp(n · (1 − t/tau))
 *
 * qui vaut 0 en t=0, atteint exactement A en t=tau, puis decroit. Avec n = 2 la
 * redescente est nette sans etre brutale : il reste environ 17 % du pic a 3·tau
 * et 4 % a 4·tau, ce qui correspond au retour a la ligne de base observe en
 * trois a cinq heures.
 *
 * Ce modele ne sait rien du stress, du sommeil, d'une infection ni d'une dose
 * d'insuline non declaree. Il produit une ESTIMATION, et l'interface doit la
 * presenter comme telle.
 */
object GlucosePrediction {

    /** Coefficient de depart, en mg/dL par unite de charge glycemique. */
    const val K_DEFAUT = 1.6

    /** Bornes physiologiques du coefficient personnel. */
    const val K_MIN = 0.6
    const val K_MAX = 3.5

    /** Nombre d'observations en deca duquel on reste sur le coefficient par defaut. */
    const val OBSERVATIONS_MIN = 4

    /** Delai suppose entre le repas et la mesure "apres repas" (minutes). */
    const val MESURE_POST_PRANDIALE_MIN = 120.0

    /** Exposant de la courbe gamma. */
    private const val N = 2.0

    /** Constante de retour vers la glycemie habituelle, en minutes. */
    private const val TAU_RETOUR_BASE = 240.0

    // ─────────────────────────────────────────────────────────────────────
    // Entrees
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Un repas, ramene a ce qui influence la glycemie.
     *
     * @param minutesAvantMaintenant positif si le repas est passe, negatif s'il
     *        est a venir (simulation "et si je mangeais ceci ?").
     */
    data class Repas(
        val minutesAvantMaintenant: Double,
        val glucides: Double,
        val indexGlycemique: Int,
    ) {
        /** Charge glycemique : c'est elle qui gouverne l'amplitude, pas les glucides seuls. */
        val chargeGlycemique: Double
            get() = glucides * indexGlycemique / 100.0
    }

    /** Une observation appariee, issue des champs "avant repas" / "apres repas". */
    data class Observation(
        val chargeGlycemique: Double,
        val monteeObservee: Double,
        val indexGlycemique: Int,
    )

    /** Coefficient personnel et ce qui le fonde. */
    data class Calibration(
        val k: Double,
        val nombreObservations: Int,
        val personnalise: Boolean,
    )

    data class Point(
        val minutes: Double,
        val valeur: Double,
    )

    /** Resultat lisible par l'interface et par les notifications. */
    data class Excursion(
        val pointDepart: Double,
        val valeurPic: Double,
        val monteePic: Double,
        val minutesJusquAuPic: Double,
        val courbe: List<Point>,
        val incertitude: Double,
        val calibration: Calibration,
    ) {
        /** Fourchette a afficher plutot qu'un chiffre sec. */
        val picBas: Double get() = valeurPic - incertitude
        val picHaut: Double get() = valeurPic + incertitude
    }

    // ─────────────────────────────────────────────────────────────────────
    // Forme de la courbe
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Temps du pic, en minutes, deduit de l'index glycemique.
     * Un plat a index bas monte moins haut ET plus tard.
     */
    fun tempsDuPic(indexGlycemique: Int): Double = when {
        indexGlycemique >= 70 -> 60.0
        indexGlycemique >= 56 -> 80.0
        else -> 105.0
    }

    /** Part du pic atteinte a l'instant t. Vaut 1,0 en t = tau. */
    fun forme(minutes: Double, tau: Double): Double {
        if (minutes <= 0.0) return 0.0
        val x = minutes / tau
        return Math.pow(x, N) * exp(N * (1.0 - x))
    }

    /** Montee attendue, en mg/dL, a l'instant t apres le repas. */
    fun montee(minutes: Double, repas: Repas, k: Double): Double =
        k * repas.chargeGlycemique * forme(minutes, tempsDuPic(repas.indexGlycemique))

    // ─────────────────────────────────────────────────────────────────────
    // Calibration sur les mesures du patient
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Ajuste le coefficient personnel par moindres carres passant par l'origine.
     *
     * Chaque repas ou le patient a saisi sa glycemie avant ET apres fournit une
     * observation : voici la charge glycemique, voici ma montee reelle. Comme la
     * mesure "apres repas" n'est pas prise au pic mais environ deux heures plus
     * tard, on compare la montee observee a ce que le modele predit A CET
     * INSTANT — sans quoi le coefficient serait systematiquement sous-estime.
     *
     * En dessous de [OBSERVATIONS_MIN] observations, on garde le coefficient de
     * population : quatre repas ne suffisent pas a caracteriser quelqu'un.
     */
    fun calibrer(observations: List<Observation>): Calibration {
        val utiles = observations.filter {
            it.chargeGlycemique > 1.0 && it.monteeObservee.isFinite()
        }
        if (utiles.size < OBSERVATIONS_MIN) {
            return Calibration(K_DEFAUT, utiles.size, personnalise = false)
        }

        var numerateur = 0.0
        var denominateur = 0.0
        for (o in utiles) {
            // Predicteur : ce que vaudrait la montee pour k = 1 a l'instant de la mesure.
            val predicteur = o.chargeGlycemique *
                forme(MESURE_POST_PRANDIALE_MIN, tempsDuPic(o.indexGlycemique))
            numerateur += predicteur * o.monteeObservee
            denominateur += predicteur * predicteur
        }
        if (denominateur <= 0.0) {
            return Calibration(K_DEFAUT, utiles.size, personnalise = false)
        }

        val k = (numerateur / denominateur).coerceIn(K_MIN, K_MAX)
        return Calibration(k, utiles.size, personnalise = true)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Prediction
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Projette la glycemie sur [horizonMinutes] a partir de la derniere mesure.
     *
     * Point important sur les repas deja pris : une partie de leur effet est
     * DEJA contenue dans la derniere mesure. On ne rajoute donc pas la montee
     * brute, mais l'ecart entre ce que le modele prevoit a l'instant vise et ce
     * qu'il prevoyait a l'instant de la mesure. Sans cette soustraction, un
     * repas pris deux heures plus tot serait compte deux fois.
     *
     * @param derniereValeur derniere glycemie mesuree (mg/dL).
     * @param minutesDepuisMesure age de cette mesure.
     * @param glycemieHabituelle niveau vers lequel le patient revient hors repas.
     */
    fun predire(
        derniereValeur: Double,
        minutesDepuisMesure: Double,
        glycemieHabituelle: Double,
        repas: List<Repas>,
        calibration: Calibration,
        horizonMinutes: Double = 360.0,
        pasMinutes: Double = 15.0,
    ): Excursion {
        val k = calibration.k
        val points = mutableListOf<Point>()

        // Contribution deja refletee par la derniere mesure.
        val dejaMesure = repas.sumOf { r ->
            montee(r.minutesAvantMaintenant - minutesDepuisMesure, r, k)
        }

        var minutes = 0.0
        while (minutes <= horizonMinutes) {
            // Derive lente vers la glycemie habituelle, hors effet des repas.
            val derive = (glycemieHabituelle - derniereValeur) *
                (1.0 - exp(-(minutes + minutesDepuisMesure) / TAU_RETOUR_BASE))

            val apportRepas = repas.sumOf { r ->
                montee(r.minutesAvantMaintenant + minutes, r, k)
            } - dejaMesure

            val valeur = (derniereValeur + derive + apportRepas).coerceIn(40.0, 500.0)
            points.add(Point(minutes, valeur))
            minutes += pasMinutes
        }

        val pic = points.maxByOrNull { it.valeur } ?: Point(0.0, derniereValeur)

        return Excursion(
            pointDepart = derniereValeur,
            valeurPic = pic.valeur,
            monteePic = pic.valeur - derniereValeur,
            minutesJusquAuPic = pic.minutes,
            courbe = points,
            incertitude = incertitude(pic.valeur - derniereValeur, calibration),
            calibration = calibration,
        )
    }

    /**
     * Demi-largeur de la fourchette affichee.
     *
     * Tant que le coefficient n'est pas personnalise, l'ecart entre individus
     * domine tout le reste : on annonce large. Une fois calibre, l'incertitude
     * se resserre avec le nombre d'observations, sans jamais descendre sous un
     * plancher — une prediction glycemique n'est jamais exacte.
     */
    fun incertitude(montee: Double, calibration: Calibration): Double {
        val part = if (!calibration.personnalise) 0.40
        else (0.30 / Math.sqrt(calibration.nombreObservations.toDouble())).coerceAtLeast(0.12)
        return (kotlin.math.abs(montee) * part).coerceAtLeast(12.0)
    }

    /** Arrondi d'affichage : au multiple de 5, la precision au mg/dL etant illusoire. */
    fun arrondiAffichage(valeur: Double): Int = (valeur / 5.0).roundToInt() * 5
}
