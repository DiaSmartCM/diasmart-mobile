package com.diabeto.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.api.NotificationApi
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.CommunityRepository
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.diabeto.util.MessageErreur

data class CommunityMessage(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val content: String = "",
    val timestamp: Timestamp = Timestamp.now()
)

data class CommunityUiState(
    val messages: List<CommunityMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val currentUserId: String = "",
    val membersCount: Int = 0,
    val typersNames: List<String> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val communityRepository: CommunityRepository,
    private val notificationApi: NotificationApi
) : ViewModel() {

    companion object {
        // Coherent avec ConversationDetailViewModel (cf. commentaire la-bas).
        private const val TYPING_DEBOUNCE_MS = 3_000L
        private const val TYPING_REFRESH_MS = 8_000L
    }

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    private var userName: String = "Anonyme"
    private var isCurrentlyTyping = false
    private var typingStopJob: Job? = null
    private var typingRefreshJob: Job? = null

    init {
        _uiState.update { it.copy(currentUserId = authRepository.currentUserId ?: "") }
        observeMessages()
        observeTypers()
        countMembers()
        viewModelScope.launch {
            val profile = authRepository.getCurrentUserProfile()
            userName = profile?.nomComplet?.ifBlank { profile.email } ?: "Anonyme"
        }
    }

    private fun observeTypers() {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            communityRepository.observeTypers(excludeUid = uid)
                .catch { /* silencieux : l'indicateur de frappe n'est pas critique */ }
                .collect { names -> _uiState.update { it.copy(typersNames = names) } }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            communityRepository.observeMessages().collect { messages ->
                _uiState.update { it.copy(messages = messages, isLoading = false) }
            }
        }
    }

    private fun countMembers() {
        viewModelScope.launch {
            val count = communityRepository.countPatientMembers()
            _uiState.update { it.copy(membersCount = count) }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }

        if (text.isBlank()) {
            stopTyping()
            return
        }
        if (!isCurrentlyTyping) {
            isCurrentlyTyping = true
            val uid = authRepository.currentUserId
            if (uid != null) {
                viewModelScope.launch { communityRepository.setTyping(uid, userName, true) }
                startTypingRefresh(uid)
            }
        }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(TYPING_DEBOUNCE_MS)
            stopTyping()
        }
    }

    private fun startTypingRefresh(uid: String) {
        typingRefreshJob?.cancel()
        typingRefreshJob = viewModelScope.launch {
            while (isCurrentlyTyping) {
                delay(TYPING_REFRESH_MS)
                if (isCurrentlyTyping) {
                    communityRepository.setTyping(uid, userName, true)
                }
            }
        }
    }

    private fun stopTyping() {
        typingStopJob?.cancel()
        typingRefreshJob?.cancel()
        if (!isCurrentlyTyping) return
        isCurrentlyTyping = false
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch { communityRepository.setTyping(uid, userName, false) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return
        stopTyping()

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            try {
                val profile = authRepository.getCurrentUserProfile()
                val userName = profile?.nomComplet?.ifBlank { profile.email } ?: "Anonyme"

                communityRepository.postMessage(
                    userId = authRepository.currentUserId ?: "",
                    userName = userName,
                    content = text
                ).getOrThrow()

                // Push FCM topic "community" (best-effort).
                try {
                    notificationApi.notifyCommunity(preview = text, senderName = userName)
                } catch (e: Exception) {
                    Log.w("CommunityVM", MessageErreur.lisible(e))
                }

                _uiState.update { it.copy(inputText = "", isSending = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, error = MessageErreur.lisible(e)) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
