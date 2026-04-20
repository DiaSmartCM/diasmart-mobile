package com.diabeto.ui.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.model.UserProfile
import com.diabeto.data.model.UserRole
import android.util.Log
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.CloudBackupRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Mode de connexion actif sur la page Login
 */
enum class LoginMode {
    EMAIL,
    PHONE
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val currentUser: FirebaseUser? = null,
    val userProfile: UserProfile? = null,
    val error: String? = null,
    val showRegister: Boolean = false,
    // Champs formulaire
    val email: String = "",
    val password: String = "",
    val nom: String = "",
    val prenom: String = "",
    val selectedRole: UserRole = UserRole.PATIENT,
    val resetEmailSent: Boolean = false,
    // Mode de connexion
    val loginMode: LoginMode = LoginMode.EMAIL,
    // Phone Auth
    val phoneNumber: String = "",
    val smsCode: String = "",
    val verificationId: String? = null,
    val isCodeSent: Boolean = false,
    val phoneAutoVerified: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudBackupRepository: CloudBackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            val user = authRepository.currentUser
            if (user != null) {
                val profile = authRepository.getCurrentUserProfile()
                _uiState.update {
                    it.copy(
                        isLoggedIn = true,
                        currentUser = user,
                        userProfile = profile
                    )
                }
            }
        }
    }

    // ── Mise à jour des champs ──────────────────────────────────────
    fun onEmailChange(email: String) { if (email.length <= 254) _uiState.update { it.copy(email = email) } }
    fun onPasswordChange(pw: String) { if (pw.length <= 128) _uiState.update { it.copy(password = pw) } }
    fun onNomChange(nom: String) { if (nom.length <= 50) _uiState.update { it.copy(nom = nom) } }
    fun onPrenomChange(prenom: String) { if (prenom.length <= 50) _uiState.update { it.copy(prenom = prenom) } }
    fun onRoleChange(role: UserRole) = _uiState.update { it.copy(selectedRole = role) }
    fun onPhoneNumberChange(phone: String) { if (phone.length <= 15) _uiState.update { it.copy(phoneNumber = phone) } }
    fun onSmsCodeChange(code: String) { if (code.length <= 6) _uiState.update { it.copy(smsCode = code.filter { it.isDigit() }) } }

    fun toggleRegister(show: Boolean) = _uiState.update {
        it.copy(showRegister = show, error = null)
    }

    fun setLoginMode(mode: LoginMode) = _uiState.update {
        it.copy(loginMode = mode, error = null, isCodeSent = false, smsCode = "", verificationId = null)
    }

    // ================================================================
    //  EMAIL / PASSWORD
    // ================================================================

    fun signIn() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Email et mot de passe requis") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = authRepository.signIn(state.email.trim(), state.password)
            result.fold(
                onSuccess = { user ->
                    val profile = authRepository.getCurrentUserProfile()
                    // Auto-restore cloud data if local DB is empty (reinstall)
                    tryAutoRestore()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            currentUser = user,
                            userProfile = profile
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = traduitErreurFirebase(e.message))
                    }
                }
            )
        }
    }

    fun signUp() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank() ||
            state.nom.isBlank() || state.prenom.isBlank()
        ) {
            _uiState.update { it.copy(error = "Tous les champs sont obligatoires") }
            return
        }
        if (state.password.length < 8) {
            _uiState.update { it.copy(error = "Le mot de passe doit contenir au moins 8 caractères") }
            return
        }
        if (!state.password.any { it.isUpperCase() } || !state.password.any { it.isDigit() }) {
            _uiState.update { it.copy(error = "Le mot de passe doit contenir au moins une majuscule et un chiffre") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = authRepository.signUp(
                email = state.email.trim(),
                password = state.password,
                nom = state.nom.trim(),
                prenom = state.prenom.trim(),
                role = state.selectedRole
            )
            result.fold(
                onSuccess = { _ ->
                    // Le repository a deja deconnecte l'utilisateur et envoye l'email de verification.
                    // On repasse en mode "connexion" et on affiche un message clair.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            showRegister = false,
                            password = "",
                            error = "Compte cree. Un email de verification vient d'etre envoye a ${state.email.trim()}. Cliquez sur le lien avant de vous connecter."
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = traduitErreurFirebase(e.message))
                    }
                }
            )
        }
    }

    // ================================================================
    //  GOOGLE SIGN-IN
    // ================================================================

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = authRepository.signInWithGoogle(idToken)
            result.fold(
                onSuccess = { user ->
                    val profile = authRepository.getCurrentUserProfile()
                    tryAutoRestore()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            currentUser = user,
                            userProfile = profile
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = "Erreur Google : ${e.message}")
                    }
                }
            )
        }
    }

    // ================================================================
    //  PHONE AUTH
    // ================================================================

    fun sendPhoneVerification(activity: Activity) {
        val phone = _uiState.value.phoneNumber.trim()
        if (phone.isBlank()) {
            _uiState.update { it.copy(error = "Entrez votre numéro de téléphone") }
            return
        }
        // Formater le numéro (ajouter +213 si pas de préfixe international)
        val formattedPhone = if (phone.startsWith("+")) phone
        else if (phone.startsWith("0")) "+213${phone.substring(1)}"
        else "+213$phone"

        _uiState.update { it.copy(isLoading = true, error = null) }

        authRepository.sendVerificationCode(
            phoneNumber = formattedPhone,
            activity = activity,
            onCodeSent = { verificationId ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isCodeSent = true,
                        verificationId = verificationId
                    )
                }
            },
            onVerificationCompleted = { credential ->
                _uiState.update { it.copy(phoneAutoVerified = true) }
                signInWithPhoneCredential(credential)
            },
            onError = { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = traduitErreurFirebase(e.message))
                }
            }
        )
    }

    fun verifyPhoneCode() {
        val state = _uiState.value
        val verificationId = state.verificationId
        if (verificationId == null || state.smsCode.isBlank()) {
            _uiState.update { it.copy(error = "Entrez le code reçu par SMS") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = authRepository.verifyPhoneCode(verificationId, state.smsCode.trim())
            result.fold(
                onSuccess = { user ->
                    val profile = authRepository.getCurrentUserProfile()
                    tryAutoRestore()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            currentUser = user,
                            userProfile = profile
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = traduitErreurFirebase(e.message))
                    }
                }
            )
        }
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = authRepository.signInWithCredential(credential)
            result.fold(
                onSuccess = { user ->
                    val profile = authRepository.getCurrentUserProfile()
                    tryAutoRestore()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            currentUser = user,
                            userProfile = profile
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = "Erreur : ${e.message}")
                    }
                }
            )
        }
    }

    // ================================================================
    //  UTILITAIRES
    // ================================================================

    fun signOut() {
        authRepository.signOut()
        _uiState.update { AuthUiState() }
    }

    fun resetPassword() {
        val email = _uiState.value.email
        if (email.isBlank()) {
            _uiState.update { it.copy(error = "Entrez votre email pour réinitialiser le mot de passe") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = authRepository.resetPassword(email.trim())
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, resetEmailSent = true) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = traduitErreurFirebase(e.message))
                    }
                }
            )
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    /**
     * Auto-restore cloud backup if local DB is empty (e.g. after reinstall).
     * Runs silently in background — does not block login.
     */
    private fun tryAutoRestore() {
        viewModelScope.launch {
            try {
                if (cloudBackupRepository.isLocalDbEmpty() && cloudBackupRepository.hasCloudBackup()) {
                    Log.d("AuthVM", "Local DB empty + cloud backup found → restoring...")
                    val result = cloudBackupRepository.performFullRestore()
                    result.fold(
                        onSuccess = { count ->
                            Log.d("AuthVM", "Auto-restore complete: $count documents restored")
                        },
                        onFailure = { e ->
                            Log.e("AuthVM", "Auto-restore failed", e)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("AuthVM", "Auto-restore check failed", e)
            }
        }
    }

    private fun traduitErreurFirebase(message: String?): String = when {
        message == null -> "Erreur inconnue"
        // Message explicite de verification email (laisse passer tel quel)
        message.contains("non verifie", ignoreCase = true) ||
            message.contains("verification", ignoreCase = true) -> message
        // Sécurité : messages génériques pour login/signup (pas de fuite d'existence de compte)
        message.contains("password", ignoreCase = true) -> "Email ou mot de passe incorrect"
        message.contains("email") && message.contains("already") -> "Impossible de créer le compte. Vérifiez vos informations."
        message.contains("email", ignoreCase = true) -> "Format d'email invalide"
        message.contains("network", ignoreCase = true) -> "Pas de connexion réseau"
        message.contains("user-not-found") || message.contains("no user") -> "Email ou mot de passe incorrect"
        message.contains("too-many-requests") -> "Trop de tentatives, réessayez plus tard"
        message.contains("invalid-phone") -> "Numéro de téléphone invalide"
        message.contains("invalid-verification") -> "Code de vérification invalide"
        message.contains("quota-exceeded") -> "Service temporairement indisponible, réessayez plus tard"
        message.contains("session-expired") -> "Session expirée, renvoyez le code"
        message.contains("credential") -> "Identifiants invalides"
        else -> "Une erreur est survenue. Veuillez réessayer."
    }
}
