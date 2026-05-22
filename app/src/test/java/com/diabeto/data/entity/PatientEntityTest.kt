package com.diabeto.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Tests des calculs metaboliques sur PatientEntity :
 * - IMC (kg/m²)
 * - Categorie IMC selon OMS
 * - Risque metabolique selon tour de taille (IDF)
 *
 * Une erreur de classification = mauvaise stratification du risque
 * → mauvais conseil thérapeutique pour le patient.
 */
class PatientEntityTest {

    private fun patient(
        poids: Double? = null,
        taille: Double? = null,
        tourDeTaille: Double? = null,
        sexe: Sexe = Sexe.HOMME
    ) = PatientEntity(
        id = 1L,
        prenom = "Jean",
        nom = "Dupont",
        dateNaissance = LocalDate.of(1990, 1, 1),
        sexe = sexe,
        typeDiabete = TypeDiabete.TYPE_2,
        poids = poids,
        taille = taille,
        tourDeTaille = tourDeTaille
    )

    // ─────────────────────────────────────────────────────────────────────
    // IMC = poids (kg) / taille² (m²)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `IMC null si poids manquant`() {
        assertNull(patient(taille = 170.0).imc)
    }

    @Test
    fun `IMC null si taille manquante`() {
        assertNull(patient(poids = 70.0).imc)
    }

    @Test
    fun `IMC null si taille zero`() {
        assertNull(patient(poids = 70.0, taille = 0.0).imc)
    }

    @Test
    fun `IMC 70kg pour 175cm = 22_86`() {
        // 70 / (1.75² = 3.0625) = 22.857
        assertEquals(22.86, patient(poids = 70.0, taille = 175.0).imc!!, 0.01)
    }

    @Test
    fun `IMC 50kg pour 160cm = 19_53 (poids normal limite)`() {
        // 50 / (1.6² = 2.56) = 19.53
        assertEquals(19.53, patient(poids = 50.0, taille = 160.0).imc!!, 0.01)
    }

    @Test
    fun `IMC 100kg pour 180cm = 30_86 (obesite I)`() {
        // 100 / (1.8² = 3.24) = 30.86
        assertEquals(30.86, patient(poids = 100.0, taille = 180.0).imc!!, 0.01)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Categories IMC selon OMS
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `categorie IMC 17 = Insuffisance ponderale`() {
        // 17 < 18.5
        assertEquals("Insuffisance pondérale", patient(poids = 45.0, taille = 162.0).categorieImc)
        // verif: 45 / 1.62² = 17.15
    }

    @Test
    fun `categorie IMC 22 = Poids normal`() {
        assertEquals("Poids normal", patient(poids = 60.0, taille = 165.0).categorieImc)
    }

    @Test
    fun `categorie IMC 28 = Surpoids`() {
        // 28 is in [25, 30)
        assertEquals("Surpoids", patient(poids = 85.0, taille = 175.0).categorieImc)
        // verif: 85 / 1.75² = 27.76
    }

    @Test
    fun `categorie IMC 32 = Obesite classe I`() {
        assertEquals("Obésité classe I", patient(poids = 95.0, taille = 172.0).categorieImc)
        // verif: 95 / 1.72² = 32.11
    }

    @Test
    fun `categorie IMC 37 = Obesite classe II`() {
        assertEquals("Obésité classe II", patient(poids = 110.0, taille = 172.0).categorieImc)
        // 110 / 1.72² = 37.18
    }

    @Test
    fun `categorie IMC 45 = Obesite classe III`() {
        assertEquals("Obésité classe III", patient(poids = 130.0, taille = 170.0).categorieImc)
        // 130 / 1.7² = 44.98
    }

    @Test
    fun `categorie IMC null si donnees manquantes`() {
        assertNull(patient(poids = 70.0).categorieImc)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tour de taille — risque metabolique (criteres IDF)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `risque tour taille HOMME 80cm = Normal`() {
        assertEquals("Normal", patient(tourDeTaille = 80.0, sexe = Sexe.HOMME).risqueTourDeTaille)
    }

    @Test
    fun `risque tour taille HOMME 96cm = Risque accru`() {
        // [94, 102) chez l'homme
        assertEquals("Risque accru", patient(tourDeTaille = 96.0, sexe = Sexe.HOMME).risqueTourDeTaille)
    }

    @Test
    fun `risque tour taille HOMME 110cm = Risque eleve`() {
        assertEquals("Risque élevé", patient(tourDeTaille = 110.0, sexe = Sexe.HOMME).risqueTourDeTaille)
    }

    @Test
    fun `risque tour taille FEMME 75cm = Normal`() {
        assertEquals("Normal", patient(tourDeTaille = 75.0, sexe = Sexe.FEMME).risqueTourDeTaille)
    }

    @Test
    fun `risque tour taille FEMME 82cm = Risque accru`() {
        // [80, 88) chez la femme
        assertEquals("Risque accru", patient(tourDeTaille = 82.0, sexe = Sexe.FEMME).risqueTourDeTaille)
    }

    @Test
    fun `risque tour taille FEMME 95cm = Risque eleve`() {
        assertEquals("Risque élevé", patient(tourDeTaille = 95.0, sexe = Sexe.FEMME).risqueTourDeTaille)
    }

    @Test
    fun `risque tour taille null si donnee manquante`() {
        assertNull(patient(sexe = Sexe.HOMME).risqueTourDeTaille)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Seuils exacts (frontiere des categories — non-regression)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `IMC frontiere 18_5 = Poids normal`() {
        // exact 18.5 doit etre "Poids normal" (>= 18.5)
        // 50.5 / 1.65² = 18.54
        assertEquals("Poids normal", patient(poids = 50.5, taille = 165.0).categorieImc)
    }

    @Test
    fun `IMC frontiere 25 = Surpoids`() {
        // 72.3 / 1.7² = 25.02
        assertEquals("Surpoids", patient(poids = 72.3, taille = 170.0).categorieImc)
    }

    @Test
    fun `IMC frontiere 30 = Obesite classe I`() {
        // 86.8 / 1.7² = 30.03
        assertEquals("Obésité classe I", patient(poids = 86.8, taille = 170.0).categorieImc)
    }
}
