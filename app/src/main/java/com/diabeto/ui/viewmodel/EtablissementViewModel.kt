package com.diabeto.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.model.Affiliation
import com.diabeto.data.model.Etablissement
import com.diabeto.data.model.MembreEtablissement
import com.diabeto.data.model.PrioriteSuivi
import com.diabeto.data.model.RoleEtablissement
import com.diabeto.data.model.SuiviPatient
import com.diabeto.data.model.TypeCode
import com.diabeto.data.model.UserRole
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.EtablissementRepository
import com.diabeto.report.RapportPayeurGenerator
import com.diabeto.util.MessageErreur
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class FiltreSuivi { TOUS, A_RISQUE, PERDUS_DE_VUE }

data class EtablissementUiState(
    val isLoading: Boolean = true,
    val estSoignant: Boolean = false,
    val affiliation: Affiliation? = null,
    val etablissement: Etablissement? = null,
    val membres: List<MembreEtablissement> = emptyList(),
    val suivis: List<SuiviPatient> = emptyList(),
    val chargementSuivi: Boolean = false,
    val filtre: FiltreSuivi = FiltreSuivi.TOUS,
    val codeAConfirmer: EtablissementRepository.InfoCode? = null,
    val enCours: Boolean = false,
    val rapportPdf: File? = null,
    val message: String? = null
) {
    val estAdmin: Boolean get() = affiliation?.role == RoleEtablissement.ADMIN
    val suivisFiltres: List<SuiviPatient> get() = when (filtre) {
        FiltreSuivi.TOUS -> suivis
        FiltreSuivi.A_RISQUE -> suivis.filter { it.priorite == PrioriteSuivi.HAUTE }
        FiltreSuivi.PERDUS_DE_VUE -> suivis.filter { it.perduDeVue }
    }
}

@HiltViewModel
class EtablissementViewModel @Inject constructor(
    private val repository: EtablissementRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EtablissementUiState())
    val uiState: StateFlow<EtablissementUiState> = _uiState.asStateFlow()

    init { charger() }

    fun charger() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val profil = authRepository.getCurrentUserProfile()
                val estSoignant = profil?.role == UserRole.MEDECIN
                var aff = repository.getMonAffiliation()
                // Retire par l'administrateur (ou centre quitte ailleurs) : on nettoie
                if (aff != null && aff.role != RoleEtablissement.ADMIN && !repository.inscriptionToujoursValide(aff)) {
                    repository.effacerAffiliation()
                    aff = null
                    _uiState.update { it.copy(message = "Tu ne fais plus partie de cet etablissement") }
                }
                val etab = if (aff != null && aff.role != RoleEtablissement.PATIENT)
                    repository.getEtablissement(aff.etablissementId) else null
                _uiState.update {
                    it.copy(isLoading = false, estSoignant = estSoignant, affiliation = aff, etablissement = etab)
                }
                if (etab != null) chargerEquipeEtSuivi(etab.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, message = MessageErreur.lisible(e)) }
            }
        }
    }

    private suspend fun chargerEquipeEtSuivi(etabId: String) {
        _uiState.update { it.copy(chargementSuivi = true) }
        try {
            val membres = repository.getMembres(etabId)
            val patients = repository.getPatients(etabId)
            _uiState.update { it.copy(membres = membres) }
            val suivis = repository.calculerSuivi(patients)
                .sortedWith(compareBy<SuiviPatient>({ it.priorite.ordinal }, { !it.perduDeVue }, { it.nom }))
            _uiState.update { it.copy(suivis = suivis, chargementSuivi = false) }
        } catch (e: Exception) {
            _uiState.update { it.copy(chargementSuivi = false, message = MessageErreur.lisible(e)) }
        }
    }

    fun actualiserSuivi() {
        val etab = _uiState.value.etablissement ?: return
        viewModelScope.launch { chargerEquipeEtSuivi(etab.id) }
    }

    fun setFiltre(f: FiltreSuivi) = _uiState.update { it.copy(filtre = f) }

    fun creer(nom: String, ville: String) {
        if (nom.isBlank()) { _uiState.update { it.copy(message = "Indique le nom de l'etablissement") }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(enCours = true) }
            repository.creerEtablissement(nom, ville).fold(
                onSuccess = { _uiState.update { s -> s.copy(enCours = false, message = "Etablissement cree. Tu en es l'administrateur.") }; charger() },
                onFailure = { e -> _uiState.update { it.copy(enCours = false, message = MessageErreur.lisible(e)) } }
            )
        }
    }

    fun verifierCode(saisie: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(enCours = true) }
            repository.verifierCode(saisie).fold(
                onSuccess = { info -> _uiState.update { it.copy(enCours = false, codeAConfirmer = info) } },
                onFailure = { e -> _uiState.update { it.copy(enCours = false, message = e.message ?: MessageErreur.lisible(e)) } }
            )
        }
    }

    fun annulerCode() = _uiState.update { it.copy(codeAConfirmer = null) }

    fun confirmerRejoindre() {
        val info = _uiState.value.codeAConfirmer ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(enCours = true) }
            repository.rejoindre(info).fold(
                onSuccess = {
                    _uiState.update { it.copy(enCours = false, codeAConfirmer = null, message = "Inscription faite : ${info.etablissementNom}") }
                    charger()
                },
                onFailure = { e -> _uiState.update { it.copy(enCours = false, codeAConfirmer = null, message = e.message ?: MessageErreur.lisible(e)) } }
            )
        }
    }

    fun quitter() {
        val aff = _uiState.value.affiliation ?: return
        viewModelScope.launch {
            repository.quitter(aff).fold(
                onSuccess = {
                    _uiState.update { EtablissementUiState(isLoading = false, estSoignant = it.estSoignant, message = "Tu as quitte ${aff.etablissementNom}") }
                },
                onFailure = { e -> _uiState.update { it.copy(message = e.message ?: MessageErreur.lisible(e)) } }
            )
        }
    }

    fun regenererCode(type: TypeCode) {
        val etab = _uiState.value.etablissement ?: return
        viewModelScope.launch {
            repository.regenererCode(etab, type).fold(
                onSuccess = { maj -> _uiState.update { it.copy(etablissement = maj, message = "Nouveau code cree. L'ancien ne marche plus.") } },
                onFailure = { e -> _uiState.update { it.copy(message = MessageErreur.lisible(e)) } }
            )
        }
    }

    fun retirerMembre(uid: String) {
        val etab = _uiState.value.etablissement ?: return
        viewModelScope.launch {
            repository.retirerMembre(etab.id, uid).fold(
                onSuccess = { _uiState.update { s -> s.copy(membres = s.membres.filter { it.uid != uid }, message = "Soignant retire") } },
                onFailure = { e -> _uiState.update { it.copy(message = MessageErreur.lisible(e)) } }
            )
        }
    }

    fun retirerPatient(uid: String) {
        val etab = _uiState.value.etablissement ?: return
        viewModelScope.launch {
            repository.retirerPatient(etab.id, uid).fold(
                onSuccess = { _uiState.update { s -> s.copy(suivis = s.suivis.filter { it.uid != uid }, message = "Patient retire de l'etablissement") } },
                onFailure = { e -> _uiState.update { it.copy(message = MessageErreur.lisible(e)) } }
            )
        }
    }

    fun genererRapport(context: Context) {
        val s = _uiState.value
        val etab = s.etablissement ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(enCours = true) }
            try {
                val file = withContext(Dispatchers.IO) {
                    RapportPayeurGenerator(context.applicationContext)
                        .generer(etab, s.membres.size, s.suivis)
                }
                _uiState.update { it.copy(enCours = false, rapportPdf = file) }
            } catch (e: Exception) {
                _uiState.update { it.copy(enCours = false, message = MessageErreur.lisible(e)) }
            }
        }
    }

    fun rapportPartage() = _uiState.update { it.copy(rapportPdf = null) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
