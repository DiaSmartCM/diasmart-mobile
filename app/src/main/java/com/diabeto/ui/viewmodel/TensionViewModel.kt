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
    val message: String? = null,
    val erreur: String? = null
)

@HiltViewModel
class TensionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TensionRepository
) : ViewModel() {

    private val patientId: Long = savedStateHandle["patientId"] ?: 0L

    val tensions: StateFlow<List<TensionEntity>> = repository.getTensions(patientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _saisie = MutableStateFlow(SaisieTension())
    val saisie: StateFlow<SaisieTension> = _saisie.asStateFlow()

    private fun chiffres(s: String) = s.filter { it.isDigit() }.take(3)

    fun onSystolique(v: String) = _saisie.update { it.copy(systolique = chiffres(v), erreur = null) }
    fun onDiastolique(v: String) = _saisie.update { it.copy(diastolique = chiffres(v), erreur = null) }
    fun onPouls(v: String) = _saisie.update { it.copy(pouls = chiffres(v), erreur = null) }
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
        if (patientId <= 0) {
            _saisie.update { it.copy(erreur = "Dossier introuvable.") }
            return
        }
        viewModelScope.launch {
            try {
                val t = repository.ajouter(TensionEntity(
                    patientId = patientId, systolique = sys, diastolique = dia, pouls = pouls,
                    dateHeure = LocalDateTime.now().withNano(0)
                ))
                _saisie.value = SaisieTension(message = "Mesure enregistrée")
                repository.envoyer(t)
            } catch (e: Exception) {
                _saisie.update { it.copy(erreur = e.message ?: "Enregistrement impossible") }
            }
        }
    }

    fun supprimer(t: TensionEntity) {
        viewModelScope.launch { runCatching { repository.supprimer(t) } }
    }
}
