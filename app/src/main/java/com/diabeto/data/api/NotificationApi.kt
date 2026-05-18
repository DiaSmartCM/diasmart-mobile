package com.diabeto.data.api

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
 * Client HTTP pour les endpoints de notifications push (Vercel).
 *
 * Les pushes FCM ne peuvent pas etre envoyes directement depuis le client
 * (cle FCM = secret serveur). On passe donc par `/api/notify-message` et
 * `/api/notify-community` sur Vercel, qui detiennent les credentials
 * Firebase Admin SDK.
 *
 * Auth : Firebase ID token (Bearer). Echec d'envoi non-fatal pour l'UX —
 * le message Firestore est deja ecrit, la notif n'est qu'un bonus.
 */
@Singleton
class NotificationApi @Inject constructor(
    private val auth: FirebaseAuth
) {
    companion object {
        private const val TAG = "NotificationApi"
        private const val BASE = "https://website-omega-umber-20.vercel.app/api"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Notifie le destinataire d'une conversation 1-to-1.
     * `attachmentName` est utilise quand le message porte une piece jointe.
     * Renvoie true si la requete HTTP a abouti (200), false sinon.
     */
    suspend fun notifyMessage(
        conversationId: String,
        preview: String,
        attachmentName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: return@runCatching false
            val body = JSONObject().apply {
                put("conversationId", conversationId)
                put("preview", preview)
                if (!attachmentName.isNullOrBlank()) put("attachmentName", attachmentName)
            }.toString()
            val req = Request.Builder()
                .url("$BASE/notify-message")
                .addHeader("Authorization", "Bearer $token")
                .post(body.toRequestBody(JSON))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "notify-message HTTP ${resp.code} : ${resp.body?.string()?.take(120)}")
                    false
                } else true
            }
        }.getOrElse { e ->
            Log.w(TAG, "notify-message failed: ${e.message}")
            false
        }
    }

    /**
     * Notifie le topic "community" avec un apercu du message.
     */
    suspend fun notifyCommunity(
        preview: String,
        senderName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: return@runCatching false
            val body = JSONObject().apply {
                put("preview", preview)
                if (!senderName.isNullOrBlank()) put("senderName", senderName)
            }.toString()
            val req = Request.Builder()
                .url("$BASE/notify-community")
                .addHeader("Authorization", "Bearer $token")
                .post(body.toRequestBody(JSON))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "notify-community HTTP ${resp.code} : ${resp.body?.string()?.take(120)}")
                    false
                } else true
            }
        }.getOrElse { e ->
            Log.w(TAG, "notify-community failed: ${e.message}")
            false
        }
    }
}
