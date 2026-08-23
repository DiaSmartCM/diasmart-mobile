package com.diabeto.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.entity.*
import com.diabeto.data.repository.MedicamentRepository
import com.diabeto.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * État de la gestion des médicaments
 */
data class MedicamentUiState(
    val patient: PatientEntity? = null,
    val medicaments: List<MedicamentEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddDialog: Boolean = false,
    val addSuccess: Boolean = false
)

/**
 * État du formulaire d'ajout de médicament
 */
data class AddMedicamentState(
    val nom: String = "",
    val dosage: String = "",
    val frequence: FrequencePrise = FrequencePrise.QUOTIDIEN,
    val heurePrise: LocalTime = LocalTime.of(8, 0),
    val dateDebut: LocalDate = LocalDate.now(),
    val dateFin: LocalDate? = null,
    val rappelActive: Boolean = true,
    val notes: String = "",
    val error: String? = null
)

@HiltViewModel
class MedicamentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val appContext: android.content.Context,
    private val medicamentRepository: MedicamentRepository,
    private val patientRepository: PatientRepository
) : ViewModel() {

    private val patientId: Long = savedStateHandle["patientId"] ?: 0L

    private val _uiState = MutableStateFlow(MedicamentUiState())
    val uiState: StateFlow<MedicamentUiState> = _uiState.asStateFlow()

    private val _addState = MutableStateFlow(AddMedicamentState())
    val addState: StateFlow<AddMedicamentState> = _addState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val patient = patientRepository.getPatientById(patientId)
                val medicaments = medicamentRepository.getMedicamentsByPatientList(patientId)
                
                _uiState.update {
                    it.copy(
                        patient = patient,
                        medicaments = medicaments,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleAddDialog(show: Boolean) {
        _uiState.update { it.copy(showAddDialog = show) }
        if (!show) {
            _addState.value = AddMedicamentState()
        }
    }

    fun updateAddField(field: String, value: Any?) {
        _addState.update { state ->
            when (field) {
                "nom" -> state.copy(nom = value as String)
                "dosage" -> state.copy(dosage = value as String)
                "frequence" -> state.copy(frequence = value as FrequencePrise)
                "heurePrise" -> state.copy(heurePrise = value as LocalTime)
                "dateDebut" -> state.copy(dateDebut = value as LocalDate)
                "dateFin" -> state.copy(dateFin = value as? LocalDate)
                "rappelActive" -> state.copy(rappelActive = value as Boolean)
                "notes" -> state.copy(notes = value as String)
                else -> state
            }
        }
    }

    fun addMedicament() {
        viewModelScope.launch {
            try {
                val state = _addState.value
                
                if (state.nom.isBlank() || state.dosage.isBlank()) {
                    _addState.update { it.copy(error = "Nom et dosage sont obligatoires") }
                    return@launch
                }
                
                val medicament = MedicamentEntity(
                    patientId = patientId,
                    nom = state.nom.trim(),
                    dosage = state.dosage.trim(),
                    frequence = state.frequence,
                    heurePrise = state.heurePrise,
                    dateDebut = state.dateDebut,
                    dateFin = state.dateFin,
                    rappelActive = state.rappelActive,
                    notes = state.notes.trim()
                )
                
                val nouvelId = medicamentRepository.insertMedicament(medicament)

                // v2.1.86 : l'alarme se pose ICI, a la creation.
                // Elle n'etait programmee qu'au lancement suivant de
                // l'application : une prise saisie pour dans cinq minutes ne
                // sonnait jamais, et se retrouvait reportee au lendemain.
                // runCatching : une alarme qui echoue (permission retiree,
                // constructeur restrictif) ne doit pas emporter avec elle la
                // fermeture du dialogue ni le rechargement de la liste. Le
                // traitement est deja enregistre a ce stade.
                if (medicament.rappelActive) runCatching {
                    com.diabeto.notifications.AlarmScheduler.programmerMedicament(
                        context = appContext,
                        medicamentId = if (nouvelId > 0) nouvelId else medicament.id,
                        nom = medicament.nom,
                        dosage = medicament.dosage,
                        heurePrise = medicament.heurePrise,
                        dateFin = medicament.dateFin,
                    )
                }.onFailure {
                    android.util.Log.w("MedicamentVM", "Alarme non posee", it)
                }

                _uiState.update { it.copy(showAddDialog = false, addSuccess = true) }
                _addState.value = AddMedicamentState()
                
                loadData()
                
            } catch (e: Exception) {
                _addState.update { it.copy(error = e.message) }
            }
        }
    }

    fun toggleMedicamentStatus(medicament: MedicamentEntity) {
        viewModelScope.launch {
            try {
                medicamentRepository.toggleActifStatus(medicament.id, medicament.estActif)
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun toggleRappelStatus(medicament: MedicamentEntity) {
        viewModelScope.launch {
            try {
                medicamentRepository.toggleRappelStatus(medicament.id, medicament.rappelActive)

                // Le rappel vient d'etre bascule : on pose ou on retire
                // l'alarme en consequence, sans attendre un redemarrage.
                runCatching { if (!medicament.rappelActive) {
                    com.diabeto.notifications.AlarmScheduler.programmerMedicament(
                        context = appContext,
                        medicamentId = medicament.id,
                        nom = medicament.nom,
                        dosage = medicament.dosage,
                        heurePrise = medicament.heurePrise,
                        dateFin = medicament.dateFin,
                    )
                } else {
                    com.diabeto.notifications.AlarmScheduler.annulerMedicament(
                        appContext, medicament.id
                    )
                } }.onFailure { android.util.Log.w("MedicamentVM", "Alarme non mise a jour", it) }
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteMedicament(medicament: MedicamentEntity) {
        viewModelScope.launch {
            try {
                medicamentRepository.deleteMedicament(medicament)
                // Sans cette annulation, l'alarme d'un traitement supprime
                // continuerait de sonner jusqu'a son echeance.
                runCatching {
                    com.diabeto.notifications.AlarmScheduler.annulerMedicament(
                        appContext, medicament.id
                    )
                }
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
        _addState.update { it.copy(error = null) }
    }
    
    fun clearAddSuccess() {
        _uiState.update { it.copy(addSuccess = false) }
    }
    
    fun refresh() {
        loadData()
    }
}
