package com.diabeto.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocalAIManager"
private const val MODEL_FILENAME = "gemma3-1b-it-int4.task"
private const val MODEL_SIZE_MB = 550 // Taille approximative du modele

// URL publique HTTPS (hebergement GitHub Release - gratuit, bande passante illimitee)
// Ce asset est publie avec chaque release. Pour changer de version du modele,
// mettre a jour cette URL pour pointer vers le nouveau release.
private const val MODEL_URL =
    "https://github.com/theongos/diasmart-mobile/releases/download/v2.1.1/gemma3-1b-it-int4.task"

/**
 * Gestionnaire d'IA locale (on-device) pour DiaSmart.AI
 *
 * Utilise MediaPipe LLM Inference pour executer Gemma 3 1B directement
 * sur l'appareil Android, sans connexion internet.
 *
 * Architecture hybride :
 * - En ligne  -> Gemini 2.5 Flash (cloud, puissant)
 * - Hors ligne -> Gemma 3 1B (local, leger, basique)
 */
@Singleton
class LocalAIManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var llmInference: LlmInference? = null
    private var isInitialized = false
    private var initError: String? = null

    // Prompt systeme MINIMAL pour vitesse maximale (Gemma 3 1B).
    // v2.1.5 : reduit de ~90 -> ~25 tokens pour accelerer le prefill.
    // La detection de langue multilingue etait un leurre sur 1B, on reste FR.
    private val systemPrompt = """ROLLY, assistant diabete. Reponds court en francais. Pas de diagnostic. Urgence si glycemie<0.70 ou >3.0 g/L."""

    // ─────────────────────────────────────────────────────────────────
    // CONNECTIVITE
    // ─────────────────────────────────────────────────────────────────

    /**
     * Verifie si l'appareil a une connexion internet active.
     */
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // ─────────────────────────────────────────────────────────────────
    // GESTION DU MODELE LOCAL
    // ─────────────────────────────────────────────────────────────────

    /**
     * Verifie si le modele Gemma est telecharge localement.
     */
    fun isModelDownloaded(): Boolean {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        return modelFile.exists() && modelFile.length() > 1_000_000 // > 1 Mo
    }

    /**
     * Retourne le chemin du fichier modele.
     */
    private fun getModelPath(): String {
        return File(context.filesDir, MODEL_FILENAME).absolutePath
    }

    /**
     * Retourne la taille estimee du modele en Mo.
     */
    fun getModelSizeMB(): Int = MODEL_SIZE_MB

    /**
     * Retourne le statut d'initialisation du modele local.
     */
    fun getStatus(): LocalAIStatus {
        return when {
            !isModelDownloaded() -> LocalAIStatus.NOT_DOWNLOADED
            !isInitialized && initError != null -> LocalAIStatus.ERROR
            !isInitialized -> LocalAIStatus.DOWNLOADED
            else -> LocalAIStatus.READY
        }
    }

    /**
     * Initialise le modele local Gemma.
     * Appeler une seule fois, idealement au demarrage de l'app.
     */
    suspend fun initializeModel(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized && llmInference != null) return@withContext true
        if (!isModelDownloaded()) {
            initError = "Modele non telecharge"
            Log.w(TAG, "Model not downloaded at: ${getModelPath()}")
            return@withContext false
        }

        // v2.1.8 : CPU-FIRST pour fiabilite.
        // Les pilotes GPU (Adreno/Mali) sur les devices africains d'entree de gamme
        // peuvent creer des hangs silencieux ou des init qui "reussissent" mais
        // ne generent jamais de tokens. CPU est plus lent mais toujours repondant.
        // GPU reste en fallback si CPU echoue (rare).
        val backendsToTry = listOf(
            LlmInference.Backend.CPU to "CPU",
            LlmInference.Backend.GPU to "GPU"
        )
        for ((backend, label) in backendsToTry) {
            try {
                Log.d(TAG, "Initializing local Gemma model on $label backend...")
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(getModelPath())
                    .setMaxTokens(256)
                    .setPreferredBackend(backend)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                isInitialized = true
                initError = null
                Log.d(TAG, "Local Gemma model initialized successfully on $label")
                return@withContext true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to init Gemma on $label backend: ${e.message}")
                initError = e.message
                isInitialized = false
                // On continue sur le backend suivant (CPU si GPU a echoue)
            }
        }
        Log.e(TAG, "All backends failed to initialize Gemma. Last error: $initError")
        false
    }

    /**
     * Pre-chauffe le modele en arriere-plan. A appeler au demarrage de l'app
     * (ou au login) pour que la 1ere question de l'utilisateur soit instantanee
     * au lieu d'attendre 5-15s d'initialisation MediaPipe.
     */
    suspend fun preloadIfAvailable() {
        if (isInitialized) return
        if (!isModelDownloaded()) return
        try {
            Log.d(TAG, "Preloading Gemma model in background...")
            initializeModel()
        } catch (e: Exception) {
            Log.w(TAG, "Preload failed (non-fatal)", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // TELECHARGEMENT DU MODELE DEPUIS GITHUB RELEASE (HTTPS public)
    // ─────────────────────────────────────────────────────────────────

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    /**
     * Telecharge le modele Gemma depuis GitHub Release (HTTPS public).
     *
     * Pourquoi GitHub Release plutot que Firebase Storage ?
     * - Firebase Storage necessite le plan Blaze (payant) depuis 2024
     * - GitHub Release = gratuit, bande passante illimitee, CDN Fastly
     * - Limite 2 Go par asset (Gemma fait 550 Mo, OK)
     *
     * URL : $MODEL_URL
     * Destination : context.filesDir/gemma3-1b-it-int4.task
     *
     * Gere les redirections (GitHub -> CDN) via instanceFollowRedirects.
     */
    suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        if (isModelDownloaded()) {
            Log.d(TAG, "Model already downloaded")
            return@withContext true
        }
        if (_isDownloading.value) {
            Log.w(TAG, "Download already in progress")
            return@withContext false
        }

        _isDownloading.value = true
        _downloadProgress.value = 0f
        _downloadError.value = null

        val destFile = File(context.filesDir, MODEL_FILENAME)
        val tempFile = File(context.filesDir, "${MODEL_FILENAME}.tmp")

        try {
            Log.d(TAG, "Starting model download from $MODEL_URL")

            val url = URL(MODEL_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true // GitHub redirige vers CDN Fastly
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "DiaSmart-Android")
                setRequestProperty("Accept", "application/octet-stream")
            }

            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw Exception("HTTP $responseCode : ${connection.responseMessage}")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
                ?: (MODEL_SIZE_MB * 1024L * 1024L)
            Log.d(TAG, "Model size on server: ${totalBytes / 1024 / 1024} MB")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024) // 64 Ko par chunk
                    var totalRead = 0L
                    var bytesRead: Int
                    var lastLoggedMB = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        _downloadProgress.value =
                            (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

                        // Log toutes les 25 Mo pour ne pas spammer
                        val currentMB = totalRead / (25 * 1024 * 1024)
                        if (currentMB > lastLoggedMB) {
                            Log.d(
                                TAG,
                                "Download: ${(_downloadProgress.value * 100).toInt()}% (${totalRead / 1024 / 1024} MB)"
                            )
                            lastLoggedMB = currentMB
                        }
                    }
                }
            }

            connection.disconnect()

            // Verifier taille (un 404 HTML ferait ~1-5 Ko -> a rejeter)
            val downloadedSize = tempFile.length()
            if (downloadedSize < 100 * 1024 * 1024) { // < 100 Mo
                tempFile.delete()
                throw Exception(
                    "Fichier telecharge trop petit (${downloadedSize / 1024} Ko). " +
                            "Le serveur a peut-etre retourne une erreur HTML."
                )
            }

            // Renommer temp -> destination
            if (tempFile.renameTo(destFile)) {
                Log.d(TAG, "Model downloaded successfully: ${destFile.length() / 1024 / 1024} MB")
                _downloadProgress.value = 1f
                _isDownloading.value = false
                true
            } else {
                throw Exception("Renommage du fichier temporaire echoue")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model", e)
            _downloadError.value = e.message ?: "Erreur de telechargement"
            _isDownloading.value = false
            _downloadProgress.value = 0f
            tempFile.delete()
            false
        }
    }

    /**
     * Supprime le modele local pour liberer l'espace.
     */
    fun deleteModel() {
        release()
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        if (modelFile.exists()) {
            modelFile.delete()
            Log.d(TAG, "Local model deleted")
        }
    }

    /**
     * Libere les ressources du modele local.
     */
    fun release() {
        try {
            llmInference?.close()
            llmInference = null
            isInitialized = false
            Log.d(TAG, "Local model released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing local model", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // GENERATION DE REPONSES (HORS LIGNE)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Genere une reponse locale (non-streaming).
     *
     * v2.1.9 : utilise EN INTERNE le path async (generateResponseAsync) car
     * generateResponse() est un appel JNI bloquant. withTimeoutOrNull autour
     * d'un appel JNI bloquant ne peut PAS l'interrompre (coroutines cooperative
     * cancellation only). En routant via CompletableDeferred.await(), on obtient
     * un vrai timeout cooperatif.
     */
    suspend fun generateResponse(prompt: String): String {
        if (!isModelDownloaded()) {
            return "Le modele IA local n'est pas installe. " +
                    "Allez dans Parametres > Mode hors-ligne pour le telecharger (~550 Mo)."
        }
        if (!isInitialized || llmInference == null) {
            if (!initializeModel()) {
                return "Impossible de charger le modele local (${initError ?: "raison inconnue"})."
            }
        }
        val fullPrompt = "$systemPrompt\nQ: $prompt\nROLLY:"
        return generateAsyncWithTimeout(fullPrompt)
    }

    /**
     * Genere une reponse locale avec contexte patient.
     *
     * v2.1.9 : meme path async que generateResponse(). De plus, sur device
     * tres faible (detecte via timeout first-token), on drop le contexte
     * patient et l'historique, car le prefill explose le temps de reponse
     * sur CPU d'entree de gamme (Kirin 710, MT6761, etc).
     */
    suspend fun generateResponseWithContext(
        message: String,
        patientContext: String = "",
        historiqueChat: String = ""
    ): String {
        if (!isModelDownloaded()) {
            return "Le modele IA local n'est pas installe. " +
                    "Allez dans Parametres > Mode hors-ligne pour le telecharger (~550 Mo)."
        }
        if (!isInitialized || llmInference == null) {
            if (!initializeModel()) {
                return "Impossible de charger le modele local (${initError ?: "raison inconnue"})."
            }
        }

        // v2.1.9 : PREFILL MINIMAL pour devices faibles (Huawei Y9 Prime 2019, Tecno Spark, etc.)
        // Chaque token de contexte = ~100-500 ms de prefill sur Kirin 710F.
        // On limite agressivement a 80 chars de contexte patient + 2 lignes d'historique.
        val sb = StringBuilder()
        sb.appendLine(systemPrompt)
        sb.appendLine()
        if (patientContext.isNotBlank()) {
            sb.appendLine("Patient: ${patientContext.take(80)}")
        }
        if (historiqueChat.isNotBlank()) {
            val recentHistory = historiqueChat.lines().takeLast(2).joinToString(" ")
            sb.appendLine("Hist: ${recentHistory.take(120)}")
        }
        sb.appendLine("Q: $message")
        sb.appendLine("ROLLY:")

        return generateAsyncWithTimeout(sb.toString())
    }

    /**
     * Helper interne : route TOUTES les generations via generateResponseAsync
     * pour beneficier du timeout cooperatif (CompletableDeferred.await()).
     *
     * - first-token timeout 45s : si aucun token n'arrive, l'appareil est
     *   trop lent ou MediaPipe est hung -> on bail avec un message clair.
     * - total timeout 120s : garde-fou global.
     */
    private suspend fun generateAsyncWithTimeout(fullPrompt: String): String = withContext(Dispatchers.IO) {
        try {
            val accumulated = StringBuilder()
            val firstToken = CompletableDeferred<Unit>()
            val completed = CompletableDeferred<Unit>()

            llmInference?.generateResponseAsync(fullPrompt) { partialResult, done ->
                if (!partialResult.isNullOrEmpty()) {
                    if (!firstToken.isCompleted) firstToken.complete(Unit)
                    accumulated.append(partialResult)
                }
                if (done) {
                    if (!firstToken.isCompleted) firstToken.complete(Unit)
                    completed.complete(Unit)
                }
            }

            // Phase 1 : attendre le premier token (max 45s)
            val firstOk = withTimeoutOrNull(45_000L) {
                firstToken.await()
                true
            }
            if (firstOk == null) {
                Log.w(TAG, "Local AI: first-token timeout after 45s (device too slow)")
                return@withContext "L'IA locale prend trop de temps sur cet appareil " +
                        "(aucun token en 45s). Essayez une question plus courte, ou " +
                        "connectez-vous a internet pour utiliser ROLLY en ligne."
            }

            // Phase 2 : attendre la fin complete (max 120s apres le premier token)
            val allOk = withTimeoutOrNull(120_000L) {
                completed.await()
                true
            }
            when {
                allOk == null && accumulated.isNotEmpty() ->
                    accumulated.toString() + "\n\n[Generation interrompue - delai depasse]"
                allOk == null ->
                    "Le modele local met trop de temps a repondre. Essayez en ligne."
                accumulated.isBlank() ->
                    "Je n'ai pas pu generer de reponse en mode hors-ligne."
                else -> accumulated.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateAsyncWithTimeout", e)
            "Erreur du modele local : ${e.message}"
        }
    }

    /**
     * Genere une reponse locale en VRAI streaming (MediaPipe generateResponseAsync).
     *
     * OPTIMISATION VITESSE PERCUE :
     * Avant : on attendait la reponse complete, puis on simulait le streaming.
     *         -> l'utilisateur voyait "..." pendant 5-15s
     * Apres : chaque token est affiche des qu'il est genere par le modele.
     *         -> l'utilisateur voit les mots apparaitre immediatement (UX x10)
     */
    fun generateResponseStream(prompt: String): Flow<String> = channelFlow {
        // v2.1.8 : message explicite si modele absent (au lieu du texte generique)
        if (!isModelDownloaded()) {
            send(
                "Le modele IA local n'est pas installe sur cet appareil.\n\n" +
                "Pour discuter avec ROLLY hors-ligne, allez dans " +
                "Parametres > Mode hors-ligne et telechargez le modele (~550 Mo, WiFi recommande)."
            )
            return@channelFlow
        }
        if (!isInitialized || llmInference == null) {
            if (!initializeModel()) {
                send(
                    "Impossible de charger le modele local (${initError ?: "raison inconnue"}). " +
                    "Essayez de redemarrer l'application ou de retelecharger le modele " +
                    "depuis Parametres > Mode hors-ligne."
                )
                return@channelFlow
            }
        }

        try {
            val fullPrompt = "$systemPrompt\nQ: $prompt\nROLLY:"
            val accumulated = StringBuilder()
            val firstToken = CompletableDeferred<Unit>()
            val completed = CompletableDeferred<Unit>()

            // MediaPipe streaming natif : callback appele avec chaque token
            llmInference?.generateResponseAsync(fullPrompt) { partialResult, done ->
                if (!partialResult.isNullOrEmpty()) {
                    if (!firstToken.isCompleted) firstToken.complete(Unit)
                    accumulated.append(partialResult)
                    trySend(accumulated.toString())
                }
                if (done) {
                    if (!firstToken.isCompleted) firstToken.complete(Unit)
                    if (accumulated.isEmpty()) {
                        trySend("Je n'ai pas pu generer de reponse en mode hors-ligne.")
                    }
                    completed.complete(Unit)
                }
            }

            // v2.1.9 : first-token timeout (45s) puis total timeout (120s)
            val firstOk = withTimeoutOrNull(45_000L) {
                firstToken.await()
                true
            }
            if (firstOk == null) {
                Log.w(TAG, "Local stream: first-token timeout after 45s")
                send(
                    "L'IA locale prend trop de temps sur cet appareil (aucun token en 45s).\n\n" +
                    "Essayez une question plus courte, ou connectez-vous a internet " +
                    "pour utiliser ROLLY en ligne (plus rapide)."
                )
                return@channelFlow
            }

            val allOk = withTimeoutOrNull(120_000L) {
                completed.await()
                true
            }
            if (allOk == null) {
                Log.w(TAG, "Local stream: total timeout after 120s")
                if (accumulated.isEmpty()) {
                    send("Le modele local met trop de temps a repondre. Essayez en ligne.")
                } else {
                    send(accumulated.toString() + "\n\n[Generation interrompue - delai depasse]")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in local stream", e)
            send("Erreur du modele local : ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Analyse basique de glycemie en mode hors-ligne.
     * Utilise des regles codees en dur + modele local pour les conseils.
     */
    suspend fun analyserGlycemieLocale(glycemie: Double, moment: String = ""): String {
        val interpretation = when {
            glycemie < 0.54 -> "URGENCE HYPOGLYCEMIE SEVERE"
            glycemie < 0.70 -> "Hypoglycemie - prenez du sucre rapidement"
            glycemie in 0.70..1.10 -> "Glycemie normale a jeun"
            glycemie in 1.10..1.26 -> "Pre-diabete - surveillance recommandee"
            glycemie in 1.26..1.80 -> "Hyperglycemie moderee"
            glycemie in 1.80..2.50 -> "Hyperglycemie elevee - consultez votre medecin"
            glycemie > 2.50 -> "URGENCE HYPERGLYCEMIE SEVERE"
            else -> "Valeur non reconnue"
        }

        val baseResponse = """
            |Analyse glycemie (mode hors-ligne) :
            |Valeur : ${glycemie} g/L ${if (moment.isNotBlank()) "($moment)" else ""}
            |Interpretation : $interpretation
        """.trimMargin()

        // Si le modele local est dispo, enrichir avec des conseils
        return if (isInitialized && llmInference != null) {
            try {
                val conseil = generateResponse(
                    "Ma glycemie est de $glycemie g/L. $interpretation. Donne 2-3 conseils pratiques courts."
                )
                "$baseResponse\n\nConseils ROLLY :\n$conseil"
            } catch (e: Exception) {
                baseResponse
            }
        } else {
            "$baseResponse\n\nConseil : Consultez votre medecin pour un suivi personnalise."
        }
    }
}

/**
 * Statut du modele IA local.
 */
enum class LocalAIStatus {
    NOT_DOWNLOADED,  // Modele pas encore telecharge
    DOWNLOADED,      // Telecharge mais pas charge en memoire
    READY,           // Charge et pret a utiliser
    ERROR            // Erreur d'initialisation
}
