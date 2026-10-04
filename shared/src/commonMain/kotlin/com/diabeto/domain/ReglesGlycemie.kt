package com.diabeto.domain

import com.diabeto.data.entity.HbA1cInterpretation

/**
 * Regles medicales de base, communes a toutes les versions de DiaSmart
 * (Android, PC, puis iPhone). Pur Kotlin : aucune dependance a Android.
 *
 * Seuils glycemiques : criteres ADA 2024. Formule HbA1c <-> glycemie
 * moyenne : ADAG (Nathan DM et al., Diabetes Care 2008;31(8):1473-1478).
 */
object ReglesGlycemie {

    /** Glycemie moyenne estimee (eAG, mg/dL) : 28.7 x HbA1c - 46.7 */
    fun glycemieMoyenneDepuisHbA1c(hba1c: Double): Double = 28.7 * hba1c - 46.7

    /** HbA1c estimee (%) depuis la glycemie moyenne : (eAG + 46.7) / 28.7 */
    fun hba1cDepuisGlycemieMoyenne(glycemieMoyenne: Double): Double = (glycemieMoyenne + 46.7) / 28.7

    /** Statut d'une glycemie en mg/dL. */
    fun statutGlycemie(valeur: Double): String = when {
        valeur < 54 -> "Hypoglycémie sévère"
        valeur < 70 -> "Hypoglycémie"
        valeur <= 180 -> "Dans la cible"
        valeur <= 250 -> "Hyperglycémie"
        else -> "Hyperglycémie sévère"
    }

    /**
     * Interpretation d'une HbA1c (%). Les bornes sont continues : une valeur
     * entre deux paliers (ex. 6.45 %) tombe dans le palier du dessous, au
     * lieu de "Tres mauvais controle" comme avant.
     */
    fun interpreterHbA1c(valeur: Double): HbA1cInterpretation = when {
        valeur < 5.7 -> HbA1cInterpretation.NORMAL
        valeur < 6.5 -> HbA1cInterpretation.PREDIABETE
        valeur <= 7.0 -> HbA1cInterpretation.CIBLE_ATTEINTE
        valeur <= 8.0 -> HbA1cInterpretation.AU_DESSUS_CIBLE
        valeur <= 9.0 -> HbA1cInterpretation.MAUVAIS_CONTROLE
        else -> HbA1cInterpretation.TRES_MAUVAIS_CONTROLE
    }
}
