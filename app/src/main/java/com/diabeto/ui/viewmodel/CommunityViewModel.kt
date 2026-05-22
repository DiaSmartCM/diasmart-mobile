package com.diabeto.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.api.NotificationApi
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.CommunityRepository
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val error: String? = null
)

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val communityRepository: CommunityRepository,
    private val notificationApi: NotificationApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(currentUserId = authRepository.currentUserId ?: "") }
        observeMessages()
        countMembers()
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
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return

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
                    Log.w("CommunityVM", "notifyCommunity failed: ${e.message}")
                }

                _uiState.update { it.copy(inputText = "", isSending = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, error = "Erreur: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
