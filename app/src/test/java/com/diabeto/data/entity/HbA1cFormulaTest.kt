package com.diabeto.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la formule ADAG (HbA1c <-> Glycemie moyenne).
 *
 * Formule officielle ADAG (A Diabetes Control and Complications Trial) :
 *   eAG (mg/dL) = 28.7 × HbA1c − 46.7
 *
 * Reference : Nathan DM et al., Translating the A1C Assay Into Estimated
 * Average Glucose Values, Diabetes Care 2008;31(8):1473-1478.
 *
 * Une erreur ici = mauvais conseil au patient sur son controle glycemique.
 */
class HbA1cFormulaTest {

    private val tolerance = 0.05  // tolerance pour les comparaisons Double

    // ─────────────────────────────────────────────────────────────────────
    // Direction HbA1c -> Glycemie moyenne (eAG)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `HbA1c 5 pourcent donne eAG 97 mg par dL`() {
        // 28.7 * 5.0 - 46.7 = 96.8
        val hba1c = HbA1cEntity(patientId = 1L, dateMesure = java.time.LocalDate.now(), valeur =5.0)
        assertEquals(96.8, hba1c.getGlycemieMoyenneEstimee(), tolerance)
    }

    @Test
    fun `HbA1c 6 pourcent donne eAG 126 mg par dL (seuil diabete)`() {
        // 28.7 * 6.0 - 46.7 = 125.5
        val hba1c = HbA1cEntity(patientId = 1L, dateMesure = java.time.LocalDate.now(), valeur =6.0)
        assertEquals(125.5, hba1c.getGlycemieMoyenneEstimee(), tolerance)
    }

    @Test
    fun `HbA1c 7 pourcent donne eAG 154 mg par dL (cible ADA)`() {
        // 28.7 * 7.0 - 46.7 = 154.2
        val hba1c = HbA1cEntity(patientId = 1L, dateMesure = java.time.LocalDate.now(), valeur =7.0)
        assertEquals(154.2, hba1c.getGlycemieMoyenneEstimee(), tolerance)
    }

    @Test
    fun `HbA1c 8 pourcent donne eAG 183 mg par dL`() {
        val hba1c = HbA1cEntity(patientId = 1L, dateMesure = java.time.LocalDate.now(), valeur =8.0)
        assertEquals(182.9, hba1c.getGlycemieMoyenneEstimee(), tolerance)
    }

    @Test
    fun `HbA1c 10 pourcent donne eAG 240 mg par dL`() {
        // 28.7 * 10.0 - 46.7 = 240.3
        val hba1c = HbA1cEntity(patientId = 1L, dateMesure = java.time.LocalDate.now(), valeur =10.0)
        assertEquals(240.3, hba1c.getGlycemieMoyenneEstimee(), tolerance)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Direction Glycemie moyenne -> HbA1c
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `eAG 100 donne HbA1c approx 5_1 pourcent`() {
        // (100 + 46.7) / 28.7 = 5.11
        val estimated = HbA1cEntity.estimerDepuisGlycemieMoyenne(100.0)
        assertEquals(5.11, estimated, tolerance)
    }

    @Test
    fun `eAG 154 donne HbA1c approx 7 pourcent (reverse de cible ADA)`() {
        val estimated = HbA1cEntity.estimerDepuisGlycemieMoyenne(154.0)
        assertEquals(7.0, estimated, 0.02)
    }

    @Test
    fun `eAG 240 donne HbA1c approx 10 pourcent`() {
        val estimated = HbA1cEntity.estimerDepuisGlycemieMoyenne(240.0)
        assertEquals(9.99, estimated, tolerance)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Aller-retour (reversibilite)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `aller-retour HbA1c -- eAG -- HbA1c preserve la valeur`() {
        for (hba1c in listOf(5.5, 6.5, 7.5, 8.5, 9.5)) {
            val entity = HbA1cEntity(patientId = 1L, dateMesure = java.time.LocalDate.now(), valeur =hba1c)
            val eag = entity.getGlycemieMoyenneEstimee()
            val back = HbA1cEntity.estimerDepuisGlycemieMoyenne(eag)
            assertEquals(
                "Aller-retour pour HbA1c=$hba1c devrait revenir a $hba1c",
                hba1c, back, tolerance
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Interpretation clinique
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `HbA1c 5_5 = NORMAL`() {
        assertEquals(HbA1cInterpretation.NORMAL, HbA1cEntity(patientId = 1L, dateMesure = java.time.LocalDate.now(), valeur =5.5).getInterpretation())
    }

    @Test
    fun `HbA1c 6_0 = PREDIABETE`() {
        assertEquals(HbA1cInterpretation.PREDIABETE, HbA1cEntity(patientId = 1L, dateMesure = java.time.LocalDate.now(), valeur =6.0).getInterpretation())
    }

    @Test
    fun `HbA1c 6_5 = CIBLE_ATTEINTE`() {
        assertEquals(HbA1cInterpretation.CIBLE_ATTEINTE, HbA1cEntity(patientId = 1L, dateMesure = java.time.LocalDate.now(), valeur =6.5).getInterpretation())
    }

    @Test
    fun `HbA1c 7_5 = AU_DESSUS_CIBLE`() {
        assertEquals(HbA1cInterpretation.AU_DESSUS_CIBLE, HbA1cEntity(patientId = 1L, dateMesure = java.time.LocalDate.now(), valeur =7.5).getInterpretation())
    }

    @Test
    fun `HbA1c 8_5 = MAUVAIS_CONTROLE`() {
        assertEquals(HbA1cInterpretation.MAUVAIS_CONTROLE, HbA1cEntity(patientId = 1L, dateMesure = java.time.LocalDate.now(), valeur =8.5).getInterpretation())
    }

    @Test
    fun `HbA1c 10_0 = TRES_MAUVAIS_CONTROLE`() {
        assertEquals(HbA1cInterpretation.TRES_MAUVAIS_CONTROLE, HbA1cEntity(patientId = 1L, dateMesure = java.time.LocalDate.now(), valeur =10.0).getInterpretation())
    }

    // ─────────────────────────────────────────────────────────────────────
    // Edge cases
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `eAG 0 donne HbA1c positive (sanity check formule)`() {
        // 0 + 46.7 / 28.7 = 1.62 — meme avec glycemie 0, la formule ne crash pas
        val estimated = HbA1cEntity.estimerDepuisGlycemieMoyenne(0.0)
        assertTrue(estimated > 0)
    }

    @Test
    fun `eAG extreme 500 donne HbA1c plausible`() {
        // 500 + 46.7 / 28.7 = 19.06 — physiologiquement extreme mais formule OK
        val estimated = HbA1cEntity.estimerDepuisGlycemieMoyenne(500.0)
        assertEquals(19.05, estimated, tolerance)
    }
}
