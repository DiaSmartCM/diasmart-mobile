package com.diabeto.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.model.EntreeDossier
import com.diabeto.data.model.TypeEntree
import com.diabeto.data.model.UserProfile
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.DossierRepository
import com.diabeto.util.MessageErreur
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DossierUiState(
    val chargement: Boolean = true,
    val commePatient: Boolean = false,
    val patientUid: String = "",
    val medecinUid: String = "",
    val entrees: List<EntreeDossier> = emptyList(),
    val rendezVous: List<Map<String, Any?>> = emptyList(),
    val enregistrement: Boolean = false,
    val message: String? = null
)

/**
 * Deux entrees possibles, selon la route :
 * - `patientUid` : le medecin ouvre le dossier d'un de ses patients ;
 * - `medecinUid` : le patient consulte ce que ce medecin a partage avec lui.
 */
@HiltViewModel
class DossierViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dossierRepository: DossierRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val patientArg: String = savedStateHandle.get<String>("patientUid").orEmpty()
    private val medecinArg: String = savedStateHandle.get<String>("medecinUid").orEmpty()

    private val _uiState = MutableStateFlow(DossierUiState())
    val uiState: StateFlow<DossierUiState> = _uiState.asStateFlow()

    init {
        charger()
    }

    private fun charger() {
        viewModelScope.launch {
            val moi = authRepository.currentUserId
            if (moi == null) {
                _uiState.update { it.copy(chargement = false, message = "Session expirée, reconnectez-vous.") }
                return@launch
            }
            val commePatient = medecinArg.isNotBlank()
            val patientUid = if (commePatient) moi else patientArg
            val medecinUid = if (commePatient) medecinArg else moi
            _uiState.update {
                it.copy(commePatient = commePatient, patientUid = patientUid, medecinUid = medecinUid)
            }

            if (!commePatient) {
                val medecin = authRepository.getCurrentUserProfileRapide()
                val patient = authRepository.getUserProfile(patientUid)
                dossierRepository.assurerDossier(
                    patientUid = patientUid,
                    medecinUid = medecinUid,
                    patientNom = patient?.nomComplet.orEmpty(),
                    medecinNom = medecin?.nomComplet.orEmpty()
                ).onSuccess { (_, nouveau) ->
                    // Dossier ouvert pour la premiere fois : l'identite est
                    // reprise du profil, le medecin n'a plus qu'a completer.
                    if (nouveau && patient != null) preRemplirIdentite(patientUid, medecinUid, patient)
                }.onFailure {
                    _uiState.update {
                        it.copy(message = "Dossier inaccessible : le lien avec ce patient n'est peut-être plus actif.")
                    }
                }
                _uiState.update { it.copy(rendezVous = dossierRepository.rendezVous(patientUid, medecinUid)) }
            }

            dossierRepository.observerEntrees(patientUid, medecinUid, commePatient)
                .catch { e -> _uiState.update { it.copy(chargement = false, message = MessageErreur.lisible(e)) } }
                .collect { fiches -> _uiState.update { it.copy(chargement = false, entrees = fiches) } }
        }
    }

    private fun medecinmedecinNom(profil: UserProfile?) = profil?.nomComplet.orEmpty()

    /** Bouton « Importer l'identite » quand le dossier a ete cree cote patient. */
    fun importerIdentite() {
        val etat = _uiState.value
        viewModelScope.launch {
            val profil = authRepository.getUserProfile(etat.patientUid)
            if (profil == null) {
                _uiState.update { it.copy(message = "Profil du patient introuvable.") }
                return@launch
            }
            preRemplirIdentite(etat.patientUid, etat.medecinUid, profil)
        }
    }

    private fun preRemplirIdentite(patientUid: String, medecinUid: String, profil: UserProfile) {
        viewModelScope.launch {
            val adresse = listOf(profil.adresse, profil.ville).filter { it.isNotBlank() }.joinToString(", ")
            val identite = EntreeDossier(
                patientUid = patientUid,
                medecinUid = medecinUid,
                type = TypeEntree.IDENTITE,
                date = DossierRepository.aujourdhui(),
                champs = mapOf(
                    "nom" to profil.nomComplet,
                    "telephone" to profil.telephone,
                    "email" to profil.email,
                    "adresse" to adresse
                ).filterValues { it.isNotBlank() },
                visiblePatient = true
            )
            dossierRepository.enregistrer(identite)
        }
    }

    fun enregistrer(entree: EntreeDossier) {
        val etat = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(enregistrement = true) }
            dossierRepository.enregistrer(
                entree.copy(patientUid = etat.patientUid, medecinUid = etat.medecinUid)
            ).fold(
                onSuccess = { _uiState.update { it.copy(enregistrement = false, message = "Fiche enregistrée") } },
                onFailure = { e -> _uiState.update { it.copy(enregistrement = false, message = MessageErreur.lisible(e)) } }
            )
        }
    }

    fun changerVisibilite(entree: EntreeDossier, visible: Boolean) {
        viewModelScope.launch {
            dossierRepository.changerVisibilite(entree, visible).onFailure { e ->
                _uiState.update { it.copy(message = MessageErreur.lisible(e)) }
            }
        }
    }

    fun supprimer(entree: EntreeDossier) {
        viewModelScope.launch {
            dossierRepository.supprimer(entree).fold(
                onSuccess = { _uiState.update { it.copy(message = "Fiche supprimée") } },
                onFailure = { e -> _uiState.update { it.copy(message = MessageErreur.lisible(e)) } }
            )
        }
    }

    fun effacerMessage() = _uiState.update { it.copy(message = null) }
}
