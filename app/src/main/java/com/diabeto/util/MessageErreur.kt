package com.diabeto.util

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Traduit une exception technique en une phrase utile pour un patient.
 *
 * Pourquoi ce fichier existe
 * --------------------------
 * L'application affichait le message brut des exceptions. L'utilisateur lisait
 * des choses comme :
 *
 *     Upload echoue: IllegalStateException: HTTP 502 — {"error":"supabase_unreachable"}
 *
 * C'est doublement mauvais. Cela n'aide personne — un patient diabetique ne sait
 * pas quoi faire d'un code 502 — et cela expose la structure interne du service :
 * noms de classes, prestataires utilises, forme des reponses serveur.
 *
 * Principe retenu : dire ce qui s'est passe et ce que l'utilisateur peut faire.
 * Jamais le nom d'une exception, jamais un code HTTP, jamais un nom de service.
 * Le detail technique part dans les journaux, ou les developpeurs le trouveront.
 */
object MessageErreur {

    /** Message par defaut, quand rien de plus precis ne peut etre dit. */
    private const val GENERIQUE =
        "Une erreur est survenue. Réessayez dans un moment."

    private const val RESEAU =
        "Pas de connexion internet. Vérifiez votre réseau et réessayez."

    private const val LENT =
        "La connexion est trop lente. Réessayez quand le réseau sera meilleur."

    private const val SERVEUR =
        "Le service est momentanément indisponible. Réessayez dans quelques minutes."

    private const val DROITS =
        "Vous n'avez pas accès à cette information."

    private const val SESSION =
        "Votre session a expiré. Reconnectez-vous."

    /**
     * @param contexte courte phrase decrivant l'action tentee, ex. « Envoi du
     *        rapport ». Elle prefixe le message pour situer l'echec.
     */
    fun lisible(e: Throwable?, contexte: String = ""): String {
        val message = pour(e)
        return if (contexte.isBlank()) message else "$contexte : ${message.lowerFirst()}"
    }

    private fun String.lowerFirst(): String =
        if (isEmpty()) this else this[0].lowercaseChar() + substring(1)

    private fun pour(e: Throwable?): String {
        if (e == null) return GENERIQUE

        // On parcourt toute la chaine de causes : l'exception visible est
        // souvent un emballage sans interet ("IllegalStateException"), la
        // cause reelle se trouve en dessous.
        val texte = buildString {
            var t: Throwable? = e
            var profondeur = 0
            while (t != null && profondeur < 6) {
                append(t.javaClass.simpleName).append(' ')
                append(t.message ?: "").append(' ')
                t = t.cause
                profondeur++
            }
        }.lowercase()

        return when {
            // Reseau absent ou DNS injoignable
            e.chaineContient<UnknownHostException>() ||
                texte.contains("unable to resolve host") ||
                texte.contains("no address associated") ||
                texte.contains("unreachable") ||
                texte.contains("network is unreachable") -> RESEAU

            // Delai depasse
            e.chaineContient<SocketTimeoutException>() ||
                texte.contains("timeout") ||
                texte.contains("timed out") -> LENT

            // Serveur en difficulte
            texte.contains("http 5") ||
                texte.contains("502") || texte.contains("503") ||
                texte.contains("504") ||
                texte.contains("unavailable") ||
                texte.contains("internal error") -> SERVEUR

            // Droits refuses
            texte.contains("permission_denied") ||
                texte.contains("permission denied") ||
                texte.contains("missing or insufficient permissions") ||
                texte.contains("http 403") -> DROITS

            // Session perdue
            texte.contains("unauthenticated") ||
                texte.contains("http 401") ||
                texte.contains("aucun utilisateur") ||
                texte.contains("non connecte") ||
                texte.contains("non connecté") -> SESSION

            // Autre probleme reseau generique
            e.chaineContient<IOException>() -> RESEAU

            else -> GENERIQUE
        }
    }

    private inline fun <reified T : Throwable> Throwable.chaineContient(): Boolean {
        var t: Throwable? = this
        var profondeur = 0
        while (t != null && profondeur < 6) {
            if (t is T) return true
            t = t.cause
            profondeur++
        }
        return false
    }
}
