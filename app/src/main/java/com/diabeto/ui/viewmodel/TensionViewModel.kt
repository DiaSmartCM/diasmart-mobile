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
    private val patientRepository: com.diabeto.data.repository.PatientRepository,
    private val objectifRepository: com.diabeto.data.repository.ObjectifTensionRepository,
    private val notificationApi: com.diabeto.data.api.NotificationApi,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val patientId: Long = savedStateHandle["patientId"] ?: 0L

    val tensions: StateFlow<List<TensionEntity>> = repository.getTensions(patientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Age du patient : objectif assoupli a partir de 65 ans. */
    private val _age = MutableStateFlow<Int?>(null)
    val age: StateFlow<Int?> = _age.asStateFlow()

    /** Objectif personnel fixe par le medecin (null = objectif general). */
    private val _objectif = MutableStateFlow<com.diabeto.domain.ObjectifTension?>(null)
    val objectif: StateFlow<com.diabeto.domain.ObjectifTension?> = _objectif.asStateFlow()

    /** Fin de la serie "regle des 3" programmee, ou null. */
    private val _regleDes3Fin = MutableStateFlow(com.diabeto.notifications.AlarmScheduler.finRegleDes3(appContext))
    val regleDes3Fin: StateFlow<LocalDateTime?> = _regleDes3Fin.asStateFlow()

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
                // Mesure tres elevee : le medecin et les soignants sont prevenus
                if (ReglesTension.alerteSoignant(sys, dia) && quand.isAfter(LocalDateTime.now().minusHours(24))) {
                    val prevenus = notificationApi.notifyTensionAlerte(sys, dia, t.dateHeure.toString())
                    if (prevenus > 0) _saisie.update {
                        it.copy(message = "Mesure enregistrée. Votre médecin a été prévenu. Reposez-vous 5 minutes et reprenez la mesure.")
                    }
                }
            } catch (e: Exception) {
                _saisie.update { it.copy(erreur = e.message ?: "Enregistrement impossible") }
            }
        }
    }

    init {
        // Reprend position, bras et traitement de la derniere mesure
        viewModelScope.launch {
            _age.value = runCatching { patientRepository.getPatientById(patientId)?.age }.getOrNull()
            _objectif.value = objectifRepository.lireMien()
            repository.derniere(patientId)?.let { d ->
                _saisie.update { it.copy(bras = d.bras, traitement = d.traitement) }
            }
        }
    }

    /** Programme les 6 rappels de la regle des 3 (3 jours, matin et soir). */
    fun programmerRegleDes3() {
        val fin = com.diabeto.notifications.AlarmScheduler.programmerRegleDes3(appContext)
        _regleDes3Fin.value = fin
        _saisie.update { it.copy(message = "Rappels programmés : 3 jours, à 7 h et à 19 h.") }
    }

    fun annulerRegleDes3() {
        com.diabeto.notifications.AlarmScheduler.annulerRegleDes3(appContext)
        _regleDes3Fin.value = null
        _saisie.update { it.copy(message = "Rappels de la règle des 3 annulés.") }
    }

    fun supprimer(t: TensionEntity) {
        viewModelScope.launch { runCatching { repository.supprimer(t) } }
    }
}
