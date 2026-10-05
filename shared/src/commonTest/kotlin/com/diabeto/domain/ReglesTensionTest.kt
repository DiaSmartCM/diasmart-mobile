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
    fun categoriesAutomesureDiabetique() {
        assertEquals(CategorieTension.OBJECTIF, ReglesTension.categorie(125, 76))
        assertEquals(CategorieTension.AU_DESSUS_OBJECTIF, ReglesTension.categorie(132, 78))
        assertEquals(CategorieTension.AU_DESSUS_OBJECTIF, ReglesTension.categorie(125, 82))
        assertEquals(CategorieTension.HTA_DOMICILE, ReglesTension.categorie(136, 80))
        assertEquals(CategorieTension.HTA_DOMICILE, ReglesTension.categorie(128, 86))
        assertEquals(CategorieTension.URGENCE, ReglesTension.categorie(182, 95))
        assertEquals(CategorieTension.URGENCE, ReglesTension.categorie(150, 112))
        assertEquals(CategorieTension.BASSE, ReglesTension.categorie(85, 55))
        // Sujet age : PAS 130-139 dans l'objectif assoupli
        assertEquals(CategorieTension.OBJECTIF_AGE, ReglesTension.categorie(137, 75, age = 72))
        assertEquals(CategorieTension.HTA_DOMICILE, ReglesTension.categorie(137, 75, age = 50))
    }

    @Test
    fun pressionPulseeEtPam() {
        assertEquals(60, ReglesTension.pressionPulsee(140, 80))
        assertEquals(100, ReglesTension.pam(140, 80))
        assertTrue(ReglesTension.pressionPulseeElevee(160, 90))
    }

    @Test
    fun hypotensionOrthostatique() {
        val c = MesureTension(LocalDateTime(2026, 10, 4, 8, 0), 130, 80, position = ReglesTension.COUCHE)
        val d1 = MesureTension(LocalDateTime(2026, 10, 4, 8, 2), 118, 76, position = ReglesTension.DEBOUT_1MIN)
        val d3 = MesureTension(LocalDateTime(2026, 10, 4, 8, 4), 108, 74, position = ReglesTension.DEBOUT_3MIN)
        val t = ReglesTension.testsOrthostatiques(listOf(d3, c, d1)).single()
        assertEquals(22, t.baissePas)
        assertTrue(t.positif)
        // Pas de mesure debout dans les 15 min : pas de test
        val tard = d1.copy(date = LocalDateTime(2026, 10, 4, 9, 0))
        assertTrue(ReglesTension.testsOrthostatiques(listOf(c, tard)).isEmpty())
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

    @Test
    fun objectifPersonnelRemplaceObjectifGeneral() {
        val obj = ObjectifTension(140, 90, "Dr Test")
        assertEquals(CategorieTension.OBJECTIF_PERSO, ReglesTension.categorie(136, 86, objectif = obj))
        assertEquals(CategorieTension.AU_DESSUS_OBJECTIF, ReglesTension.categorie(125, 78, objectif = ObjectifTension(120, 80)))
        assertEquals(CategorieTension.HTA_DOMICILE, ReglesTension.categorie(145, 80, objectif = obj))
        assertEquals(CategorieTension.URGENCE, ReglesTension.categorie(185, 90, objectif = ObjectifTension(190, 120)))
        assertEquals(CategorieTension.BASSE, ReglesTension.categorie(85, 55, objectif = obj))
        assertEquals(6, ReglesTension.creneauxRegleDes3().size)
        assertTrue(ReglesTension.alerteSoignant(150, 110))
    }
}
