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
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "ChatbotRepository"

/**
 * Repository pour les interactions avec Gemini AI (ROLLY).
 *
 * Architecture cloud-only depuis v2.1.31 :
 * - Le modele on-device (Gemma 3 1B via MediaPipe) a ete supprime pour
 *   reduire la taille de l'APK, la conso batterie et la complexite.
 * - Mode hors-ligne : ROLLY indique simplement "Pas de connexion" et
 *   propose de reessayer ; les donnees patient (glycemies, repas...)
 *   continuent d'etre saisies localement (Room) puis synchronisees au
 *   retour reseau via les repositories habituels.
 *
 * Cache local Room : evite les appels redondants pour questions generiques.
 */
@Singleton
class ChatbotRepository @Inject constructor(
    @Named("primary") private val geminiModel: GenerativeModel,
    @Named("fallback") private val fallbackModel: GenerativeModel,
    private val aiCacheDao: AiCacheDao,
    @ApplicationContext private val context: Context
) {
    private var chatSession = geminiModel.startChat()
    private val chatMutex = Mutex()

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

    /**
     * Détecte si une question est "générique" (cacheable) vs patient-spécifique.
     * Questions génériques : définitions, conseils généraux, symptômes
     * Questions spécifiques : celles avec données patient, glycémie perso, etc.
     */
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
     * Envoie un message et stream la réponse chunk par chunk.
     * Chaque emit contient le texte ACCUMULÉ (pas juste le delta).
     *
     * ROUTAGE HYBRIDE :
     * - En ligne  → Gemini cloud (streaming natif)
     * - Hors ligne → Gemma local (streaming simulé)
     */
    fun envoyerMessage(message: String): Flow<String> = flow {
        try {
            // ── DETECTION D'URGENCE (latence 0 ms) ──
            // Analyse locale AVANT l'appel LLM. Si symptomes d'urgence detectes,
            // on affiche immediatement les numeros SAMU/Police/Pompiers + conseils.
            // Le patient n'attend pas que le modele (Gemini ou Gemma) repondent.
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

            // ── MODE EN LIGNE : Gemini cloud ──
            Log.d(TAG, "envoyerMessage (stream): $message")
            val accumulated = StringBuilder()
            chatMutex.withLock {
                chatSession.sendMessageStream(message).collect { chunk ->
                    chunk.text?.let {
                        accumulated.append(it)
                        emit("$urgencyPrefix${accumulated}")
                    }
                }
            }
            if (accumulated.isEmpty()) {
                emit("${urgencyPrefix}Je n'ai pas pu générer de réponse.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur envoyerMessage", e)
            if (!isOnline() || e.message?.contains("Unable to resolve host") == true) {
                emit(
                    "📴 *Pas de connexion internet*\n\n" +
                        "Reessayez quand votre signal sera retabli. Vos donnees " +
                        "personnelles continuent d'etre enregistrees localement."
                )
            } else {
                emit("❌ Erreur IA : ${cleanGeminiException(e)}")
            }
        }
    }

    /**
     * Envoie un message avec contexte patient.
     * - Vérifie le cache local d'abord pour les questions génériques
     * - Stream la réponse en temps réel sinon
     *
     * ROUTAGE HYBRIDE :
     * - En ligne  → Gemini cloud (streaming natif + contexte complet)
     * - Hors ligne → Gemma local (contexte réduit, réponse basique)
     */
    fun envoyerMessageAvecContexte(
        message: String,
        patient: PatientEntity?,
        lecturesRecentes: List<LectureGlucoseEntity>,
        latestHbA1c: HbA1cEntity? = null,
        hba1cEstimee: Double? = null,
        historiqueChat: String = ""
    ): Flow<String> = flow {
        try {
            // ── DETECTION D'URGENCE (latence 0 ms) ──
            // Affichee IMMEDIATEMENT avant toute autre logique (cache, LLM)
            val urgencyPrefix = when {
                UrgencyDetector.detectUrgency(message) -> UrgencyDetector.getEmergencyResponse()
                UrgencyDetector.detectWarning(message) -> UrgencyDetector.getWarningResponse()
                else -> ""
            }
            if (urgencyPrefix.isNotEmpty()) {
                Log.d(TAG, "Urgency detected with context, showing emergency info first")
                emit(urgencyPrefix)
            }

            // Check cache for generic questions (no patient-specific data needed)
            if (isCacheableQuestion(message) && patient == null && lecturesRecentes.isEmpty()) {
                val cached = getCachedResponse(message)
                if (cached != null) {
                    emit("$urgencyPrefix$cached")
                    return@flow
                }
            }

            val contexte = buildContexte(patient, lecturesRecentes, latestHbA1c, hba1cEstimee)

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

            // ── MODE EN LIGNE : Gemini cloud ──
            val sb = StringBuilder()
            if (historiqueChat.isNotBlank()) {
                sb.appendLine(historiqueChat)
                sb.appendLine()
            }
            if (contexte.isNotBlank()) {
                sb.appendLine("Données patient :")
                sb.appendLine(contexte)
                sb.appendLine()
            }
            sb.appendLine("Question : $message")
            val messageComplet = sb.toString().trim()
            Log.d(TAG, "envoyerMessageAvecContexte (stream): ${messageComplet.take(300)}")

            val accumulated = StringBuilder()
            chatMutex.withLock {
                chatSession.sendMessageStream(messageComplet).collect { chunk ->
                    chunk.text?.let {
                        accumulated.append(it)
                        emit("$urgencyPrefix${accumulated}")
                    }
                }
            }

            val finalResponse = accumulated.toString()
            if (finalResponse.isBlank()) {
                emit("${urgencyPrefix}Je n'ai pas pu générer de réponse.")
            } else {
                // Cache generic responses for future use (sans prefix urgence)
                if (isCacheableQuestion(message)) {
                    cacheResponse(message, finalResponse)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur envoyerMessageAvecContexte", e)
            if (!isOnline() || e.message?.contains("Unable to resolve host") == true) {
                emit(
                    "📴 *Pas de connexion internet*\n\n" +
                        "Reessayez quand votre signal sera retabli."
                )
            } else {
                emit("❌ Erreur IA : ${cleanGeminiException(e)}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANALYSE DE REPAS → JSON STRUCTURÉ (streaming not used — needs full JSON)
    // Cache : repas identiques retournent le même résultat
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun analyserRepasJson(
        descriptionRepas: String,
        patient: PatientEntity? = null
    ): String {
        // Check cache for identical meal descriptions
        val cacheKey = "repas:$descriptionRepas"
        val cached = getCachedResponse(cacheKey)
        if (cached != null) return cached

        val contextePatient = patient?.let {
            "Patient : ${it.nomComplet}, ${it.age} ans, Diabète ${it.typeDiabete.name.replace("_", " ")}"
        } ?: ""

        val prompt = """
            $contextePatient

            Analyse nutritionnelle du repas suivant pour un patient diabétique :
            "$descriptionRepas"

            CONSIGNES STRICTES :
            - Réponds UNIQUEMENT avec un objet JSON valide. Aucun texte avant ni après. Pas de balises markdown.
            - Toutes les valeurs nutritionnelles sont des ESTIMATIONS basées sur des portions standards. Ne les présente pas comme exactes.
            - Si un aliment est ambigu ou non identifiable, utilise la variante la plus courante et indique-le dans "description".

            Schéma JSON OBLIGATOIRE :
            {
              "nom_repas": "Nom court du repas",
              "description": "Ingrédients identifiés et hypothèses de portions",
              "glucides_estimes": 45.0,
              "index_glycemique": 55,
              "charge_glycemique": 24.8,
              "calories_estimees": 450,
              "proteines_estimees": 25.0,
              "lipides_estimes": 12.0,
              "fibres_estimees": 6.0,
              "categorie_ig": "moyen",
              "impact_glycemique": "Impact attendu sur la glycémie post-prandiale",
              "recommandations": ["conseil 1", "conseil 2"],
              "alternatives_saines": ["alternative 1", "alternative 2"],
              "score_diabete": 65
            }

            Règles de calcul :
            - glucides_estimes : grammes (décimal)
            - index_glycemique : 0-100 (entier), basé sur les tables IG reconnues
            - charge_glycemique : glucides × IG / 100 (décimal)
            - categorie_ig : "bas" (≤55), "moyen" (56-69), "eleve" (≥70)
            - score_diabete : 0 = très défavorable, 100 = excellent pour un diabétique
            - recommandations : 2-3 conseils concrets et actionnables
            - alternatives_saines : 1-2 substitutions à IG plus bas
        """.trimIndent()

        // Try primary model (gemini-2.5-flash), then fallback (gemini-2.0-flash)
        val models = listOf(geminiModel, fallbackModel)
        var lastException: Exception? = null

        for ((index, model) in models.withIndex()) {
            try {
                val response = model.generateContent(prompt)
                val text = response.text ?: throw Exception("Réponse vide de Gemini")
                // Cache this meal analysis (6h TTL for meals)
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
            } catch (e: Exception) {
                Log.e(TAG, "Erreur analyse repas (modèle ${index + 1})", e)
                lastException = e
                val msg = e.message.orEmpty()
                val isTransient = msg.contains("503") || msg.contains("UNAVAILABLE") ||
                        msg.contains("high demand") || msg.contains("overloaded") ||
                        msg.contains("details")
                if (isTransient && index < models.lastIndex) {
                    Log.w(TAG, "Basculement vers le modèle de secours...")
                    kotlinx.coroutines.delay(1000)
                } else if (!isTransient) {
                    break
                }
            }
        }

        throw cleanGeminiException(lastException)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANALYSE GLYCÉMIQUE — STREAMING
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
        val prompt = """
            $contexte

            ANALYSE GLYCÉMIQUE — Réponds de manière concise et structurée.

            1. **Contrôle glycémique** : Temps dans la cible (70-180 mg/dL), variabilité, moyenne vs objectif.
            2. **Tendances** : Patterns hypo/hyperglycémiques identifiés (heures, contextes). Base-toi UNIQUEMENT sur les données ci-dessus.
            3. **Corrélation HbA1c** : Si HbA1c disponible, compare avec la glycémie moyenne observée. Cohérence ? Écart ?
            4. **Recommandations** : 3-4 actions concrètes et mesurables pour améliorer le contrôle.
            5. **Alertes** : Risques immédiats identifiés dans les données.

            IMPORTANT :
            - N'invente AUCUNE donnée. Analyse UNIQUEMENT ce qui est fourni.
            - Si les données sont insuffisantes pour un point, indique-le.
            - Ton professionnel. Maximum 250 mots.
            - Termine par : "Avis informatif — consultez votre médecin."
        """.trimIndent()

        try {
            val accumulated = StringBuilder()
            geminiModel.generateContentStream(prompt).collect { chunk ->
                chunk.text?.let {
                    accumulated.append(it)
                    emit(accumulated.toString())
                }
            }
            if (accumulated.isEmpty()) emit("Analyse indisponible.")
        } catch (e: Exception) {
            emit("Erreur lors de l'analyse : ${e.message}")
        }
    }

    /** Non-streaming version (backward compat) */
    suspend fun analyserGlycemie(
        patient: PatientEntity,
        lectures: List<LectureGlucoseEntity>,
        latestHbA1c: HbA1cEntity? = null,
        hba1cEstimee: Double? = null
    ): String {
        if (lectures.isEmpty()) return "Aucune lecture de glycémie disponible pour l'analyse."

        val contexte = buildContexte(patient, lectures, latestHbA1c, hba1cEstimee)
        val prompt = """
            $contexte

            ANALYSE GLYCÉMIQUE — Réponds de manière concise et structurée.

            1. **Contrôle glycémique** : Temps dans la cible (70-180 mg/dL), variabilité, moyenne vs objectif.
            2. **Tendances** : Patterns hypo/hyperglycémiques identifiés (heures, contextes). Base-toi UNIQUEMENT sur les données ci-dessus.
            3. **Corrélation HbA1c** : Si HbA1c disponible, compare avec la glycémie moyenne observée. Cohérence ? Écart ?
            4. **Recommandations** : 3-4 actions concrètes et mesurables pour améliorer le contrôle.
            5. **Alertes** : Risques immédiats identifiés dans les données.

            IMPORTANT :
            - N'invente AUCUNE donnée. Analyse UNIQUEMENT ce qui est fourni.
            - Si les données sont insuffisantes pour un point, indique-le.
            - Ton professionnel. Maximum 250 mots.
            - Termine par : "Avis informatif — consultez votre médecin."
        """.trimIndent()

        return try {
            val response = geminiModel.generateContent(prompt)
            response.text ?: "Analyse indisponible."
        } catch (e: Exception) {
            "Erreur lors de l'analyse : ${e.message}"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSEILS NUTRITIONNELS — STREAMING
    // ─────────────────────────────────────────────────────────────────────────

    fun conseilsNutritionnelsStream(
        patient: PatientEntity,
        derniereLecture: LectureGlucoseEntity?,
        latestHbA1c: HbA1cEntity? = null
    ): Flow<String> = flow {
        val typeD = patient.typeDiabete.name.replace("_", " ")
        val glycemie = derniereLecture?.let {
            "Dernière glycémie : ${it.valeur.toInt()} mg/dL (${it.contexte.getDisplayName()})"
        } ?: "Pas de lecture récente"
        val hba1cInfo = latestHbA1c?.let {
            "Dernière HbA1c : ${it.valeur}% (${it.getInterpretation().name.replace("_", " ").lowercase()})"
        } ?: ""

        val prompt = """
            Patient : ${patient.nomComplet}, Diabète $typeD, ${patient.age} ans
            $glycemie
            $hba1cInfo

            CONSEILS NUTRITIONNELS — Concis et actionnables.

            1. **Aliments recommandés** : 5-6 aliments à IG bas, adaptés au diabète $typeD
            2. **Aliments à limiter** : 4-5 aliments à éviter ou réduire, avec alternatives
            3. **Exemple de repas** : 1 journée type (petit-déjeuner, déjeuner, dîner) adaptée
            4. **Collations** : 2-3 options pour prévenir l'hypoglycémie
            5. **Hydratation** : recommandations pratiques

            RÈGLES :
            - Conseils basés sur les recommandations ADA/HAS pour le diabète $typeD.
            - Si la glycémie actuelle est hors cible, adapte les conseils en conséquence.
            - Ne recommande AUCUN complément alimentaire ou produit commercial spécifique.
            - Maximum 200 mots. Ton professionnel.
            - Termine par : "Avis informatif — consultez votre médecin/diététicien."
        """.trimIndent()

        try {
            val accumulated = StringBuilder()
            geminiModel.generateContentStream(prompt).collect { chunk ->
                chunk.text?.let {
                    accumulated.append(it)
                    emit(accumulated.toString())
                }
            }
            if (accumulated.isEmpty()) emit("Conseils indisponibles.")
        } catch (e: Exception) {
            emit("Erreur : ${e.message}")
        }
    }

    suspend fun conseilsNutritionnels(
        patient: PatientEntity,
        derniereLecture: LectureGlucoseEntity?,
        latestHbA1c: HbA1cEntity? = null
    ): String {
        val typeD = patient.typeDiabete.name.replace("_", " ")
        val glycemie = derniereLecture?.let {
            "Dernière glycémie : ${it.valeur.toInt()} mg/dL (${it.contexte.getDisplayName()})"
        } ?: "Pas de lecture récente"
        val hba1cInfo = latestHbA1c?.let {
            "Dernière HbA1c : ${it.valeur}% (${it.getInterpretation().name.replace("_", " ").lowercase()})"
        } ?: ""

        val prompt = """
            Patient : ${patient.nomComplet}, Diabète $typeD, ${patient.age} ans
            $glycemie
            $hba1cInfo

            CONSEILS NUTRITIONNELS — Concis et actionnables.

            1. **Aliments recommandés** : 5-6 aliments à IG bas, adaptés au diabète $typeD
            2. **Aliments à limiter** : 4-5 aliments à éviter ou réduire, avec alternatives
            3. **Exemple de repas** : 1 journée type adaptée
            4. **Collations** : 2-3 options
            5. **Hydratation** : recommandations

            Maximum 200 mots. Termine par : "Avis informatif — consultez votre médecin/diététicien."
        """.trimIndent()

        return try {
            val response = geminiModel.generateContent(prompt)
            response.text ?: "Conseils indisponibles."
        } catch (e: Exception) {
            "Erreur : ${e.message}"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRÉDICTION DE RISQUE — STREAMING
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
        val prompt = """
            $contexte

            PRÉVISION DE RISQUE GLYCÉMIQUE — Analyse basée STRICTEMENT sur les données ci-dessus.

            1. **Risque hypoglycémie** (prochaines 6h) : Faible / Modéré / Élevé
            2. **Risque hyperglycémie** (prochaines 6h) : Faible / Modéré / Élevé
            3. **Signaux d'alerte** : Quels symptômes surveiller
            4. **Actions préventives** : 2-3 mesures concrètes
            5. **Consultation urgente** : Dans quels cas appeler le 15/SAMU

            RÈGLES STRICTES :
            - Base-toi UNIQUEMENT sur les tendances observées.
            - N'invente AUCUN pattern non visible dans les données.
            - Maximum 200 mots. Ton professionnel.
        """.trimIndent()

        try {
            val accumulated = StringBuilder()
            geminiModel.generateContentStream(prompt).collect { chunk ->
                chunk.text?.let {
                    accumulated.append(it)
                    emit(accumulated.toString())
                }
            }
            if (accumulated.isEmpty()) emit("Prévision indisponible.")
        } catch (e: Exception) {
            emit("Erreur : ${e.message}")
        }
    }

    suspend fun previsionRisque(
        patient: PatientEntity,
        lectures: List<LectureGlucoseEntity>,
        latestHbA1c: HbA1cEntity? = null
    ): String {
        if (lectures.size < 3) return "Minimum 3 lectures nécessaires pour une prévision fiable."
        val contexte = buildContexte(patient, lectures, latestHbA1c, null)
        val prompt = """
            $contexte
            PRÉVISION DE RISQUE GLYCÉMIQUE — Analyse basée STRICTEMENT sur les données ci-dessus.
            1. Risque hypoglycémie (prochaines 6h) 2. Risque hyperglycémie (prochaines 6h)
            3. Signaux d'alerte 4. Actions préventives 5. Consultation urgente
            Maximum 200 mots.
        """.trimIndent()

        return try {
            val response = geminiModel.generateContent(prompt)
            response.text ?: "Prévision indisponible."
        } catch (e: Exception) {
            "Erreur : ${e.message}"
        }
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

        val prompt = """
            $contextePatient

            Tu es un expert en nutrition spécialisé dans le diabète.
            Analyse cette image de repas. Identifie TOUS les aliments visibles, estime les portions et la charge en glucides.

            CONSIGNES STRICTES :
            - Réponds UNIQUEMENT avec un objet JSON valide. Aucun texte avant ni après. Pas de balises markdown.
            - Identifie chaque aliment visible dans l'image et estime les portions.

            Schéma JSON OBLIGATOIRE :
            {
              "nom_repas": "Nom descriptif du repas identifié",
              "description": "Aliments identifiés dans l'image avec portions estimées",
              "glucides_estimes": 45.0,
              "index_glycemique": 55,
              "charge_glycemique": 24.8,
              "calories_estimees": 450,
              "proteines_estimees": 25.0,
              "lipides_estimes": 12.0,
              "fibres_estimees": 6.0,
              "categorie_ig": "moyen",
              "impact_glycemique": "Impact attendu sur la glycémie post-prandiale",
              "recommandations": ["conseil 1", "conseil 2"],
              "alternatives_saines": ["alternative 1", "alternative 2"],
              "score_diabete": 65
            }

            Règles de calcul :
            - index_glycemique : 0-100, basé sur les tables IG reconnues
            - charge_glycemique : glucides × IG / 100
            - categorie_ig : "bas" (≤55), "moyen" (56-69), "eleve" (≥70)
            - score_diabete : 0 = très défavorable, 100 = excellent pour un diabétique
        """.trimIndent()

        // Try primary model (gemini-2.5-flash), then fallback (gemini-2.0-flash)
        val models = listOf(geminiModel, fallbackModel)
        var lastException: Exception? = null

        for ((index, model) in models.withIndex()) {
            try {
                val inputContent = content {
                    image(bitmap)
                    text(prompt)
                }
                val response = model.generateContent(inputContent)
                return response.text ?: throw Exception("Réponse vide de Gemini Vision")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur Vision repas (modèle ${index + 1})", e)
                lastException = e
                val msg = e.message.orEmpty()
                val isTransient = msg.contains("503") || msg.contains("UNAVAILABLE") ||
                        msg.contains("high demand") || msg.contains("overloaded") ||
                        msg.contains("details")
                if (isTransient && index < models.lastIndex) {
                    Log.w(TAG, "Basculement vers le modèle de secours...")
                    kotlinx.coroutines.delay(1000)
                } else if (!isTransient) {
                    break
                }
            }
        }

        throw cleanGeminiException(lastException)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANALYSE PRÉDICTIVE 7 JOURS — STREAMING
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
        val prompt = """
            $contexte

            ANALYSE PRÉDICTIVE DES TENDANCES GLYCÉMIQUES — 7 DERNIERS JOURS

            ## 📊 Résumé statistique
            ## 📈 Tendances identifiées
            ## ⚠️ Risques prédictifs (prochaines 24-48h)
            ## 🎯 Recommandations préventives
            ## 🔮 Projection

            RÈGLES : N'invente AUCUNE donnée. Ton professionnel.
            Termine par : "Avis informatif — consultez votre médecin."
        """.trimIndent()

        try {
            val accumulated = StringBuilder()
            geminiModel.generateContentStream(prompt).collect { chunk ->
                chunk.text?.let {
                    accumulated.append(it)
                    emit(accumulated.toString())
                }
            }
            if (accumulated.isEmpty()) emit("Analyse prédictive indisponible.")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur analyse prédictive stream", e)
            emit("Erreur lors de l'analyse prédictive : ${e.message}")
        }
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
        val prompt = """
            $contexte
            ANALYSE PRÉDICTIVE — 7 JOURS. Résumé, tendances, risques, recommandations, projection.
            Ton professionnel. Termine par : "Avis informatif — consultez votre médecin."
        """.trimIndent()
        return try {
            val response = geminiModel.generateContent(prompt)
            response.text ?: "Analyse prédictive indisponible."
        } catch (e: Exception) {
            "Erreur : ${e.message}"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITAIRES
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun reinitialiserChat() {
        chatMutex.withLock {
            chatSession = geminiModel.startChat()
        }
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
    // HELPER — Clean Gemini API exceptions into user-friendly messages
    // ─────────────────────────────────────────────────────────────────────────

    private fun cleanGeminiException(e: Exception?): Exception {
        val msg = e?.message.orEmpty()
        return when {
            msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand") || msg.contains("overloaded") ->
                Exception("Le service IA est temporairement surchargé. Veuillez réessayer dans quelques instants.")
            msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") ->
                Exception("Trop de requêtes envoyées. Veuillez patienter un moment avant de réessayer.")
            msg.contains("400") || msg.contains("INVALID_ARGUMENT") ->
                Exception("L'image n'a pas pu être analysée. Essayez avec une photo plus nette.")
            msg.contains("403") || msg.contains("PERMISSION_DENIED") ->
                Exception("Accès au service IA refusé. Vérifiez la configuration de l'application.")
            msg.contains("network") || msg.contains("timeout") || msg.contains("connect") ->
                Exception("Erreur de connexion. Vérifiez votre accès Internet et réessayez.")
            else -> Exception("Erreur d'analyse IA. Veuillez réessayer.")
        }
    }
}
