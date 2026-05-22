package com.diabeto.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.model.DataSharingConsent
import com.diabeto.data.model.DoctorReview
import com.diabeto.data.model.GeoUtils
import com.diabeto.data.model.UserProfile
import com.diabeto.data.model.UserRole
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.DataSharingRepository
import com.diabeto.data.repository.DoctorReviewRepository
import com.diabeto.data.repository.GlucoseRepository
import com.diabeto.data.repository.LocationRepository
import com.diabeto.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Mode de tri des medecins dans la recherche.
 *  - SCORE : meilleure note ponderee par le nombre d'avis (defaut)
 *  - DISTANCE : proximite geographique (necessite la position du patient)
 */
enum class DoctorSortMode { SCORE, DISTANCE }

/** Medecin enrichi pour l'affichage : note + distance pre-calculee. */
data class DoctorListItem(
    val profile: UserProfile,
    val distanceKm: Double? = null
)

data class DataSharingUiState(
    val isPatient: Boolean = true,
    val consents: List<DataSharingConsent> = emptyList(),
    val availableMedecins: List<DoctorListItem> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val exportData: String? = null,
    val sortMode: DoctorSortMode = DoctorSortMode.SCORE,
    val patientLat: Double? = null,
    val patientLon: Double? = null,
    // Rating dialog
    val ratingTarget: UserProfile? = null,
    val myExistingReview: DoctorReview? = null,
    val isSubmittingReview: Boolean = false,
    // Reviews viewing sheet
    val reviewsTarget: UserProfile? = null,
    val reviewsList: List<DoctorReview> = emptyList(),
    val isLoadingReviews: Boolean = false
)

@HiltViewModel
class DataSharingViewModel @Inject constructor(
    private val dataSharingRepository: DataSharingRepository,
    private val authRepository: AuthRepository,
    private val patientRepository: PatientRepository,
    private val glucoseRepository: GlucoseRepository,
    private val doctorReviewRepository: DoctorReviewRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataSharingUiState())
    val uiState: StateFlow<DataSharingUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val profile = authRepository.getCurrentUserProfile()
                val isPatient = profile?.role != UserRole.MEDECIN

                val consents = if (isPatient) {
                    dataSharingRepository.getMyConsents()
                } else {
                    dataSharingRepository.getSharedPatients()
                }

                _uiState.update {
                    it.copy(
                        isPatient = isPatient,
                        consents = consents,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, message = "Erreur: ${e.message}") }
            }
        }
    }

    /**
     * Charge la liste des medecins et calcule la liste enrichie (distance si GPS dispo).
     * Tri selon `sortMode` courant.
     */
    fun loadMedecins() {
        viewModelScope.launch {
            try {
                val medecins = dataSharingRepository.getAvailableMedecins()
                val lat = _uiState.value.patientLat
                val lon = _uiState.value.patientLon
                val items = medecins.map { m ->
                    val dist = if (lat != null && lon != null && m.latitude != null && m.longitude != null) {
                        GeoUtils.distanceKm(lat, lon, m.latitude, m.longitude)
                    } else null
                    DoctorListItem(profile = m, distanceKm = dist)
                }
                _uiState.update { it.copy(availableMedecins = sortMedecins(items, it.sortMode)) }
            } catch (e: Exception) {
                Log.w("DataSharingVM", "Failed to load medecins", e)
            }
        }
    }

    private fun sortMedecins(
        items: List<DoctorListItem>,
        mode: DoctorSortMode
    ): List<DoctorListItem> = when (mode) {
        DoctorSortMode.SCORE -> items.sortedByDescending { it.profile.doctorScore }
        DoctorSortMode.DISTANCE -> items.sortedWith(
            compareBy(
                { it.distanceKm ?: Double.MAX_VALUE },
                { -it.profile.doctorScore } // egalite : meilleure note en premier
            )
        )
    }

    /** Change le tri et re-trie sans refaire l'appel reseau. */
    fun setSortMode(mode: DoctorSortMode) {
        _uiState.update { state ->
            state.copy(
                sortMode = mode,
                availableMedecins = sortMedecins(state.availableMedecins, mode)
            )
        }
    }

    /**
     * Tente de capturer la position GPS du patient (permission doit etre accordee cote UI).
     * Recalcule les distances + trie par DISTANCE si succes.
     */
    fun captureMyLocation() {
        viewModelScope.launch {
            if (!locationRepository.hasLocationPermission()) {
                _uiState.update { it.copy(message = "Autorisation de localisation requise") }
                return@launch
            }
            val point = locationRepository.getCurrentLocation()
            if (point == null) {
                _uiState.update { it.copy(message = "Impossible d'obtenir la position") }
                return@launch
            }
            _uiState.update { state ->
                val updated = state.availableMedecins.map { item ->
                    val doc = item.profile
                    val dist = if (doc.latitude != null && doc.longitude != null) {
                        GeoUtils.distanceKm(point.latitude, point.longitude, doc.latitude, doc.longitude)
                    } else null
                    item.copy(distanceKm = dist)
                }
                state.copy(
                    patientLat = point.latitude,
                    patientLon = point.longitude,
                    sortMode = DoctorSortMode.DISTANCE,
                    availableMedecins = sortMedecins(updated, DoctorSortMode.DISTANCE),
                    message = "Position capturee — medecins tries par distance"
                )
            }
        }
    }

    fun grantConsent(medecinUid: String) {
        viewModelScope.launch {
            val result = dataSharingRepository.grantConsent(medecinUid)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(message = "Accès accordé avec succès") }
                    loadData()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(message = "Erreur: ${e.message}") }
                }
            )
        }
    }

    fun revokeConsent(medecinUid: String) {
        viewModelScope.launch {
            val result = dataSharingRepository.revokeConsent(medecinUid)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(message = "Accès révoqué") }
                    loadData()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(message = "Erreur: ${e.message}") }
                }
            )
        }
    }

    /** v2.1.44 : medecin se desabonne d'un patient. */
    fun unlinkPatient(patientUid: String) {
        viewModelScope.launch {
            dataSharingRepository.unlinkAsDoctor(patientUid).fold(
                onSuccess = {
                    _uiState.update { it.copy(message = "Patient retire de votre liste") }
                    loadData()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(message = "Erreur: ${e.message}") }
                }
            )
        }
    }

    /** v2.1.44 : reactive un lien precedemment revoque (n'importe quelle des 2 parties). */
    fun reactivateLink(otherUid: String) {
        viewModelScope.launch {
            dataSharingRepository.reactivateConsent(otherUid).fold(
                onSuccess = {
                    _uiState.update { it.copy(message = "Lien reactive") }
                    loadData()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(message = "Erreur: ${e.message}") }
                }
            )
        }
    }

    // ── Ratings ─────────────────────────────────────────────────────

    /** Ouvre le dialog de notation pour un medecin. Precharge l'avis existant du patient. */
    fun openRateDoctor(doctor: UserProfile) {
        viewModelScope.launch {
            val existing = runCatching { doctorReviewRepository.getMyReviewFor(doctor.uid) }
                .getOrNull()
            _uiState.update {
                it.copy(ratingTarget = doctor, myExistingReview = existing)
            }
        }
    }

    fun closeRateDoctor() {
        _uiState.update { it.copy(ratingTarget = null, myExistingReview = null) }
    }

    /** Ouvre la liste des avis d'un medecin et charge les 50 plus recents. */
    fun openDoctorReviews(doctor: UserProfile) {
        _uiState.update { it.copy(reviewsTarget = doctor, reviewsList = emptyList(), isLoadingReviews = true) }
        viewModelScope.launch {
            val list = runCatching { doctorReviewRepository.getReviewsForDoctor(doctor.uid) }
                .getOrElse { emptyList() }
            _uiState.update { it.copy(reviewsList = list, isLoadingReviews = false) }
        }
    }

    fun closeDoctorReviews() {
        _uiState.update { it.copy(reviewsTarget = null, reviewsList = emptyList()) }
    }

    fun submitReview(rating: Int, comment: String) {
        val target = _uiState.value.ratingTarget ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingReview = true) }
            val me = authRepository.getCurrentUserProfile()
            val result = doctorReviewRepository.submitReview(
                doctorUid = target.uid,
                rating = rating,
                comment = comment,
                patientNom = me?.nomComplet ?: ""
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSubmittingReview = false,
                            ratingTarget = null,
                            myExistingReview = null,
                            message = "Merci pour votre avis !"
                        )
                    }
                    // Rafraichir la liste pour que le score soit a jour
                    loadMedecins()
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isSubmittingReview = false,
                            message = "Erreur: ${e.message}"
                        )
                    }
                }
            )
        }
    }

    fun generateExportData(format: String) {
        viewModelScope.launch {
            try {
                val patients = patientRepository.getAllPatientsList()
                val sb = StringBuilder()
                val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

                if (format == "csv") {
                    // CSV format
                    sb.appendLine("Date,Glycémie (mg/dL),Contexte,HbA1c (%),Type HbA1c")
                    patients.forEach { patient ->
                        val lectures = glucoseRepository.getLecturesByPatientList(patient.id, 200)
                        lectures.forEach { l ->
                            sb.appendLine("${l.dateHeure.format(dateFmt)},${l.valeur.toInt()},${l.contexte.getDisplayName()},,")
                        }
                        val hba1cList = glucoseRepository.getHbA1cByPatientList(patient.id)
                        hba1cList.forEach { h ->
                            sb.appendLine("${h.dateMesure.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))},,,${h.valeur},${if (h.estEstimation) "estimée" else "labo"}")
                        }
                    }
                } else {
                    // Text report
                    sb.appendLine("═══════════════════════════════════════")
                    sb.appendLine("     RAPPORT DIASMART")
                    sb.appendLine("     Généré le ${java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
                    sb.appendLine("═══════════════════════════════════════")
                    sb.appendLine()

                    patients.forEach { patient ->
                        sb.appendLine("── Patient: ${patient.nomComplet} ──")
                        sb.appendLine("Age: ${patient.age} ans | Sexe: ${patient.sexe.name}")
                        sb.appendLine("Diabète: ${patient.typeDiabete.name.replace("_", " ")}")
                        patient.imc?.let { sb.appendLine("IMC: ${"%.1f".format(it)} (${patient.categorieImc})") }
                        sb.appendLine()

                        val stats = glucoseRepository.getStatistics(patient.id, 30)
                        sb.appendLine("Statistiques (30j):")
                        sb.appendLine("  Moyenne: ${stats.moyenne.toInt()} mg/dL")
                        sb.appendLine("  TIR: ${stats.timeInRange.toInt()}%")
                        sb.appendLine("  Min: ${stats.minimum.toInt()} | Max: ${stats.maximum.toInt()}")
                        sb.appendLine()

                        val hba1cList = glucoseRepository.getHbA1cByPatientList(patient.id)
                        if (hba1cList.isNotEmpty()) {
                            sb.appendLine("HbA1c:")
                            hba1cList.take(5).forEach { h ->
                                sb.appendLine("  ${h.dateMesure}: ${h.valeur}% (${if (h.estEstimation) "estimée" else "labo"})")
                            }
                            sb.appendLine()
                        }

                        val lectures = glucoseRepository.getLecturesByPatientList(patient.id, 30)
                        sb.appendLine("Dernières lectures:")
                        lectures.take(20).forEach { l ->
                            sb.appendLine("  ${l.dateHeure.format(dateFmt)} : ${l.valeur.toInt()} mg/dL (${l.contexte.getDisplayName()})")
                        }
                        sb.appendLine()
                    }

                    sb.appendLine("═══════════════════════════════════════")
                    sb.appendLine("Avis informatif — consultez votre médecin.")
                }

                _uiState.update { it.copy(exportData = sb.toString()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Erreur export: ${e.message}") }
            }
        }
    }

    /** Patient accepte une demande d'acces d'un medecin. */
    fun acceptRequest(medecinUid: String) {
        viewModelScope.launch {
            dataSharingRepository.acceptRequest(medecinUid).fold(
                onSuccess = {
                    _uiState.update { it.copy(message = "Demande acceptee") }
                    loadData()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(message = "Erreur: ${e.message}") }
                }
            )
        }
    }

    /** Patient refuse une demande d'acces d'un medecin. */
    fun rejectRequest(medecinUid: String) {
        viewModelScope.launch {
            dataSharingRepository.rejectRequest(medecinUid).fold(
                onSuccess = {
                    _uiState.update { it.copy(message = "Demande refusee") }
                    loadData()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(message = "Erreur: ${e.message}") }
                }
            )
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
    fun clearExportData() = _uiState.update { it.copy(exportData = null) }
}
