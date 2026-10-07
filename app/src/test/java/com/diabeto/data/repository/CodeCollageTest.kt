package com.diabeto.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/** Coller tout le message recu dans le champ du code : seul le code est garde. */
class CodeCollageTest {

    private fun code(saisie: String) = EtablissementRepository.codeDepuisCollage(saisie)

    @Test
    fun messageDePartage() {
        val m = "Bonjour, Centre Mbalmayo vous suit avec l'application DiaSmart. Installez DiaSmart, " +
            "puis dans Parametres > Etablissement de sante, tapez le code : K7MP3QXA"
        assertEquals("K7MP3QXA", code(m))
    }

    @Test
    fun codeSeulOuAvecEspace() {
        assertEquals("K7MP 3QXA", code("K7MP 3QXA"))
        assertEquals("K7MP3QXA", code("Voici le code K7MP 3QXA merci"))
    }

    @Test
    fun motsDeHuitLettresIgnores() {
        assertEquals("K7MP3QXA", code("le code K7MP3QXA pour DIASMART messages"))
        assertEquals("K7MP3QXA", code("K7MP3QXA est votre code d'accès svp"))
    }

    @Test
    fun texteSansCodeInchange() {
        val t = "bonjour docteur comment allez vous"
        assertEquals(t, code(t))
    }
}
