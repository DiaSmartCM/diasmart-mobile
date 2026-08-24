package com.diabeto.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.model.ChatbotMessage
import com.diabeto.data.model.UserProfile
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.ChatHistoryRepository
import com.diabeto.data.repository.ChatSession
import com.diabeto.data.repository.ChatbotRepository
import com.diabeto.data.repository.GlucoseRepository
import com.diabeto.data.repository.PatientRepository
import com.diabeto.data.repository.QuotaRepository
import com.diabeto.data.repository.QuotaStatus
import com.diabeto.data.repository.ValidationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.diabeto.util.MessageErreur

private const val TAG = "ChatbotViewModel"

data class ChatbotUiState(
    val messages: List<ChatbotMessage> = listOf(
        ChatbotMessage(
            contenu = "Bonjour. Je suis **ROLLY**, assistant clinique IA de DiaSmart.\n\nMes compétences :\n• 📊 Analyse glycémique et HbA1c\n• 🔮 Prédiction des risques hypo/hyperglycémie\n• 🥗 Détection alimentaire et conseils nutritionnels\n• ⚠️ Alertes et actions préventives\n\nComment puis-je vous aider ?",
            estUtilisateur = false
        )
    ),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isLoadingHistory: Boolean = true,
    val error: String? = null,
    val analyseRapide: String? = null,
    val showAnalyse: Boolean = false,
    val quotaStatus: QuotaStatus = QuotaStatus(),
    val messageCount: Long = 0,
    // Validation flow
    val showValidationDialog: Boolean = false,
    val availableDoctors: List<UserProfile> = emptyList(),
    val lastQuestion: String = "",
    val lastResponse: String = "",
    val validationSent: Boolean = false,
    val validationDoctorName: String = "",
    // Sessions
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String? = null,
    val currentSessionTitle: String = "Nouvelle discussion",
    val showSessionDrawer: Boolean = false
)

@HiltViewModel
class ChatbotViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatbotRepository: ChatbotRepository,
    private val patientRepository: PatientRepository,
    private val glucoseRepository: GlucoseRepository,
    private val authRepository: AuthRepository,
    private val quotaRepository: QuotaRepository,
    private val validationRepository: ValidationRepository,
    private val chatHistoryRepository: ChatHistoryRepository
) : ViewModel() {

    private val patientId: Long? = savedStateHandle.get<Long>("patientId")?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(ChatbotUiState())
    val uiState: StateFlow<ChatbotUiState> = _uiState.asStateFlow()

    private var cachedHistoryContext: String = ""

    private val medicalKeywords = Regex(
        "glyc|diab|insul|medic|traitement|dose|symptom|tension|HbA1c|glucos|hypoglyc|hyperglyc|regime|alimenta|repas|calorie|glucide|poids|IMC",
        RegexOption.IGNORE_CASE
    )

    init {
        loadQuota()
        loadDoctors()
        loadChatHistory()
    }

    // ── Sessions ──────────────────────────────────────────────────────
    fun toggleSessionDrawer() {
        _uiState.update { it.copy(showSessionDrawer = !it.showSessionDrawer) }
        if (_uiState.value.showSessionDrawer) {
            loadSessions()
        }
    }

    fun closeSessionDrawer() {
        _uiState.update { it.copy(showSessionDrawer = false) }
    }

    private fun loadSessions() {
        viewModelScope.launch {
            try {
                val sessions = chatHistoryRepository.getAllSessions()
                _uiState.update {
                    it.copy(
                        sessions = sessions,
                        currentSessionId = chatHistoryRepository.currentSessionId
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur chargement sessions", e)
            }
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            try {
                val sessionId = chatHistoryRepository.createSession()
                cachedHistoryContext = ""
                chatbotRepository.reinitialiserChat()
                _uiState.update {
                    ChatbotUiState(
                        isLoadingHistory = false,
                        quotaStatus = it.quotaStatus,
                        availableDoctors = it.availableDoctors,
                        currentSessionId = sessionId,
                        currentSessionTitle = "Nouvelle discussion",
                        showSessionDrawer = false
                    )
                }
                loadSessions()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur création session", e)
            }
        }
    }

    fun switchToSession(sessionId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoadingHistory = true, showSessionDrawer = false) }
                chatHistoryRepository.setCurrentSession(sessionId)
                val messages = chatHistoryRepository.getSessionMessages(sessionId)
                val sessions = chatHistoryRepository.getAllSessions()
                val session = sessions.find { it.id == sessionId }

                cachedHistoryContext = chatHistoryRepository.buildHistoryContext()

                val welcomeMsg = ChatbotMessage(
                    contenu = "Bonjour. Je suis **ROLLY**, assistant clinique IA de DiaSmart.\n\nComment puis-je vous aider ?",
                    estUtilisateur = false
                )

                _uiState.update {
                    it.copy(
                        messages = if (messages.isEmpty()) listOf(welcomeMsg) else messages,
                        isLoadingHistory = false,
                        currentSessionId = sessionId,
                        currentSessionTitle = session?.title ?: "Discussion",
                        sessions = sessions,
                        messageCount = messages.size.toLong()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur switch session", e)
                _uiState.update { it.copy(isLoadingHistory = false) }
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                chatHistoryRepository.deleteSession(sessionId)
                if (chatHistoryRepository.currentSessionId == null || sessionId == _uiState.value.currentSessionId) {
                    // Create a new session if we deleted the current one
                    createNewSession()
                } else {
                    loadSessions()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur suppression session", e)
            }
        }
    }

    // ── Chargement de l'historique Firestore ──────────────────────────
    private fun loadChatHistory() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoadingHistory = true) }

                // Check for sessions
                val sessions = chatHistoryRepository.getAllSessions()
                if (sessions.isNotEmpty()) {
                    // Load the most recent session
                    val latest = sessions.first()
                    chatHistoryRepository.setCurrentSession(latest.id)
                    val messages = chatHistoryRepository.getSessionMessages(latest.id)

                    if (messages.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                messages = messages,
                                isLoadingHistory = false,
                                messageCount = messages.size.toLong(),
                                sessions = sessions,
                                currentSessionId = latest.id,
                                currentSessionTitle = latest.title
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoadingHistory = false,
                                sessions = sessions,
                                currentSessionId = latest.id,
                                currentSessionTitle = latest.title
                            )
                        }
                    }
                } else {
                    // Try migrating old flat messages
                    val migrated = chatHistoryRepository.migrateOldMessages()
                    if (migrated) {
                        val messages = chatHistoryRepository.getCurrentSessionMessages()
                        val newSessions = chatHistoryRepository.getAllSessions()
                        _uiState.update {
                            it.copy(
                                messages = if (messages.isNotEmpty()) messages else it.messages,
                                isLoadingHistory = false,
                                messageCount = messages.size.toLong(),
                                sessions = newSessions,
                                currentSessionId = chatHistoryRepository.currentSessionId,
                                currentSessionTitle = "Discussion précédente"
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoadingHistory = false, messageCount = 0) }
                    }
                }

                cachedHistoryContext = chatHistoryRepository.buildHistoryContext()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur chargement historique", e)
                _uiState.update { it.copy(isLoadingHistory = false) }
            }
        }
    }

    private fun loadDoctors() {
        viewModelScope.launch {
            try {
                val doctors = validationRepository.getAvailableDoctors()
                _uiState.update { it.copy(availableDoctors = doctors) }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur chargement médecins", e)
            }
        }
    }

    private fun loadQuota() {
        viewModelScope.launch {
            val status = quotaRepository.getQuotaStatus()
            _uiState.update { it.copy(quotaStatus = status) }
        }
    }

    fun onInputChange(text: String) = _uiState.update { it.copy(inputText = text) }

    fun envoyerMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return

        if (_uiState.value.quotaStatus.isExceeded) {
            val msgQuota = ChatbotMessage(
                contenu = "⚠️ Vous avez atteint votre limite de ${_uiState.value.quotaStatus.limit} requêtes pour aujourd'hui. Revenez demain !",
                estUtilisateur = false
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatbotMessage(contenu = text, estUtilisateur = true) + msgQuota,
                    inputText = ""
                )
            }
            return
        }

        val msgUtilisateur = ChatbotMessage(contenu = text, estUtilisateur = true)
        val msgChargement = ChatbotMessage(contenu = "", estUtilisateur = false, enChargement = true)

        _uiState.update {
            it.copy(
                messages = it.messages + msgUtilisateur + msgChargement,
                inputText = "",
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                val quotaOk = quotaRepository.consumeQuota()
                if (!quotaOk) {
                    val newStatus = quotaRepository.getQuotaStatus()
                    val msgQuota = ChatbotMessage(
                        contenu = "⚠️ Vous avez atteint votre limite de ${newStatus.limit} requêtes pour aujourd'hui. Revenez demain !",
                        estUtilisateur = false
                    )
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.dropLast(1) + msgQuota,
                            isLoading = false,
                            quotaStatus = newStatus
                        )
                    }
                    return@launch
                }

                Log.d(TAG, "envoyerMessage: patientId=$patientId, text=${text.take(50)}")
                val patient = patientId?.let { patientRepository.getPatientById(it) }
                val lectures = patientId?.let {
                    glucoseRepository.getLast7DaysLectures(it)
                } ?: emptyList()
                val hba1cList = patientId?.let {
                    glucoseRepository.getHbA1cByPatientList(it)
                } ?: emptyList()
                val latestHbA1c = hba1cList.firstOrNull()
                val stats = patientId?.let { glucoseRepository.getStatistics(it, 30) }
                val hba1cEstimee = if (stats != null && stats.totalLectures >= 10 && stats.moyenne > 0) {
                    val est = com.diabeto.data.entity.HbA1cEntity.estimerDepuisGlycemieMoyenne(stats.moyenne)
                    (est * 10).toInt() / 10.0
                } else null

                var reponse = ""
                chatbotRepository.envoyerMessageAvecContexte(
                    message = text,
                    patient = patient,
                    lecturesRecentes = lectures,
                    latestHbA1c = latestHbA1c,
                    hba1cEstimee = hba1cEstimee,
                    historiqueChat = cachedHistoryContext
                ).collect { chunk ->
                    reponse = chunk
                    // Update UI progressively — replace loading message with accumulated text
                    _uiState.update { state ->
                        val streamingMsg = ChatbotMessage(contenu = reponse, estUtilisateur = false)
                        state.copy(messages = state.messages.dropLast(1) + streamingMsg)
                    }
                }

                val msgIA = ChatbotMessage(contenu = reponse, estUtilisateur = false)
                chatHistoryRepository.saveExchange(msgUtilisateur, msgIA)
                cachedHistoryContext = chatHistoryRepository.buildHistoryContext()

                val newStatus = quotaRepository.getQuotaStatus()
                val count = chatHistoryRepository.getMessageCount()
                val isMedical = medicalKeywords.containsMatchIn(text + " " + reponse)
                val hasDoctors = _uiState.value.availableDoctors.isNotEmpty()

                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.dropLast(1) + msgIA,
                        isLoading = false,
                        quotaStatus = newStatus,
                        messageCount = count,
                        lastQuestion = text,
                        lastResponse = reponse,
                        showValidationDialog = isMedical && hasDoctors,
                        validationSent = false,
                        currentSessionId = chatHistoryRepository.currentSessionId
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur envoyerMessage", e)
                val msgErreur = ChatbotMessage(
                    contenu = MessageErreur.lisible(e),
                    estUtilisateur = false
                )
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.dropLast(1) + msgErreur,
                        isLoading = false,
                        error = MessageErreur.lisible(e)
                    )
                }
            }
        }
    }

    private suspend fun checkAndConsumeQuota(): Boolean {
        val quotaOk = quotaRepository.consumeQuota()
        if (!quotaOk) {
            val newStatus = quotaRepository.getQuotaStatus()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    quotaStatus = newStatus,
                    error = "Limite de ${newStatus.limit} requêtes/jour atteinte. Revenez demain !"
                )
            }
        }
        return quotaOk
    }

    private suspend fun refreshQuota() {
        val newStatus = quotaRepository.getQuotaStatus()
        _uiState.update { it.copy(quotaStatus = newStatus) }
    }

    fun analyserGlycemie() {
        val pid = patientId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showAnalyse = false) }
            if (!checkAndConsumeQuota()) return@launch
            try {
                val patient = patientRepository.getPatientById(pid) ?: return@launch
                val lectures = glucoseRepository.getLast7DaysLectures(pid)
                val hba1cList = glucoseRepository.getHbA1cByPatientList(pid)
                val latestHbA1c = hba1cList.firstOrNull { !it.estEstimation }
                val stats = glucoseRepository.getStatistics(pid, 30)
                val hba1cEstimee = if (stats.totalLectures >= 10 && stats.moyenne > 0) {
                    val est = com.diabeto.data.entity.HbA1cEntity.estimerDepuisGlycemieMoyenne(stats.moyenne)
                    (est * 10).toInt() / 10.0
                } else null

                var analyse = ""
                chatbotRepository.analyserGlycemieStream(patient, lectures, latestHbA1c, hba1cEstimee)
                    .collect { chunk ->
                        analyse = chunk
                        _uiState.update { it.copy(analyseRapide = analyse, showAnalyse = true) }
                    }
                refreshQuota()

                val userMsg = ChatbotMessage(contenu = "[Analyse glycémique demandée]", estUtilisateur = true)
                val aiMsg = ChatbotMessage(contenu = analyse, estUtilisateur = false)
                chatHistoryRepository.saveExchange(userMsg, aiMsg)
                cachedHistoryContext = chatHistoryRepository.buildHistoryContext()

                _uiState.update { it.copy(isLoading = false, analyseRapide = analyse, showAnalyse = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = MessageErreur.lisible(e)) }
            }
        }
    }

    fun previsionRisque() {
        val pid = patientId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showAnalyse = false) }
            if (!checkAndConsumeQuota()) return@launch
            try {
                val patient = patientRepository.getPatientById(pid) ?: return@launch
                val lectures = glucoseRepository.getLast7DaysLectures(pid)
                val latestHbA1c = glucoseRepository.getHbA1cByPatientList(pid).firstOrNull { !it.estEstimation }

                var prevision = ""
                chatbotRepository.previsionRisqueStream(patient, lectures, latestHbA1c)
                    .collect { chunk ->
                        prevision = chunk
                        _uiState.update { it.copy(analyseRapide = prevision, showAnalyse = true) }
                    }
                refreshQuota()

                val userMsg = ChatbotMessage(contenu = "[Prévision de risque demandée]", estUtilisateur = true)
                val aiMsg = ChatbotMessage(contenu = prevision, estUtilisateur = false)
                chatHistoryRepository.saveExchange(userMsg, aiMsg)
                cachedHistoryContext = chatHistoryRepository.buildHistoryContext()

                _uiState.update { it.copy(isLoading = false, analyseRapide = prevision, showAnalyse = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = MessageErreur.lisible(e)) }
            }
        }
    }

    fun conseilsNutritionnels() {
        val pid = patientId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showAnalyse = false) }
            if (!checkAndConsumeQuota()) return@launch
            try {
                val patient = patientRepository.getPatientById(pid) ?: return@launch
                val dernier = glucoseRepository.getLatestReading(pid)
                val latestHbA1c = glucoseRepository.getHbA1cByPatientList(pid).firstOrNull { !it.estEstimation }

                var conseils = ""
                chatbotRepository.conseilsNutritionnelsStream(patient, dernier, latestHbA1c)
                    .collect { chunk ->
                        conseils = chunk
                        _uiState.update { it.copy(analyseRapide = conseils, showAnalyse = true) }
                    }
                refreshQuota()

                val userMsg = ChatbotMessage(contenu = "[Conseils nutritionnels demandés]", estUtilisateur = true)
                val aiMsg = ChatbotMessage(contenu = conseils, estUtilisateur = false)
                chatHistoryRepository.saveExchange(userMsg, aiMsg)
                cachedHistoryContext = chatHistoryRepository.buildHistoryContext()

                _uiState.update { it.copy(isLoading = false, analyseRapide = conseils, showAnalyse = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = MessageErreur.lisible(e)) }
            }
        }
    }

    fun dismissAnalyse() = _uiState.update { it.copy(showAnalyse = false, analyseRapide = null) }

    fun reinitialiserChat() {
        createNewSession()
    }

    fun effacerHistorique() {
        viewModelScope.launch {
            chatHistoryRepository.clearHistory()
            chatbotRepository.reinitialiserChat()
            cachedHistoryContext = ""
            _uiState.update {
                ChatbotUiState(
                    isLoadingHistory = false,
                    messageCount = 0,
                    quotaStatus = it.quotaStatus,
                    availableDoctors = it.availableDoctors
                )
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun dismissValidation() = _uiState.update { it.copy(showValidationDialog = false) }

    fun submitForValidation(medecin: UserProfile) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                validationRepository.createValidation(
                    question = state.lastQuestion,
                    rollyResponse = state.lastResponse,
                    medecin = medecin
                )
                _uiState.update {
                    it.copy(
                        showValidationDialog = false,
                        validationSent = true,
                        validationDoctorName = medecin.nomComplet
                    )
                }
                val confirmMsg = ChatbotMessage(
                    contenu = "✅ Réponse envoyée à **Dr. ${medecin.nomComplet}** pour validation médicale.\nVous serez notifié de sa réponse.",
                    estUtilisateur = false
                )
                _uiState.update { it.copy(messages = it.messages + confirmMsg) }
                chatHistoryRepository.saveMessage(confirmMsg)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur validation", e)
                _uiState.update { it.copy(error = MessageErreur.lisible(e)) }
            }
        }
    }

    val suggestionsRapides = listOf(
        "Que puis-je manger ce soir avec ma glycémie ?",
        "Quels aliments font baisser la glycémie ?",
        "Comment éviter une hypoglycémie la nuit ?",
        "Quels sont les signes d'une hyperglycémie ?",
        "L'exercice physique influence-t-il la glycémie ?"
    )
}
