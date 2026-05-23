package com.diabeto.util

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * v2.1.49 : voice I/O 100% local Android (gratuit, hors-ligne quand le
 * modele de langue est telecharge cote OS). Pas de service cloud, pas
 * besoin de Blaze.
 *
 * - **Text-to-Speech** (`speak`) : utilise `TextToSpeech` natif. Voix
 *   francaise par defaut (la plus probable d'etre installee).
 *   Autres : anglais, arabe. Langues camerounaises (Bassa, Duala,
 *   Fulfulde) ne sont PAS supportees par Android TTS — on bascule sur
 *   francais avec un avertissement console.
 *
 * - **Speech-to-Text** (`startListening`) : utilise `SpeechRecognizer`.
 *   Necessite que Google App soit installee + permission RECORD_AUDIO.
 *   La langue peut etre changee via le Locale (fr, en, ar). Pour les
 *   langues locales, fallback francais.
 *
 * Singleton process-level pour partager l'engine TTS entre composables.
 */
object VoiceManager {

    private const val TAG = "VoiceManager"

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ttsReady: Boolean = false
    @Volatile private var currentLocale: Locale = Locale.FRENCH

    /** Initialise TTS. Idempotent. Appelle ca au demarrage du Chat. */
    fun initTts(context: Context, onReady: ((Boolean) -> Unit)? = null) {
        if (tts != null && ttsReady) { onReady?.invoke(true); return }
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val result = tts?.setLanguage(currentLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Locale $currentLocale not supported, fallback default")
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(0.9f)  // legerement plus lent pour usage medical
            } else {
                Log.e(TAG, "TTS init failed: status=$status")
            }
            onReady?.invoke(ttsReady)
        }
    }

    /**
     * Lit le texte a voix haute. Locales supportees : "fr", "en", "ar".
     * Pour les autres ("bas", "dua", "ful", "pcm") on tombe sur francais.
     */
    fun speak(
        text: String,
        languageTag: String = "fr",
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val engine = tts ?: run {
            onError?.invoke("TTS pas initialise"); return
        }
        if (!ttsReady) {
            onError?.invoke("TTS pas pret"); return
        }
        val locale = resolveLocale(languageTag)
        if (locale != currentLocale) {
            engine.setLanguage(locale)
            currentLocale = locale
        }
        val utteranceId = "rolly-${System.currentTimeMillis()}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { onStart?.invoke() }
            override fun onDone(id: String?) { onDone?.invoke() }
            @Deprecated("kept for API compat")
            override fun onError(id: String?) { onError?.invoke("TTS error") }
            override fun onError(id: String?, errorCode: Int) {
                onError?.invoke("TTS error code=$errorCode")
            }
        })
        val cleaned = sanitizeForSpeech(text)
        engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /** Stoppe la lecture en cours. */
    fun stop() {
        tts?.stop()
    }

    /** Libere les ressources (a appeler depuis onDispose final). */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    /**
     * Cree un Intent pour SpeechRecognizer (RecognizerIntent.ACTION_RECOGNIZE_SPEECH).
     * A lancer via `rememberLauncherForActivityResult(StartActivityForResult)`.
     */
    fun buildSpeechIntent(languageTag: String = "fr"): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolveLocale(languageTag).toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle a ROLLY...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
    }

    /**
     * Mode "continu" sans dialog systeme : utilise SpeechRecognizer directement.
     * Appelle stopListening() pour arreter manuellement.
     */
    fun createContinuousRecognizer(
        context: Context,
        languageTag: String = "fr",
        onResult: (String) -> Unit,
        onPartial: ((String) -> Unit)? = null,
        onError: ((Int) -> Unit)? = null,
        onReadyForSpeech: (() -> Unit)? = null,
        onEndOfSpeech: (() -> Unit)? = null
    ): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError?.invoke(-1)
            return null
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { onReadyForSpeech?.invoke() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { onEndOfSpeech?.invoke() }
            override fun onError(error: Int) { onError?.invoke(error) }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { onResult(it) }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { onPartial?.invoke(it) }
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        val intent = buildSpeechIntent(languageTag).apply {
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
        return recognizer
    }

    private fun resolveLocale(languageTag: String): Locale = when (languageTag.lowercase()) {
        "fr", "fr-fr", "fr-cm" -> Locale.FRENCH
        "en", "en-us", "en-gb" -> Locale.ENGLISH
        "ar", "ar-cm" -> Locale("ar")
        // Langues camerounaises locales : pas de support TTS/STT Android.
        // Fallback francais avec un Log.w pour aider au debug.
        "bas", "dua", "ful", "pcm", "ewo" -> {
            Log.w(TAG, "Langue '$languageTag' non supportee par Android TTS/STT, fallback FR")
            Locale.FRENCH
        }
        else -> Locale.FRENCH
    }

    /**
     * Nettoie le markdown / emojis avant la synthese vocale, pour eviter
     * que TTS lise "asterisque asterisque gras asterisque asterisque".
     */
    private fun sanitizeForSpeech(text: String): String {
        return text
            .replace(Regex("\\*{1,3}([^*]+)\\*{1,3}"), "$1")  // bold/italic
            .replace(Regex("`([^`]+)`"), "$1")                // inline code
            .replace(Regex("#+\\s*"), "")                     // headers
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1") // markdown links
            .replace(Regex("[⚠️🩸🍽️💊🩺🚨]"), "")            // common emojis
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
