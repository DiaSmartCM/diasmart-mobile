package com.diabeto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v2.1.46 : ViewModel partage pour les tooltips d'onboarding contextuel.
 * Centralise la lecture/marquage des flags DataStore pour les 4+ ecrans
 * principaux. Reduit la duplication de code dans chaque ViewModel d'ecran.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val glucoseSeen: StateFlow<Boolean> =
        preferencesRepository.onboardingGlucoseSeen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val rollySeen: StateFlow<Boolean> =
        preferencesRepository.onboardingRollySeen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val messagerieSeen: StateFlow<Boolean> =
        preferencesRepository.onboardingMessagerieSeen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val reportsSeen: StateFlow<Boolean> =
        preferencesRepository.onboardingReportsSeen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun dismissGlucose() { viewModelScope.launch { preferencesRepository.markOnboardingGlucoseSeen() } }
    fun dismissRolly() { viewModelScope.launch { preferencesRepository.markOnboardingRollySeen() } }
    fun dismissMessagerie() { viewModelScope.launch { preferencesRepository.markOnboardingMessagerieSeen() } }
    fun dismissReports() { viewModelScope.launch { preferencesRepository.markOnboardingReportsSeen() } }
}
