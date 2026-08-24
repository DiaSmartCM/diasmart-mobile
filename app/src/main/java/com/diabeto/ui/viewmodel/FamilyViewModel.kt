package com.diabeto.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.model.FamilyLink
import com.diabeto.data.repository.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.diabeto.util.MessageErreur

data class FamilyUiState(
    val isLoading: Boolean = true,
    /** Aidants invites (= proches que JE suis owner pour). */
    val myAidants: List<FamilyLink> = emptyList(),
    /** Owners que J'aide (= patients qui m'ont invite). */
    val myOwners: List<FamilyLink> = emptyList(),
    val isInviting: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val familyRepository: FamilyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FamilyUiState())
    val uiState: StateFlow<FamilyUiState> = _uiState.asStateFlow()

    init {
        observeMyAidants()
        observeMyOwners()
    }

    private fun observeMyAidants() {
        viewModelScope.launch {
            familyRepository.getMyAidantsFlow().collect { aidants ->
                _uiState.update { it.copy(myAidants = aidants, isLoading = false) }
            }
        }
    }

    private fun observeMyOwners() {
        viewModelScope.launch {
            familyRepository.getMyOwnersFlow().collect { owners ->
                _uiState.update { it.copy(myOwners = owners) }
            }
        }
    }

    /**
     * Owner invite un aidant par email.
     * V1 : exige que l'aidant ait deja un compte DiaSmart.
     */
    fun inviteAidantByEmail(email: String, relation: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isInviting = true, error = null, message = null) }
            val profile = familyRepository.findUserByEmail(email.trim())
            if (profile == null || profile.uid.isBlank()) {
                _uiState.update {
                    it.copy(
                        isInviting = false,
                        error = "Aucun utilisateur DiaSmart avec cet email. Demande-lui de s'inscrire d'abord."
                    )
                }
                return@launch
            }
            familyRepository.inviteAidant(
                aidantUid = profile.uid,
                aidantEmail = email.trim(),
                aidantNom = profile.nomComplet.ifBlank { profile.email },
                relation = relation.trim()
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isInviting = false,
                            message = "Invitation envoyee a ${profile.nomComplet.ifBlank { email }}"
                        )
                    }
                },
                onFailure = { e ->
                    Log.w("FamilyVM", "invite failed", e)
                    _uiState.update { it.copy(isInviting = false, error = MessageErreur.lisible(e)) }
                }
            )
        }
    }

    fun acceptInvitation(ownerUid: String) {
        viewModelScope.launch {
            familyRepository.acceptInvitation(ownerUid).fold(
                onSuccess = { _uiState.update { it.copy(message = "Invitation acceptee") } },
                onFailure = { err -> _uiState.update { it.copy(error = MessageErreur.lisible(err)) } }
            )
        }
    }

    fun rejectInvitation(ownerUid: String) {
        viewModelScope.launch {
            familyRepository.rejectInvitation(ownerUid).fold(
                onSuccess = { _uiState.update { it.copy(message = "Invitation refusee") } },
                onFailure = { err -> _uiState.update { it.copy(error = MessageErreur.lisible(err)) } }
            )
        }
    }

    fun revokeAidant(aidantUid: String) {
        viewModelScope.launch {
            familyRepository.revokeAsOwner(aidantUid).fold(
                onSuccess = { _uiState.update { it.copy(message = "Acces aidant revoque") } },
                onFailure = { err -> _uiState.update { it.copy(error = MessageErreur.lisible(err)) } }
            )
        }
    }

    fun unlinkOwner(ownerUid: String) {
        viewModelScope.launch {
            familyRepository.unlinkAsAidant(ownerUid).fold(
                onSuccess = { _uiState.update { it.copy(message = "Lien supprime") } },
                onFailure = { err -> _uiState.update { it.copy(error = MessageErreur.lisible(err)) } }
            )
        }
    }

    fun reactivate(otherUid: String) {
        viewModelScope.launch {
            familyRepository.reactivateLink(otherUid).fold(
                onSuccess = { _uiState.update { it.copy(message = "Lien reactive") } },
                onFailure = { err -> _uiState.update { it.copy(error = MessageErreur.lisible(err)) } }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}
