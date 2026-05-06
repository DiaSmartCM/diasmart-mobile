package com.diabeto.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Methodes de verrouillage proposees a l'utilisateur.
 * - NONE      : pas de verrouillage
 * - BIOMETRIC : capteur biometrique systeme (empreinte / face) avec fallback
 *               systeme automatique si configure dans Android
 * - PIN       : code PIN a 4 chiffres saisis dans l'app (hash stocke localement)
 * - PASSWORD  : mot de passe alphanumerique saisi dans l'app (hash stocke localement)
 */
enum class AppLockMethod {
    NONE, BIOMETRIC, PIN, PASSWORD
}

/**
 * Derivation de cle (hash) pour stocker les credentials PIN ou mot de passe
 * sans exposer la valeur en clair.
 *
 * Algorithme : PBKDF2WithHmacSHA256, 100 000 iterations, sel 16 octets.
 * Le hash + sel sont stockes dans DataStore (Base64). Verification :
 * recalculer le hash avec le sel persiste et comparer en temps constant.
 *
 * 100 000 iterations garantissent que meme en cas d'extraction du DataStore,
 * l'attaquant doit passer ~100 ms par tentative pour brute-forcer un PIN
 * (soit ~17 minutes pour 10 000 PIN possibles, plus si le mot de passe est
 * complexe).
 */
object AppLockCredential {

    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    /** Hash + sel encodes en Base64 → "salt$hash" pour stockage. */
    data class HashedCredential(val saltB64: String, val hashB64: String) {
        fun serialize(): String = "$saltB64\$$hashB64"
        companion object {
            fun parse(serialized: String): HashedCredential? {
                val parts = serialized.split("$")
                if (parts.size != 2) return null
                return HashedCredential(parts[0], parts[1])
            }
        }
    }

    fun hash(secret: String): HashedCredential {
        require(secret.isNotEmpty()) { "Secret vide" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(secret, salt)
        return HashedCredential(
            saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        )
    }

    fun verify(secret: String, stored: HashedCredential): Boolean {
        if (secret.isEmpty()) return false
        val salt = runCatching { Base64.decode(stored.saltB64, Base64.NO_WRAP) }.getOrNull()
            ?: return false
        val expected = runCatching { Base64.decode(stored.hashB64, Base64.NO_WRAP) }.getOrNull()
            ?: return false
        val computed = derive(secret, salt)
        return constantTimeEquals(computed, expected)
    }

    private fun derive(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }
}
