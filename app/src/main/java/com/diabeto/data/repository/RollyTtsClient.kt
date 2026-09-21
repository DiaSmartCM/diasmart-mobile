package com.diabeto.data.repository

import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client de la voix naturelle de ROLLY (option, v2.1.101).
 *
 * La voix par defaut reste le TextToSpeech d'Android : gratuite, hors-ligne,
 * sans quota. Elle sonne robotique quand le telephone n'a que le moteur du
 * constructeur. Ce client appelle /api/rolly-tts, qui fait synthetiser la
 * phrase par un modele TTS Gemini et renvoie du PCM 16 bits mono a 24 kHz.
 *
 * Ce n'est PAS le chemin par defaut : chaque phrase consomme le quota gratuit
 * partage par tous les patients et transporte de l'audio sur le reseau mobile.
 * L'appelant ne doit l'utiliser que si le patient a active l'option lui-meme,
 * et doit retomber sur le TextToSpeech local des que [synthetiser] echoue.
 */
@Singleton
class RollyTtsClient @Inject constructor(
    private val auth: FirebaseAuth
) {
    companion object {
        private const val TAG = "RollyTtsClient"
        private const val TTS_URL =
            "https://website-omega-umber-20.vercel.app/api/rolly-tts"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** Doit rester aligne sur MAX_TEXT_LENGTH cote serveur. */
        const val MAX_TEXT_LENGTH = 1200

        /** Frequence d'echantillonnage du PCM renvoye par le serveur. */
        const val SAMPLE_RATE = 24000
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // La synthese d'une reponse de quelques phrases prend plusieurs
            // secondes, et le telechargement de l'audio se fait sur une 4G
            // souvent lente : meme marge que pour l'analyse photo.
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Synthetise [texte] et renvoie le PCM brut (16 bits, mono, 24 kHz).
     *
     * Le texte est tronque a [MAX_TEXT_LENGTH] : au-dela le serveur refuse la
     * requete, et une reponse plus longue couterait du quota pour un audio que
     * le patient n'ecoutera pas jusqu'au bout.
     */
    suspend fun synthetiser(texte: String, voix: String? = null): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
                    ?: throw IllegalStateException("Aucun utilisateur connecte (voix ROLLY)")

                val body = JSONObject().apply {
                    put("text", texte.take(MAX_TEXT_LENGTH))
                    if (!voix.isNullOrBlank()) put("voice", voix)
                }.toString()

                val req = Request.Builder()
                    .url(TTS_URL)
                    .addHeader("Authorization", "Bearer $idToken")
                    .post(body.toRequestBody(JSON_MEDIA))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException(
                            "Voix ROLLY HTTP ${resp.code} : ${raw.take(200)}"
                        )
                    }
                    val json = JSONObject(raw)
                    val b64 = json.optString("audioBase64")
                    if (b64.isBlank()) throw IllegalStateException("Reponse audio vide")
                    val pcm = Base64.decode(b64, Base64.DEFAULT)
                    Log.d(TAG, "Voix naturelle : ${pcm.size / 1024} Ko PCM, modele=${json.optString("model")}")
                    pcm
                }
            }
        }
}
