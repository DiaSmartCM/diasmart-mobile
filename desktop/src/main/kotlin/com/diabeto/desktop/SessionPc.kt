package com.diabeto.desktop

import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Session gardee sur ce PC apres la premiere connexion.
 *
 * Le jeton de reconnexion Firebase est chiffre (AES-GCM) avec une cle tiree
 * du mot de passe local (PBKDF2). Sans ce mot de passe, le jeton est
 * illisible : ni l'email ni le mot de passe du compte ne sont stockes.
 */
object SessionPc {
    const val LONGUEUR_MIN = 6

    data class Enregistree(val uid: String, val email: String, val nomSoignant: String, val nomEtablissement: String)

    private val prefs = Preferences.userRoot().node("diasmart/session_pc")
    private val hasard = SecureRandom()

    fun existe(): Boolean = prefs.get("uid", null) != null && prefs.get("jeton", null) != null

    fun lire(): Enregistree? {
        val uid = prefs.get("uid", null) ?: return null
        if (prefs.get("jeton", null) == null) return null
        return Enregistree(uid, prefs.get("email", ""), prefs.get("nomSoignant", ""), prefs.get("nomEtablissement", ""))
    }

    /** Enregistre la session, chiffree avec le mot de passe local. */
    fun enregistrer(uid: String, email: String, nomSoignant: String, refreshToken: String, motDePasseLocal: String) {
        val sel = ByteArray(16).also(hasard::nextBytes)
        val iv = ByteArray(12).also(hasard::nextBytes)
        val chiffre = chiffreur(Cipher.ENCRYPT_MODE, motDePasseLocal, sel, iv).doFinal(refreshToken.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        prefs.put("uid", uid)
        prefs.put("email", email)
        prefs.put("nomSoignant", nomSoignant)
        prefs.put("sel", b64.encodeToString(sel))
        prefs.put("iv", b64.encodeToString(iv))
        prefs.put("jeton", b64.encodeToString(chiffre))
        prefs.flush()
    }

    /** Le jeton de reconnexion, ou null si le mot de passe local est faux. */
    fun ouvrir(motDePasseLocal: String): String? {
        val b64 = Base64.getDecoder()
        val sel = prefs.get("sel", null)?.let(b64::decode) ?: return null
        val iv = prefs.get("iv", null)?.let(b64::decode) ?: return null
        val jeton = prefs.get("jeton", null)?.let(b64::decode) ?: return null
        return try {
            String(chiffreur(Cipher.DECRYPT_MODE, motDePasseLocal, sel, iv).doFinal(jeton), Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            null
        }
    }

    /** Nom affiche sur l'ecran d'ouverture (mis a jour apres chaque chargement). */
    fun retenirEtablissement(nom: String?) {
        if (!existe()) return
        prefs.put("nomEtablissement", nom.orEmpty())
        prefs.flush()
    }

    /** Oublie ce compte sur ce PC (il faudra l'email et le mot de passe). */
    fun oublier() {
        listOf("uid", "email", "nomSoignant", "nomEtablissement", "sel", "iv", "jeton").forEach(prefs::remove)
        prefs.flush()
        LimiteurPc("mdp_local_pc").reussite()
    }

    private fun chiffreur(mode: Int, mdp: String, sel: ByteArray, iv: ByteArray): Cipher {
        val spec = PBEKeySpec(mdp.toCharArray(), sel, 210_000, 256)
        val cle = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        spec.clearPassword()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, cle, GCMParameterSpec(128, iv)) }
    }
}
