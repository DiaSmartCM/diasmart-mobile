package com.diabeto.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.entity.*
import com.diabeto.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * État de la liste des patients
 */
data class PatientListUiState(
    val patients: List<PatientEntity> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * État du détail d'un patient
 */
data class PatientDetailUiState(
    val patient: PatientEntity? = null,
    val glucoseStats: GlucoseStatistics = GlucoseStatistics(),
    val latestGlucose: Double? = null,
    val activeMedicaments: Int = 0,
    val upcomingRendezVous: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val deleteSuccess: Boolean = false
)

@HiltViewModel
class PatientListViewModel @Inject constructor(
    private val patientRepository: PatientRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientListUiState())
    val uiState: StateFlow<PatientListUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        patientRepository.getAllPatients()
                    } else {
                        patientRepository.searchPatients(query)
                    }
                }
                .collect { patients ->
                    _uiState.update { it.copy(patients = patients, isLoading = false) }
                }
        }
        
        loadPatients()
    }

    fun loadPatients() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Les patients sont chargés via le flow ci-dessus
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

@HiltViewModel
class PatientDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val patientRepository: PatientRepository,
    private val glucoseRepository: GlucoseRepository,
    private val medicamentRepository: MedicamentRepository,
    private val rendezVousRepository: RendezVousRepository
) : ViewModel() {

    private val patientId: Long = savedStateHandle["patientId"] ?: 0L

    private val _uiState = MutableStateFlow(PatientDetailUiState())
    val uiState: StateFlow<PatientDetailUiState> = _uiState.asStateFlow()

    init {
        loadPatientDetail()
    }

    fun loadPatientDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val patient = patientRepository.getPatientById(patientId)
                
                if (patient != null) {
                    val stats = glucoseRepository.getStatistics(patientId, 30)
                    val latest = glucoseRepository.getLatestReading(patientId)
                    val medCount = medicamentRepository.getActiveMedicamentCount(patientId)
                    val rdvCount = rendezVousRepository.getUpcomingRendezVous(100)
                        .count { it.patient.id == patientId }
                    
                    _uiState.update {
                        it.copy(
                            patient = patient,
                            glucoseStats = stats,
                            latestGlucose = latest?.valeur,
                            activeMedicaments = medCount,
                            upcomingRendezVous = rdvCount,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Patient non trouvé")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }
    
    fun deletePatient() {
        viewModelScope.launch {
            try {
                _uiState.value.patient?.let { patient ->
                    patientRepository.deletePatient(patient)
                    _uiState.update { it.copy(deleteSuccess = true) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun refresh() {
        loadPatientDetail()
    }
}

@HiltViewModel
class PatientEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val patientRepository: PatientRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val patientId: Long? = savedStateHandle.get<Long>("patientId")?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(PatientEditUiState())
    val uiState: StateFlow<PatientEditUiState> = _uiState.asStateFlow()

    data class PatientEditUiState(
        val nom: String = "",
        val prenom: String = "",
        val dateNaissance: LocalDate = LocalDate.of(1980, 1, 1),
        val sexe: Sexe = Sexe.HOMME,
        val telephone: String = "",
        val email: String = "",
        val adresse: String = "",
        val typeDiabete: TypeDiabete = TypeDiabete.TYPE_2,
        val dateDiagnostic: LocalDate? = null,
        val notes: String = "",
        // Données corporelles
        val poids: String = "",           // kg (String pour le champ texte)
        val taille: String = "",          // cm
        val tourDeTaille: String = "",    // cm
        val masseGrasse: String = "",     // %
        val isLoading: Boolean = false,
        val saveSuccess: Boolean = false,
        val error: String? = null
    )

    init {
        patientId?.let { loadPatient(it) }
    }

    private fun loadPatient(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val patient = patientRepository.getPatientById(id)
                if (patient != null) {
                    _uiState.update {
                        it.copy(
                            nom = patient.nom,
                            prenom = patient.prenom,
                            dateNaissance = patient.dateNaissance,
                            sexe = patient.sexe,
                            telephone = patient.telephone,
                            email = patient.email,
                            adresse = patient.adresse,
                            typeDiabete = patient.typeDiabete,
                            dateDiagnostic = patient.dateDiagnostic,
                            notes = patient.notes,
                            poids = patient.poids?.toString() ?: "",
                            taille = patient.taille?.toString() ?: "",
                            tourDeTaille = patient.tourDeTaille?.toString() ?: "",
                            masseGrasse = patient.masseGrasse?.toString() ?: "",
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Patient non trouvé") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateField(field: String, value: Any?) {
        _uiState.update { state ->
            when (field) {
                "nom" -> state.copy(nom = value as String)
                "prenom" -> state.copy(prenom = value as String)
                "dateNaissance" -> state.copy(dateNaissance = value as LocalDate)
                "sexe" -> state.copy(sexe = value as Sexe)
                "telephone" -> state.copy(telephone = value as String)
                "email" -> state.copy(email = value as String)
                "adresse" -> state.copy(adresse = value as String)
                "typeDiabete" -> state.copy(typeDiabete = value as TypeDiabete)
                "dateDiagnostic" -> state.copy(dateDiagnostic = value as? LocalDate)
                "notes" -> state.copy(notes = value as String)
                "poids" -> state.copy(poids = value as String)
                "taille" -> state.copy(taille = value as String)
                "tourDeTaille" -> state.copy(tourDeTaille = value as String)
                "masseGrasse" -> state.copy(masseGrasse = value as String)
                else -> state
            }
        }
    }

    fun savePatient() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val state = _uiState.value
                
                if (state.nom.isBlank() || state.prenom.isBlank()) {
                    _uiState.update { it.copy(isLoading = false, error = "Nom et prénom sont obligatoires") }
                    return@launch
                }
                
                val patient = PatientEntity(
                    id = patientId ?: 0,
                    nom = state.nom.trim(),
                    prenom = state.prenom.trim(),
                    dateNaissance = state.dateNaissance,
                    sexe = state.sexe,
                    telephone = state.telephone.trim(),
                    email = state.email.trim(),
                    adresse = state.adresse.trim(),
                    typeDiabete = state.typeDiabete,
                    dateDiagnostic = state.dateDiagnostic,
                    notes = state.notes.trim(),
                    poids = state.poids.toDoubleOrNull(),
                    taille = state.taille.toDoubleOrNull(),
                    tourDeTaille = state.tourDeTaille.toDoubleOrNull(),
                    masseGrasse = state.masseGrasse.toDoubleOrNull()
                )
                
                if (patientId == null) {
                    patientRepository.insertPatient(patient)
                } else {
                    patientRepository.updatePatient(patient)
                }

                // ── Sync automatique vers le profil Firestore ──
                syncMorphoToFirestore(patient)

                _uiState.update { it.copy(isLoading = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Synchronise les données morphométriques du PatientEntity vers le profil Firestore
     * pour éviter la double saisie entre Patient et Profil.
     */
    private fun syncMorphoToFirestore(patient: PatientEntity) {
        viewModelScope.launch {
            try {
                val morphoData = mutableMapOf<String, Any>()
                patient.poids?.let { morphoData["poids"] = it }
                patient.taille?.let { morphoData["taille"] = it }
                patient.tourDeTaille?.let { morphoData["tourTaille"] = it }
                patient.masseGrasse?.let { morphoData["masseGrasse"] = it }
                if (patient.nom.isNotBlank()) morphoData["name"] = "${patient.prenom} ${patient.nom}"
                if (patient.telephone.isNotBlank()) morphoData["phone"] = patient.telephone

                if (morphoData.isNotEmpty()) {
                    // v2.1.47 : passe par AuthRepository.mergeUserFields() au lieu
                    // d'attaquer FirebaseFirestore directement.
                    authRepository.mergeUserFields(morphoData)
                    android.util.Log.i("PatientEditVM", "Morpho sync -> Firestore profil OK")
                }
            } catch (e: Exception) {
                android.util.Log.w("PatientEditVM", "Sync morpho Firestore échouée (non bloquant)", e)
            }
        }
    }
}
