package com.diabeto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.entity.PatientEntity
import com.diabeto.data.model.ConsentStatus
import com.diabeto.data.model.DataSharingConsent
import com.diabeto.data.model.UserProfile
import com.diabeto.data.model.UserRole
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.DataSharingRepository
import com.diabeto.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.diabeto.util.MessageErreur

data class PatientsUiState(
    val isMedecin: Boolean = false,
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val message: String? = null,

    // Vue médecin
    val allPlatformPatients: List<UserProfile> = emptyList(),
    val myPatients: List<DataSharingConsent> = emptyList(),  // Patients ayant accepté
    val myRequests: List<DataSharingConsent> = emptyList(),   // Toutes les demandes du médecin
    val requestingUid: String? = null,

    // Vue patient
    val localPatients: List<PatientEntity> = emptyList(),
    val pendingRequests: List<DataSharingConsent> = emptyList()  // Demandes en attente côté patient
)

@HiltViewModel
class PatientsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataSharingRepository: DataSharingRepository,
    private val patientRepository: PatientRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientsUiState())
    val uiState: StateFlow<PatientsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val profile = authRepository.getCurrentUserProfile()
            val isMedecin = profile?.role == UserRole.MEDECIN

            _uiState.update { it.copy(isMedecin = isMedecin) }

            if (isMedecin) {
                loadMedecinData()
            } else {
                loadPatientData()
            }
        }
    }

    private suspend fun loadMedecinData() {
        try {
            val allPatients = dataSharingRepository.getAllPlatformPatients()
            val myRequests = dataSharingRepository.getMyRequests()
            val myPatients = myRequests.filter { it.status == ConsentStatus.ACCEPTED && it.isActive }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    allPlatformPatients = allPatients,
                    myPatients = myPatients,
                    myRequests = myRequests
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, message = MessageErreur.lisible(e)) }
        }
    }

    private suspend fun loadPatientData() {
        try {
            val localPatients = patientRepository.getAllPatientsList()
            val pendingRequests = dataSharingRepository.getPendingRequests()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    localPatients = localPatients,
                    pendingRequests = pendingRequests
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, message = MessageErreur.lisible(e)) }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // ── Médecin : Demander l'accès à un patient ──
    fun requestAccess(patientUid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(requestingUid = patientUid) }

            val result = dataSharingRepository.requestAccess(patientUid)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(requestingUid = null, message = "Demande envoyee au patient") }
                    // Recharger les données
                    loadMedecinData()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(requestingUid = null, message = MessageErreur.lisible(e)) }
                }
            )
        }
    }

    // ── Patient : Accepter une demande ──
    fun acceptRequest(medecinUid: String) {
        viewModelScope.launch {
            val result = dataSharingRepository.acceptRequest(medecinUid)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(message = "Acces accorde au medecin") }
                    loadPatientData()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(message = MessageErreur.lisible(e)) }
                }
            )
        }
    }

    // ── Patient : Refuser une demande ──
    fun rejectRequest(medecinUid: String) {
        viewModelScope.launch {
            val result = dataSharingRepository.rejectRequest(medecinUid)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(message = "Demande refusee") }
                    loadPatientData()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(message = MessageErreur.lisible(e)) }
                }
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
