package com.diabeto.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "diasmart_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AppLanguage(val code: String, val displayName: String) {
    FRENCH("fr", "Français"),
    ENGLISH("en", "English"),
    ARABIC("ar", "العربية")
}
enum class GlucoseUnit(val label: String, val shortLabel: String) {
    MG_DL("mg/dL", "mg/dL"),
    MMOL_L("mmol/L", "mmol/L");

    fun convert(valueInMgDl: Double): Double = when (this) {
        MG_DL -> valueInMgDl
        MMOL_L -> valueInMgDl / 18.0182
    }

    fun format(valueInMgDl: Double): String = when (this) {
        MG_DL -> "${valueInMgDl.toInt()}"
        MMOL_L -> "%.1f".format(valueInMgDl / 18.0182)
    }
}
enum class MeasureType(val displayName: String) {
    CAPILLARY("Capillaire (doigt)"),
    CGM("CGM (capteur continu)"),
    VENOUS("Veineux (laboratoire)")
}

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val MEDICATION_REMINDERS = booleanPreferencesKey("medication_reminders")
        val MEASUREMENT_REMINDERS = booleanPreferencesKey("measurement_reminders")
        val APPOINTMENT_REMINDERS = booleanPreferencesKey("appointment_reminders")
        val GLUCOSE_UNIT = stringPreferencesKey("glucose_unit")
        val MEASURE_TYPE = stringPreferencesKey("measure_type")
        val TARGET_MIN = stringPreferencesKey("glycemic_target_min")
        val TARGET_MAX = stringPreferencesKey("glycemic_target_max")
        val PENDING_UPDATE_VERSION = stringPreferencesKey("pending_update_version")
        val PENDING_UPDATE_URL = stringPreferencesKey("pending_update_url")
        val PENDING_UPDATE_CHANGELOG = stringPreferencesKey("pending_update_changelog")
        val PENDING_UPDATE_FORCE = booleanPreferencesKey("pending_update_force")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_METHOD = stringPreferencesKey("app_lock_method")
        val APP_LOCK_CREDENTIAL = stringPreferencesKey("app_lock_credential")
        val ONBOARDING_DASHBOARD_SEEN = booleanPreferencesKey("onboarding_dashboard_seen")
        val ONBOARDING_GLUCOSE_SEEN = booleanPreferencesKey("onboarding_glucose_seen")
        val ONBOARDING_ROLLY_SEEN = booleanPreferencesKey("onboarding_rolly_seen")
        val ONBOARDING_MESSAGERIE_SEEN = booleanPreferencesKey("onboarding_messagerie_seen")
        val ONBOARDING_REPORTS_SEEN = booleanPreferencesKey("onboarding_reports_seen")
        val LAST_SYNC_AT = androidx.datastore.preferences.core.longPreferencesKey("last_sync_at")
    }

    // v2.1.46 : tutoriels contextuels par ecran. Un flag DataStore par ecran.
    val onboardingGlucoseSeen: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_GLUCOSE_SEEN] ?: false }
    suspend fun markOnboardingGlucoseSeen() { context.dataStore.edit { it[Keys.ONBOARDING_GLUCOSE_SEEN] = true } }

    val onboardingRollySeen: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_ROLLY_SEEN] ?: false }
    suspend fun markOnboardingRollySeen() { context.dataStore.edit { it[Keys.ONBOARDING_ROLLY_SEEN] = true } }

    val onboardingMessagerieSeen: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_MESSAGERIE_SEEN] ?: false }
    suspend fun markOnboardingMessagerieSeen() { context.dataStore.edit { it[Keys.ONBOARDING_MESSAGERIE_SEEN] = true } }

    val onboardingReportsSeen: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_REPORTS_SEEN] ?: false }
    suspend fun markOnboardingReportsSeen() { context.dataStore.edit { it[Keys.ONBOARDING_REPORTS_SEEN] = true } }

    /**
     * v2.1.44 : timestamp (epoch ms) du dernier sync reussi vers Firestore.
     * Utilise par BatchSyncWorker pour ne push que les docs modifies depuis.
     * 0 = jamais sync (declenche un full backup au prochain run).
     */
    val lastSyncAt: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_SYNC_AT] ?: 0L
    }

    suspend fun setLastSyncAt(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNC_AT] = timestamp }
    }

    /**
     * v2.1.42 : true des que l'utilisateur a vu (ou skipped) l'onboarding du
     * dashboard. Empeche de re-afficher le tutoriel a chaque ouverture.
     */
    val onboardingDashboardSeen: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_DASHBOARD_SEEN] ?: false
    }

    suspend fun markOnboardingDashboardSeen() {
        context.dataStore.edit { it[Keys.ONBOARDING_DASHBOARD_SEEN] = true }
    }

    /**
     * Verrouillage de l'application au demarrage / retour en avant-plan.
     * - true : l'utilisateur doit s'authentifier (empreinte / PIN / mot de passe)
     * - false (defaut) : pas de verrouillage.
     */
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_LOCK_ENABLED] ?: false
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    /**
     * Methode de verrouillage choisie par l'utilisateur :
     * BIOMETRIC | PIN | PASSWORD | NONE.
     */
    val appLockMethod: Flow<com.diabeto.security.AppLockMethod> = context.dataStore.data.map { prefs ->
        try {
            com.diabeto.security.AppLockMethod.valueOf(
                prefs[Keys.APP_LOCK_METHOD] ?: com.diabeto.security.AppLockMethod.NONE.name
            )
        } catch (_: Exception) {
            com.diabeto.security.AppLockMethod.NONE
        }
    }

    suspend fun setAppLockMethod(method: com.diabeto.security.AppLockMethod) {
        context.dataStore.edit { it[Keys.APP_LOCK_METHOD] = method.name }
    }

    /**
     * Credential serialise (sel\$hash en Base64) pour les methodes PIN et
     * PASSWORD. La valeur en clair n'est JAMAIS stockee.
     */
    val appLockCredential: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_LOCK_CREDENTIAL]?.takeIf { it.isNotBlank() }
    }

    suspend fun setAppLockCredential(serialized: String?) {
        context.dataStore.edit {
            if (serialized.isNullOrBlank()) it.remove(Keys.APP_LOCK_CREDENTIAL)
            else it[Keys.APP_LOCK_CREDENTIAL] = serialized
        }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        try {
            ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val language: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        try {
            AppLanguage.valueOf(prefs[Keys.LANGUAGE] ?: AppLanguage.FRENCH.name)
        } catch (_: Exception) {
            AppLanguage.FRENCH
        }
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
    }

    val medicationReminders: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.MEDICATION_REMINDERS] ?: true
    }

    val measurementReminders: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.MEASUREMENT_REMINDERS] ?: true
    }

    val appointmentReminders: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.APPOINTMENT_REMINDERS] ?: true
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setMedicationReminders(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MEDICATION_REMINDERS] = enabled }
    }

    suspend fun setMeasurementReminders(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MEASUREMENT_REMINDERS] = enabled }
    }

    suspend fun setAppointmentReminders(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APPOINTMENT_REMINDERS] = enabled }
    }

    val glucoseUnit: Flow<GlucoseUnit> = context.dataStore.data.map { prefs ->
        try {
            GlucoseUnit.valueOf(prefs[Keys.GLUCOSE_UNIT] ?: GlucoseUnit.MG_DL.name)
        } catch (_: Exception) { GlucoseUnit.MG_DL }
    }

    val measureType: Flow<MeasureType> = context.dataStore.data.map { prefs ->
        try {
            MeasureType.valueOf(prefs[Keys.MEASURE_TYPE] ?: MeasureType.CAPILLARY.name)
        } catch (_: Exception) { MeasureType.CAPILLARY }
    }

    val targetMin: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[Keys.TARGET_MIN]?.toDoubleOrNull() ?: 70.0
    }

    val targetMax: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[Keys.TARGET_MAX]?.toDoubleOrNull() ?: 180.0
    }

    suspend fun setGlucoseUnit(unit: GlucoseUnit) {
        context.dataStore.edit { it[Keys.GLUCOSE_UNIT] = unit.name }
    }

    suspend fun setMeasureType(type: MeasureType) {
        context.dataStore.edit { it[Keys.MEASURE_TYPE] = type.name }
    }

    suspend fun setGlycemicTarget(min: Double, max: Double) {
        context.dataStore.edit {
            it[Keys.TARGET_MIN] = min.toString()
            it[Keys.TARGET_MAX] = max.toString()
        }
    }

    // ── Pending app update ──

    data class PendingUpdate(
        val version: String,
        val url: String,
        val changelog: String,
        val force: Boolean
    )

    val pendingUpdate: Flow<PendingUpdate?> = context.dataStore.data.map { prefs ->
        val version = prefs[Keys.PENDING_UPDATE_VERSION]
        val url = prefs[Keys.PENDING_UPDATE_URL]
        if (!version.isNullOrBlank() && !url.isNullOrBlank()) {
            PendingUpdate(
                version = version,
                url = url,
                changelog = prefs[Keys.PENDING_UPDATE_CHANGELOG] ?: "",
                force = prefs[Keys.PENDING_UPDATE_FORCE] ?: false
            )
        } else null
    }

    suspend fun setPendingUpdate(version: String, url: String, changelog: String, force: Boolean) {
        context.dataStore.edit {
            it[Keys.PENDING_UPDATE_VERSION] = version
            it[Keys.PENDING_UPDATE_URL] = url
            it[Keys.PENDING_UPDATE_CHANGELOG] = changelog
            it[Keys.PENDING_UPDATE_FORCE] = force
        }
    }

    suspend fun clearPendingUpdate() {
        context.dataStore.edit {
            it.remove(Keys.PENDING_UPDATE_VERSION)
            it.remove(Keys.PENDING_UPDATE_URL)
            it.remove(Keys.PENDING_UPDATE_CHANGELOG)
            it.remove(Keys.PENDING_UPDATE_FORCE)
        }
    }
}
