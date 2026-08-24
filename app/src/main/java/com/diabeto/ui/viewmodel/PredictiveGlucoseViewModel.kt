package com.diabeto.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.entity.LectureGlucoseEntity
import com.diabeto.data.repository.ChatbotRepository
import com.diabeto.data.repository.GlucoseRepository
import com.diabeto.data.repository.PatientRepository
import com.diabeto.data.repository.RepasRepository
import com.diabeto.domain.prediction.ConseilGlycemique
import com.diabeto.domain.prediction.GlucosePrediction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import com.diabeto.util.MessageErreur

/**
 * Point de données prédictif pour le graphique
 */
data class PredictivePoint(
    val hoursFromNow: Float,
    val value: Double,
    val isPrediction: Boolean = false,
    val confidence: Float = 1f // 1.0 = certain, 0.0 = très incertain
)

data class PredictiveUiState(
    val isLoading: Boolean = true,
    val historicalPoints: List<PredictivePoint> = emptyList(),
    val predictedPoints: List<PredictivePoint> = emptyList(),
    val currentValue: Double? = null,
    val predictedMin: Double? = null,
    val predictedMax: Double? = null,
    val trendDescription: String = "",
    val rollyAnalysis: String? = null,
    val isAnalyzing: Boolean = false,
    val riskLevel: RiskLevel = RiskLevel.NORMAL,
    val alerts: List<String> = emptyList(),
    val error: String? = null,

    // ── v2.1.79 : prediction d'excursion post-prandiale ──
    /** Pic attendu, arrondi au multiple de 5 mg/dL. Null si aucun repas actif. */
    val picPrevu: Int? = null,
    /** Bornes basse et haute de la fourchette annoncee. */
    val picBas: Int? = null,
    val picHaut: Int? = null,
    /** Montee attendue depuis la derniere mesure. */
    val monteePrevue: Int? = null,
    /** Heure du pic, deja formatee (ex. "18:40"). */
    val heurePic: String? = null,
    /** Repas a l'origine de l'excursion. */
    val repasDeclencheur: String? = null,
    /** Sur quoi repose la prediction, dit franchement a l'utilisateur. */
    val baseCalibration: String = "",
    /** Conseil rattache au niveau attendu. */
    val conseil: ConseilGlycemique.Conseil? = null,

    /** Bulletin heure par heure, facon prevision meteo. */
    val bulletin: List<HeurePrevue> = emptyList(),
)

/**
 * Une echeance du bulletin glycemique.
 *
 * L'analogie meteo n'est pas qu'une image : comme une prevision, chaque valeur
 * porte une heure, un niveau lisible d'un coup d'oeil, et une fiabilite qui
 * decroit a mesure qu'on s'eloigne.
 */
data class HeurePrevue(
    val heure: String,
    val valeur: Int,
    val niveau: ConseilGlycemique.Niveau,
    val fiabilite: Float,
)

enum class RiskLevel(val label: String, val color: Long) {
    LOW("Faible", 0xFF4CAF50),
    NORMAL("Normal", 0xFF2196F3),
    MODERATE("Modéré", 0xFFFF9800),
    HIGH("Élevé", 0xFFF44336),
    CRITICAL("Critique", 0xFF9C27B0)
}

@HiltViewModel
class PredictiveGlucoseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val glucoseRepository: GlucoseRepository,
    private val patientRepository: PatientRepository,
    private val chatbotRepository: ChatbotRepository,
    private val repasRepository: RepasRepository
) : ViewModel() {

    private val patientId: Long = savedStateHandle.get<Long>("patientId")?.takeIf { it > 0 }
        ?: -1L

    private val _uiState = MutableStateFlow(PredictiveUiState())
    val uiState: StateFlow<PredictiveUiState> = _uiState.asStateFlow()

    init {
        loadDataAndPredict()
    }

    private fun loadDataAndPredict() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val pid = if (patientId > 0) patientId else {
                    patientRepository.getAllPatientsList().firstOrNull()?.id ?: run {
                        _uiState.update { it.copy(isLoading = false, error = "Aucun patient") }
                        return@launch
                    }
                }

                // Charger les 48 dernières heures de lectures
                val lectures = glucoseRepository.getLast7DaysLectures(pid)
                    .sortedBy { it.dateHeure }

                if (lectures.isEmpty()) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = "Aucune donnée glycémique disponible"
                    ) }
                    return@launch
                }

                val now = LocalDateTime.now()

                // Points historiques (dernières 12h)
                val recent = lectures.filter {
                    ChronoUnit.HOURS.between(it.dateHeure, now) <= 12
                }
                val historicalPoints = recent.map { lecture ->
                    val hoursAgo = ChronoUnit.MINUTES.between(lecture.dateHeure, now) / 60f
                    PredictivePoint(
                        hoursFromNow = -hoursAgo,
                        value = lecture.valeur,
                        isPrediction = false
                    )
                }

                val currentValue = lectures.last().valeur

                // ── v2.1.79 : prediction pilotee par les repas ────────────
                // Les repas des 7 derniers jours servent a calibrer le
                // coefficient personnel ; ceux des 6 dernieres heures sont
                // encore en train d'agir et entrent dans la projection.
                val repasRecents = runCatching { repasRepository.getRepasDepuis(7) }
                    .getOrDefault(emptyList())

                val calibration = GlucosePrediction.calibrer(
                    repasRecents.mapNotNull { r ->
                        val avant = r.glycemieAvantRepas
                        val apres = r.glycemieApresRepas
                        if (avant != null && apres != null && r.chargeGlycemique > 1.0) {
                            GlucosePrediction.Observation(
                                chargeGlycemique = r.chargeGlycemique,
                                monteeObservee = apres - avant,
                                indexGlycemique = r.indexGlycemique,
                            )
                        } else null
                    }
                )

                val repasActifs = repasRecents.mapNotNull { r ->
                    val minutes = (System.currentTimeMillis() - r.timestamp.toDate().time) / 60_000.0
                    if (minutes in 0.0..360.0 && r.glucidesEstimes > 0) {
                        r to GlucosePrediction.Repas(
                            minutesAvantMaintenant = minutes,
                            glucides = r.glucidesEstimes,
                            indexGlycemique = r.indexGlycemique.coerceIn(1, 110),
                        )
                    } else null
                }

                val minutesDepuisMesure =
                    ChronoUnit.MINUTES.between(lectures.last().dateHeure, now)
                        .toDouble().coerceAtLeast(0.0)

                val excursion = GlucosePrediction.predire(
                    derniereValeur = currentValue,
                    minutesDepuisMesure = minutesDepuisMesure,
                    glycemieHabituelle = lectures.takeLast(20).map { it.valeur }.average(),
                    repas = repasActifs.map { it.second },
                    calibration = calibration,
                )

                val predictedPoints = excursion.courbe
                    .filter { it.minutes > 0 }
                    .map { p ->
                        PredictivePoint(
                            hoursFromNow = (p.minutes / 60.0).toFloat(),
                            value = p.valeur,
                            isPrediction = true,
                            confidence = (1f - (p.minutes / 480.0).toFloat()).coerceIn(0.2f, 1f),
                        )
                    }

                // Bulletin horaire : on echantillonne la courbe a +1h .. +6h.
                val fmtHeure = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                val bulletin = (1..6).map { h ->
                    val minutes = h * 60.0
                    val valeur = excursion.courbe
                        .minByOrNull { kotlin.math.abs(it.minutes - minutes) }
                        ?.valeur ?: currentValue
                    HeurePrevue(
                        heure = now.plusHours(h.toLong()).format(fmtHeure),
                        valeur = GlucosePrediction.arrondiAffichage(valeur),
                        niveau = ConseilGlycemique.niveauDe(valeur),
                        fiabilite = (1f - h / 8f).coerceIn(0.25f, 1f),
                    )
                }

                val aUnRepasActif = repasActifs.isNotEmpty()
                val heurePic = now.plusMinutes(excursion.minutesJusquAuPic.toLong())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

                // Analyser risques
                val (riskLevel, alerts) = analyzeRisks(currentValue, predictedPoints)

                // Description tendance
                val trend = glucoseRepository.analyzeTrend(lectures.reversed())
                val trendDesc = when (trend) {
                    GlucoseRepository.TrendResult.RISING -> "Tendance à la hausse"
                    GlucoseRepository.TrendResult.FALLING -> "Tendance à la baisse"
                    GlucoseRepository.TrendResult.STABLE -> "Glycémie stable"
                }

                val predictedValues = predictedPoints.map { it.value }
                _uiState.update { it.copy(
                    isLoading = false,
                    historicalPoints = historicalPoints,
                    predictedPoints = predictedPoints,
                    currentValue = currentValue,
                    predictedMin = predictedValues.minOrNull(),
                    predictedMax = predictedValues.maxOrNull(),
                    trendDescription = trendDesc,
                    riskLevel = riskLevel,
                    alerts = alerts,

                    picPrevu = if (aUnRepasActif) GlucosePrediction.arrondiAffichage(excursion.valeurPic) else null,
                    picBas = if (aUnRepasActif) GlucosePrediction.arrondiAffichage(excursion.picBas) else null,
                    picHaut = if (aUnRepasActif) GlucosePrediction.arrondiAffichage(excursion.picHaut) else null,
                    monteePrevue = if (aUnRepasActif) GlucosePrediction.arrondiAffichage(excursion.monteePic) else null,
                    heurePic = if (aUnRepasActif) heurePic else null,
                    repasDeclencheur = repasActifs.maxByOrNull { it.second.chargeGlycemique }
                        ?.first?.nomRepas,
                    baseCalibration = if (calibration.personnalise)
                        "Calibre sur ${calibration.nombreObservations} repas mesures"
                    else
                        "Estimation generique — saisis tes glycemies avant et apres repas " +
                            "pour l'ajuster a toi",
                    conseil = if (aUnRepasActif)
                        ConseilGlycemique.pourExcursionPrevue(excursion)
                    else
                        ConseilGlycemique.pour(currentValue),
                    bulletin = bulletin,
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = MessageErreur.lisible(e)) }
            }
        }
    }

    private fun analyzeRisks(
        currentValue: Double,
        predictions: List<PredictivePoint>
    ): Pair<RiskLevel, List<String>> {
        val alerts = mutableListOf<String>()

        // Vérifier la valeur actuelle
        when {
            currentValue < 54 -> alerts.add("URGENCE: Hypoglycémie sévère (${currentValue.toInt()} mg/dL)")
            currentValue < 70 -> alerts.add("Hypoglycémie détectée (${currentValue.toInt()} mg/dL)")
            currentValue > 300 -> alerts.add("Hyperglycémie sévère (${currentValue.toInt()} mg/dL)")
            currentValue > 180 -> alerts.add("Hyperglycémie (${currentValue.toInt()} mg/dL)")
        }

        // Vérifier les prédictions
        val predictedMin = predictions.minOfOrNull { it.value } ?: currentValue
        val predictedMax = predictions.maxOfOrNull { it.value } ?: currentValue

        if (predictedMin < 70) {
            alerts.add("Risque d'hypoglycémie dans les prochaines heures")
        }
        if (predictedMax > 250) {
            alerts.add("Risque d'hyperglycémie dans les prochaines heures")
        }

        val riskLevel = when {
            currentValue < 54 || currentValue > 300 -> RiskLevel.CRITICAL
            currentValue < 70 || currentValue > 250 -> RiskLevel.HIGH
            predictedMin < 70 || predictedMax > 250 -> RiskLevel.MODERATE
            currentValue in 70.0..180.0 && predictedMin >= 60 && predictedMax <= 200 -> RiskLevel.LOW
            else -> RiskLevel.NORMAL
        }

        return Pair(riskLevel, alerts)
    }

    fun requestRollyAnalysis() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isAnalyzing = true) }

                val pid = if (patientId > 0) patientId else {
                    patientRepository.getAllPatientsList().firstOrNull()?.id ?: return@launch
                }

                val patient = patientRepository.getPatientById(pid) ?: return@launch
                val lectures = glucoseRepository.getLast7DaysLectures(pid)
                val latestHbA1c = glucoseRepository.getLatestHbA1c(pid)

                val stats = glucoseRepository.getStatistics(pid, 7)
                val hba1cEstimee = if (stats.totalLectures >= 10) {
                    com.diabeto.data.entity.HbA1cEntity.estimerDepuisGlycemieMoyenne(stats.moyenne)
                } else null

                val analysis = chatbotRepository.analysePredictive7Jours(
                    patient, lectures, latestHbA1c, hba1cEstimee
                )

                _uiState.update { it.copy(
                    isAnalyzing = false,
                    rollyAnalysis = analysis
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isAnalyzing = false,
                    error = MessageErreur.lisible(e)
                ) }
            }
        }
    }

    fun dismissAnalysis() {
        _uiState.update { it.copy(rollyAnalysis = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
