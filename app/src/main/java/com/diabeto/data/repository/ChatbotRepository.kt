package com.diabeto.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.diabeto.data.dao.AiCacheDao
import com.diabeto.data.entity.AiCacheEntity
import com.diabeto.data.entity.HbA1cEntity
import com.diabeto.data.entity.LectureGlucoseEntity
import com.diabeto.data.entity.PatientEntity
import com.diabeto.util.UrgencyDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChatbotRepository"

/**
 * Repository pour ROLLY (assistant IA DiaSmart).
 *
 * v2.1.37+ : tous les appels Gemini passent par le proxy Vercel
 * (`/api/rolly-chat`) via [RollyChatClient]. La cle API Gemini n'est plus
 * embarquee dans l'APK ; seul l'ID token Firebase de l'utilisateur est
 * transmis (header Bearer) pour authentifier la requete cote serveur.
 *
 * Architecture cloud-only depuis v2.1.31 :
 * - Le modele on-device (Gemma 3 1B via MediaPipe) a ete supprime.
 * - Hors ligne : ROLLY indique "Pas de connexion" et propose de reessayer.
 *
 * Cache local Room : evite les appels redondants pour questions generiques.
 */
@Singleton
class ChatbotRepository @Inject constructor(
    private val rollyClient: RollyChatClient,
    private val aiCacheDao: AiCacheDao,
    @ApplicationContext private val context: Context
) {
    /** Verifie la connectivite reseau via le ConnectivityManager systeme. */
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // HMAC key derived from app package — prevents cache tampering
    private val hmacKey: ByteArray = "diasmart-ai-cache-integrity-key".toByteArray(Charsets.UTF_8)

    // ─────────────────────────────────────────────────────────────────────────
    // CACHE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun hashQuery(text: String): String {
        val normalized = text.trim().lowercase()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[,.!?;:]+"), "")
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun isCacheableQuestion(message: String): Boolean {
        val lower = message.lowercase()
        val genericPatterns = listOf(
            "qu'est-ce que", "c'est quoi", "définition", "definition",
            "qu'est ce que", "explique", "comment fonctionne",
            "symptômes", "symptomes", "signes", "causes",
            "différence entre", "difference entre",
            "aliments", "manger", "éviter", "régime", "regime",
            "exercice", "sport", "activité physique",
            "hypoglycémie", "hypoglycemie", "hyperglycémie", "hyperglycemie",
            "hba1c", "insuline", "glycémie", "glycemie",
            "diabète type 1", "diabete type 1", "diabète type 2", "diabete type 2",
            "conseils", "recommandations", "prévenir", "prevenir"
        )
        return genericPatterns.any { lower.contains(it) }
    }

    private suspend fun getCachedResponse(query: String): String? {
        val hash = hashQuery(query)
        val cached = aiCacheDao.getCached(hash)
        if (cached != null) {
            if (!cached.verifyIntegrity(hmacKey)) {
                Log.w(TAG, "Cache HMAC mismatch — discarding tampered entry: ${query.take(50)}...")
                aiCacheDao.deleteByHash(hash)
                return null
            }
            aiCacheDao.incrementHitCount(hash)
            Log.d(TAG, "Cache HIT for: ${query.take(50)}... (hits: ${cached.hitCount + 1})")
            return cached.response
        }
        return null
    }

    private suspend fun cacheResponse(query: String, response: String, category: String = "general") {
        val hash = hashQuery(query)
        val signature = AiCacheEntity.computeHmac(hash, response, hmacKey)
        aiCacheDao.insert(
            AiCacheEntity(
                queryHash = hash,
                query = query.take(200),
                response = response,
                category = category,
                expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000, // 24h
                hmac = signature
            )
        )
        Log.d(TAG, "Cached response for: ${query.take(50)}...")
    }

    /** Purge expired cache entries. Call periodically. */
    suspend fun purgeCache() {
        aiCacheDao.purgeExpired()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONVERSATION LIBRE — STREAMING
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Envoie un message et stream la reponse (chunk accumule a chaque emit).
     */
    fun envoyerMessage(message: String): Flow<String> = flow {
        // ── DETECTION D'URGENCE (latence 0 ms) ──
        val urgencyPrefix = when {
            UrgencyDetector.detectUrgency(message) -> UrgencyDetector.getEmergencyResponse()
            UrgencyDetector.detectWarning(message) -> UrgencyDetector.getWarningResponse()
            else -> ""
        }
        if (urgencyPrefix.isNotEmpty()) {
            Log.d(TAG, "Urgency detected in message, showing emergency info first")
            emit(urgencyPrefix)
        }

        if (!isOnline()) {
            emit(
                "${urgencyPrefix}📴 *Pas de connexion internet*\n\n" +
                    "ROLLY a besoin d'une connexion internet pour repondre. " +
                    "Vos saisies (glycemies, repas, journal...) sont enregistrees " +
                    "localement et seront synchronisees automatiquement au retour " +
                    "du reseau."
            )
            return@flow
        }

        Log.d(TAG, "envoyerMessage (stream proxy): $message")
        var emitted = false
        rollyClient.streamText(mode = "chat", message = message).collect { acc ->
            emitted = true
            emit("$urgencyPrefix$acc")
        }
        if (!emitted) {
            emit("${urgencyPrefix}Je n'ai pas pu générer de réponse.")
        }
    }.catch { e ->
        Log.e(TAG, "Erreur envoyerMessage", e)
        if (!isOnline() || (e.message?.contains("Unable to resolve host") == true)) {
            emit(
                "📴 *Pas de connexion internet*\n\n" +
                    "Reessayez quand votre signal sera retabli. Vos donnees " +
                    "personnelles continuent d'etre enregistrees localement."
            )
        } else {
            emit("❌ Erreur IA : ${friendlyError(e).message}")
        }
    }

    /**
     * Envoie un message avec contexte patient (mode chat_context).
     */
    fun envoyerMessageAvecContexte(
        message: String,
        patient: PatientEntity?,
        lecturesRecentes: List<LectureGlucoseEntity>,
        latestHbA1c: HbA1cEntity? = null,
        hba1cEstimee: Double? = null,
        historiqueChat: String = ""
    ): Flow<String> = flow {
        val urgencyPrefix = when {
            UrgencyDetector.detectUrgency(message) -> UrgencyDetector.getEmergencyResponse()
            UrgencyDetector.detectWarning(message) -> UrgencyDetector.getWarningResponse()
            else -> ""
        }
        if (urgencyPrefix.isNotEmpty()) {
            Log.d(TAG, "Urgency detected with context, showing emergency info first")
            emit(urgencyPrefix)
        }

        // Cache hit pour questions generiques sans contexte patient
        if (isCacheableQuestion(message) && patient == null && lecturesRecentes.isEmpty()) {
            val cached = getCachedResponse(message)
            if (cached != null) {
                emit("$urgencyPrefix$cached")
                return@flow
            }
        }

        if (!isOnline()) {
            emit(
                "${urgencyPrefix}📴 *Pas de connexion internet*\n\n" +
                    "ROLLY a besoin d'internet pour analyser vos donnees " +
                    "(glycemies, contexte clinique). Vos saisies restent " +
                    "enregistrees localement et seront synchronisees au retour " +
                    "du reseau."
            )
            return@flow
        }

        val contexte = buildContexte(patient, lecturesRecentes, latestHbA1c, hba1cEstimee)
        Log.d(TAG, "envoyerMessageAvecContexte (stream proxy): ${message.take(120)}")

        val accumulated = StringBuilder()
        rollyClient.streamText(
            mode = "chat_context",
            message = message,
            context = contexte,
            history = historiqueChat
        ).collect { acc ->
            accumulated.clear()
            accumulated.append(acc)
            emit("$urgencyPrefix$acc")
        }

        val finalResponse = accumulated.toString()
        if (finalResponse.isBlank()) {
            emit("${urgencyPrefix}Je n'ai pas pu générer de réponse.")
        } else if (isCacheableQuestion(message)) {
            cacheResponse(message, finalResponse)
        }
    }.catch { e ->
        Log.e(TAG, "Erreur envoyerMessageAvecContexte", e)
        if (!isOnline() || (e.message?.contains("Unable to resolve host") == true)) {
            emit(
                "📴 *Pas de connexion internet*\n\n" +
                    "Reessayez quand votre signal sera retabli."
            )
        } else {
            emit("❌ Erreur IA : ${friendlyError(e).message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANALYSE DE REPAS → JSON STRUCTURE (one-shot, JSON parsing serveur)
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun analyserRepasJson(
        descriptionRepas: String,
        patient: PatientEntity? = null
    ): String {
        val cacheKey = "repas:$descriptionRepas"
        val cached = getCachedResponse(cacheKey)
        if (cached != null) return cached

        val contextePatient = patient?.let {
            "Patient : ${it.nomComplet}, ${it.age} ans, Diabète ${it.typeDiabete.name.replace("_", " ")}"
        } ?: ""
        val userMessage = buildString {
            if (contextePatient.isNotBlank()) {
                appendLine(contextePatient)
                appendLine()
            }
            append("Analyse nutritionnelle du repas : \"$descriptionRepas\"")
        }

        // Essai primary puis fallback (gemini-2.5-flash → gemini-2.0-flash)
        val attempts = listOf(false, true)
        var lastException: Throwable? = null
        for ((index, useFallback) in attempts.withIndex()) {
            val result = rollyClient.sendJson(
                mode = "meal_json",
                message = userMessage,
                useFallback = useFallback
            )
            result.fold(
                onSuccess = { text ->
                    val mealHash = hashQuery(cacheKey)
                    aiCacheDao.insert(
                        AiCacheEntity(
                            queryHash = mealHash,
                            query = cacheKey.take(200),
                            response = text,
                            category = "meal",
                            expiresAt = System.currentTimeMillis() + 6 * 60 * 60 * 1000,
                            hmac = AiCacheEntity.computeHmac(mealHash, text, hmacKey)
                        )
                    )
                    return text
                },
                onFailure = { e ->
                    Log.e(TAG, "Erreur analyse repas (tentative ${index + 1})", e)
                    lastException = e
                    if (!isTransient(e) && index == 0) {
                        // erreur non transitoire dès le premier essai → on arrête
                        throw friendlyError(e)
                    }
                    if (index < attempts.lastIndex) {
                        kotlinx.coroutines.delay(1000)
                    }
                }
            )
        }
        throw friendlyError(lastException)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANALYSE GLYCEMIQUE — STREAMING
    // ─────────────────────────────────────────────────────────────────────────

    fun analyserGlycemieStream(
        patient: PatientEntity,
        lectures: List<LectureGlucoseEntity>,
        latestHbA1c: HbA1cEntity? = null,
        hba1cEstimee: Double? = null
    ): Flow<String> = flow {
        if (lectures.isEmpty()) {
            emit("Aucune lecture de glycémie disponible pour l'analyse.")
            return@flow
        }
        val contexte = buildContexte(patient, lectures, latestHbA1c, hba1cEstimee)
        var emitted = false
        rollyClient.streamText(
            mode = "glucose_analysis",
            message = "Analyse les tendances glycémiques fournies.",
            context = contexte
        ).collect { acc ->
            emitted = true
            emit(acc)
        }
        if (!emitted) emit("Analyse indisponible.")
    }.catch { e ->
        Log.e(TAG, "Erreur analyse glycemie stream", e)
        emit("Erreur lors de l'analyse : ${friendlyError(e).message}")
    }

    /** Non-streaming (one-shot). */
    suspend fun analyserGlycemie(
        patient: PatientEntity,
        lectures: List<LectureGlucoseEntity>,
        latestHbA1c: HbA1cEntity? = null,
        hba1cEstimee: Double? = null
    ): String {
        if (lectures.isEmpty()) return "Aucune lecture de glycémie disponible pour l'analyse."
        val contexte = buildContexte(patient, lectures, latestHbA1c, hba1cEstimee)
        return rollyClient.sendText(
            mode = "glucose_analysis",
            message = "Analyse les tendances glycémiques fournies.",
            context = contexte
        ).getOrElse { e ->
            Log.e(TAG, "Erreur analyse glycemie", e)
            "Erreur lors de l'analyse : ${friendlyError(e).message}"
        }.ifBlank { "Analyse indisponible." }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSEILS NUTRITIONNELS — STREAMING
    // ─────────────────────────────────────────────────────────────────────────

    fun conseilsNutritionnelsStream(
        patient: PatientEntity,
        derniereLecture: LectureGlucoseEntity?,
        latestHbA1c: HbA1cEntity? = null
    ): Flow<String> = flow {
        val contexte = buildNutritionContext(patient, derniereLecture, latestHbA1c)
        var emitted = false
        rollyClient.streamText(
            mode = "nutrition_advice",
            message = "Donne des conseils nutritionnels personnalisés.",
            context = contexte
        ).collect { acc ->
            emitted = true
            emit(acc)
        }
        if (!emitted) emit("Conseils indisponibles.")
    }.catch { e ->
        Log.e(TAG, "Erreur conseils nutritionnels stream", e)
        emit("Erreur : ${friendlyError(e).message}")
    }

    suspend fun conseilsNutritionnels(
        patient: PatientEntity,
        derniereLecture: LectureGlucoseEntity?,
        latestHbA1c: HbA1cEntity? = null
    ): String {
        val contexte = buildNutritionContext(patient, derniereLecture, latestHbA1c)
        return rollyClient.sendText(
            mode = "nutrition_advice",
            message = "Donne des conseils nutritionnels personnalisés.",
            context = contexte
        ).getOrElse { e ->
            Log.e(TAG, "Erreur conseils nutritionnels", e)
            "Erreur : ${friendlyError(e).message}"
        }.ifBlank { "Conseils indisponibles." }
    }

    private fun buildNutritionContext(
        patient: PatientEntity,
        derniereLecture: LectureGlucoseEntity?,
        latestHbA1c: HbA1cEntity?
    ): String {
        val typeD = patient.typeDiabete.name.replace("_", " ")
        val sb = StringBuilder()
        sb.appendLine("Patient : ${patient.nomComplet}, Diabète $typeD, ${patient.age} ans")
        derniereLecture?.let {
            sb.appendLine("Dernière glycémie : ${it.valeur.toInt()} mg/dL (${it.contexte.getDisplayName()})")
        } ?: sb.appendLine("Pas de lecture récente")
        latestHbA1c?.let {
            sb.appendLine("Dernière HbA1c : ${it.valeur}% (${it.getInterpretation().name.replace("_", " ").lowercase()})")
        }
        return sb.toString().trim()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PREVISION DE RISQUE — STREAMING
    // ─────────────────────────────────────────────────────────────────────────

    fun previsionRisqueStream(
        patient: PatientEntity,
        lectures: List<LectureGlucoseEntity>,
        latestHbA1c: HbA1cEntity? = null
    ): Flow<String> = flow {
        if (lectures.size < 3) {
            emit("Minimum 3 lectures nécessaires pour une prévision fiable.")
            return@flow
        }
        val contexte = buildContexte(patient, lectures, latestHbA1c, null)
        var emitted = false
        rollyClient.streamText(
            mode = "risk_prediction",
            message = "Évalue le risque métabolique sur les 6 prochaines heures.",
            context = contexte
        ).collect { acc ->
            emitted = true
            emit(acc)
        }
        if (!emitted) emit("Prévision indisponible.")
    }.catch { e ->
        Log.e(TAG, "Erreur prevision risque stream", e)
        emit("Erreur : ${friendlyError(e).message}")
    }

    suspend fun previsionRisque(
        patient: PatientEntity,
        lectures: List<LectureGlucoseEntity>,
        latestHbA1c: HbA1cEntity? = null
    ): String {
        if (lectures.size < 3) return "Minimum 3 lectures nécessaires pour une prévision fiable."
        val contexte = buildContexte(patient, lectures, latestHbA1c, null)
        return rollyClient.sendText(
            mode = "risk_prediction",
            message = "Évalue le risque métabolique sur les 6 prochaines heures.",
            context = contexte
        ).getOrElse { e ->
            Log.e(TAG, "Erreur prevision risque", e)
            "Erreur : ${friendlyError(e).message}"
        }.ifBlank { "Prévision indisponible." }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VISION — RECONNAISSANCE D'IMAGE DE REPAS
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun analyserRepasImage(
        bitmap: Bitmap,
        patient: PatientEntity? = null
    ): String {
        val contextePatient = patient?.let {
            "Patient : ${it.nomComplet}, ${it.age} ans, Diabète ${it.typeDiabete.name.replace("_", " ")}"
        } ?: ""
        val userMessage = buildString {
            if (contextePatient.isNotBlank()) {
                appendLine(contextePatient)
                appendLine()
            }
            append("Analyse cette photo de repas et estime la charge en glucides.")
        }

        val attempts = listOf(false, true)
        var lastException: Throwable? = null
        for ((index, useFallback) in attempts.withIndex()) {
            val result = rollyClient.sendJson(
                mode = "meal_image",
                message = userMessage,
                imageBitmap = bitmap,
                useFallback = useFallback
            )
            result.fold(
                onSuccess = { text -> return text },
                onFailure = { e ->
                    Log.e(TAG, "Erreur Vision repas (tentative ${index + 1})", e)
                    lastException = e
                    if (!isTransient(e) && index == 0) {
                        throw friendlyError(e)
                    }
                    if (index < attempts.lastIndex) {
                        kotlinx.coroutines.delay(1000)
                    }
                }
            )
        }
        throw friendlyError(lastException)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANALYSE PREDICTIVE 7 JOURS — STREAMING
    // ─────────────────────────────────────────────────────────────────────────

    fun analysePredictive7JoursStream(
        patient: PatientEntity,
        lectures: List<LectureGlucoseEntity>,
        latestHbA1c: HbA1cEntity? = null,
        hba1cEstimee: Double? = null
    ): Flow<String> = flow {
        if (lectures.isEmpty()) {
            emit("Aucune donnée glycémique disponible pour l'analyse prédictive.")
            return@flow
        }
        if (lectures.size < 5) {
            emit("Minimum 5 lectures nécessaires. Actuellement : ${lectures.size} lectures.")
            return@flow
        }
        val contexte = buildContexte(patient, lectures, latestHbA1c, hba1cEstimee)
        var emitted = false
        rollyClient.streamText(
            mode = "predictive_7days",
            message = "Analyse les 7 derniers jours et projette les tendances.",
            context = contexte
        ).collect { acc ->
            emitted = true
            emit(acc)
        }
        if (!emitted) emit("Analyse prédictive indisponible.")
    }.catch { e ->
        Log.e(TAG, "Erreur analyse predictive stream", e)
        emit("Erreur lors de l'analyse prédictive : ${friendlyError(e).message}")
    }

    suspend fun analysePredictive7Jours(
        patient: PatientEntity,
        lectures: List<LectureGlucoseEntity>,
        latestHbA1c: HbA1cEntity? = null,
        hba1cEstimee: Double? = null
    ): String {
        if (lectures.isEmpty()) return "Aucune donnée glycémique disponible."
        if (lectures.size < 5) return "Minimum 5 lectures nécessaires. Actuellement : ${lectures.size}."
        val contexte = buildContexte(patient, lectures, latestHbA1c, hba1cEstimee)
        return rollyClient.sendText(
            mode = "predictive_7days",
            message = "Analyse les 7 derniers jours et projette les tendances.",
            context = contexte
        ).getOrElse { e ->
            Log.e(TAG, "Erreur analyse predictive", e)
            "Erreur : ${friendlyError(e).message}"
        }.ifBlank { "Analyse prédictive indisponible." }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITAIRES
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reset chat session. v2.1.37+ : la session est gerée cote serveur a chaque
     * requete (stateless), donc cette methode est un no-op. Conservee pour
     * compatibilite avec les ViewModels existants.
     */
    suspend fun reinitialiserChat() {
        Log.d(TAG, "reinitialiserChat() — no-op (stateless proxy)")
    }

    private fun buildContexte(
        patient: PatientEntity?,
        lectures: List<LectureGlucoseEntity>,
        latestHbA1c: HbA1cEntity? = null,
        hba1cEstimee: Double? = null
    ): String {
        if (patient == null && lectures.isEmpty()) return ""
        val sb = StringBuilder()

        patient?.let {
            sb.appendLine("Patient : ${it.nomComplet}, ${it.age} ans, Sexe : ${it.sexe.name}")
            sb.appendLine("Type de diabète : ${it.typeDiabete.name.replace("_", " ")}")
            val metrics = mutableListOf<String>()
            it.poids?.let { p -> metrics.add("Poids : ${p}kg") }
            it.taille?.let { t -> metrics.add("Taille : ${t}cm") }
            it.imc?.let { imc -> metrics.add("IMC : ${"%.1f".format(imc)} kg/m² (${it.categorieImc})") }
            it.tourDeTaille?.let { tdt -> metrics.add("Tour de taille : ${tdt}cm (${it.risqueTourDeTaille})") }
            it.masseGrasse?.let { mg -> metrics.add("Masse grasse : ${mg}%") }
            if (metrics.isNotEmpty()) {
                sb.appendLine("Données corporelles : ${metrics.joinToString(" | ")}")
            }
        }

        latestHbA1c?.let {
            val source = if (it.estEstimation) "estimée" else "labo${if (it.laboratoire.isNotBlank()) " (${it.laboratoire})" else ""}"
            sb.appendLine("Dernière HbA1c : ${it.valeur}% ($source) — ${it.dateMesure}")
            sb.appendLine("  → Interprétation : ${it.getInterpretation().name.replace("_", " ")}")
            sb.appendLine("  → Glycémie moyenne estimée (eAG) : ${it.getGlycemieMoyenneEstimee().toInt()} mg/dL")
        }
        hba1cEstimee?.let {
            sb.appendLine("HbA1c estimée (30 derniers jours) : ${it}%")
        }

        if (lectures.isNotEmpty()) {
            val fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            sb.appendLine("Lectures glycémiques récentes (${lectures.size}) :")
            lectures.take(15).forEach { l ->
                sb.appendLine("  - ${l.dateHeure.format(fmt)} : ${l.valeur.toInt()} mg/dL (${l.contexte.getDisplayName()})")
            }
            val moyenne = lectures.map { it.valeur }.average()
            val dansLaCible = lectures.count { it.valeur in 70.0..180.0 }
            val hypos = lectures.count { it.valeur < 70 }
            val hypers = lectures.count { it.valeur > 180 }
            val tir = if (lectures.isNotEmpty()) (dansLaCible * 100 / lectures.size) else 0
            sb.appendLine("Moyenne : ${moyenne.toInt()} mg/dL | TIR : $tir% | Hypos : $hypos | Hypers : $hypers")
        }

        return sb.toString().trim()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ERREURS — messages user-friendly
    // ─────────────────────────────────────────────────────────────────────────

    private fun isTransient(e: Throwable?): Boolean {
        val msg = e?.message.orEmpty()
        return msg.contains("503") || msg.contains("UNAVAILABLE") ||
            msg.contains("high demand") || msg.contains("overloaded") ||
            msg.contains("502") || msg.contains("504") ||
            msg.contains("generation_failed")
    }

    private fun friendlyError(e: Throwable?): Exception {
        val msg = e?.message.orEmpty()
        return when {
            msg.contains("rate_limit_exceeded") ->
                Exception("Limite quotidienne ROLLY atteinte (200 requêtes/jour). Réessayez demain.")
            msg.contains("invalid_token") || msg.contains("401") ->
                Exception("Session expirée. Reconnectez-vous pour continuer à utiliser ROLLY.")
            msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand") || msg.contains("overloaded") ->
                Exception("Le service IA est temporairement surchargé. Veuillez réessayer dans quelques instants.")
            msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") ->
                Exception("Trop de requêtes envoyées. Veuillez patienter un moment avant de réessayer.")
            msg.contains("400") || msg.contains("INVALID_ARGUMENT") || msg.contains("image_too_large") ->
                Exception("L'image n'a pas pu être analysée. Essayez avec une photo plus nette ou plus petite.")
            msg.contains("403") || msg.contains("PERMISSION_DENIED") ->
                Exception("Accès au service IA refusé. Vérifiez la configuration de l'application.")
            msg.contains("network") || msg.contains("timeout") || msg.contains("connect") || msg.contains("Unable to resolve host") ->
                Exception("Erreur de connexion. Vérifiez votre accès Internet et réessayez.")
            // v2.1.71 : on expose le detail brut du serveur pour les erreurs
            // non reconnues (ex: generation_failed, gemini_not_configured, 500,
            // 502) — indispensable pour diagnostiquer, plutot qu'un message
            // opaque qui empeche tout debug.
            else -> Exception(
                "Erreur d'analyse IA." + if (msg.isNotBlank()) " Détail : ${msg.take(180)}" else " Veuillez réessayer."
            )
        }
    }
}
