package com.diabeto.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.entity.TensionEntity
import com.diabeto.data.repository.TensionRepository
import com.diabeto.domain.ReglesTension
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class SaisieTension(
    val systolique: String = "",
    val diastolique: String = "",
    val pouls: String = "",
    val position: String = ReglesTension.ASSIS,
    val bras: String = ReglesTension.GAUCHE,
    val traitement: Boolean = false,
    val dateHeure: LocalDateTime? = null,   // null = maintenant
    val message: String? = null,
    val erreur: String? = null
)

@HiltViewModel
class TensionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TensionRepository,
    private val patientRepository: com.diabeto.data.repository.PatientRepository
) : ViewModel() {

    private val patientId: Long = savedStateHandle["patientId"] ?: 0L

    val tensions: StateFlow<List<TensionEntity>> = repository.getTensions(patientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Age du patient : objectif assoupli a partir de 65 ans. */
    private val _age = MutableStateFlow<Int?>(null)
    val age: StateFlow<Int?> = _age.asStateFlow()

    private val _saisie = MutableStateFlow(SaisieTension())
    val saisie: StateFlow<SaisieTension> = _saisie.asStateFlow()

    private fun chiffres(s: String) = s.filter { it.isDigit() }.take(3)

    fun onSystolique(v: String) = _saisie.update { it.copy(systolique = chiffres(v), erreur = null) }
    fun onDiastolique(v: String) = _saisie.update { it.copy(diastolique = chiffres(v), erreur = null) }
    fun onPouls(v: String) = _saisie.update { it.copy(pouls = chiffres(v), erreur = null) }
    fun onPosition(v: String) = _saisie.update { it.copy(position = v) }
    fun onBras(v: String) = _saisie.update { it.copy(bras = v) }
    fun onTraitement(v: Boolean) = _saisie.update { it.copy(traitement = v) }
    fun onDateHeure(v: LocalDateTime?) = _saisie.update { it.copy(dateHeure = v, erreur = null) }
    fun effacerMessage() = _saisie.update { it.copy(message = null, erreur = null) }

    fun ajouter() {
        val s = _saisie.value
        val sys = s.systolique.toIntOrNull()
        val dia = s.diastolique.toIntOrNull()
        val pouls = s.pouls.toIntOrNull()
        if (sys == null || dia == null || !ReglesTension.valide(sys, dia)) {
            _saisie.update { it.copy(erreur = "Valeurs invalides : le premier chiffre (systolique) doit être plus grand que le second (diastolique), ex. 130 / 85.") }
            return
        }
        if (pouls != null && pouls !in 30..220) {
            _saisie.update { it.copy(erreur = "Pouls invalide (30 à 220).") }
            return
        }
        val quand = s.dateHeure ?: LocalDateTime.now()
        if (quand.isAfter(LocalDateTime.now().plusMinutes(5))) {
            _saisie.update { it.copy(erreur = "La date ne peut pas être dans le futur.") }
            return
        }
        if (patientId <= 0) {
            _saisie.update { it.copy(erreur = "Dossier introuvable.") }
            return
        }
        viewModelScope.launch {
            try {
                val t = repository.ajouter(TensionEntity(
                    patientId = patientId, systolique = sys, diastolique = dia, pouls = pouls,
                    dateHeure = quand.withNano(0), position = s.position, bras = s.bras, traitement = s.traitement
                ))
                // Test couche-debout : on propose directement l'etape suivante
                val (suivante, message) = when (s.position) {
                    ReglesTension.COUCHE -> ReglesTension.DEBOUT_1MIN to "Mesure enregistrée. Levez-vous et mesurez après 1 minute debout."
                    ReglesTension.DEBOUT_1MIN -> ReglesTension.DEBOUT_3MIN to "Mesure enregistrée. Restez debout et mesurez à 3 minutes."
                    else -> ReglesTension.ASSIS to "Mesure enregistrée"
                }
                _saisie.value = SaisieTension(position = suivante, bras = s.bras, traitement = s.traitement, message = message)
                repository.envoyer(t)
            } catch (e: Exception) {
                _saisie.update { it.copy(erreur = e.message ?: "Enregistrement impossible") }
            }
        }
    }

    init {
        // Reprend position, bras et traitement de la derniere mesure
        viewModelScope.launch {
            _age.value = runCatching { patientRepository.getPatientById(patientId)?.age }.getOrNull()
            repository.derniere(patientId)?.let { d ->
                _saisie.update { it.copy(bras = d.bras, traitement = d.traitement) }
            }
        }
    }

    fun supprimer(t: TensionEntity) {
        viewModelScope.launch { runCatching { repository.supprimer(t) } }
    }
}
