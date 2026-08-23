package com.diabeto.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.entity.ContexteGlucose
import com.diabeto.data.entity.LectureGlucoseEntity
import com.diabeto.data.model.ChatbotMessage
import com.diabeto.data.model.ConsentStatus
import com.diabeto.data.model.RepasAnalyse
import com.diabeto.data.model.RepasDocument
import com.diabeto.data.repository.ChatHistoryRepository
import com.diabeto.data.repository.ChatbotRepository
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.DataSharingRepository
import com.diabeto.data.repository.GlucoseRepository
import com.diabeto.data.repository.MessagerieRepository
import com.diabeto.data.repository.PatientRepository
import com.diabeto.data.repository.RepasRepository
import com.diabeto.domain.prediction.GlucosePrediction
import com.diabeto.notifications.ExcursionScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * État UI pour l'écran d'analyse de repas
 */
data class RepasUiState(
    // Saisie
    val descriptionRepas: String = "",

    // Image Vision
    val capturedBitmap: Bitmap? = null,
    val isImageMode: Boolean = false,

    // Analyse
    val isAnalysing: Boolean = false,
    val analyseResult: RepasAnalyse? = null,

    // Champs éditables (l'utilisateur peut corriger avant de confirmer)
    val nomRepasEdite: String = "",
    val glucidesEdites: String = "",
    val indexGlycemiqueEdite: String = "",
    val caloriesEditees: String = "",
    val proteinesEditees: String = "",
    val lipidesEdites: String = "",
    val fibresEditees: String = "",
    val glycemieAvant: String = "",
    val glycemieApres: String = "",

    // Validation & sauvegarde
    val showConfirmationDialog: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,

    // Dialog intégration glycémie dans la courbe de prédiction
    val showIntegrerGlycemieDialog: Boolean = false,
    val glycemieSauvegardee: Double? = null,
    val glycemieAvantSauvegardee: Double? = null,
    val glycemieApresSauvegardee: Double? = null,

    // Historique
    val historique: List<RepasDocument> = emptyList(),
    val isLoadingHistorique: Boolean = false,
    val expandedRepasId: String? = null,

    // Erreur
    val error: String? = null
)

@HiltViewModel
class RepasViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val appContext: android.content.Context,
    private val chatbotRepository: ChatbotRepository,
    private val repasRepository: RepasRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val authRepository: AuthRepository,
    private val glucoseRepository: GlucoseRepository,
    private val patientRepository: PatientRepository,
    private val dataSharingRepository: DataSharingRepository,
    private val messagerieRepository: MessagerieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepasUiState())
    val uiState: StateFlow<RepasUiState> = _uiState.asStateFlow()

    private var historiqueJob: Job? = null

    init {
        chargerHistorique()
    }

    // ─── Saisie ──────────────────────────────────────────────────────────────

    fun onDescriptionChange(text: String) {
        _uiState.update { it.copy(descriptionRepas = text) }
    }

    fun onNomRepasChange(text: String) {
        _uiState.update { it.copy(nomRepasEdite = text) }
    }

    fun onGlucidesChange(text: String) {
        _uiState.update { it.copy(glucidesEdites = text) }
    }

    fun onIndexGlycemiqueChange(text: String) {
        _uiState.update { it.copy(indexGlycemiqueEdite = text) }
    }

    fun onCaloriesChange(text: String) {
        _uiState.update { it.copy(caloriesEditees = text) }
    }

    fun onProteinesChange(text: String) {
        _uiState.update { it.copy(proteinesEditees = text) }
    }

    fun onLipidesChange(text: String) {
        _uiState.update { it.copy(lipidesEdites = text) }
    }

    fun onFibresChange(text: String) {
        _uiState.update { it.copy(fibresEditees = text) }
    }

    fun onGlycemieAvantChange(text: String) {
        _uiState.update { it.copy(glycemieAvant = text) }
    }

    fun onGlycemieApresChange(text: String) {
        _uiState.update { it.copy(glycemieApres = text) }
    }

    // ─── Image Vision ────────────────────────────────────────────────────────

    fun setCapturedBitmap(bitmap: Bitmap) {
        _uiState.update { it.copy(capturedBitmap = bitmap, isImageMode = true) }
    }

    fun clearImage() {
        _uiState.update { it.copy(capturedBitmap = null, isImageMode = false) }
    }

    /**
     * Lance l'analyse d'image de repas via Gemini Vision.
     */
    fun analyserRepasImage() {
        val bitmap = _uiState.value.capturedBitmap
        if (bitmap == null) {
            _uiState.update { it.copy(error = "Aucune image selectionnee.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isAnalysing = true, error = null, analyseResult = null, savedSuccessfully = false)
            }

            try {
                // Si le patient a decrit son plat avant de photographier, on
                // transmet ce nom : il sait ce qu'il mange mieux que le modele.
                val jsonBrut = chatbotRepository.analyserRepasImage(
                    bitmap = bitmap,
                    nomIndique = _uiState.value.descriptionRepas.trim().ifBlank { null }
                )
                val parseResult = repasRepository.parseAnalyseJson(jsonBrut)

                parseResult.fold(
                    onSuccess = { analyse ->
                        _uiState.update {
                            it.copy(
                                isAnalysing = false,
                                analyseResult = analyse,
                                descriptionRepas = analyse.description,
                                nomRepasEdite = analyse.nomRepas,
                                glucidesEdites = analyse.glucidesEstimes.toString(),
                                indexGlycemiqueEdite = analyse.indexGlycemique.toString(),
                                caloriesEditees = analyse.caloriesEstimees.toString(),
                                proteinesEditees = analyse.proteinesEstimees.toString(),
                                lipidesEdites = analyse.lipidesEstimes.toString(),
                                fibresEditees = analyse.fibresEstimees.toString()
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(isAnalysing = false, error = "Erreur parsing: ${e.message}")
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalysing = false,
                        error = e.message ?: "Erreur inattendue lors de l'analyse."
                    )
                }
            }
        }
    }

    /**
     * Relance l'estimation nutritionnelle a partir du nom corrige par le
     * patient.
     *
     * Jusqu'ici, corriger le nom ne changeait que l'etiquette enregistree :
     * glucides, index glycemique et calories restaient ceux du plat mal
     * reconnu. Le patient croyait avoir corrige son repas alors que les
     * chiffres, eux, decrivaient toujours autre chose — et c'est sur ces
     * chiffres qu'il ajuste son traitement.
     */
    fun recalculerAvecNomCorrige() {
        val nom = _uiState.value.nomRepasEdite.trim()
        if (nom.isBlank()) {
            _uiState.update { it.copy(error = "Indiquez d'abord le nom du plat.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalysing = true, error = null, savedSuccessfully = false) }
            try {
                val jsonBrut = chatbotRepository.analyserRepasJson(nom)
                repasRepository.parseAnalyseJson(jsonBrut).fold(
                    onSuccess = { analyse ->
                        _uiState.update {
                            it.copy(
                                isAnalysing = false,
                                analyseResult = analyse,
                                descriptionRepas = analyse.description,
                                // Le nom du patient fait foi, on ne le reecrit pas.
                                nomRepasEdite = nom,
                                glucidesEdites = analyse.glucidesEstimes.toString(),
                                indexGlycemiqueEdite = analyse.indexGlycemique.toString(),
                                caloriesEditees = analyse.caloriesEstimees.toString(),
                                proteinesEditees = analyse.proteinesEstimees.toString(),
                                lipidesEdites = analyse.lipidesEstimes.toString(),
                                fibresEditees = analyse.fibresEstimees.toString()
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(isAnalysing = false, error = "Erreur parsing: ${e.message}")
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isAnalysing = false, error = e.message ?: "Erreur lors du recalcul.")
                }
            }
        }
    }

    // ─── Analyse par ROLLY ──────────────────────────────────────────────────

    fun analyserRepas() {
        val description = _uiState.value.descriptionRepas.trim()
        if (description.isBlank()) {
            _uiState.update { it.copy(error = "Veuillez decrire votre repas.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isAnalysing = true, error = null, analyseResult = null, savedSuccessfully = false)
            }

            try {
                val jsonBrut = chatbotRepository.analyserRepasJson(description)
                val parseResult = repasRepository.parseAnalyseJson(jsonBrut)

                parseResult.fold(
                    onSuccess = { analyse ->
                        _uiState.update {
                            it.copy(
                                isAnalysing = false,
                                analyseResult = analyse,
                                nomRepasEdite = analyse.nomRepas,
                                glucidesEdites = analyse.glucidesEstimes.toString(),
                                indexGlycemiqueEdite = analyse.indexGlycemique.toString(),
                                caloriesEditees = analyse.caloriesEstimees.toString(),
                                proteinesEditees = analyse.proteinesEstimees.toString(),
                                lipidesEdites = analyse.lipidesEstimes.toString(),
                                fibresEditees = analyse.fibresEstimees.toString()
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(isAnalysing = false, error = e.message)
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalysing = false,
                        error = e.message ?: "Erreur inattendue lors de l'analyse."
                    )
                }
            }
        }
    }

    // ─── Validation médicale ────────────────────────────────────────────────

    fun demanderConfirmation() {
        _uiState.update { it.copy(showConfirmationDialog = true) }
    }

    fun annulerConfirmation() {
        _uiState.update { it.copy(showConfirmationDialog = false) }
    }

    /**
     * Confirme et sauvegarde le repas dans Firestore.
     * Utilise les valeurs éditées par l'utilisateur.
     * Sauvegarde aussi dans l'historique ROLLY.
     */
    fun confirmerEtSauvegarder() {
        val state = _uiState.value
        val analyse = state.analyseResult ?: return

        // Appliquer TOUTES les corrections de l'utilisateur
        val analyseCorrigee = analyse.copy(
            nomRepas = state.nomRepasEdite.ifBlank { analyse.nomRepas },
            glucidesEstimes = state.glucidesEdites.toDoubleOrNull() ?: analyse.glucidesEstimes,
            indexGlycemique = state.indexGlycemiqueEdite.toIntOrNull() ?: analyse.indexGlycemique,
            caloriesEstimees = state.caloriesEditees.toIntOrNull() ?: analyse.caloriesEstimees,
            proteinesEstimees = state.proteinesEditees.toDoubleOrNull() ?: analyse.proteinesEstimees,
            lipidesEstimes = state.lipidesEdites.toDoubleOrNull() ?: analyse.lipidesEstimes,
            fibresEstimees = state.fibresEditees.toDoubleOrNull() ?: analyse.fibresEstimees
        )

        val glycemieAvant = state.glycemieAvant.toDoubleOrNull()
        val glycemieApres = state.glycemieApres.toDoubleOrNull()

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, showConfirmationDialog = false, error = null) }

            val result = repasRepository.saveToFirestore(
                analyse = analyseCorrigee,
                glycemieAvant = glycemieAvant,
                glycemieApres = glycemieApres
            )

            result.fold(
                onSuccess = {
                    // Sauvegarder dans l'historique ROLLY
                    sauvegarderDansHistoriqueRolly(analyseCorrigee, glycemieAvant, glycemieApres)

                    // v2.1.79 : programmer les alertes d'excursion. Non
                    // critique — un echec ici ne doit jamais faire perdre
                    // l'enregistrement du repas.
                    runCatching { programmerAlertesExcursion(analyseCorrigee, glycemieAvant) }
                        .onFailure { Log.w(TAG, "Alertes d'excursion non programmees", it) }

                    // Notifier le(s) médecin(s) traitant(s) (non-critique)
                    notifierMedecinRepas(analyseCorrigee.nomRepas, analyseCorrigee.scoreDiabete)

                    // Déterminer si on doit proposer l'intégration glycémie
                    val hasGlycemie = glycemieAvant != null || glycemieApres != null
                    val glycemieAffichee = glycemieApres ?: glycemieAvant

                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            savedSuccessfully = true,
                            showIntegrerGlycemieDialog = hasGlycemie,
                            glycemieSauvegardee = glycemieAffichee,
                            glycemieAvantSauvegardee = glycemieAvant,
                            glycemieApresSauvegardee = glycemieApres,
                            // Reset du formulaire
                            descriptionRepas = "",
                            analyseResult = null,
                            capturedBitmap = null,
                            isImageMode = false,
                            nomRepasEdite = "",
                            glucidesEdites = "",
                            indexGlycemiqueEdite = "",
                            caloriesEditees = "",
                            proteinesEditees = "",
                            lipidesEdites = "",
                            fibresEditees = "",
                            glycemieAvant = "",
                            glycemieApres = ""
                        )
                    }
                    chargerHistorique()
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isSaving = false, error = e.message)
                    }
                }
            )
        }
    }

    /**
     * Sauvegarde l'analyse dans l'historique de discussion ROLLY
     * pour pouvoir la consulter plus tard dans le chatbot.
     */
    /**
     * Programme les alertes qui suivent le repas.
     *
     * Le point de depart est la glycemie saisie « avant repas » si elle existe,
     * sinon la derniere mesure connue. Sans aucun des deux, on ne programme
     * rien : annoncer un pic a partir d'une base inventee serait pire que se
     * taire.
     *
     * Le coefficient personnel est recalcule ici a partir des repas deja
     * mesures, pour que la notification annonce la meme valeur que l'ecran.
     */
    private suspend fun programmerAlertesExcursion(
        analyse: RepasAnalyse,
        glycemieAvant: Double?,
    ) {
        val depart = glycemieAvant
            ?: patientRepository.getAllPatientsList().firstOrNull()?.id?.let { pid ->
                glucoseRepository.getLast7DaysLectures(pid)
                    .maxByOrNull { it.dateHeure }?.valeur
            }
            ?: return

        val calibration = GlucosePrediction.calibrer(
            repasRepository.getRepasDepuis(7).mapNotNull { r ->
                val a = r.glycemieAvantRepas
                val b = r.glycemieApresRepas
                if (a != null && b != null && r.chargeGlycemique > 1.0) {
                    GlucosePrediction.Observation(
                        chargeGlycemique = r.chargeGlycemique,
                        monteeObservee = b - a,
                        indexGlycemique = r.indexGlycemique,
                    )
                } else null
            }
        )

        ExcursionScheduler.programmerApresRepas(
            context = appContext,
            nomRepas = analyse.nomRepas.ifBlank { "Repas" },
            glucides = analyse.glucidesEstimes,
            indexGlycemique = analyse.indexGlycemique,
            glycemieDepart = depart,
            calibration = calibration,
        )
    }

    private suspend fun sauvegarderDansHistoriqueRolly(
        analyse: RepasAnalyse,
        glycemieAvant: Double?,
        glycemieApres: Double?
    ) {
        try {
            // Message utilisateur (résumé de la demande)
            val userMsg = ChatbotMessage(
                contenu = "Analyse de repas : ${analyse.nomRepas}",
                estUtilisateur = true,
                timestamp = System.currentTimeMillis()
            )

            // Message ROLLY (résultat complet)
            val sb = StringBuilder()
            sb.appendLine("**Analyse nutritionnelle** : ${analyse.nomRepas}")
            sb.appendLine()
            sb.appendLine("| Nutriment | Valeur |")
            sb.appendLine("|---|---|")
            sb.appendLine("| Glucides | ${analyse.glucidesEstimes.toInt()}g |")
            sb.appendLine("| Proteines | ${analyse.proteinesEstimees.toInt()}g |")
            sb.appendLine("| Lipides | ${analyse.lipidesEstimes.toInt()}g |")
            sb.appendLine("| Fibres | ${analyse.fibresEstimees.toInt()}g |")
            sb.appendLine("| Calories | ${analyse.caloriesEstimees} kcal |")
            sb.appendLine("| Index Glycemique | ${analyse.indexGlycemique} (${analyse.categorieIG}) |")
            sb.appendLine("| Score Diabete | ${analyse.scoreDiabete}/100 |")
            sb.appendLine()
            if (glycemieAvant != null) sb.appendLine("Glycemie avant repas : ${glycemieAvant.toInt()} mg/dL")
            if (glycemieApres != null) sb.appendLine("Glycemie apres repas : ${glycemieApres.toInt()} mg/dL")
            if (analyse.impactGlycemique.isNotBlank()) {
                sb.appendLine()
                sb.appendLine("**Impact glycemique** : ${analyse.impactGlycemique}")
            }
            if (analyse.recommandations.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("**Recommandations** :")
                analyse.recommandations.forEach { sb.appendLine("- $it") }
            }

            val rollyMsg = ChatbotMessage(
                contenu = sb.toString(),
                estUtilisateur = false,
                timestamp = System.currentTimeMillis() + 1
            )

            chatHistoryRepository.saveExchange(userMsg, rollyMsg)
        } catch (e: Exception) {
            // Pas critique si ça échoue — log seulement
            android.util.Log.e("RepasVM", "Erreur sauvegarde historique ROLLY", e)
        }
    }

    /**
     * Envoie un message dans la messagerie de chaque médecin traitant actif
     * pour l'informer qu'un nouveau repas a ete analyse.
     * Non-critique : les erreurs sont absorbees silencieusement.
     */
    private suspend fun notifierMedecinRepas(nomRepas: String, score: Int) {
        try {
            val consents = dataSharingRepository.getMyConsents()
            val medecinActifs = consents.filter {
                it.isActive && it.status == ConsentStatus.ACCEPTED
            }
            if (medecinActifs.isEmpty()) return

            val currentProfile = authRepository.getCurrentUserProfile() ?: return
            medecinActifs.forEach { consent ->
                val medecinProfile = authRepository.getUserProfile(consent.medecinUid) ?: return@forEach
                val convId = messagerieRepository
                    .findOrCreateConversationWith(currentProfile, medecinProfile)
                    .getOrNull() ?: return@forEach
                messagerieRepository.envoyerMessage(
                    conversationId = convId,
                    contenu = "Nouveau repas analyse par ROLLY : \"$nomRepas\" — Score diabete : $score/100"
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("RepasVM", "notifierMedecinRepas echoue (non-critique): ${e.message}")
        }
    }

    // ─── Intégration glycémie dans la courbe de prédiction ──────────────────

    /**
     * Intègre la glycémie du repas dans la base locale Room
     * pour la courbe de prédiction hypo/hyperglycémie.
     * PredictiveGlucoseViewModel lit depuis Room (getLast7DaysLectures),
     * donc on doit écrire ici pour que la donnée soit visible sur la courbe.
     */
    fun integrerGlycemiePrediction() {
        val state = _uiState.value
        val glycemieAvant = state.glycemieAvantSauvegardee
        val glycemieApres = state.glycemieApresSauvegardee
        if (glycemieAvant == null && glycemieApres == null) return

        viewModelScope.launch {
            try {
                // Récupérer le patientId depuis Room
                val patient = patientRepository.getAllPatientsList().firstOrNull()
                if (patient == null) {
                    _uiState.update {
                        it.copy(
                            showIntegrerGlycemieDialog = false,
                            error = "Aucun patient trouvé. Ajoutez d'abord un profil patient."
                        )
                    }
                    return@launch
                }

                val now = LocalDateTime.now()

                // Insérer la glycémie AVANT repas (si renseignée)
                if (glycemieAvant != null) {
                    val lectureAvant = LectureGlucoseEntity(
                        patientId = patient.id,
                        valeur = glycemieAvant,
                        dateHeure = now.minusMinutes(30), // 30 min avant maintenant (approximation)
                        contexte = ContexteGlucose.AVANT_REPAS,
                        notes = "Avant repas - analyse ROLLY"
                    )
                    glucoseRepository.insertLecture(lectureAvant)
                }

                // Insérer la glycémie APRÈS repas (si renseignée)
                if (glycemieApres != null) {
                    val lectureApres = LectureGlucoseEntity(
                        patientId = patient.id,
                        valeur = glycemieApres,
                        dateHeure = now, // maintenant
                        contexte = ContexteGlucose.APRES_REPAS_2H,
                        notes = "Après repas - analyse ROLLY"
                    )
                    glucoseRepository.insertLecture(lectureApres)
                }

                val nbValeurs = listOfNotNull(glycemieAvant, glycemieApres).size
                _uiState.update {
                    it.copy(
                        showIntegrerGlycemieDialog = false,
                        glycemieSauvegardee = null,
                        glycemieAvantSauvegardee = null,
                        glycemieApresSauvegardee = null
                    )
                }
                android.util.Log.i("RepasVM", "$nbValeurs valeur(s) glycemie integree(s) dans Room (patient=${patient.id})")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showIntegrerGlycemieDialog = false,
                        error = "Erreur integration glycemie: ${e.message}"
                    )
                }
            }
        }
    }

    fun refuserIntegrationGlycemie() {
        _uiState.update {
            it.copy(
                showIntegrerGlycemieDialog = false,
                glycemieSauvegardee = null,
                glycemieAvantSauvegardee = null,
                glycemieApresSauvegardee = null
            )
        }
    }

    // ─── Historique ─────────────────────────────────────────────────────────

    private fun chargerHistorique() {
        // Annuler le collecteur précédent pour éviter les doublons
        historiqueJob?.cancel()
        historiqueJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistorique = true) }
            repasRepository.getRepasHistoriqueFlow()
                .catch { e ->
                    android.util.Log.e("RepasVM", "Erreur chargement historique", e)
                    _uiState.update { it.copy(isLoadingHistorique = false, error = e.message) }
                }
                .collect { repas ->
                    _uiState.update { it.copy(historique = repas, isLoadingHistorique = false) }
                }
        }
    }

    fun toggleExpandRepas(repasId: String) {
        _uiState.update {
            it.copy(expandedRepasId = if (it.expandedRepasId == repasId) null else repasId)
        }
    }

    fun supprimerRepas(repasId: String) {
        viewModelScope.launch {
            repasRepository.supprimerRepas(repasId)
        }
    }

    // ─── Utilitaires ────────────────────────────────────────────────────────

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun clearSuccess() = _uiState.update { it.copy(savedSuccessfully = false) }

    fun reinitialiser() {
        _uiState.update {
            RepasUiState(historique = it.historique)
        }
    }

    private companion object {
        const val TAG = "RepasViewModel"
    }
}
