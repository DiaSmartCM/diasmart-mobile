package com.diabeto.domain

import com.diabeto.data.entity.HbA1cInterpretation
import kotlin.test.Test
import kotlin.test.assertEquals

class ReglesGlycemieTest {

    @Test
    fun formuleAdagDansLesDeuxSens() {
        assertEquals(154.2, ReglesGlycemie.glycemieMoyenneDepuisHbA1c(7.0), 0.05)
        assertEquals(7.0, ReglesGlycemie.hba1cDepuisGlycemieMoyenne(154.2), 0.01)
    }

    @Test
    fun seuilsGlycemie() {
        assertEquals("Hypoglycémie sévère", ReglesGlycemie.statutGlycemie(53.0))
        assertEquals("Hypoglycémie", ReglesGlycemie.statutGlycemie(54.0))
        assertEquals("Dans la cible", ReglesGlycemie.statutGlycemie(70.0))
        assertEquals("Dans la cible", ReglesGlycemie.statutGlycemie(180.0))
        assertEquals("Hyperglycémie", ReglesGlycemie.statutGlycemie(180.5))
        assertEquals("Hyperglycémie", ReglesGlycemie.statutGlycemie(250.0))
        assertEquals("Hyperglycémie sévère", ReglesGlycemie.statutGlycemie(251.0))
    }

    @Test
    fun interpretationHbA1cSansTrou() {
        assertEquals(HbA1cInterpretation.NORMAL, ReglesGlycemie.interpreterHbA1c(5.6))
        assertEquals(HbA1cInterpretation.PREDIABETE, ReglesGlycemie.interpreterHbA1c(6.45))
        assertEquals(HbA1cInterpretation.CIBLE_ATTEINTE, ReglesGlycemie.interpreterHbA1c(7.0))
        assertEquals(HbA1cInterpretation.AU_DESSUS_CIBLE, ReglesGlycemie.interpreterHbA1c(7.05))
        assertEquals(HbA1cInterpretation.MAUVAIS_CONTROLE, ReglesGlycemie.interpreterHbA1c(8.05))
        assertEquals(HbA1cInterpretation.TRES_MAUVAIS_CONTROLE, ReglesGlycemie.interpreterHbA1c(9.1))
    }
}
