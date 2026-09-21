package com.diabeto.util

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
    private const val MOTEUR_GOOGLE = "com.google.android.tts"

    /** Marqueur Android pour une voix listee mais pas encore telechargee. */
    private const val VOIX_NON_INSTALLEE = "notInstalled"

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ttsReady: Boolean = false
    @Volatile private var currentLocale: Locale = Locale.FRENCH

    /**
     * Vrai quand la derniere synthese a ete tentee avec une voix reseau. Sert
     * au repli : si elle echoue (avion, 4G coupee), on rebascule sur la
     * meilleure voix locale et on rejoue la phrase, une seule fois.
     */
    @Volatile private var voixReseauEnCours: Boolean = false

    /** Lecture en cours de la voix naturelle (PCM renvoye par le serveur). */
    @Volatile private var audioTrack: AudioTrack? = null

    /**
     * Initialise TTS. Idempotent. Appelle ca au demarrage du Chat.
     *
     * v2.1.67 : selection auto de la voix de meilleure qualite disponible.
     * Priorite : fr-CM > fr-CA > fr-FR > default. fr-CA est l'accent le
     * plus proche du francais camerounais parmi les voix premium Google /
     * Samsung TTS, et beaucoup plus chaleureux que fr-FR Parisien.
     */
    fun initTts(context: Context, onReady: ((Boolean) -> Unit)? = null) {
        if (tts != null && ttsReady) { onReady?.invoke(true); return }
        val app = context.applicationContext
        // v2.1.101 : on nomme le moteur au lieu de subir celui du systeme.
        // Sans ce parametre, Android prend le moteur par defaut, qui est
        // souvent celui du constructeur (Samsung, Huawei, Pico) : ce sont ses
        // vieilles voix a formants qui donnent le rendu "robot". Le moteur
        // Google est present sur la quasi-totalite des telephones vendus ici.
        val moteur = moteurGoogleDisponible(app)
        tts = TextToSpeech(app, { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val result = tts?.setLanguage(currentLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Locale $currentLocale not supported, fallback default")
                    tts?.setLanguage(Locale.getDefault())
                }
                // v2.1.67 : selection de la meilleure voix dispo
                pickBestVoice(currentLocale)
                tts?.setSpeechRate(0.92f)  // legerement plus lent — clinique mais humain
                tts?.setPitch(0.95f)        // pitch legerement plus chaud (defaut 1.0)
            } else {
                Log.e(TAG, "TTS init failed: status=$status")
            }
            onReady?.invoke(ttsReady)
        }, moteur)
    }

    /**
     * Renvoie "com.google.android.tts" si le moteur Google est installe,
     * sinon null (TextToSpeech prend alors le moteur par defaut).
     */
    private fun moteurGoogleDisponible(context: Context): String? {
        // On interroge le PackageManager plutot que TextToSpeech.getEngines() :
        // lister les moteurs demande une instance deja initialisee, et c'est
        // precisement l'instance qu'on cherche a construire.
        val present = try {
            val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            context.packageManager
                .queryIntentServices(intent, 0)
                .any { it.serviceInfo?.packageName == MOTEUR_GOOGLE }
        } catch (t: Throwable) {
            Log.w(TAG, "Impossible de lister les moteurs TTS : ${t.message}")
            false
        }
        if (!present) Log.w(TAG, "Moteur Google TTS absent — voix du constructeur utilisee")
        return if (present) MOTEUR_GOOGLE else null
    }

    /**
     * v2.1.67 : recherche la voix de qualite la plus elevee pour la locale
     * demandee. Heuristique :
     *   1. Voix Network (Neural / WaveNet) avec quality >= VERY_HIGH
     *   2. Voix locale avec quality >= HIGH
     *   3. Fallback : laisse setLanguage choisir
     *
     * Affinite d'accent : fr-CM (rare mais existe sur certains Samsung), puis
     * fr-CA (plus chaleureux que fr-FR pour oreilles camerounaises), puis fr-FR.
     */
    private fun pickBestVoice(locale: Locale, autoriserReseau: Boolean = true) {
        val engine = tts ?: return
        val lang = locale.language
        val availableVoices = try { engine.voices?.toList().orEmpty() } catch (_: Throwable) { emptyList() }
        if (availableVoices.isEmpty()) {
            Log.w(TAG, "Pas de voices disponibles — engine TTS ${engine.defaultEngine}")
            return
        }

        // v2.1.101 : une voix marquee "notInstalled" est listee mais absente du
        // telephone. setVoice l'accepte, puis la synthese retombe sur la voix
        // par defaut — la plus pauvre. C'est ce tri qui faisait passer une voix
        // fantome VERY_HIGH devant une voix HIGH reellement presente.
        val utilisables = availableVoices.filter { voix ->
            voix.locale?.language == lang &&
                !voix.features.orEmpty().contains(VOIX_NON_INSTALLEE) &&
                (autoriserReseau || !voix.isNetworkConnectionRequired)
        }
        if (utilisables.isEmpty()) {
            Log.w(TAG, "Aucune voix $lang installee parmi ${availableVoices.size} — voix par defaut conservee")
            return
        }

        // Ordre de preference par region (pour FR uniquement — pour les autres on prend juste la meilleure qualite)
        val regionPriority = when (lang) {
            "fr" -> listOf("CM", "CA", "FR", "BE", "CH")  // Cameroun > Canada > France > Belgique > Suisse
            "en" -> listOf("GB", "US", "AU", "CA")
            else -> emptyList()
        }

        // v2.1.101 : les voix vraiment humaines de Google sont les voix
        // neuronales, et elles exigent le reseau. Elles passent donc en
        // premier quand on les autorise ; le repli hors-ligne rappelle cette
        // fonction avec autoriserReseau = false.
        val sorted = utilisables.sortedWith(
            compareByDescending<Voice> { it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }   // VERY_HIGH (500) > HIGH (400) > NORMAL (300) > LOW (200) > VERY_LOW (100)
                .thenBy { v -> regionPriority.indexOf(v.locale?.country).let { if (it == -1) Int.MAX_VALUE else it } }
        )

        val chosen = sorted.firstOrNull()
        if (chosen != null) {
            val result = engine.setVoice(chosen)
            if (result == TextToSpeech.SUCCESS) {
                voixReseauEnCours = chosen.isNetworkConnectionRequired
                Log.i(TAG, "Voix selectionnee: ${chosen.name} (${chosen.locale}, quality=${chosen.quality}, network=${chosen.isNetworkConnectionRequired})")
            } else {
                Log.w(TAG, "setVoice failed ($result), conservation voix par defaut")
            }
        } else {
            Log.w(TAG, "Aucune voix $lang trouvee parmi ${availableVoices.size} voices")
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
            // v2.1.67 : re-selectionne la meilleure voix pour la nouvelle locale
            pickBestVoice(locale)
        }
        val cleaned = sanitizeForSpeech(text)
        parler(engine, cleaned, locale, onStart, onDone, onError, replisRestants = 1)
    }

    /**
     * Envoie la phrase au moteur, avec un repli unique.
     *
     * v2.1.101 : une voix neuronale de Google a besoin du reseau. Quand il
     * tombe — ce qui arrive souvent ici — la synthese echoue silencieusement et
     * le patient n'entend rien. Dans ce cas on rebascule sur la meilleure voix
     * presente sur le telephone et on rejoue la phrase une fois. Un seul repli :
     * si la voix locale echoue aussi, le probleme n'est pas le reseau.
     */
    private fun parler(
        engine: TextToSpeech,
        cleaned: String,
        locale: Locale,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?,
        replisRestants: Int
    ) {
        val utteranceId = "rolly-${System.currentTimeMillis()}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { onStart?.invoke() }
            override fun onDone(id: String?) { onDone?.invoke() }
            @Deprecated("kept for API compat")
            override fun onError(id: String?) { gererEchec("TTS error") }
            override fun onError(id: String?, errorCode: Int) {
                gererEchec("TTS error code=$errorCode")
            }

            private fun gererEchec(message: String) {
                if (replisRestants > 0 && voixReseauEnCours) {
                    Log.w(TAG, "$message — repli sur une voix locale")
                    pickBestVoice(locale, autoriserReseau = false)
                    parler(engine, cleaned, locale, onStart, onDone, onError, replisRestants - 1)
                } else {
                    onError?.invoke(message)
                }
            }
        })
        engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Joue un PCM 16 bits mono renvoye par /api/rolly-tts (voix naturelle).
     *
     * v2.1.101 : le serveur ne renvoie pas un fichier mais des echantillons
     * bruts, donc pas de MediaPlayer ici — AudioTrack les ecrit directement sur
     * le flux musique. La lecture se fait sur un thread dedie : `write` bloque
     * tant que le tampon n'est pas consomme.
     */
    fun playPcm(
        pcm: ByteArray,
        sampleRate: Int = 24000,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        stop()
        if (pcm.isEmpty()) { onError?.invoke("audio vide"); return }
        try {
            val tailleMin = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val piste = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(tailleMin, pcm.size.coerceAtMost(256 * 1024)))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = piste
            piste.play()
            onStart?.invoke()

            Thread({
                try {
                    var offset = 0
                    while (offset < pcm.size) {
                        val piste2 = audioTrack ?: break   // stop() est passe par la
                        val bloc = minOf(tailleMin, pcm.size - offset)
                        val ecrits = piste2.write(pcm, offset, bloc)
                        if (ecrits <= 0) break
                        offset += ecrits
                    }
                    if (audioTrack === piste) {
                        // Laisse le tampon se vider avant de couper le son.
                        try { piste.stop() } catch (_: Throwable) {}
                        onDone?.invoke()
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Lecture PCM interrompue : ${t.message}")
                    onError?.invoke(t.message ?: "lecture audio impossible")
                } finally {
                    if (audioTrack === piste) audioTrack = null
                    try { piste.release() } catch (_: Throwable) {}
                }
            }, "rolly-voix-naturelle").start()
        } catch (t: Throwable) {
            Log.e(TAG, "AudioTrack indisponible : ${t.message}")
            audioTrack = null
            onError?.invoke(t.message ?: "audio indisponible")
        }
    }

    /** Stoppe la lecture en cours (TTS local comme voix naturelle). */
    fun stop() {
        tts?.stop()
        audioTrack?.let { piste ->
            audioTrack = null
            try { piste.pause(); piste.flush(); piste.stop() } catch (_: Throwable) {}
            try { piste.release() } catch (_: Throwable) {}
        }
    }

    /** Libere les ressources (a appeler depuis onDispose final). */
    fun shutdown() {
        stop()
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
