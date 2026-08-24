package com.diabeto.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.entity.*
import com.diabeto.data.repository.GlucoseRepository
import com.diabeto.data.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import com.diabeto.util.MessageErreur

data class JournalUiState(
    val entries: List<JournalEntity> = emptyList(),
    val currentEntry: JournalEntity? = null,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val editHumeur: Humeur = Humeur.NEUTRE,
    val editStress: NiveauStress = NiveauStress.AUCUN,
    val editSommeil: QualiteSommeil = QualiteSommeil.BONNE,
    val editHeuresSommeil: String = "7.0",
    val editActivitePhysique: Boolean = false,
    val editMinutesActivite: String = "0",
    val editNotes: String = "",
    val todayGlycemie: Double? = null,
    val error: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class JournalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val journalRepository: JournalRepository,
    private val glucoseRepository: GlucoseRepository
) : ViewModel() {

    private val patientId: Long = savedStateHandle.get<Long>("patientId")?.takeIf { it > 0 } ?: 0L

    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    init {
        if (patientId > 0) {
            loadEntries()
            loadTodayEntry()
        }
    }

    private fun loadEntries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val entries = journalRepository.getEntriesByPatientList(patientId)
                _uiState.update { it.copy(entries = entries, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = MessageErreur.lisible(e)) }
            }
        }
    }

    private fun loadTodayEntry() {
        viewModelScope.launch {
            try {
                val today = journalRepository.getEntryByDate(patientId, LocalDate.now())
                val glycemie = try {
                    glucoseRepository.getLast24HoursAverage(patientId).takeIf { it > 0 }
                } catch (e: Exception) { Log.w("JournalVM", "Glucose avg failed", e); null }

                if (today != null) {
                    _uiState.update {
                        it.copy(
                            currentEntry = today,
                            editHumeur = today.humeur,
                            editStress = today.niveauStress,
                            editSommeil = today.qualiteSommeil,
                            editHeuresSommeil = today.heuresSommeil.toString(),
                            editActivitePhysique = today.activitePhysique,
                            editMinutesActivite = today.minutesActivite.toString(),
                            editNotes = today.notes,
                            todayGlycemie = glycemie
                        )
                    }
                } else {
                    _uiState.update { it.copy(todayGlycemie = glycemie) }
                }
            } catch (e: Exception) {
                Log.w("JournalVM", "Failed to load today entry", e)
            }
        }
    }

    fun startEditing() = _uiState.update { it.copy(isEditing = true, saved = false) }
    fun cancelEditing() = _uiState.update { it.copy(isEditing = false) }

    fun setHumeur(humeur: Humeur) = _uiState.update { it.copy(editHumeur = humeur) }
    fun setStress(stress: NiveauStress) = _uiState.update { it.copy(editStress = stress) }
    fun setSommeil(sommeil: QualiteSommeil) = _uiState.update { it.copy(editSommeil = sommeil) }
    fun setHeuresSommeil(h: String) = _uiState.update { it.copy(editHeuresSommeil = h) }
    fun setActivitePhysique(active: Boolean) = _uiState.update { it.copy(editActivitePhysique = active) }
    fun setMinutesActivite(m: String) = _uiState.update { it.copy(editMinutesActivite = m) }
    fun setNotes(n: String) = _uiState.update { it.copy(editNotes = n) }

    fun saveEntry() {
        if (patientId <= 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val state = _uiState.value
                val entry = state.currentEntry?.copy(
                    humeur = state.editHumeur,
                    niveauStress = state.editStress,
                    qualiteSommeil = state.editSommeil,
                    heuresSommeil = state.editHeuresSommeil.toDoubleOrNull() ?: 7.0,
                    activitePhysique = state.editActivitePhysique,
                    minutesActivite = state.editMinutesActivite.toIntOrNull() ?: 0,
                    notes = state.editNotes,
                    glycemieCorrelation = state.todayGlycemie
                ) ?: JournalEntity(
                    patientId = patientId,
                    date = LocalDate.now(),
                    humeur = state.editHumeur,
                    niveauStress = state.editStress,
                    qualiteSommeil = state.editSommeil,
                    heuresSommeil = state.editHeuresSommeil.toDoubleOrNull() ?: 7.0,
                    activitePhysique = state.editActivitePhysique,
                    minutesActivite = state.editMinutesActivite.toIntOrNull() ?: 0,
                    notes = state.editNotes,
                    glycemieCorrelation = state.todayGlycemie
                )

                val id = journalRepository.saveEntry(entry)
                val saved = entry.copy(id = if (entry.id == 0L) id else entry.id)
                _uiState.update {
                    it.copy(
                        currentEntry = saved,
                        isEditing = false,
                        isLoading = false,
                        saved = true
                    )
                }
                loadEntries()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = MessageErreur.lisible(e)) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
