package com.diabeto.util

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor
import androidx.core.content.ContextCompat

/**
 * Verrouillage de l'application au demarrage et au retour en avant-plan.
 *
 * Strategie :
 *  - On utilise BiometricPrompt avec authenticators = BIOMETRIC_WEAK |
 *    DEVICE_CREDENTIAL : Android propose automatiquement la meilleure
 *    methode disponible sur l'appareil (empreinte digitale en priorite,
 *    PIN / schema / mot de passe systeme en fallback).
 *  - Si aucun credential n'est configure sur l'appareil, l'utilisateur est
 *    informe et l'authentification est consideree "passee" (sinon on le
 *    bloque definitivement hors de l'app).
 *
 * Recommandation Android (BiometricPrompt) : appeler depuis une
 * FragmentActivity (MainActivity en herite via ComponentActivity ⊂
 * FragmentActivity).
 */
object AppLockManager {

    /**
     * Statut du systeme de verrouillage sur l'appareil.
     */
    enum class CredentialAvailability {
        /** Au moins une methode (biometrie OU credential systeme) est disponible. */
        AVAILABLE,
        /** Aucun credential n'est configure (l'utilisateur n'a ni PIN ni biometrie). */
        NONE_ENROLLED,
        /** Le hardware ne supporte aucun credential (rare sur Android moderne). */
        UNSUPPORTED
    }

    /**
     * Combinaison d'authenticators acceptes :
     *  - BIOMETRIC_WEAK : empreinte digitale, reconnaissance faciale 2D
     *  - DEVICE_CREDENTIAL : code PIN / schema / mot de passe systeme
     *
     * BIOMETRIC_STRONG (capteurs class 3) n'est pas disponible avant API 30
     * en combinaison avec DEVICE_CREDENTIAL — on prend WEAK pour rester
     * compatible Android 8 → 14.
     */
    @Suppress("DEPRECATION")
    private val authenticators: Int =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun checkAvailability(activity: FragmentActivity): CredentialAvailability {
        val mgr = BiometricManager.from(activity)
        return when (mgr.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> CredentialAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> CredentialAvailability.NONE_ENROLLED
            else -> CredentialAvailability.UNSUPPORTED
        }
    }

    /**
     * Lance la fenetre d'authentification systeme.
     *
     * @param onSuccess invoque sur le thread principal apres validation
     * @param onError   invoque sur erreur (utilisateur a annule, hardware
     *                  indisponible, trop de tentatives) avec un message court
     * @param onFailed  invoque quand l'authentification echoue mais que le
     *                  prompt reste ouvert (mauvaise empreinte par ex.) — on
     *                  ne fait rien, le prompt va re-essayer tout seul
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Deverrouiller DiaSmart",
        subtitle: String = "Confirmez votre identite",
        description: String = "Utilisez votre empreinte, votre code PIN, votre schema ou votre mot de passe pour acceder a l'application.",
        onSuccess: () -> Unit,
        onError: (msg: String) -> Unit,
        onFailed: () -> Unit = {}
    ) {
        val executor: Executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                onFailed()
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setAllowedAuthenticators(authenticators)
            // Le bouton "Annuler" est masque automatiquement quand on
            // autorise DEVICE_CREDENTIAL : Android impose le fallback.
            .build()
        prompt.authenticate(info)
    }

    /**
     * Sur certains constructeurs (Huawei EMUI, Xiaomi MIUI), le combine
     * BIOMETRIC_STRONG | DEVICE_CREDENTIAL est buggy avant Android 11. On
     * detecte la version pour ajuster le message d'aide cote UI.
     */
    val supportsBiometricCombo: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
}
