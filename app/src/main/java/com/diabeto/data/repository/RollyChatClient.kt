package com.diabeto.data.repository

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client HTTP pour le proxy ROLLY (v2.1.37+).
 *
 * Securite : la cle API Gemini n'est plus dans l'APK. Toutes les requetes
 * passent par /api/rolly-chat sur Vercel, qui detient la cle cote serveur
 * et la transmet a Gemini. L'authentification se fait via le Firebase ID
 * token de l'utilisateur (Bearer header), donc seul un user authentifie
 * peut invoquer Rolly.
 *
 * Modes supportes (parametre `mode` du body) :
 *  - chat : conversation libre simple
 *  - chat_context : conversation avec contexte patient injecte
 *  - meal_json : analyse texte d'un repas → JSON nutritionnel
 *  - meal_image : analyse d'une image de repas → JSON nutritionnel
 *  - glucose_analysis : analyse tendance glycemique
 *  - nutrition_advice : conseils nutritionnels personnalises
 *  - risk_prediction : prevision de risque metabolique
 *  - predictive_7days : analyse predictive 7 jours
 *
 * Pour les modes streaming, le serveur retourne du SSE (`text/event-stream`)
 * et `streamText()` parse les chunks au fil de l'eau. Pour les modes
 * one-shot, `sendText()` ou `sendJson()` retournent le resultat complet.
 */
@Singleton
class RollyChatClient @Inject constructor(
    private val auth: FirebaseAuth
) {
    companion object {
        private const val TAG = "RollyChatClient"
        private const val PROXY_URL =
            "https://website-omega-umber-20.vercel.app/api/rolly-chat"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        // v2.1.73 : plafond du plus grand cote d'une image envoyee a ROLLY
        // Vision. Gemini retaille en tuiles ~768 px : au-dela on paie de la
        // bande passante (rare et chere ici) sans gagner en precision.
        private const val IMAGE_MAX_SIDE = 1024
        private const val IMAGE_JPEG_QUALITY = 80
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)  // Gemini peut prendre du temps
            // v2.1.73 : 30 s ne suffisaient pas pour televerser une photo sur
            // une 4G camerounaise instable -> SocketTimeoutException sans
            // message. L'image est desormais bien plus legere, mais on garde
            // une marge pour les reseaux tres lents.
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Envoie une requete one-shot et retourne le texte complet.
     * Utilise pour : conseils nutritionnels, prevision risque, etc. quand
     * le streaming n'est pas critique pour l'UX.
     */
    suspend fun sendText(
        mode: String,
        message: String,
        context: String = "",
        history: String = "",
        imageBitmap: Bitmap? = null,
        useFallback: Boolean = false
    ): Result<String> = runCatching {
        val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("Aucun utilisateur connecte (Rolly)")
        val body = buildBody(mode, message, context, history, imageBitmap, useFallback, stream = false)
        val req = Request.Builder()
            .url(PROXY_URL)
            .addHeader("Authorization", "Bearer $idToken")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Rolly HTTP ${resp.code} : ${raw.take(200)}")
            }
            val json = JSONObject(raw)
            json.optString("text").ifBlank {
                // Si on a recu un JSON (mode meal_*), retourne-le sous forme de chaine
                json.optJSONObject("json")?.toString().orEmpty()
            }
        }
    }

    /**
     * Variant qui retourne directement la chaine JSON brute (pour les modes
     * meal_json / meal_image qui attendent un JSON parsable).
     */
    suspend fun sendJson(
        mode: String,
        message: String,
        imageBitmap: Bitmap? = null,
        useFallback: Boolean = false
    ): Result<String> = runCatching {
        val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("Aucun utilisateur connecte (Rolly)")
        val body = buildBody(mode, message, "", "", imageBitmap, useFallback, stream = false)
        val req = Request.Builder()
            .url(PROXY_URL)
            .addHeader("Authorization", "Bearer $idToken")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Rolly HTTP ${resp.code} : ${raw.take(200)}")
            }
            val json = JSONObject(raw)
            val structured = json.optJSONObject("json")
            structured?.toString() ?: json.optString("text", raw)
        }
    }

    /**
     * Streaming SSE. Emit chaque chunk de texte accumule au fur et a mesure
     * (compatible avec l'ancien comportement Gemini.sendMessageStream).
     */
    fun streamText(
        mode: String,
        message: String,
        context: String = "",
        history: String = "",
        useFallback: Boolean = false
    ): Flow<String> = flow {
        val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("Aucun utilisateur connecte (Rolly)")
        val body = buildBody(mode, message, context, history, null, useFallback, stream = true)
        val req = Request.Builder()
            .url(PROXY_URL)
            .addHeader("Authorization", "Bearer $idToken")
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        val accumulated = StringBuilder()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                throw IllegalStateException("Rolly stream HTTP ${resp.code} : ${errBody.take(200)}")
            }
            val source = resp.body?.source()
                ?: throw IllegalStateException("Stream Rolly vide")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                if (!line.startsWith("data: ")) continue
                val payload = line.removePrefix("data: ").trim()
                if (payload == "[DONE]") break
                try {
                    val chunk = JSONObject(payload).optString("text")
                    if (chunk.isNotBlank()) {
                        accumulated.append(chunk)
                        emit(accumulated.toString())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SSE chunk parse failed: ${e.message}")
                }
            }
        }
        if (accumulated.isEmpty()) emit("")
    }.flowOn(Dispatchers.IO)

    // ── Helpers ────────────────────────────────────────────────

    private fun buildBody(
        mode: String,
        message: String,
        context: String,
        history: String,
        imageBitmap: Bitmap?,
        useFallback: Boolean,
        stream: Boolean
    ): String {
        val obj = JSONObject().apply {
            put("mode", mode)
            put("message", message)
            if (context.isNotBlank()) put("context", context)
            if (history.isNotBlank()) put("history", history)
            put("stream", stream)
            put("useFallback", useFallback)
            // v2.1.61 : transmet le code locale de l'app pour que ROLLY reponde
            // dans la langue choisie par l'utilisateur (et pas dans la langue
            // qu'il devine du message). Lecture via AppCompatDelegate qui est
            // la source de verite per-app language (Android 13+) avec fallback
            // compat pre-Tiramisu.
            val tag = currentAppLanguageTag()
            if (tag.isNotBlank()) put("userLanguage", tag)
            if (imageBitmap != null) {
                // v2.1.73 : l'image etait envoyee en pleine resolution. Une photo
                // de telephone (3000x4000) donne 2-5 Mo en JPEG, que le Base64
                // gonfle encore de ~33 % : 3 a 7 Mo pousses sur une 4G lente.
                // L'envoi depassait le writeTimeout et remontait une
                // SocketTimeoutException SANS message, d'ou un "Erreur d'analyse
                // IA" sans detail. On redimensionne a IMAGE_MAX_SIDE : Gemini
                // Vision retaille de toute facon en tuiles ~768 px, donc aucune
                // perte de qualite d'analyse, et la charge tombe a ~100-200 Ko.
                val reduit = downscaleForUpload(imageBitmap)
                val baos = ByteArrayOutputStream()
                reduit.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, baos)
                if (reduit !== imageBitmap) reduit.recycle()
                val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                Log.d(TAG, "Image Rolly : ${baos.size() / 1024} Ko compresses, ${b64.length / 1024} Ko en base64")
                put("imageBase64", b64)
            }
        }
        return obj.toString()
    }

    /**
     * v2.1.73 : ramene le plus grand cote de l'image a IMAGE_MAX_SIDE en
     * conservant les proportions. Renvoie le bitmap d'origine s'il est deja
     * assez petit (l'appelant ne doit alors pas le recycler).
     */
    private fun downscaleForUpload(source: Bitmap): Bitmap {
        val plusGrandCote = maxOf(source.width, source.height)
        if (plusGrandCote <= IMAGE_MAX_SIDE) return source
        val ratio = IMAGE_MAX_SIDE.toFloat() / plusGrandCote
        val largeur = (source.width * ratio).toInt().coerceAtLeast(1)
        val hauteur = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, largeur, hauteur, true)
    }

    /**
     * Renvoie le tag BCP 47 de la 1ere locale active de l'app (ex "fr", "pcm",
     * "dua"). Si aucune locale per-app n'est definie, retombe sur la locale
     * systeme (qui pour la plupart des telephones camerounais sera "fr").
     * Vide si tout echoue — le serveur tombera alors en mode detection auto.
     */
    private fun currentAppLanguageTag(): String = try {
        val appLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            appLocales.get(0)?.language.orEmpty()
        } else {
            java.util.Locale.getDefault().language.orEmpty()
        }
    } catch (_: Throwable) {
        ""
    }
}
