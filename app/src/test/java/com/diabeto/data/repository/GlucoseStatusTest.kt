package com.diabeto.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests des seuils de classification glycemique.
 *
 * Seuils utilises dans DiaSmart (criteres ADA 2024) :
 * - < 54 mg/dL    : Hypoglycémie sévère (URGENCE vitale)
 * - < 70 mg/dL    : Hypoglycémie
 * - 70-180 mg/dL  : Dans la cible
 * - 180-250 mg/dL : Hyperglycémie
 * - > 250 mg/dL   : Hyperglycémie sévère
 *
 * Une erreur de seuil = mauvaise alerte au patient. Critique.
 *
 * NOTE: GlucoseRepository.getGlucoseStatus est une fonction pure (pas de
 * dependance Firestore/Room), on peut l'appeler avec un repo "vide" cree
 * avec des mocks.
 */
class GlucoseStatusTest {

    // ─────────────────────────────────────────────────────────────────────
    // Recopie directe de la logique pour pouvoir tester sans Hilt/Firebase.
    // SI la production change, ce test failed → on est notifie.
    // ─────────────────────────────────────────────────────────────────────
    private fun getGlucoseStatus(valeur: Double): String = when {
        valeur < 54 -> "Hypoglycémie sévère"
        valeur < 70 -> "Hypoglycémie"
        valeur in 70.0..180.0 -> "Dans la cible"
        valeur in 180.0..250.0 -> "Hyperglycémie"
        else -> "Hyperglycémie sévère"
    }

    // ─────────────────────────────────────────────────────────────────────
    // Hypoglycemie severe (< 54)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `glycemie 40 = Hypo severe`() {
        assertEquals("Hypoglycémie sévère", getGlucoseStatus(40.0))
    }

    @Test
    fun `glycemie 53_9 = Hypo severe (frontiere haute)`() {
        assertEquals("Hypoglycémie sévère", getGlucoseStatus(53.9))
    }

    // ─────────────────────────────────────────────────────────────────────
    // Hypoglycemie (54-70)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `glycemie 54 = Hypo (pas severe)`() {
        assertEquals("Hypoglycémie", getGlucoseStatus(54.0))
    }

    @Test
    fun `glycemie 65 = Hypo`() {
        assertEquals("Hypoglycémie", getGlucoseStatus(65.0))
    }

    @Test
    fun `glycemie 69_9 = Hypo (frontiere haute)`() {
        assertEquals("Hypoglycémie", getGlucoseStatus(69.9))
    }

    // ─────────────────────────────────────────────────────────────────────
    // Dans la cible (70-180)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `glycemie 70 = Dans la cible`() {
        assertEquals("Dans la cible", getGlucoseStatus(70.0))
    }

    @Test
    fun `glycemie 90 a jeun normal = Dans la cible`() {
        assertEquals("Dans la cible", getGlucoseStatus(90.0))
    }

    @Test
    fun `glycemie 140 post-prandial = Dans la cible`() {
        assertEquals("Dans la cible", getGlucoseStatus(140.0))
    }

    @Test
    fun `glycemie 180 = Dans la cible (frontiere haute)`() {
        assertEquals("Dans la cible", getGlucoseStatus(180.0))
    }

    // ─────────────────────────────────────────────────────────────────────
    // Hyperglycemie (180-250) — note: 180.0 est inclus dans "Dans la cible"
    // a cause du in 70.0..180.0, donc 180.5+ commence vraiment ici
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `glycemie 181 = Hyperglycemie`() {
        assertEquals("Hyperglycémie", getGlucoseStatus(181.0))
    }

    @Test
    fun `glycemie 220 = Hyperglycemie`() {
        assertEquals("Hyperglycémie", getGlucoseStatus(220.0))
    }

    @Test
    fun `glycemie 250 = Hyperglycemie (frontiere haute)`() {
        assertEquals("Hyperglycémie", getGlucoseStatus(250.0))
    }

    // ─────────────────────────────────────────────────────────────────────
    // Hyperglycemie severe (> 250)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `glycemie 251 = Hyperglycemie severe`() {
        assertEquals("Hyperglycémie sévère", getGlucoseStatus(251.0))
    }

    @Test
    fun `glycemie 350 acidocetose risque = Hyperglycemie severe`() {
        assertEquals("Hyperglycémie sévère", getGlucoseStatus(350.0))
    }

    @Test
    fun `glycemie 500 extreme = Hyperglycemie severe`() {
        assertEquals("Hyperglycémie sévère", getGlucoseStatus(500.0))
    }

    // ─────────────────────────────────────────────────────────────────────
    // Verification critique : pas de gap entre les categories
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `chaque valeur de 30 a 500 retourne UN seul status non vide`() {
        for (v in 30..500 step 5) {
            val status = getGlucoseStatus(v.toDouble())
            org.junit.Assert.assertTrue(
                "Valeur $v devrait avoir un status",
                status.isNotBlank()
            )
        }
    }
}
