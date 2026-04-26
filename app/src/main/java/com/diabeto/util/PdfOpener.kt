package com.diabeto.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Telecharge un PDF (URL Supabase publique) dans le cache app puis l'ouvre
 * avec un viewer externe via Intent + FileProvider. Plus robuste qu'un
 * ACTION_VIEW direct sur l'URL HTTPS, qui echoue silencieusement quand
 * aucune app n'est associee au schema https.
 *
 * Usage :
 *   PdfOpener.open(context, message.attachmentUrl, message.attachmentName)
 *
 * Le fichier est mis en cache (nom = sha-1 de l'URL) pour eviter les
 * re-telechargements lors d'une seconde ouverture.
 */
object PdfOpener {

    private const val TAG = "PdfOpener"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    /** Lance le download + open. Affiche un Toast pour donner du feedback. */
    suspend fun open(context: Context, url: String, fileName: String?) {
        if (url.isBlank()) {
            toast(context, "URL invalide")
            return
        }
        toast(context, "Telechargement du PDF...")
        try {
            val file = downloadIfNeeded(context, url, fileName)
            launchViewer(context, file)
        } catch (e: Exception) {
            Log.e(TAG, "open failed for $url", e)
            // Fallback : tenter une ouverture directe dans le navigateur
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                toast(context, "Impossible d'ouvrir : ${e.message ?: "erreur reseau"}")
            }
        }
    }

    private suspend fun downloadIfNeeded(
        context: Context,
        url: String,
        fileName: String?
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val safeName = (fileName?.takeIf { it.isNotBlank() } ?: "document.pdf")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(100)
        // Cle = sha1(url) → meme URL = meme fichier sur disque
        val key = sha1(url).take(12)
        val target = File(dir, "${key}_$safeName")
        if (target.exists() && target.length() > 0) {
            Log.d(TAG, "Cached: ${target.path} (${target.length()} bytes)")
            return@withContext target
        }
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            val body = resp.body ?: throw IllegalStateException("Reponse vide")
            target.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        Log.d(TAG, "Downloaded: ${target.path} (${target.length()} bytes)")
        target
    }

    private fun launchViewer(context: Context, file: File) {
        val authority = "${context.packageName}.provider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(viewIntent, "Ouvrir le PDF avec...").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No PDF viewer on device", e)
            toast(context, "Aucune application PDF installee. Tentative dans le navigateur...")
            // Fallback : laisser le caller relancer une ouverture web
            throw e
        }
    }

    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
