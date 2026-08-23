package com.diabeto.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.entity.*
import com.diabeto.data.model.UserRole
import com.diabeto.data.repository.*
import com.diabeto.util.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * État du tableau de bord
 */
data class DashboardUiState(
    val totalPatients: Int = 0,
    val linkedDoctors: Int = 0,
    val avgGlucose: Double = 0.0, // always stored in mg/dL
    val todayRendezVous: Int = 0,
    val todayConfirmed: Int = 0,
    val pendingConfirmations: Int = 0,
    val upcomingMedicaments: Int = 0,
    val upcomingRendezVous: List<RendezVousAvecPatient> = emptyList(),
    val recentPatients: List<PatientEntity> = emptyList(),
    /**
     * v2.1.81 : patients ayant accorde un partage actif a ce medecin. Seule
     * source autorisee pour afficher un patient sur le tableau de bord d'un
     * medecin — la base locale ignore les consentements.
     */
    val patientsConsentants: List<com.diabeto.data.model.DataSharingConsent> = emptyList(),
    // v2.1.71 : id du dossier Room "self" du patient — sert a ouvrir l'ecran
    // glycemie depuis le dashboard (la carte glycemie du header devient
    // cliquable cote patient). null si aucun dossier local encore.
    val selfPatientId: Long? = null,
    val isLoading: Boolean = false,
    val isOnline: Boolean = true,
    val pendingSyncCount: Int = 0,
    val error: String? = null,
    val userRole: UserRole = UserRole.PATIENT,
    /**
     * v2.1.41 : indique si le role a ete charge depuis Firestore. Tant que false,
     * le Dashboard affiche un loading state au lieu du dashboard PATIENT par
     * defaut (qui apparaissait brievement chez un medecin a chaque ouverture).
     */
    val roleLoaded: Boolean = false,
    val glucoseUnit: com.diabeto.data.repository.GlucoseUnit = com.diabeto.data.repository.GlucoseUnit.MG_DL,
    /** v2.1.42 : true tant que l'utilisateur n'a pas encore vu l'onboarding (1ere ouverture). */
    val showOnboarding: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val glucoseRepository: GlucoseRepository,
    private val rendezVousRepository: RendezVousRepository,
    private val medicamentRepository: MedicamentRepository,
    private val authRepository: AuthRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val cloudBackupRepository: CloudBackupRepository,
    private val preferencesRepository: PreferencesRepository,
    private val pendingOperationDao: com.diabeto.data.dao.PendingOperationDao,
    private val dataSharingRepository: DataSharingRepository
) : ViewModel() {

    companion object {
        private const val TAG = "DashboardVM"
    }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadUserRole()
        checkAndRestoreFromCloud()
        loadDashboardData()
        observeConnectivity()
        observeGlucoseUnit()
        loadPendingSyncCount()
        observeOnboardingState()
    }

    /** v2.1.42 : ecoute l'etat onboarding pour declencher l'overlay. */
    private fun observeOnboardingState() {
        viewModelScope.launch {
            preferencesRepository.onboardingDashboardSeen.collect { seen ->
                _uiState.update { it.copy(showOnboarding = !seen) }
            }
        }
    }

    fun markOnboardingSeen() {
        viewModelScope.launch {
            preferencesRepository.markOnboardingDashboardSeen()
        }
    }

    private fun observeGlucoseUnit() {
        viewModelScope.launch {
            preferencesRepository.glucoseUnit.collect { unit ->
                _uiState.update { it.copy(glucoseUnit = unit) }
            }
        }
    }

    /**
     * Auto-restore from cloud if local DB is empty (e.g., after app reinstall).
     * This runs every time Dashboard loads, ensuring data is restored after login.
     */
    private fun checkAndRestoreFromCloud() {
        viewModelScope.launch {
            try {
                if (cloudBackupRepository.isLocalDbEmpty() && cloudBackupRepository.hasCloudBackup()) {
                    Log.d(TAG, "Local DB empty, restoring from cloud backup...")
                    val result = cloudBackupRepository.performFullRestore()
                    result.onSuccess { count ->
                        Log.d(TAG, "Cloud restore complete: $count documents restored")
                        // Reload dashboard data after restore
                        loadDashboardData()
                    }.onFailure { e ->
                        Log.e(TAG, "Cloud restore failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-restore check failed", e)
            }
        }
    }

    private fun loadUserRole() {
        viewModelScope.launch {
            try {
                val profile = authRepository.getCurrentUserProfile()
                val role = profile?.role ?: UserRole.PATIENT
                _uiState.update { state -> state.copy(userRole = role, roleLoaded = true) }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load user role, defaulting to PATIENT", e)
                _uiState.update { state -> state.copy(roleLoaded = true) }
            }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.observe().collect { online ->
                _uiState.update { it.copy(isOnline = online) }
                if (online) loadPendingSyncCount()
            }
        }
    }

    private fun loadPendingSyncCount() {
        viewModelScope.launch {
            try {
                val count = pendingOperationDao.pendingCount()
                _uiState.update { it.copy(pendingSyncCount = count) }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load pending sync count", e)
            }
        }
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Determination du role courant pour calculer les bons compteurs
                val role = authRepository.getCurrentUserProfile()?.role ?: UserRole.PATIENT

                // Nombre de patients lies (cote medecin) ou de medecins suivis (cote patient).
                // - MEDECIN : data_sharing where medecinUid==me & isActive=true
                // - PATIENT : data_sharing where patientUid==me & isActive=true
                val patientCount = if (role == UserRole.MEDECIN) {
                    runCatching { dataSharingRepository.getSharedPatients().size }
                        .getOrElse { patientRepository.getPatientCount() }
                } else {
                    patientRepository.getPatientCount()
                }
                val doctorCount = if (role == UserRole.PATIENT) {
                    runCatching {
                        dataSharingRepository.getMyConsents()
                            .count { it.isActive }
                    }.getOrDefault(0)
                } else 0
                
                // Rendez-vous du jour
                val todayCount = rendezVousRepository.getCountForDate(LocalDate.now())
                val confirmedCount = rendezVousRepository.getConfirmedCountForDate(LocalDate.now())
                val pending = rendezVousRepository.getPendingConfirmations()
                
                // Prochains rendez-vous
                val upcomingRdvs = rendezVousRepository.getUpcomingRendezVous(5)
                
                // Médicaments à venir
                val upcomingMeds = medicamentRepository.getUpcomingMedicaments()
                
                // ── Moyenne glycemique et patients recents ────────────────
                // v2.1.81 — correction de confidentialite.
                //
                // Ces deux valeurs etaient tirees de getAllPatientsList(), donc
                // de la base LOCALE, sans aucun filtre de consentement. Un
                // medecin voyait ainsi la glycemie moyenne et la fiche d'un
                // patient auquel il n'etait pas lie et qui n'avait rien
                // autorise. Le compteur "Patients", lui, lisait deja les
                // partages actifs : il affichait 0 pendant qu'une fiche
                // s'affichait juste en dessous — l'incoherence trahissait la
                // fuite.
                //
                // Cote patient, la moyenne reste celle de son propre dossier :
                // c'est sa donnee, il la consulte chez lui.
                // Cote medecin, aucune donnee glycemique n'est agregee sur le
                // tableau de bord. Les chiffres d'un patient se consultent
                // dans sa fiche, apres liaison et consentement.
                val patients = patientRepository.getAllPatientsList()
                val avgGlucose = if (role == UserRole.MEDECIN) {
                    0.0
                } else {
                    var totalGlucose = 0.0
                    var count = 0
                    patients.take(10).forEach { p ->
                        val avg = glucoseRepository.getLast24HoursAverage(p.id)
                        if (avg > 0) {
                            totalGlucose += avg
                            count++
                        }
                    }
                    if (count > 0) totalGlucose / count else 0.0
                }

                // Patients recents : uniquement ceux qui ont accorde un partage
                // actif a ce medecin. La liste vient de Firestore, ou le
                // consentement est la condition de lecture — pas de la base
                // locale, qui ne connait pas les autorisations.
                val patientsConsentants = if (role == UserRole.MEDECIN) {
                    runCatching { dataSharingRepository.getSharedPatients() }
                        .getOrDefault(emptyList())
                        .filter { it.isActive }
                        .take(5)
                } else emptyList()
                
                _uiState.update {
                    it.copy(
                        totalPatients = patientCount,
                        linkedDoctors = doctorCount,
                        avgGlucose = avgGlucose,
                        todayRendezVous = todayCount,
                        todayConfirmed = confirmedCount,
                        pendingConfirmations = pending.size,
                        upcomingMedicaments = upcomingMeds.size,
                        upcomingRendezVous = upcomingRdvs,
                        // Cote medecin la liste locale n'est plus exposee ;
                        // seuls les partages consentis alimentent l'ecran.
                        recentPatients = if (role == UserRole.MEDECIN) emptyList()
                                         else patients.take(5),
                        patientsConsentants = patientsConsentants,
                        // v2.1.71 : cote patient, garantit un dossier "self"
                        // (cree si absent) pour que la saisie glycemie / carnet /
                        // podometre / predictions fonctionnent.
                        selfPatientId = if (role == UserRole.PATIENT) {
                            val prof = authRepository.getCurrentUserProfile()
                            patientRepository.getOrCreateSelfPatientId(
                                nom = prof?.nom.orEmpty(),
                                prenom = prof?.prenom.orEmpty()
                            )
                        } else null,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                // Collect full exception chain to detect SQLite/SQLCipher errors
                val fullMsg = buildString {
                    var t: Throwable? = e
                    while (t != null) { append(t.message ?: ""); t = t.cause }
                }
                val cleanMsg = when {
                    fullMsg.contains("file is not a database", ignoreCase = true)
                            || fullMsg.contains("sqlite_master", ignoreCase = true)
                            || fullMsg.contains("SQLiteDatabaseCorruptException", ignoreCase = true)
                            || e.javaClass.name.contains("SQLite") ->
                        "Base de données réinitialisée. Veuillez relancer l'application."
                    fullMsg.contains("network", ignoreCase = true)
                            || fullMsg.contains("connect", ignoreCase = true)
                            || fullMsg.contains("UNAVAILABLE", ignoreCase = true) ->
                        "Erreur de connexion. Vérifiez votre accès Internet."
                    else -> "Erreur lors du chargement. Veuillez réessayer."
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = cleanMsg
                    )
                }
            }
        }
    }
    
    fun refresh() {
        loadDashboardData()
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Pending update (DataStore) ──

    val pendingUpdate: StateFlow<PreferencesRepository.PendingUpdate?> =
        preferencesRepository.pendingUpdate
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun clearPendingUpdate() {
        viewModelScope.launch {
            preferencesRepository.clearPendingUpdate()
        }
    }
}
