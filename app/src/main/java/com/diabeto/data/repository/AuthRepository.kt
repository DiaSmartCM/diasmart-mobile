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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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
        // Base URL Vercel pour l'API de verification email OTP
        // (alias Vercel stable → on reste sur le domaine personnalise du projet)
        private const val API_BASE = "https://website-omega-umber-20.vercel.app"
        private const val API_SEND_OTP = "$API_BASE/api/send-email-otp"
        private const val API_VERIFY_OTP = "$API_BASE/api/verify-email-otp"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
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

    /**
     * Resultat d'une tentative de connexion.
     * - Authenticated : email verifie, acces complet
     * - NeedsOtp : compte cree ou non verifie, un code a 6 chiffres a ete envoye par email
     */
    sealed class SignInOutcome {
        data class Authenticated(val user: FirebaseUser) : SignInOutcome()
        data class NeedsOtp(val email: String) : SignInOutcome()
    }

    suspend fun signIn(email: String, password: String): Result<SignInOutcome> {
        return try {
            val result = withTimeoutOrNull(15_000L) {
                auth.signInWithEmailAndPassword(email, password).await()
            } ?: return Result.failure(Exception("Délai de connexion dépassé. Vérifiez votre connexion internet."))
            val user = result.user!!
            // Si l'email n'est pas verifie, on envoie un code OTP et on laisse l'utilisateur signed-in
            // (l'UI navigue alors vers l'ecran OTP). Les comptes telephone (sans email) passent direct.
            if (!user.isEmailVerified && !user.email.isNullOrBlank()) {
                try { sendEmailOtp() } catch (_: Exception) { /* l'ecran OTP permettra de relancer */ }
                return Result.success(SignInOutcome.NeedsOtp(user.email!!))
            }
            Result.success(SignInOutcome.Authenticated(user))
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
    ): Result<SignInOutcome> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user!!
            createUserProfile(user, nom, prenom, role, email)
            // Envoyer un code a 6 chiffres par email (via Cloud Function)
            try { sendEmailOtp() } catch (_: Exception) { /* l'ecran OTP permet de relancer */ }
            Result.success(SignInOutcome.NeedsOtp(email))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ================================================================
    //  EMAIL OTP (Cloud Functions)
    // ================================================================

    /**
     * Recupere un Firebase ID token frais pour authentifier les appels Vercel.
     */
    private suspend fun getIdToken(): String? {
        val user = auth.currentUser ?: return null
        return try {
            user.getIdToken(false).await()?.token
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Demande l'envoi d'un code OTP a 6 chiffres par email (POST /api/send-email-otp).
     * L'utilisateur doit etre connecte (Firebase Auth) avant l'appel.
     */
    suspend fun sendEmailOtp(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getIdToken() ?: return@withContext Result.failure(Exception("Connexion requise."))
            val req = Request.Builder()
                .url(API_SEND_OTP)
                .header("Authorization", "Bearer $token")
                .post("{}".toRequestBody(JSON_MEDIA))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(Exception(extractApiError(resp.body?.string(), resp.code)))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Erreur reseau : ${e.message ?: "inconnue"}"))
        }
    }

    /**
     * Valide un code OTP a 6 chiffres (POST /api/verify-email-otp).
     * En cas de succes, rafraichit le token Firebase pour recuperer emailVerified=true.
     */
    suspend fun verifyEmailOtp(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getIdToken() ?: return@withContext Result.failure(Exception("Connexion requise."))
            val payload = JSONObject().put("code", code).toString()
            val req = Request.Builder()
                .url(API_VERIFY_OTP)
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception(extractApiError(resp.body?.string(), resp.code)))
                }
            }
            // Rafraichir l'etat utilisateur pour que user.isEmailVerified devienne true cote client
            try { auth.currentUser?.reload()?.await() } catch (_: Exception) {}
            try { auth.currentUser?.getIdToken(true)?.await() } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Erreur reseau : ${e.message ?: "inconnue"}"))
        }
    }

    private fun extractApiError(body: String?, status: Int): String {
        if (body.isNullOrBlank()) return "Erreur serveur (HTTP $status)"
        return try {
            val j = JSONObject(body)
            j.optString("message").takeIf { it.isNotBlank() }
                ?: j.optString("error").takeIf { it.isNotBlank() }
                ?: "Erreur serveur (HTTP $status)"
        } catch (_: Exception) {
            "Erreur serveur (HTTP $status)"
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
    /**
     * v2.1.45 : suppression RGPD complete via endpoint Vercel
     * /api/delete-account qui cascade tout (Firestore + FCM + Auth).
     * Avant cette version, seuls data_sharing + users etaient supprimes,
     * laissant glucose, repas, journal, conversations, etc. orphelins.
     */
    suspend fun deleteAccount(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("Non connecte"))
        return try {
            val token = user.getIdToken(false).await()?.token
                ?: return Result.failure(Exception("ID token indisponible"))
            val body = """{"confirm":"DELETE_${user.uid}"}"""
            val req = okhttp3.Request.Builder()
                .url("https://website-omega-umber-20.vercel.app/api/delete-account")
                .addHeader("Authorization", "Bearer $token")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string().orEmpty()
                    return Result.failure(Exception("Delete HTTP ${resp.code}: ${errBody.take(120)}"))
                }
            }
            // Sign out locally (le token serveur est deja invalide cote Auth)
            runCatching { auth.signOut() }
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

    /**
     * v2.1.47 : merge generique de champs dans users/{uid}. Utilise par
     * - ProfileSyncViewModel (location GPS)
     * - PatientViewModel (morpho : poids, taille, IMC, tour taille...)
     *
     * Centralise pour eviter que des ViewModels accedent directement a
     * FirebaseFirestore.
     */
    suspend fun mergeUserFields(fields: Map<String, Any?>): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Non connecte")
        firestore.collection(COLLECTION_USERS).document(uid)
            .set(fields, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }
}
