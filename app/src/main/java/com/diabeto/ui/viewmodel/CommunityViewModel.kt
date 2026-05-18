package com.diabeto.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.api.NotificationApi
import com.diabeto.data.repository.AuthRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    private val notificationApi: NotificationApi
) : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(currentUserId = authRepository.currentUserId ?: "") }
        observeMessages()
        countMembers()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            callbackFlow {
                val listener = db.collection("community_messages")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .limit(200)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            trySend(emptyList())
                            return@addSnapshotListener
                        }
                        val messages = snapshot?.documents?.mapNotNull { doc ->
                            try {
                                CommunityMessage(
                                    id = doc.id,
                                    userId = doc.getString("userId") ?: "",
                                    userName = doc.getString("userName") ?: "Anonyme",
                                    content = doc.getString("content") ?: "",
                                    timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now()
                                )
                            } catch (_: Exception) { null }
                        } ?: emptyList()
                        trySend(messages)
                    }
                awaitClose { listener.remove() }
            }.collect { messages ->
                _uiState.update { it.copy(messages = messages, isLoading = false) }
            }
        }
    }

    private fun countMembers() {
        viewModelScope.launch {
            try {
                val count = db.collection("users")
                    .whereEqualTo("role", "PATIENT")
                    .get().await()
                    .size()
                _uiState.update { it.copy(membersCount = count) }
            } catch (e: Exception) {
                Log.w("CommunityVM", "Failed to count members", e)
            }
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

                db.collection("community_messages").add(
                    mapOf(
                        "userId" to (authRepository.currentUserId ?: ""),
                        "userName" to userName,
                        "content" to text,
                        "timestamp" to Timestamp.now()
                    )
                ).await()

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
