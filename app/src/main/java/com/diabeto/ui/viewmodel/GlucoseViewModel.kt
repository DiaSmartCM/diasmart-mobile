package com.diabeto.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.entity.*
import com.diabeto.data.repository.ChatbotRepository
import com.diabeto.data.repository.GlucoseRepository
import com.diabeto.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Onglet actif dans l'écran de suivi
 */
enum class GlucoseTab {
    GLYCEMIE, HBA1C, SUIVI
}

data class GlucoseUiState(
    val patient: PatientEntity? = null,
    val lectures: List<LectureGlucoseEntity> = emptyList(),
    val statistics: GlucoseStatistics = GlucoseStatistics(),
    val newValeur: String = "",
    val selectedContexte: ContexteGlucose = ContexteGlucose.AUTRE,
    val showGraph: Boolean = true,
    val isLoading: Boolean = false,
    val addSuccess: Boolean = false,
    val error: String? = null,
    // HbA1c
    val activeTab: GlucoseTab = GlucoseTab.GLYCEMIE,
    val hba1cHistorique: List<HbA1cEntity> = emptyList(),
    val latestHbA1c: HbA1cEntity? = null,
    val hba1cEstimee: Double? = null,
    val newHbA1cValeur: String = "",
    val newHbA1cLabo: String = "",
    val showAddHbA1cDialog: Boolean = false,
    // Suivi (diagramme double courbe)
    val dailyAverages: List<GlucoseRepository.DailyGlucoseAverage> = emptyList(),
    // ROLLY auto-analyse
    val rollyAnalysis: String? = null,
    val showRollyAnalysis: Boolean = false,
    // Export
    val exportCsvData: String? = null
)

@HiltViewModel
class GlucoseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val glucoseRepository: GlucoseRepository,
    private val patientRepository: PatientRepository,
    private val chatbotRepository: ChatbotRepository
) : ViewModel() {

    private val patientId: Long = savedStateHandle["patientId"] ?: 0L

    private val _uiState = MutableStateFlow(GlucoseUiState())
    val uiState: StateFlow<GlucoseUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val patient = patientRepository.getPatientById(patientId)
                val lectures = glucoseRepository.getLecturesByPatientList(patientId, 50)
                val stats = glucoseRepository.getStatistics(patientId, 30)
                val hba1cList = glucoseRepository.getHbA1cByPatientList(patientId)
                val latestHbA1c = hba1cList.firstOrNull()
                val hba1cEstimee = glucoseRepository.estimateHbA1c(stats)
                val dailyAvg = glucoseRepository.calculateDailyAverages(lectures)

                _uiState.update {
                    it.copy(
                        patient = patient, lectures = lectures, statistics = stats,
                        isLoading = false, hba1cHistorique = hba1cList,
                        latestHbA1c = latestHbA1c, hba1cEstimee = hba1cEstimee,
                        dailyAverages = dailyAvg
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setActiveTab(tab: GlucoseTab) = _uiState.update { it.copy(activeTab = tab) }

    fun onValeurChange(value: String) {
        _uiState.update { it.copy(newValeur = value.filter { c -> c.isDigit() || c == '.' }) }
    }
    fun onContexteChange(contexte: ContexteGlucose) = _uiState.update { it.copy(selectedContexte = contexte) }
    fun toggleViewMode() = _uiState.update { it.copy(showGraph = !it.showGraph) }

    fun addLecture() {
        viewModelScope.launch {
            try {
                val valeur = _uiState.value.newValeur.toDoubleOrNull()
                if (valeur == null || valeur <= 0) {
                    _uiState.update { it.copy(error = "Valeur invalide") }; return@launch
                }
                if (valeur > 600) {
                    _uiState.update { it.copy(error = "Valeur trop élevée") }; return@launch
                }
                glucoseRepository.insertLecture(LectureGlucoseEntity(
                    patientId = patientId, valeur = valeur,
                    dateHeure = LocalDateTime.now(), contexte = _uiState.value.selectedContexte
                ))
                _uiState.update { it.copy(newValeur = "", addSuccess = true) }
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteLecture(lecture: LectureGlucoseEntity) {
        viewModelScope.launch {
            try { glucoseRepository.deleteLecture(lecture); loadData() }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ── HbA1c ──
    fun onHbA1cValeurChange(value: String) = _uiState.update { it.copy(newHbA1cValeur = value.filter { c -> c.isDigit() || c == '.' }) }
    fun onHbA1cLaboChange(value: String) = _uiState.update { it.copy(newHbA1cLabo = value) }
    fun showAddHbA1cDialog() = _uiState.update { it.copy(showAddHbA1cDialog = true) }
    fun hideAddHbA1cDialog() = _uiState.update { it.copy(showAddHbA1cDialog = false, newHbA1cValeur = "", newHbA1cLabo = "") }

    fun addHbA1c() {
        viewModelScope.launch {
            try {
                val valeur = _uiState.value.newHbA1cValeur.toDoubleOrNull()
                if (valeur == null || valeur < 3.0 || valeur > 20.0) {
                    _uiState.update { it.copy(error = "HbA1c invalide (3% à 20%)") }; return@launch
                }
                glucoseRepository.insertHbA1c(HbA1cEntity(
                    patientId = patientId, valeur = valeur, dateMesure = LocalDate.now(),
                    laboratoire = _uiState.value.newHbA1cLabo.trim(), estEstimation = false
                ))
                _uiState.update { it.copy(showAddHbA1cDialog = false, newHbA1cValeur = "", newHbA1cLabo = "", addSuccess = true) }
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteHbA1c(hba1c: HbA1cEntity) {
        viewModelScope.launch {
            try { glucoseRepository.deleteHbA1c(hba1c); loadData() }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun sauvegarderEstimation() {
        viewModelScope.launch {
            try {
                val result = glucoseRepository.estimerEtSauvegarderHbA1c(patientId)
                if (result != null) { _uiState.update { it.copy(addSuccess = true) }; loadData() }
                else _uiState.update { it.copy(error = "Minimum 10 lectures pour estimer l'HbA1c") }
            } catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    /**
     * Code couleur glycemique aligne sur le design system (`theme/Color.kt`).
     * v2.1.35 : ne plus utiliser de hex codes hardcodes — chaque seuil clinique
     * doit etre rendu avec la meme teinte partout dans l'app (dashboard,
     * carnet, predictif, message).
     */
    fun getGlucoseColor(valeur: Double): androidx.compose.ui.graphics.Color = when {
        valeur < 54 -> com.diabeto.ui.theme.GlucoseSevere   // Hypoglycemie severe — alerte
        valeur < 70 -> com.diabeto.ui.theme.GlucoseLow      // Hypoglycemie
        valeur > 250 -> com.diabeto.ui.theme.GlucoseSevere  // Hyperglycemie severe
        valeur > 180 -> com.diabeto.ui.theme.GlucoseHigh    // Hyperglycemie
        else -> com.diabeto.ui.theme.GlucoseNormal          // Cible 70-180
    }

    fun getGlucoseStatus(valeur: Double): String = glucoseRepository.getGlucoseStatus(valeur)

    fun getHbA1cColor(valeur: Double): androidx.compose.ui.graphics.Color = when {
        valeur < 5.7 -> com.diabeto.ui.theme.HbA1cNormal
        valeur < 6.5 -> com.diabeto.ui.theme.HbA1cPreDiabete
        valeur < 7.0 -> com.diabeto.ui.theme.HbA1cCible
        valeur < 8.0 -> com.diabeto.ui.theme.HbA1cAuDessus
        valeur < 9.0 -> com.diabeto.ui.theme.HbA1cRisque
        else -> com.diabeto.ui.theme.HbA1cDanger
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearAddSuccess() = _uiState.update { it.copy(addSuccess = false) }
    fun refresh() = loadData()

    // ── ROLLY Auto-analyse après ajout de données ──
    fun triggerRollyAnalysis() {
        viewModelScope.launch {
            try {
                val patient = _uiState.value.patient ?: return@launch
                val lectures = glucoseRepository.getLast7DaysLectures(patientId)
                if (lectures.size < 3) return@launch
                val latestHbA1c = _uiState.value.hba1cHistorique.firstOrNull { !it.estEstimation }
                val hba1cEst = _uiState.value.hba1cEstimee
                val analysis = chatbotRepository.analyserGlycemie(patient, lectures, latestHbA1c, hba1cEst)
                _uiState.update { it.copy(rollyAnalysis = analysis, showRollyAnalysis = true) }
            } catch (e: Exception) {
                Log.d("GlucoseVM", "Auto-analysis skipped: ${e.message}")
            }
        }
    }

    fun dismissRollyAnalysis() = _uiState.update { it.copy(showRollyAnalysis = false, rollyAnalysis = null) }

    // ── Export CSV ──
    fun generateExportData() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val report = glucoseRepository.generateExportReport(
                    patient = state.patient,
                    statistics = state.statistics,
                    hba1cHistorique = state.hba1cHistorique,
                    lectures = state.lectures
                )
                _uiState.update { it.copy(exportCsvData = report) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Erreur export: ${e.message}") }
            }
        }
    }

    fun clearExportData() = _uiState.update { it.copy(exportCsvData = null) }
}
