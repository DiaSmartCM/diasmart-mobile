package com.diabeto.monitoring

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Helper centralise pour Firebase Crashlytics.
 *
 * v2.1.42+ : ajoute des custom keys + breadcrumbs sur tous les ecrans
 * critiques et tous les chemins critiques pour faciliter le debug post-mortem.
 *
 * Utilisation typique dans un ecran :
 * ```
 * LaunchedEffect(Unit) {
 *     CrashlyticsLogger.setScreen("GlucoseTracking")
 *     CrashlyticsLogger.setCustomKey("patientId", patientId.toString())
 * }
 * ```
 *
 * Pour logger une exception non-fatale :
 * ```
 * try { ... } catch (e: Exception) {
 *     CrashlyticsLogger.logException(e, screen = "GlucoseTracking", action = "insertReading")
 * }
 * ```
 *
 * Toutes les methodes sont safe a appeler avant l'init de Firebase
 * (catch des erreurs silencieusement, n'affecte pas l'UX).
 */
object CrashlyticsLogger {

    private const val TAG = "CrashlyticsLogger"

    /**
     * Set the current screen name. Visible dans le dashboard Crashlytics
     * sous la colonne "screen" lors d'un crash.
     */
    fun setScreen(screen: String) {
        runCatching {
            FirebaseCrashlytics.getInstance().setCustomKey("screen", screen)
            FirebaseCrashlytics.getInstance().log("nav: $screen")
        }.onFailure {
            Log.w(TAG, "setScreen failed: ${it.message}")
        }
    }

    /**
     * Set a custom key/value pair. Visible dans le dashboard Crashlytics
     * lors d'un crash.
     */
    fun setCustomKey(key: String, value: String) {
        runCatching {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        }.onFailure {
            Log.w(TAG, "setCustomKey failed: ${it.message}")
        }
    }

    fun setCustomKey(key: String, value: Int) {
        runCatching {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        }.onFailure { /* silent */ }
    }

    fun setCustomKey(key: String, value: Boolean) {
        runCatching {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        }.onFailure { /* silent */ }
    }

    /**
     * Set the user ID (Firebase UID) — apparait dans Crashlytics.
     */
    fun setUserId(uid: String?) {
        runCatching {
            if (uid != null) {
                FirebaseCrashlytics.getInstance().setUserId(uid)
            }
        }.onFailure { /* silent */ }
    }

    /**
     * Set the user role (PATIENT / MEDECIN) — pour filtrer les crashs par role.
     */
    fun setUserRole(role: String) {
        setCustomKey("role", role)
    }

    /**
     * Log a breadcrumb-style action (not a crash, just a navigation event).
     */
    fun log(message: String) {
        runCatching {
            FirebaseCrashlytics.getInstance().log(message)
        }.onFailure { /* silent */ }
    }

    /**
     * Log a non-fatal exception. Apparait dans Crashlytics sous "Issues" mais
     * n'arrete pas l'app.
     */
    fun logException(
        throwable: Throwable,
        screen: String? = null,
        action: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            screen?.let { crashlytics.setCustomKey("screen", it) }
            action?.let { crashlytics.setCustomKey("action", it) }
            metadata.forEach { (k, v) -> crashlytics.setCustomKey(k, v) }
            crashlytics.recordException(throwable)
        }.onFailure {
            Log.w(TAG, "logException failed: ${it.message}")
        }
    }

    /**
     * Indique a Crashlytics de DESACTIVER l'envoi automatique des crash reports
     * (utile pour les sessions de dev / pre-prod). Par defaut Crashlytics
     * envoie automatiquement en release builds.
     */
    fun setCollectionEnabled(enabled: Boolean) {
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        }.onFailure { /* silent */ }
    }
}
