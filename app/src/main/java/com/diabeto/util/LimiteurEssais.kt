package com.diabeto.util

import android.content.Context

/**
 * Limite les essais ratés (code PIN, mot de passe du verrou, code
 * d'invitation...). Les [essaisLibres] premiers échecs passent ; ensuite
 * chaque nouvel échec bloque de plus en plus longtemps : 30 s, 1 min,
 * 5 min, 15 min, puis 1 h.
 *
 * Le compteur est gardé dans les préférences du téléphone : fermer et
 * rouvrir l'appli ne le remet pas à zéro. Il repart à zéro après une réussite.
 */
class LimiteurEssais(
    context: Context,
    private val cle: String,
    private val essaisLibres: Int = 5
) {
    companion object {
        private const val PREFS = "limiteur_essais"
        private val DELAIS_MS = longArrayOf(30_000L, 60_000L, 5 * 60_000L, 15 * 60_000L, 60 * 60_000L)

        /** "45 s", "3 min", "1 h" : pour les messages d'attente. */
        fun formaterAttente(ms: Long): String {
            val s = (ms + 999) / 1000
            return when {
                s < 60 -> "$s s"
                s < 3600 -> "${(s + 59) / 60} min"
                else -> "${(s + 3599) / 3600} h"
            }
        }
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cleEchecs = "$cle.echecs"
    private val cleBlocage = "$cle.bloqueJusqua"

    /** Temps d'attente restant avant le prochain essai, 0 si on peut essayer. */
    fun attenteRestanteMs(): Long =
        (prefs.getLong(cleBlocage, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun essaisRestantsAvantBlocage(): Int =
        (essaisLibres - prefs.getInt(cleEchecs, 0)).coerceAtLeast(0)

    /** Note un échec. Renvoie la durée du blocage qui commence (0 si aucun). */
    fun echec(): Long {
        val n = prefs.getInt(cleEchecs, 0) + 1
        val delai = if (n >= essaisLibres) DELAIS_MS[(n - essaisLibres).coerceAtMost(DELAIS_MS.lastIndex)] else 0L
        prefs.edit()
            .putInt(cleEchecs, n)
            .putLong(cleBlocage, if (delai > 0) System.currentTimeMillis() + delai else 0L)
            .apply()
        return delai
    }

    fun reussite() {
        prefs.edit().remove(cleEchecs).remove(cleBlocage).apply()
    }

    /** Message à afficher après un échec : essais restants ou durée du blocage. */
    fun messageApresEchec(debut: String): String {
        val attente = attenteRestanteMs()
        return if (attente > 0) "$debut Trop d'essais : réessaie dans ${formaterAttente(attente)}."
        else {
            val restants = essaisRestantsAvantBlocage()
            if (restants in 1..2) "$debut Encore $restants essai(s) avant un blocage temporaire." else debut
        }
    }

    fun messageBlocage(): String = "Trop d'essais : réessaie dans ${formaterAttente(attenteRestanteMs())}."
}
