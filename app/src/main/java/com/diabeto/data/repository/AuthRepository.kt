package com.diabeto.data.repository

import android.app.Activity
import com.diabeto.data.model.UserProfile
import com.diabeto.data.model.UserRole
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour l'authentification Firebase :
 * - Email/Mot de passe
 * - Google Sign-In
 * - Numéro de téléphone (SMS OTP)
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val COLLECTION_USERS = "users"
    }

    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUserId: String? get() = auth.currentUser?.uid

    // ── Stockage temporaire pour vérification téléphone ──────────────
    var storedVerificationId: String? = null
        private set
    var resendToken: PhoneAuthProvider.ForceResendingToken? = null
        private set

    /**
     * Flow sur l'état de connexion
     */
    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // ================================================================
    //  EMAIL / MOT DE PASSE
    // ================================================================

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = withTimeoutOrNull(15_000L) {
                auth.signInWithEmailAndPassword(email, password).await()
            } ?: return Result.failure(Exception("Délai de connexion dépassé. Vérifiez votre connexion internet."))
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(
        email: String,
        password: String,
        nom: String,
        prenom: String,
        role: UserRole
    ): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user!!
            createUserProfile(user, nom, prenom, role, email)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ================================================================
    //  GOOGLE SIGN-IN
    // ================================================================

    /**
     * Authentification avec un credential Google (idToken)
     */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = withTimeoutOrNull(15_000L) {
                auth.signInWithCredential(credential).await()
            } ?: return Result.failure(Exception("Délai de connexion Google dépassé."))
            val user = result.user!!

            // Créer le profil Firestore si c'est la première connexion
            val existingProfile = getUserProfile(user.uid)
            if (existingProfile == null) {
                val displayName = user.displayName ?: ""
                val parts = displayName.split(" ", limit = 2)
                val prenom = parts.getOrElse(0) { "" }
                val nom = parts.getOrElse(1) { "" }
                createUserProfile(
                    user = user,
                    nom = nom,
                    prenom = prenom,
                    role = UserRole.PATIENT,
                    email = user.email ?: ""
                )
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Authentification avec un AuthCredential Firebase générique
     */
    suspend fun signInWithCredential(credential: AuthCredential): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user!!

            val existingProfile = getUserProfile(user.uid)
            if (existingProfile == null) {
                val displayName = user.displayName ?: ""
                val parts = displayName.split(" ", limit = 2)
                createUserProfile(
                    user = user,
                    nom = parts.getOrElse(1) { "" },
                    prenom = parts.getOrElse(0) { "" },
                    role = UserRole.PATIENT,
                    email = user.email ?: ""
                )
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ================================================================
    //  PHONE AUTH (SMS OTP)
    // ================================================================

    /**
     * Envoyer un code de vérification SMS au numéro de téléphone
     */
    fun sendVerificationCode(
        phoneNumber: String,
        activity: Activity,
        onCodeSent: (String) -> Unit,
        onVerificationCompleted: (PhoneAuthCredential) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-vérification (sur certains appareils)
                onVerificationCompleted(credential)
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                onError(e)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                storedVerificationId = verificationId
                resendToken = token
                onCodeSent(verificationId)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /**
     * Vérifier le code SMS et connecter l'utilisateur
     */
    suspend fun verifyPhoneCode(
        verificationId: String,
        code: String
    ): Result<FirebaseUser> {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user!!

            // Créer le profil si première connexion par téléphone
            val existingProfile = getUserProfile(user.uid)
            if (existingProfile == null) {
                createUserProfile(
                    user = user,
                    nom = "",
                    prenom = "",
                    role = UserRole.PATIENT,
                    email = user.email ?: "",
                    telephone = user.phoneNumber ?: ""
                )
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ================================================================
    //  PROFILS & UTILITAIRES
    // ================================================================

    /**
     * Créer un profil utilisateur dans Firestore
     */
    private suspend fun createUserProfile(
        user: FirebaseUser,
        nom: String,
        prenom: String,
        role: UserRole,
        email: String,
        telephone: String = ""
    ) {
        val profile = UserProfile(
            uid = user.uid,
            email = email.ifEmpty { user.email ?: "" },
            nom = nom,
            prenom = prenom,
            role = role,
            telephone = telephone.ifEmpty { user.phoneNumber ?: "" }
        )
        withTimeoutOrNull(10_000L) {
            firestore.collection(COLLECTION_USERS)
                .document(user.uid)
                .set(profile.toMap())
                .await()
        }
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun getCurrentUserProfile(): UserProfile? {
        val uid = currentUserId ?: return null
        return getUserProfile(uid)
    }

    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            val doc = withTimeoutOrNull(10_000L) {
                firestore.collection(COLLECTION_USERS).document(uid).get().await()
            } ?: return null
            if (doc.exists()) {
                @Suppress("UNCHECKED_CAST")
                UserProfile.fromMap(doc.data as Map<String, Any?>)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Mettre à jour le profil (pour compléter nom/prénom après connexion Google/Téléphone)
     */
    suspend fun updateUserProfile(
        nom: String,
        prenom: String,
        role: UserRole
    ): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Non connecté"))
        return try {
            firestore.collection(COLLECTION_USERS)
                .document(uid)
                .update(
                    mapOf(
                        "nom" to nom,
                        "prenom" to prenom,
                        "role" to role.name
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMedecins(): List<UserProfile> {
        return try {
            val snap = firestore.collection(COLLECTION_USERS)
                .whereEqualTo("role", UserRole.MEDECIN.name)
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                if (data["isDeleted"] == true) return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val p = UserProfile.fromMap(data as Map<String, Any?>)
                if (p.uid.isBlank()) return@mapNotNull null
                if (p.nom.isBlank() && p.prenom.isBlank() && p.email.isBlank()) return@mapNotNull null
                p
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Supprime completement le compte de l'utilisateur courant :
     * - toutes ses entrees dans `data_sharing` (cote patient ET medecin)
     * - son document dans `users`
     * - son compte Firebase Auth
     *
     * Les donnees medicales (glycemie, repas, RDV) restent Firestore mais
     * sont orphelines : le doc user disparait donc l'utilisateur n'est plus
     * listable par les medecins/patients.
     */
    suspend fun deleteAccount(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("Non connecte"))
        val uid = user.uid
        return try {
            // Supprimer les consentements ou on est patient
            val asPatient = firestore.collection("data_sharing")
                .whereEqualTo("patientUid", uid).get().await()
            for (d in asPatient.documents) d.reference.delete().await()
            // Supprimer les consentements ou on est medecin
            val asMedecin = firestore.collection("data_sharing")
                .whereEqualTo("medecinUid", uid).get().await()
            for (d in asMedecin.documents) d.reference.delete().await()
            // Supprimer le profil utilisateur
            firestore.collection(COLLECTION_USERS).document(uid).delete().await()
            // Supprimer le compte Auth
            user.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
