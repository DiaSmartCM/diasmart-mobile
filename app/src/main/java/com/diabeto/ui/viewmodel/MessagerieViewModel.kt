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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class MessagerieUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentProfile: UserProfile? = null,
    val medecins: List<UserProfile> = emptyList(),
    val showNouvelleConversation: Boolean = false
)

data class ConversationUiState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val currentUserId: String? = null
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

            // Si patient, charger la liste des médecins disponibles
            if (profile?.role == UserRole.PATIENT) {
                val medecins = authRepository.getMedecins()
                _uiState.update { it.copy(medecins = medecins) }
            }

            messagerieRepository.getConversationsFlow()
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
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
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } }
            )
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

@HiltViewModel
class ConversationDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messagerieRepository: MessagerieRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val conversationId: String = savedStateHandle["conversationId"] ?: ""

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    /** UID of the other party in this conversation */
    var interlocuteurUid: String = ""
        private set

    init {
        _uiState.update { it.copy(currentUserId = authRepository.currentUserId) }
        if (conversationId.isNotBlank()) {
            observerMessages()
            marquerCommeLus()
            loadInterlocuteurUid()
        }
    }

    private fun loadInterlocuteurUid() {
        viewModelScope.launch {
            // v2.1.47 : passe par MessagerieRepository au lieu de FirebaseFirestore direct.
            interlocuteurUid = messagerieRepository.getInterlocuteurUid(conversationId).orEmpty()
        }
    }

    private fun observerMessages() {
        viewModelScope.launch {
            messagerieRepository.getMessagesFlow(conversationId)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages, isLoading = false) }
                }
        }
    }

    fun onInputChange(text: String) = _uiState.update { it.copy(inputText = text) }

    fun envoyerMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || conversationId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, inputText = "") }
            val result = messagerieRepository.envoyerMessage(conversationId, text)
            result.fold(
                onSuccess = { _uiState.update { it.copy(isSending = false) } },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isSending = false, error = e.message, inputText = text)
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
