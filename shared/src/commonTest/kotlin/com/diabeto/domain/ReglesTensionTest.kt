package com.diabeto.domain

import com.diabeto.data.model.PrioriteSuivi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReglesTensionTest {
    @Test
    fun categorieLaPlusHauteDesDeuxValeurs() {
        assertEquals(CategorieTension.NORMALE, ReglesTension.categorie(118, 76))
        assertEquals(CategorieTension.NORMALE_HAUTE, ReglesTension.categorie(125, 86))
        assertEquals(CategorieTension.HTA_1, ReglesTension.categorie(145, 80))
        assertEquals(CategorieTension.HTA_2, ReglesTension.categorie(130, 102))
        assertEquals(CategorieTension.TRES_ELEVEE, ReglesTension.categorie(182, 95))
        assertEquals(CategorieTension.BASSE, ReglesTension.categorie(85, 55))
    }

    @Test
    fun valeursImpossiblesRefusees() {
        assertTrue(ReglesTension.valide(120, 80))
        assertFalse(ReglesTension.valide(80, 120))
        assertFalse(ReglesTension.valide(400, 80))
    }

    @Test
    fun tensionTresEleveeDonnePrioriteHaute() {
        val maintenant = LocalDateTime(2026, 10, 4, 12, 0)
        val r = EvaluationSuivi.evaluer(
            0L, listOf(LocalDateTime(2026, 10, 2, 8, 0) to 120.0),
            listOf(MesureHbA1c(LocalDate(2026, 9, 1), 6.5, false)), maintenant, TimeZone.UTC,
            tensions = listOf(
                MesureTension(LocalDateTime(2026, 10, 1, 8, 0), 185, 100),
                MesureTension(LocalDateTime(2026, 10, 3, 8, 0), 150, 92)
            )
        )
        assertEquals(PrioriteSuivi.HAUTE, r.priorite)
        assertEquals(168 to 96, r.tensionMoyenne30j)
        assertEquals(150, r.derniereTension?.systolique)
    }

    @Test
    fun tensionSeuleEviteLePerduDeVue() {
        val maintenant = LocalDateTime(2026, 10, 4, 12, 0)
        val r = EvaluationSuivi.evaluer(
            0L, listOf(LocalDateTime(2026, 6, 1, 8, 0) to 120.0), emptyList(), maintenant, TimeZone.UTC,
            tensions = listOf(MesureTension(LocalDateTime(2026, 10, 3, 8, 0), 142, 88))
        )
        assertFalse(r.perduDeVue)
        assertEquals(PrioriteSuivi.MOYENNE, r.priorite)
    }
}
