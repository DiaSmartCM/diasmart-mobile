package com.diabeto.util

/**
 * Regles communes des essais rates (code PIN, mot de passe, codes
 * d'invitation, connexion PC) : les [ESSAIS_LIBRES] premiers echecs passent,
 * puis chaque nouvel echec bloque de plus en plus longtemps.
 * Le stockage du compteur depend de l'appareil (Android : SharedPreferences,
 * PC : preferences Java).
 */
object ReglesEssais {
    const val ESSAIS_LIBRES = 5
    val DELAIS_MS = longArrayOf(30_000L, 60_000L, 5 * 60_000L, 15 * 60_000L, 60 * 60_000L)

    /** Duree du blocage apres le [nbEchecs]-ieme echec (0 si aucun). */
    fun delaiApres(nbEchecs: Int, essaisLibres: Int = ESSAIS_LIBRES): Long =
        if (nbEchecs >= essaisLibres) DELAIS_MS[(nbEchecs - essaisLibres).coerceAtMost(DELAIS_MS.lastIndex)] else 0L

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
