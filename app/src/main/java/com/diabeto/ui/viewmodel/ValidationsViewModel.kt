package com.diabeto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.model.RollyValidation
import com.diabeto.data.model.UserRole
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.ValidationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.diabeto.util.MessageErreur

data class ValidationsUiState(
    val validations: List<RollyValidation> = emptyList(),
    val isLoading: Boolean = true,
    val isMedecin: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ValidationsViewModel @Inject constructor(
    private val validationRepository: ValidationRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ValidationsUiState())
    val uiState: StateFlow<ValidationsUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        loadValidations()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val profile = authRepository.getCurrentUserProfile()
            _uiState.update { it.copy(isMedecin = profile?.role == UserRole.MEDECIN) }
        }
    }

    private fun loadValidations() {
        viewModelScope.launch {
            validationRepository.getValidationsFlow().collect { validations ->
                _uiState.update {
                    it.copy(validations = validations, isLoading = false)
                }
            }
        }
    }

    fun validate(validationId: String, approved: Boolean, comment: String) {
        viewModelScope.launch {
            try {
                validationRepository.validateResponse(validationId, approved, comment)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = MessageErreur.lisible(e)) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
