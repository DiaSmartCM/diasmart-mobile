package com.diabeto.ui.viewmodel

import android.util.Log
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.model.UserRole
import com.diabeto.data.repository.AppLanguage
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.CloudBackupRepository
import com.diabeto.data.repository.GlucoseRepository
import com.diabeto.data.repository.GlucoseUnit
import com.diabeto.data.repository.MeasureType
import com.diabeto.data.repository.PatientRepository
import com.diabeto.data.repository.PreferencesRepository
import com.diabeto.data.repository.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.FRENCH,
    val notificationsEnabled: Boolean = true,
    val medicationReminders: Boolean = true,
    val measurementReminders: Boolean = true,
    val appointmentReminders: Boolean = true,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL,
    val measureType: MeasureType = MeasureType.CAPILLARY,
    val targetMin: Double = 70.0,
    val targetMax: Double = 180.0,
    // Role utilisateur — gate les sections specifiques
    val isMedecin: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val cloudBackupRepository: CloudBackupRepository,
    private val patientRepository: PatientRepository,
    private val glucoseRepository: GlucoseRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Charger le role utilisateur pour gater les sections specifiques
        viewModelScope.launch {
            val profile = authRepository.getCurrentUserProfile()
            _uiState.update { it.copy(isMedecin = profile?.role == UserRole.MEDECIN) }
        }
        // (Suppression IA hors-ligne v2.1.31 — Rolly est cloud-only.)
        viewModelScope.launch {
            preferencesRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.language.collect { lang ->
                _uiState.update { it.copy(language = lang) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.notificationsEnabled.collect { enabled ->
                _uiState.update { it.copy(notificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.medicationReminders.collect { enabled ->
                _uiState.update { it.copy(medicationReminders = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.measurementReminders.collect { enabled ->
                _uiState.update { it.copy(measurementReminders = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.appointmentReminders.collect { enabled ->
                _uiState.update { it.copy(appointmentReminders = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.glucoseUnit.collect { unit ->
                _uiState.update { it.copy(glucoseUnit = unit) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.measureType.collect { type ->
                _uiState.update { it.copy(measureType = type) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.targetMin.collect { min ->
                _uiState.update { it.copy(targetMin = min) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.targetMax.collect { max ->
                _uiState.update { it.copy(targetMax = max) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { preferencesRepository.setLanguage(language) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotificationsEnabled(enabled) }
    }

    fun setMedicationReminders(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setMedicationReminders(enabled) }
    }

    fun setMeasurementReminders(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setMeasurementReminders(enabled) }
    }

    fun setAppointmentReminders(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAppointmentReminders(enabled) }
    }

    fun setGlucoseUnit(unit: GlucoseUnit) {
        viewModelScope.launch { preferencesRepository.setGlucoseUnit(unit) }
    }

    fun setMeasureType(type: MeasureType) {
        viewModelScope.launch { preferencesRepository.setMeasureType(type) }
    }

    fun setGlycemicTarget(min: Double, max: Double) {
        viewModelScope.launch { preferencesRepository.setGlycemicTarget(min, max) }
    }

    // (Suppression IA hors-ligne v2.1.31 : downloadLocalModel / deleteLocalModel /
    // initLocalModel retires. Rolly fonctionne uniquement en ligne.)

    suspend fun performCloudBackup() {
        cloudBackupRepository.performFullBackup()
    }

    /**
     * Supprime definitivement le compte de l'utilisateur :
     * profil Firestore + consentements + compte Firebase Auth.
     * onResult(true)  -> deconnexion a effectuer cote UI
     * onResult(false) -> afficher message d'erreur
     */
    fun deleteMyAccount(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.deleteAccount()
            result.fold(
                onSuccess = { onResult(true, null) },
                onFailure = { e -> onResult(false, e.message) }
            )
        }
    }

    fun exportData(context: Context, format: String) {
        viewModelScope.launch {
            try {
                val patients = patientRepository.getAllPatientsList()
                val sb = StringBuilder()
                val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                val unit = _uiState.value.glucoseUnit

                sb.appendLine("=== RAPPORT DIASMART ===")
                sb.appendLine("Date: ${java.time.LocalDateTime.now().format(dateFmt)}")
                sb.appendLine()

                patients.forEach { patient ->
                    sb.appendLine("--- Patient: ${patient.nomComplet} ---")
                    sb.appendLine("Age: ${patient.age} ans | Sexe: ${patient.sexe.name}")
                    sb.appendLine("Type: ${patient.typeDiabete.name.replace("_", " ")}")
                    patient.imc?.let { sb.appendLine("IMC: ${"%.1f".format(it)} kg/m²") }
                    sb.appendLine()

                    val lectures = glucoseRepository.getLecturesByPatientList(patient.id)
                    if (lectures.isNotEmpty()) {
                        val avg = lectures.map { it.valeur }.average()
                        sb.appendLine("Moyenne: ${unit.format(avg)} ${unit.shortLabel}")
                        sb.appendLine("Lectures: ${lectures.size}")
                        sb.appendLine()
                        if (format == "csv") {
                            sb.appendLine("Date,Valeur (${unit.shortLabel}),Contexte")
                            lectures.forEach { l ->
                                sb.appendLine("${l.dateHeure.format(dateFmt)},${unit.format(l.valeur)},${l.contexte.getDisplayName()}")
                            }
                        }
                    }
                    sb.appendLine()
                }

                val text = sb.toString()
                val subject = "Rapport DiaSmart - ${java.time.LocalDate.now()}"

                when (format) {
                    "email" -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, "Envoyer par email"))
                    }
                    else -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = if (format == "csv") "text/csv" else "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, "Exporter ($format)"))
                    }
                }
            } catch (e: Exception) {
                Log.w("SettingsVM", "Export failed", e)
            }
        }
    }
}
