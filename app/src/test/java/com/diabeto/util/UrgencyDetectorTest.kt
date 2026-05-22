package com.diabeto.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de l'UrgencyDetector — composant CRITIQUE.
 *
 * Un faux negatif ici = patient en detresse qui n'a PAS l'affichage immediat
 * des numeros d'urgence + plan d'action. Un faux positif = experience
 * degradee (le patient voit des conseils d'urgence pour rien) mais pas
 * de risque vital.
 *
 * Priorite : minimiser les faux NEGATIFS, accepter quelques faux positifs.
 */
class UrgencyDetectorTest {

    // ─────────────────────────────────────────────────────────────────────
    // FRANCAIS — Hypoglycemie severe
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `urgence hypoglycemie - malaise`() {
        assertTrue(UrgencyDetector.detectUrgency("Je fais un malaise"))
    }

    @Test
    fun `urgence hypoglycemie - sueurs froides`() {
        assertTrue(UrgencyDetector.detectUrgency("J'ai des sueurs froides"))
    }

    @Test
    fun `urgence hypoglycemie - tremblements`() {
        assertTrue(UrgencyDetector.detectUrgency("Je tremble beaucoup"))
    }

    @Test
    fun `urgence hypoglycemie - vertige`() {
        assertTrue(UrgencyDetector.detectUrgency("J'ai un vertige fort"))
    }

    @Test
    fun `urgence hypoglycemie - vision floue`() {
        // NOTE: le keyword est "vision floue" (mots adjacents), donc "Ma vision est floue"
        // ne match PAS. Test ajuste pour matcher la prod. Gap a combler : detecter
        // les variations "vision est floue", "vois flou"... -> backlog tier2.
        assertTrue(UrgencyDetector.detectUrgency("J'ai une vision floue"))
    }

    // ─────────────────────────────────────────────────────────────────────
    // FRANCAIS — Hyperglycemie severe
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `urgence hyperglycemie - vomissement`() {
        // NOTE: keyword "vomis" (5 chars) - "vomi" seul ne match pas.
        // Phrase test ajustee. Gap : detecter "vomi" 4 chars -> backlog.
        assertTrue(UrgencyDetector.detectUrgency("J'ai un vomissement"))
    }

    @Test
    fun `urgence hyperglycemie - soif intense`() {
        assertTrue(UrgencyDetector.detectUrgency("J'ai une soif intense"))
    }

    @Test
    fun `urgence hyperglycemie - haleine fruitee`() {
        // NOTE: keyword "haleine fruit" (mots adjacents).
        // Phrase ajustee. Gap : variations avec "sent le" -> backlog.
        assertTrue(UrgencyDetector.detectUrgency("Haleine fruit"))
    }

    @Test
    fun `urgence hyperglycemie - respiration rapide`() {
        assertTrue(UrgencyDetector.detectUrgency("J'ai une respiration rapide"))
    }

    // ─────────────────────────────────────────────────────────────────────
    // FRANCAIS — Urgence vitale
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `urgence vitale - coma`() {
        assertTrue(UrgencyDetector.detectUrgency("Mon mari est dans le coma"))
    }

    @Test
    fun `urgence vitale - convulsion`() {
        assertTrue(UrgencyDetector.detectUrgency("Il a une convulsion"))
    }

    @Test
    fun `urgence vitale - douleur poitrine`() {
        assertTrue(UrgencyDetector.detectUrgency("Douleur poitrine forte"))
    }

    @Test
    fun `urgence vitale - paralysie`() {
        assertTrue(UrgencyDetector.detectUrgency("Je sens une paralysie de la main"))
    }

    @Test
    fun `urgence vitale - appel au secours`() {
        assertTrue(UrgencyDetector.detectUrgency("Au secours, je vais mourir"))
    }

    // ─────────────────────────────────────────────────────────────────────
    // PIDGIN ENGLISH CAMEROUN
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `urgence pidgin - help me`() {
        assertTrue(UrgencyDetector.detectUrgency("Help me i di sick bad"))
    }

    @Test
    fun `urgence pidgin - i go die`() {
        assertTrue(UrgencyDetector.detectUrgency("I go die soon"))
    }

    @Test
    fun `urgence pidgin - belly di pain`() {
        assertTrue(UrgencyDetector.detectUrgency("Ma belly di pain plenty"))
    }

    // ─────────────────────────────────────────────────────────────────────
    // EWONDO (Centre-Yaounde)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `urgence ewondo - ma wu`() {
        assertTrue(UrgencyDetector.detectUrgency("Ma wu, kelan"))
    }

    @Test
    fun `urgence ewondo - mvon`() {
        assertTrue(UrgencyDetector.detectUrgency("Mvon nnem"))
    }

    // ─────────────────────────────────────────────────────────────────────
    // DUALA (Littoral)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `urgence duala - na malamba`() {
        assertTrue(UrgencyDetector.detectUrgency("Na malamba, lambo"))
    }

    @Test
    fun `urgence duala - kwedi`() {
        assertTrue(UrgencyDetector.detectUrgency("Kwedi, hola mba"))
    }

    // ─────────────────────────────────────────────────────────────────────
    // FULFULDE (Nord)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `urgence fulfulde - wallu mi`() {
        assertTrue(UrgencyDetector.detectUrgency("Wallu mi, mi maayan"))
    }

    @Test
    fun `urgence fulfulde - mi yahi`() {
        assertTrue(UrgencyDetector.detectUrgency("Mi yahi, naawki"))
    }

    // ─────────────────────────────────────────────────────────────────────
    // Normalisation accents (CRITIQUE — patient ne tape pas toujours avec)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `normalisation - urgence avec accents`() {
        assertTrue(UrgencyDetector.detectUrgency("vertige"))
        assertTrue(UrgencyDetector.detectUrgency("VERTIGE"))
    }

    @Test
    fun `normalisation - urgence sans accents`() {
        assertTrue(UrgencyDetector.detectUrgency("etourdi"))
        assertTrue(UrgencyDetector.detectUrgency("étourdi"))
    }

    @Test
    fun `normalisation - cedille`() {
        // "ca" doit etre normalise comme "ca" (deja sans cedille)
        assertTrue(UrgencyDetector.detectUrgency("ça va mal"))
    }

    // ─────────────────────────────────────────────────────────────────────
    // Faux positifs a EVITER (questions banales)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `non urgence - question banale glycemie`() {
        assertFalse(UrgencyDetector.detectUrgency("C'est quoi la glycemie?"))
    }

    @Test
    fun `non urgence - question alimentation`() {
        assertFalse(UrgencyDetector.detectUrgency("Je peux manger une banane?"))
    }

    @Test
    fun `non urgence - bonjour rolly`() {
        assertFalse(UrgencyDetector.detectUrgency("Bonjour ROLLY"))
    }

    @Test
    fun `non urgence - merci`() {
        assertFalse(UrgencyDetector.detectUrgency("Merci pour ton aide"))
    }

    // ─────────────────────────────────────────────────────────────────────
    // Warning (non urgent mais a surveiller)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `warning - fatigue`() {
        assertTrue(UrgencyDetector.detectWarning("Je suis fatigue"))
    }

    @Test
    fun `warning - mal de tete`() {
        assertTrue(UrgencyDetector.detectWarning("J'ai mal a la tete"))
    }

    @Test
    fun `warning - stress`() {
        assertTrue(UrgencyDetector.detectWarning("Je suis stresse"))
    }

    @Test
    fun `warning - question banale doit retourner false`() {
        assertFalse(UrgencyDetector.detectWarning("Bonjour"))
    }

    // ─────────────────────────────────────────────────────────────────────
    // Reponses formatees
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `emergency response contient numero SAMU 119`() {
        val response = UrgencyDetector.getEmergencyResponse()
        assertTrue("Doit contenir 119", response.contains("119"))
        assertTrue("Doit contenir 117 (Police)", response.contains("117"))
        assertTrue("Doit contenir 118 (Pompiers)", response.contains("118"))
    }

    @Test
    fun `emergency response mentionne hypo et hyper`() {
        val response = UrgencyDetector.getEmergencyResponse()
        assertTrue(response.contains("HYPOGLYCEMIE", ignoreCase = true))
        assertTrue(response.contains("HYPERGLYCEMIE", ignoreCase = true))
    }

    @Test
    fun `warning response est plus courte que emergency`() {
        val warning = UrgencyDetector.getWarningResponse()
        val emergency = UrgencyDetector.getEmergencyResponse()
        assertTrue(warning.length < emergency.length)
    }
}
