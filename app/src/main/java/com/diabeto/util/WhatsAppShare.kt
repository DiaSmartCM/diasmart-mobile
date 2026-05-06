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
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        // Si le numero commence par 00, retirer
        val noTrunk = if (digits.startsWith("00")) digits.drop(2) else digits
        // Si le numero local commence par 6/7 (mobile Cameroun) sans indicatif → prefix
        return if (noTrunk.length in 8..9 && (noTrunk.startsWith("6") || noTrunk.startsWith("2"))) {
            defaultCountryCode + noTrunk
        } else {
            noTrunk
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
