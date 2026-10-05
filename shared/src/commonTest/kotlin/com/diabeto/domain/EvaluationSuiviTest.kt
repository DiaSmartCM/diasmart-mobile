package com.diabeto.domain

import com.diabeto.data.model.PrioriteSuivi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvaluationSuiviTest {
    private val maintenant = LocalDateTime(2026, 10, 4, 12, 0)
    private val utc = TimeZone.UTC

    private fun jour(j: Int) = LocalDateTime(2026, 10, j, 8, 0)

    @Test
    fun hypoSevereDonnePrioriteHaute() {
        val r = EvaluationSuivi.evaluer(
            0L, listOf(jour(1) to 50.0, jour(2) to 120.0),
            listOf(MesureHbA1c(LocalDate(2026, 9, 1), 6.8, false)), maintenant, utc
        )
        assertEquals(PrioriteSuivi.HAUTE, r.priorite)
        assertTrue(r.raisons.any { "< 54" in it })
        assertFalse(r.perduDeVue)
    }

    @Test
    fun hba1cEntre7et9DonneMoyenneAvecVirgule() {
        val r = EvaluationSuivi.evaluer(
            0L, listOf(jour(3) to 140.0),
            listOf(MesureHbA1c(LocalDate(2026, 8, 1), 7.25, false)), maintenant, utc
        )
        assertEquals(PrioriteSuivi.MOYENNE, r.priorite)
        assertTrue("HbA1c 7,3 %" in r.raisons)
    }

    @Test
    fun hba1cDeLaboPasseAvantEstimation() {
        val r = EvaluationSuivi.evaluer(
            0L, listOf(jour(3) to 140.0),
            listOf(
                MesureHbA1c(LocalDate(2026, 9, 20), 9.5, true),
                MesureHbA1c(LocalDate(2026, 8, 1), 6.5, false)
            ), maintenant, utc
        )
        assertEquals(6.5, r.hba1c?.valeur)
        assertEquals(PrioriteSuivi.BASSE, r.priorite)
    }

    @Test
    fun perduDeVueApres60Jours() {
        val r = EvaluationSuivi.evaluer(
            0L, listOf(LocalDateTime(2026, 8, 1, 8, 0) to 120.0), emptyList(), maintenant, utc
        )
        assertTrue(r.perduDeVue)
        assertEquals("aucune mesure depuis 64 jours", r.raisons.first())
    }

    @Test
    fun sansDonneesPrioriteInconnue() {
        val r = EvaluationSuivi.evaluer(0L, emptyList(), emptyList(), maintenant, utc)
        assertEquals(PrioriteSuivi.INCONNUE, r.priorite)
    }
}
