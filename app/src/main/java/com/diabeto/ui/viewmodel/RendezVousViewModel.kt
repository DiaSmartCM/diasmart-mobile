package com.diabeto.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.entity.*
import com.diabeto.data.model.ConsentStatus
import com.diabeto.data.model.RendezVousRequest
import com.diabeto.data.model.UserProfile
import com.diabeto.data.model.UserRole
import com.diabeto.data.repository.AppointmentRequestRepository
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.DataSharingRepository
import com.diabeto.data.repository.PatientRepository
import com.diabeto.data.repository.RendezVousRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/**
 * Filtre pour les rendez-vous
 */
enum class RendezVousFilter {
    TOUS, A_VENIR, PASSES
}

/**
 * État de la liste des rendez-vous
 */
data class PatientOption(
    val id: Long,
    val uid: String = "",
    val nom: String = "",
    val isFirestore: Boolean = false
)

/**
 * RDV simplifié pour l'affichage patient (depuis Firestore)
 */
data class RendezVousPatientItem(
    val id: String = "",
    val titre: String = "",
    val dateHeure: LocalDateTime = LocalDateTime.now(),
    val dureeMinutes: Int = 30,
    val type: String = "CONSULTATION",
    val lieu: String = "",
    val notes: String = "",
    val estConfirme: Boolean = false,
    val medecinNom: String = ""
) {
    fun estPasse(): Boolean = dateHeure.isBefore(LocalDateTime.now())
    fun estAujourdhui(): Boolean = dateHeure.toLocalDate() == LocalDate.now()
}

data class RendezVousUiState(
    val rendezVous: List<RendezVousAvecPatient> = emptyList(),
    val patientRendezVous: List<RendezVousPatientItem> = emptyList(),
    val appointmentRequests: List<RendezVousRequest> = emptyList(),
    val availableMedecins: List<UserProfile> = emptyList(),
    val filter: RendezVousFilter = RendezVousFilter.A_VENIR,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddDialog: Boolean = false,
    val showBookDialog: Boolean = false,
    val addSuccess: Boolean = false,
    val bookSuccess: Boolean = false,
    val patientId: Long? = null,
    val isMedecin: Boolean = false,
    val patientOptions: List<PatientOption> = emptyList()
)

/**
 * État du formulaire d'ajout de rendez-vous
 */
data class AddRendezVousState(
    val selectedPatientId: Long = 0,
    val titre: String = "",
    val date: LocalDate = LocalDate.now().plusDays(1),
    val heure: LocalTime = LocalTime.of(9, 0),
    val duree: Int = 30,
    val type: TypeRendezVous = TypeRendezVous.CONSULTATION,
    val lieu: String = "",
    val notes: String = "",
    val error: String? = null
)

/**
 * Etat du formulaire de prise de RDV (cote PATIENT).
 */
data class BookAppointmentState(
    val selectedMedecinUid: String = "",
    val selectedMedecinNom: String = "",
    val date: LocalDate = LocalDate.now().plusDays(1),
    val heure: LocalTime = LocalTime.of(9, 0),
    val duree: Int = 30,
    val motif: String = "",
    val type: String = "CONSULTATION",
    val error: String? = null,
    val isSubmitting: Boolean = false
)

@HiltViewModel
class RendezVousViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val rendezVousRepository: RendezVousRepository,
    private val patientRepository: PatientRepository,
    private val authRepository: AuthRepository,
    private val dataSharingRepository: DataSharingRepository,
    private val appointmentRequestRepository: AppointmentRequestRepository
) : ViewModel() {

    companion object {
        private const val TAG = "RendezVousVM"
    }

    private val initialPatientId: Long? = savedStateHandle.get<Long>("patientId")?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(RendezVousUiState(patientId = initialPatientId))
    val uiState: StateFlow<RendezVousUiState> = _uiState.asStateFlow()

    private val _addState = MutableStateFlow(AddRendezVousState())
    val addState: StateFlow<AddRendezVousState> = _addState.asStateFlow()

    private val _bookState = MutableStateFlow(BookAppointmentState())
    val bookState: StateFlow<BookAppointmentState> = _bookState.asStateFlow()

    private val firestore = FirebaseFirestore.getInstance()

    init {
        initialPatientId?.let {
            _addState.update { state -> state.copy(selectedPatientId = it) }
        }
        loadData()
        loadPatientOptions()
        observeAppointmentRequests()
    }

    private fun observeAppointmentRequests() {
        viewModelScope.launch {
            appointmentRequestRepository.getRequestsFlow().collect { requests ->
                _uiState.update { it.copy(appointmentRequests = requests) }
            }
        }
    }

    private fun loadPatientOptions() {
        viewModelScope.launch {
            try {
                val profile = authRepository.getCurrentUserProfile()
                val isMedecin = profile?.role == UserRole.MEDECIN

                _uiState.update { it.copy(isMedecin = isMedecin) }

                if (isMedecin) {
                    // Charger les patients acceptes depuis Firestore
                    val myRequests = dataSharingRepository.getMyRequests()
                    val acceptedPatients = myRequests
                        .filter { it.status == ConsentStatus.ACCEPTED && it.isActive }

                    val options = acceptedPatients.mapIndexed { index, consent ->
                        PatientOption(
                            id = index.toLong() + 10000L,
                            uid = consent.patientUid,
                            nom = consent.patientNom,
                            isFirestore = true
                        )
                    }

                    // Aussi charger les patients locaux Room
                    val localPatients = patientRepository.getAllPatientsList()
                    val localOptions = localPatients.map {
                        PatientOption(id = it.id, nom = it.nomComplet, isFirestore = false)
                    }

                    _uiState.update {
                        it.copy(patientOptions = options + localOptions)
                    }
                }
                // Patient: pas de patient options - ils ne créent pas de RDV
            } catch (e: Exception) {
                Log.w("RendezVousVM", "Failed to load patient options", e)
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val profile = authRepository.getCurrentUserProfile()
                val isMedecin = profile?.role == UserRole.MEDECIN

                if (isMedecin) {
                    // Médecin: charge les RDV locaux (qu'il a créés)
                    val rdvs = if (initialPatientId != null) {
                        rendezVousRepository.getRendezVousByPatientList(initialPatientId)
                            .mapNotNull { rdv ->
                                patientRepository.getPatientById(rdv.patientId)?.let { patient ->
                                    RendezVousAvecPatient(rdv, patient)
                                }
                            }
                    } else {
                        rendezVousRepository.getUpcomingRendezVous(50)
                    }
                    _uiState.update { it.copy(rendezVous = rdvs, isLoading = false, isMedecin = true) }
                } else {
                    // Patient: charge les RDV depuis Firestore (programmés par son médecin)
                    loadPatientRendezVousFromFirestore()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Charge les RDV du patient depuis Firestore.
     * Structure: rdv_shared/{patientUid}/rendezvous/{rdvId}
     * Chaque doc contient les détails du RDV créé par le médecin.
     */
    private suspend fun loadPatientRendezVousFromFirestore() {
        val currentUid = authRepository.currentUserId ?: return
        try {
            val docs = firestore.collection("rdv_shared")
                .document(currentUid)
                .collection("rendezvous")
                .get().await()

            val rdvList = docs.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    RendezVousPatientItem(
                        id = doc.id,
                        titre = data["titre"] as? String ?: "",
                        dateHeure = (data["dateHeure"] as? String)?.let {
                            LocalDateTime.parse(it)
                        } ?: LocalDateTime.now(),
                        dureeMinutes = (data["dureeMinutes"] as? Number)?.toInt() ?: 30,
                        type = data["type"] as? String ?: "CONSULTATION",
                        lieu = data["lieu"] as? String ?: "",
                        notes = data["notes"] as? String ?: "",
                        estConfirme = data["estConfirme"] as? Boolean ?: false,
                        medecinNom = data["medecinNom"] as? String ?: "Votre médecin"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing RDV doc", e)
                    null
                }
            }.sortedBy { it.dateHeure }

            _uiState.update {
                it.copy(
                    patientRendezVous = rdvList,
                    isLoading = false,
                    isMedecin = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading patient RDV from Firestore", e)
            _uiState.update { it.copy(isLoading = false, error = "Impossible de charger les rendez-vous") }
        }
    }

    fun setFilter(filter: RendezVousFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun toggleAddDialog(show: Boolean) {
        _uiState.update { it.copy(showAddDialog = show) }
        if (!show) {
            _addState.value = AddRendezVousState(selectedPatientId = initialPatientId ?: 0)
        }
    }

    fun updateAddField(field: String, value: Any?) {
        _addState.update { state ->
            when (field) {
                "patientId" -> state.copy(selectedPatientId = value as Long)
                "titre" -> state.copy(titre = value as String)
                "date" -> state.copy(date = value as LocalDate)
                "heure" -> state.copy(heure = value as LocalTime)
                "duree" -> state.copy(duree = value as Int)
                "type" -> state.copy(type = value as TypeRendezVous)
                "lieu" -> state.copy(lieu = value as String)
                "notes" -> state.copy(notes = value as String)
                else -> state
            }
        }
    }

    fun addRendezVous() {
        viewModelScope.launch {
            try {
                val state = _addState.value

                if (state.selectedPatientId <= 0L || state.titre.isBlank()) {
                    _addState.update { it.copy(error = "Patient et titre sont obligatoires") }
                    return@launch
                }

                val dateTime = LocalDateTime.of(state.date, state.heure)

                // FIX v2.1.11 : les patients Firestore ont un id synthetique
                // (>= 10000) qui n'existe pas dans la table Room `patients`.
                // Inserer un RDV avec cet id ferait planter la contrainte FK
                // (FOREIGN KEY constraint failed - error 19/787).
                // Solution : creer (ou retrouver) un stub PatientEntity local
                // pour ce patient Firestore, puis utiliser son vrai id Room.
                val selectedOption = _uiState.value.patientOptions
                    .find { it.id == state.selectedPatientId }

                val realPatientId: Long = if (selectedOption?.isFirestore == true) {
                    resolveLocalPatientIdForFirestore(selectedOption)
                } else {
                    state.selectedPatientId
                }

                val rdv = RendezVousEntity(
                    patientId = realPatientId,
                    titre = state.titre.trim(),
                    dateHeure = dateTime,
                    dureeMinutes = state.duree,
                    type = state.type,
                    lieu = state.lieu.trim(),
                    notes = state.notes.trim()
                )

                val rdvId = rendezVousRepository.insertRendezVous(rdv)

                // Sync to Firestore rdv_shared for the patient
                // (selectedOption est deja resolu plus haut pour le FK fix)
                val patientUid = selectedOption?.uid
                if (!patientUid.isNullOrBlank()) {
                    val medecinProfile = authRepository.getCurrentUserProfile()
                    val rdvData = mapOf(
                        "titre" to rdv.titre,
                        "dateHeure" to dateTime.toString(),
                        "dureeMinutes" to rdv.dureeMinutes,
                        "type" to rdv.type.name,
                        "lieu" to rdv.lieu,
                        "notes" to rdv.notes,
                        "estConfirme" to false,
                        "medecinNom" to (medecinProfile?.nomComplet ?: "Votre médecin"),
                        "medecinUid" to (authRepository.currentUserId ?: ""),
                        "createdAt" to LocalDateTime.now().toString()
                    )
                    try {
                        firestore.collection("rdv_shared")
                            .document(patientUid)
                            .collection("rendezvous")
                            .document(rdvId.toString())
                            .set(rdvData).await()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync RDV to Firestore", e)
                    }
                }

                _uiState.update { it.copy(showAddDialog = false, addSuccess = true) }
                _addState.value = AddRendezVousState(selectedPatientId = initialPatientId ?: 0)

                loadData()

            } catch (e: Exception) {
                _addState.update { it.copy(error = e.message) }
            }
        }
    }

    fun toggleConfirmation(rendezVous: RendezVousEntity) {
        viewModelScope.launch {
            try {
                val newStatus = !rendezVous.estConfirme
                rendezVousRepository.toggleConfirmation(rendezVous.id, rendezVous.estConfirme)

                // Sync confirmation status to Firestore rdv_shared
                val selectedOption = _uiState.value.patientOptions.find { it.id == rendezVous.patientId }
                val patientUid = selectedOption?.uid
                if (!patientUid.isNullOrBlank()) {
                    try {
                        firestore.collection("rdv_shared")
                            .document(patientUid)
                            .collection("rendezvous")
                            .document(rendezVous.id.toString())
                            .update("estConfirme", newStatus).await()
                        Log.d(TAG, "RDV confirmation synced to Firestore: $newStatus")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync RDV confirmation to Firestore", e)
                    }
                }
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteRendezVous(rendezVous: RendezVousEntity) {
        viewModelScope.launch {
            try {
                rendezVousRepository.deleteRendezVous(rendezVous)

                // Also delete from Firestore rdv_shared
                val selectedOption = _uiState.value.patientOptions.find { it.id == rendezVous.patientId }
                val patientUid = selectedOption?.uid
                if (!patientUid.isNullOrBlank()) {
                    try {
                        firestore.collection("rdv_shared")
                            .document(patientUid)
                            .collection("rendezvous")
                            .document(rendezVous.id.toString())
                            .delete().await()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete RDV from Firestore", e)
                    }
                }
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun getFilteredRendezVous(): List<RendezVousAvecPatient> {
        return when (_uiState.value.filter) {
            RendezVousFilter.TOUS -> _uiState.value.rendezVous
            RendezVousFilter.A_VENIR -> _uiState.value.rendezVous.filter { !it.rendezVous.estPasse() }
            RendezVousFilter.PASSES -> _uiState.value.rendezVous.filter { it.rendezVous.estPasse() }
        }
    }

    fun getFilteredPatientRendezVous(): List<RendezVousPatientItem> {
        return when (_uiState.value.filter) {
            RendezVousFilter.TOUS -> _uiState.value.patientRendezVous
            RendezVousFilter.A_VENIR -> _uiState.value.patientRendezVous.filter { !it.estPasse() }
            RendezVousFilter.PASSES -> _uiState.value.patientRendezVous.filter { it.estPasse() }
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

    // ============================================================
    // Patient: prise de RDV (booking flow)
    // ============================================================

    fun loadAvailableMedecins() {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("users")
                    .whereEqualTo("role", "MEDECIN")
                    .get().await()
                val medecins = snap.documents.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    doc.data?.let { UserProfile.fromMap(it as Map<String, Any?>) }?.copy(uid = doc.id)
                }
                _uiState.update { it.copy(availableMedecins = medecins) }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load available medecins", e)
            }
        }
    }

    fun toggleBookDialog(show: Boolean) {
        _uiState.update { it.copy(showBookDialog = show) }
        if (show && _uiState.value.availableMedecins.isEmpty()) {
            loadAvailableMedecins()
        }
        if (!show) {
            _bookState.value = BookAppointmentState()
        }
    }

    fun updateBookField(field: String, value: Any?) {
        _bookState.update { state ->
            when (field) {
                "medecin" -> {
                    val medecin = value as? UserProfile
                    state.copy(
                        selectedMedecinUid = medecin?.uid ?: "",
                        selectedMedecinNom = medecin?.nomComplet ?: ""
                    )
                }
                "date" -> state.copy(date = value as LocalDate)
                "heure" -> state.copy(heure = value as LocalTime)
                "duree" -> state.copy(duree = value as Int)
                "motif" -> state.copy(motif = value as String)
                "type" -> state.copy(type = value as String)
                else -> state
            }
        }
    }

    fun submitBookingRequest() {
        viewModelScope.launch {
            try {
                val state = _bookState.value
                if (state.selectedMedecinUid.isBlank() || state.motif.isBlank()) {
                    _bookState.update { it.copy(error = "Médecin et motif sont obligatoires") }
                    return@launch
                }
                _bookState.update { it.copy(isSubmitting = true, error = null) }

                val dateTime = LocalDateTime.of(state.date, state.heure)
                appointmentRequestRepository.createRequest(
                    medecinUid = state.selectedMedecinUid,
                    medecinNom = state.selectedMedecinNom,
                    dateHeureIso = dateTime.toString(),
                    dureeMinutes = state.duree,
                    motif = state.motif.trim(),
                    type = state.type
                )

                _uiState.update { it.copy(showBookDialog = false, bookSuccess = true) }
                _bookState.value = BookAppointmentState()
            } catch (e: Exception) {
                _bookState.update { it.copy(isSubmitting = false, error = e.message) }
            }
        }
    }

    fun clearBookSuccess() {
        _uiState.update { it.copy(bookSuccess = false) }
    }

    // ============================================================
    // Medecin: gestion des demandes (accept/reject)
    // ============================================================

    fun acceptAppointmentRequest(requestId: String, reponse: String = "") {
        viewModelScope.launch {
            try {
                appointmentRequestRepository.acceptRequest(requestId, reponse)
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun rejectAppointmentRequest(requestId: String, reponse: String = "") {
        viewModelScope.launch {
            try {
                appointmentRequestRepository.rejectRequest(requestId, reponse)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun cancelAppointmentRequest(requestId: String) {
        viewModelScope.launch {
            try {
                appointmentRequestRepository.cancelRequest(requestId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Trouve ou cree un PatientEntity local pour un patient Firestore.
     *
     * Contexte (v2.1.11) : les PatientOption issues de Firestore ont des ids
     * synthetiques (>= 10000) qui n'existent pas dans la table Room `patients`.
     * Pour pouvoir inserer un RDV (FK sur patientId), on a besoin d'une vraie
     * ligne dans `patients`.
     *
     * Le stub utilise `email = "firestore:<uid>"` comme marqueur pour le
     * retrouver lors des RDV suivants — on ne cree donc qu'UN stub par patient.
     * Les autres champs sont des placeholders minimaux (date de naissance
     * inconnue, sexe AUTRE, type TYPE_2 par defaut). Le medecin peut enrichir
     * ces donnees plus tard via la fiche patient.
     */
    private suspend fun resolveLocalPatientIdForFirestore(option: PatientOption): Long {
        val uid = option.uid.ifBlank {
            // Pas de uid -> fallback sur le nom pour eviter des doublons
            "nom:${option.nom}"
        }
        val marker = "firestore:$uid"

        // 1. Patient stub deja cree precedemment ?
        val existing = patientRepository.getAllPatientsList()
            .find { it.email == marker }
        if (existing != null) return existing.id

        // 2. Sinon, creer un stub minimal
        val parts = option.nom.trim().split(" ", limit = 2)
        val prenom = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Patient"
        val nom = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Firestore"
        val stub = PatientEntity(
            nom = nom,
            prenom = prenom,
            dateNaissance = LocalDate.of(2000, 1, 1),
            sexe = Sexe.AUTRE,
            typeDiabete = TypeDiabete.TYPE_2,
            email = marker,
            notes = "Patient synchronise via Firestore"
        )
        return patientRepository.insertPatient(stub)
    }
}
