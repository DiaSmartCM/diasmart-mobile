package com.diabeto.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Partage via WhatsApp en utilisant le numero du destinataire.
 *
 * Strategie : on construit une URL `wa.me/{numero}?text={message}`. Quand
 * l'Intent ACTION_VIEW est lance :
 *   - si WhatsApp est installe, il ouvre directement la conversation avec
 *     le numero ou propose de creer un chat
 *   - si le numero n'est pas inscrit sur WhatsApp, WhatsApp lui-meme affiche
 *     "Le numero n'utilise pas WhatsApp" — l'utilisateur n'a rien a verifier
 *   - si WhatsApp n'est pas installe, le navigateur ouvre wa.me qui propose
 *     l'installation
 *
 * Le numero doit etre au format international (ex: +237691234567).
 * On nettoie automatiquement les espaces, tirets et parentheses.
 */
object WhatsAppShare {

    /**
     * Normalise un numero de telephone au format E.164 sans le '+' :
     *  - garde les chiffres uniquement
     *  - ajoute l'indicatif Cameroun (237) si le numero local n'en a pas
     */
    fun normalizePhone(raw: String, defaultCountryCode: String = "237"): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return ""

        // Prefixe international : "00237..." ou "+237..." (le + a deja saute).
        if (digits.startsWith("00")) digits = digits.drop(2)

        // v2.1.80 : le zero de tete manquait au traitement. Un numero note
        // "0691234567" — forme courante d'un carnet d'adresses — gardait son
        // zero, n'entrait dans aucun cas et partait tel quel vers wa.me, qui
        // ne reconnaissait pas le destinataire. On le retire d'abord, comme
        // n'importe quel prefixe d'acheminement national.
        while (digits.length > 9 && digits.startsWith("0")) digits = digits.drop(1)
        if (digits.length in 9..10 && digits.startsWith("0")) digits = digits.drop(1)

        // Deja au format international : on n'y touche pas.
        if (digits.startsWith(defaultCountryCode) && digits.length >= 11) return digits

        // Numero local camerounais : 9 chiffres (mobile en 6, fixe en 2), ou
        // 8 chiffres pour les anciennes numerotations encore en circulation.
        return if (digits.length in 8..9 && (digits.startsWith("6") || digits.startsWith("2"))) {
            defaultCountryCode + digits
        } else {
            digits
        }
    }

    fun isLikelyPhoneNumber(raw: String): Boolean {
        val n = normalizePhone(raw)
        return n.length in 9..15
    }

    /**
     * Ouvre WhatsApp avec le destinataire pre-rempli + un message contenant
     * l'URL de telechargement et un libelle court.
     */
    fun share(
        context: Context,
        phone: String,
        fileName: String,
        downloadUrl: String,
        introMessage: String = ""
    ) {
        val normalized = normalizePhone(phone)
        if (normalized.isEmpty()) {
            Toast.makeText(
                context,
                "Numero de telephone manquant ou invalide.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val text = buildString {
            if (introMessage.isNotBlank()) {
                appendLine(introMessage)
                appendLine()
            }
            appendLine("Document partage via DiaSmart : $fileName")
            append(downloadUrl)
        }
        val url = "https://wa.me/$normalized?text=" + Uri.encode(text)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Impossible d'ouvrir WhatsApp : ${e::class.java.simpleName}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
