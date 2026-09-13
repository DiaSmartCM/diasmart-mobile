package com.diabeto.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.model.Conversation
import com.diabeto.data.model.Message
import com.diabeto.data.model.UserProfile
import com.diabeto.data.model.UserRole
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.MessagerieRepository
import com.diabeto.data.repository.PresenceRepository
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.diabeto.util.MessageErreur

data class MessagerieUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentProfile: UserProfile? = null,
    val medecins: List<UserProfile> = emptyList(),
    // v2.1.71 : patients lies (cote medecin) pour le FAB "Contacter un patient"
    val patients: List<UserProfile> = emptyList(),
    val showNouvelleConversation: Boolean = false
)

data class ConversationUiState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val currentUserId: String? = null,
    val interlocutorTyping: Boolean = false,
    val interlocutorOnline: Boolean = false
)

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val messagerieRepository: MessagerieRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagerieUiState())
    val uiState: StateFlow<MessagerieUiState> = _uiState.asStateFlow()

    init {
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val profile = authRepository.getCurrentUserProfile()
            _uiState.update { it.copy(currentProfile = profile) }

            // Patient -> liste des medecins ; Medecin -> liste de ses patients lies.
            when (profile?.role) {
                UserRole.PATIENT -> {
                    val medecins = authRepository.getMedecins()
                    _uiState.update { it.copy(medecins = medecins) }
                }
                UserRole.MEDECIN -> {
                    val patients = messagerieRepository.getMesPatients()
                    _uiState.update { it.copy(patients = patients) }
                }
                else -> {}
            }

            messagerieRepository.getConversationsFlow()
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = MessageErreur.lisible(e)) } }
                .collect { conversations ->
                    _uiState.update { it.copy(conversations = conversations, isLoading = false) }
                }
        }
    }

    fun toggleNouvelleConversation(show: Boolean) {
        _uiState.update { it.copy(showNouvelleConversation = show) }
    }

    fun creerConversationAvec(medecin: UserProfile, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val result = messagerieRepository.creerConversation(medecin)
            result.fold(
                onSuccess = { convId -> onSuccess(convId) },
                onFailure = { e -> _uiState.update { it.copy(error = MessageErreur.lisible(e)) } }
            )
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

@HiltViewModel
class ConversationDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messagerieRepository: MessagerieRepository,
    private val authRepository: AuthRepository,
    private val presenceRepository: PresenceRepository
) : ViewModel() {

    companion object {
        // Delai d'inactivite avant d'ecrire typing=false. Cout : 2 ecritures
        // Firestore par "session de frappe" (debut + arret), quelle que soit
        // sa duree — jamais 1 par caractere.
        private const val TYPING_DEBOUNCE_MS = 3_000L
        // Au-dela, on ignore un typing=true reste bloque (app fermee en
        // pleine frappe) sans attendre de nouvel evenement Firestore.
        private const val TYPING_STALE_MS = 8_000L
    }

    private val conversationId: String = savedStateHandle["conversationId"] ?: ""

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    /** UID of the other party in this conversation */
    var interlocuteurUid: String = ""
        private set

    private var isMedecin = false
    private var isCurrentlyTyping = false
    private var typingStopJob: Job? = null

    init {
        _uiState.update { it.copy(currentUserId = authRepository.currentUserId) }
        if (conversationId.isNotBlank()) {
            observerMessages()
            marquerCommeLus()
            viewModelScope.launch {
                isMedecin = authRepository.getCurrentUserProfile()?.role == UserRole.MEDECIN
                // v2.1.47 : passe par MessagerieRepository au lieu de FirebaseFirestore direct.
                interlocuteurUid = messagerieRepository.getInterlocuteurUid(conversationId).orEmpty()
                observerTyping()
                observerPresence()
            }
        }
    }

    private fun observerMessages() {
        viewModelScope.launch {
            messagerieRepository.getMessagesFlow(conversationId)
                .catch { e -> _uiState.update { it.copy(error = MessageErreur.lisible(e)) } }
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages, isLoading = false) }
                }
        }
    }

    private fun observerTyping() {
        viewModelScope.launch {
            messagerieRepository.getConversationFlow(conversationId)
                .catch { /* silencieux : l'indicateur de frappe n'est pas critique */ }
                .collect { conversation ->
                    if (conversation == null) return@collect
                    val typing = if (isMedecin) conversation.typingPatient else conversation.typingMedecin
                    val typingAt = if (isMedecin) conversation.typingPatientAt else conversation.typingMedecinAt
                    val fresh = (Timestamp.now().toDate().time - typingAt.toDate().time) < TYPING_STALE_MS
                    _uiState.update { it.copy(interlocutorTyping = typing && fresh) }
                }
        }
    }

    private fun observerPresence() {
        if (interlocuteurUid.isBlank()) return
        viewModelScope.launch {
            presenceRepository.observeOnline(interlocuteurUid)
                .catch { /* silencieux : le statut en ligne n'est pas critique */ }
                .collect { online -> _uiState.update { it.copy(interlocutorOnline = online) } }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
        if (conversationId.isBlank()) return

        if (text.isBlank()) {
            stopTyping()
            return
        }
        if (!isCurrentlyTyping) {
            isCurrentlyTyping = true
            viewModelScope.launch { messagerieRepository.setTyping(conversationId, true) }
        }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(TYPING_DEBOUNCE_MS)
            stopTyping()
        }
    }

    private fun stopTyping() {
        typingStopJob?.cancel()
        if (!isCurrentlyTyping) return
        isCurrentlyTyping = false
        viewModelScope.launch { messagerieRepository.setTyping(conversationId, false) }
    }

    fun envoyerMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || conversationId.isBlank()) return
        stopTyping()
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, inputText = "") }
            val result = messagerieRepository.envoyerMessage(conversationId, text)
            result.fold(
                onSuccess = { _uiState.update { it.copy(isSending = false) } },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isSending = false, error = MessageErreur.lisible(e), inputText = text)
                    }
                }
            )
        }
    }

    private fun marquerCommeLus() {
        viewModelScope.launch {
            messagerieRepository.marquerCommeLus(conversationId)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
