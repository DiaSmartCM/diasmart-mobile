package com.diabeto.ui.viewmodel

import android.util.Log
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.repository.GlucoseRepository
import com.diabeto.service.StepCounterService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PedometerUiState(
    val steps: Int = 0,
    val dailyGoal: Int = 8000,
    val caloriesBurned: Double = 0.0,
    val distanceKm: Double = 0.0,
    val isTracking: Boolean = false,
    val sensorAvailable: Boolean = true,
    val avgGlycemie: Double? = null,
    val glycemieImpact: String? = null,
    // v2.1.72 : total du jour (toutes sessions cumulees) + historique
    // journalier consultable, conserve 90 jours en local (donc hors ligne).
    val dailyTotal: Int = 0,
    val history: List<Pair<String, Int>> = emptyList()
)

@HiltViewModel
class PedometerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val glucoseRepository: GlucoseRepository
) : ViewModel(), SensorEventListener {

    private val patientId: Long = savedStateHandle.get<Long>("patientId")?.takeIf { it > 0 } ?: 0L

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val _uiState = MutableStateFlow(PedometerUiState(
        sensorAvailable = stepSensor != null
    ))
    val uiState: StateFlow<PedometerUiState> = _uiState.asStateFlow()

    private var initialSteps: Int? = null
    private var pollingJob: kotlinx.coroutines.Job? = null

    init {
        loadGlucoseCorrelation()
        refreshHistory()
        // Restore state from service if it's already running
        if (StepCounterService.isTracking(context)) {
            val steps = StepCounterService.getSessionSteps(context)
            _uiState.update {
                it.copy(
                    isTracking = true,
                    steps = steps,
                    distanceKm = steps * 0.00075,
                    caloriesBurned = steps * 0.04
                )
            }
            startPollingServiceSteps()
        }
    }

    private fun loadGlucoseCorrelation() {
        if (patientId <= 0) return
        viewModelScope.launch {
            try {
                val avg = glucoseRepository.getLast24HoursAverage(patientId)
                if (avg > 0) {
                    val impact = when {
                        avg < 70 -> "Glycemie basse. L'exercice peut aggraver l'hypoglycemie. Prenez une collation avant de marcher."
                        avg in 70.0..130.0 -> "Glycemie optimale pour l'exercice. La marche aidera a maintenir ce niveau."
                        avg in 130.0..180.0 -> "Glycemie legerement elevee. La marche aidera a la faire baisser."
                        avg in 180.0..250.0 -> "Glycemie elevee. L'exercice modere est recommande pour aider a la regulation."
                        else -> "Glycemie tres elevee. Consultez votre medecin avant toute activite intense."
                    }
                    _uiState.update { it.copy(avgGlycemie = avg, glycemieImpact = impact) }
                }
            } catch (e: Exception) {
                Log.w("PedometerVM", "Failed to load glycemie impact", e)
            }
        }
    }

    fun startTracking() {
        if (stepSensor == null) return

        // v2.1.72 : on n'ecoute PLUS le capteur ici. Le service est la seule
        // source de verite. Avant, ce ViewModel comptait en parallele avec sa
        // propre reference (repartant de 0) pendant que le sondage lisait celle
        // du service : les deux valeurs s'ecrasaient mutuellement et l'affichage
        // restait bloque sur la plus grande.
        _uiState.update { it.copy(isTracking = true, steps = 0, distanceKm = 0.0, caloriesBurned = 0.0) }

        // Start foreground service for background tracking
        val intent = Intent(context, StepCounterService::class.java).apply {
            action = StepCounterService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        startPollingServiceSteps()
    }

    fun stopTracking() {
        sensorManager.unregisterListener(this)
        _uiState.update { it.copy(isTracking = false) }

        // Stop foreground service
        val intent = Intent(context, StepCounterService::class.java).apply {
            action = StepCounterService.ACTION_STOP
        }
        context.startService(intent)

        pollingJob?.cancel()
        pollingJob = null

        // v2.1.72 : le service vient de clore la session dans les prefs ;
        // on recharge pour que le total du jour et l'historique soient a jour.
        viewModelScope.launch {
            delay(300)
            refreshHistory()
        }
    }

    /**
     * Poll the service for step updates (for when app is in foreground
     * and service is running in background)
     */
    private fun startPollingServiceSteps() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                if (!StepCounterService.isTracking(context) && !_uiState.value.isTracking) break
                // v2.1.72 : on reflete la valeur du service telle quelle. Le
                // garde `steps > valeur actuelle` rendait le compteur monotone
                // et collant : il empechait tout retour a 0 en debut de session.
                val steps = StepCounterService.getSessionSteps(context)
                if (steps != _uiState.value.steps) {
                    _uiState.update {
                        it.copy(
                            steps = steps,
                            distanceKm = steps * 0.00075,
                            caloriesBurned = steps * 0.04,
                            dailyTotal = StepCounterService.getDailySteps(context)
                        )
                    }
                }
                delay(1000)
            }
        }
    }

    /**
     * v2.1.72 : recharge le total du jour et l'historique depuis le stockage
     * local. Aucun reseau : consultable hors ligne.
     */
    fun refreshHistory() {
        _uiState.update {
            it.copy(
                dailyTotal = StepCounterService.getDailySteps(context),
                history = StepCounterService.getDailyHistory(context)
            )
        }
    }

    // v2.1.72 : le comptage est entierement delegue a StepCounterService (il
    // continue meme app fermee). Ce callback reste vide pour ne pas creer un
    // second compteur concurrent.
    override fun onSensorChanged(event: SensorEvent?) {}

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this)
        pollingJob?.cancel()
        // Note: the service continues running in background
    }
}
