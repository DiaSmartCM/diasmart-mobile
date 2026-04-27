package com.diabeto.util

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
 * Ouvre n'importe quel fichier distant (PDF, image, doc, xlsx, ...).
 *
 * Logique cache :
 * 1) cle = sha1(url).take(12) + nom sanitize → fichier dans cacheDir/attachments/
 * 2) si le cache existe et n'est pas vide → ouverture directe (instant, offline)
 * 3) sinon download via OkHttp → ecriture cache → ouverture
 *
 * Ouverture :
 * - Intent ACTION_VIEW avec FileProvider URI + bonne MIME
 * - chooser systeme : laisse Android proposer toutes les apps capables
 *   (Adobe, WPS Office, Galerie, Excel...)
 * - fallback si aucune app n'accepte la MIME → ACTION_VIEW(URL HTTPS) navigateur
 */
object FileOpener {

    private const val TAG = "FileOpener"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Ouverture intelligente :
     *  - cache hit  → open immediatement
     *  - cache miss → download (peut prendre du temps, lance via Dispatchers.IO)
     *                 puis open
     *
     * Retourne true si l'ouverture a ete tentee avec un viewer local,
     * false si l'utilisateur doit choisir une autre option (browser).
     */
    suspend fun openOrCache(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String?
    ): OpenResult {
        if (url.isBlank()) return OpenResult.Error("URL invalide")

        // 1) cache hit
        val cached = cacheFile(context, url, fileName)
        if (cached.exists() && cached.length() > 0) {
            Log.d(TAG, "cache hit ${cached.path} (${cached.length()} bytes)")
            return tryLaunchViewer(context, cached, mimeType, url)
        }

        // 2) cache miss → download
        return try {
            withContext(Dispatchers.IO) {
                downloadTo(url, cached)
            }
            Log.d(TAG, "downloaded ${cached.path} (${cached.length()} bytes)")
            tryLaunchViewer(context, cached, mimeType, url)
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            OpenResult.Error("Echec telechargement : ${e::class.java.simpleName}: ${e.message ?: ""}")
        }
    }

    /**
     * Methodes "simples" toujours disponibles pour l'utilisateur (UI dialog) :
     * ouvrir l'URL distante directement, lancer un download systeme dans
     * /Downloads, ou copier le lien.
     */
    fun openInBrowser(context: Context, url: String) {
        if (url.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(context, "Aucun navigateur installe")
        } catch (e: Exception) {
            Log.e(TAG, "openInBrowser failed", e)
            toast(context, "Echec ouverture : ${e::class.java.simpleName}")
        }
    }

    fun downloadToDownloads(context: Context, url: String, fileName: String?) {
        if (url.isBlank()) return
        val safeName = sanitizeFileName(fileName ?: "fichier")
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(safeName)
                setDescription("Telechargement DiaSmart")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            dm.enqueue(req)
            toast(context, "Telechargement lance — voir la notification")
        } catch (e: Exception) {
            Log.e(TAG, "downloadToDownloads failed", e)
            toast(context, "Echec telechargement : ${e::class.java.simpleName}")
        }
    }

    fun copyLink(context: Context, url: String) {
        if (url.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("URL", url))
        toast(context, "Lien copie")
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun cacheFile(context: Context, url: String, fileName: String): File {
        val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val key = sha1(url).take(12)
        val name = "${key}_${sanitizeFileName(fileName)}"
        return File(dir, name)
    }

    private fun downloadTo(url: String, target: File) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            val body = resp.body ?: throw IllegalStateException("Reponse vide")
            tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            // fallback : copy + delete
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun tryLaunchViewer(
        context: Context,
        file: File,
        mimeType: String?,
        sourceUrl: String
    ): OpenResult {
        val authority = "${context.packageName}.provider"
        val uri = try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider.getUriForFile failed", e)
            return OpenResult.Error("Cache inaccessible (${e::class.java.simpleName})")
        }
        val finalMime = mimeType?.takeIf { it.isNotBlank() } ?: guessMime(file.name)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, finalMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, "Ouvrir avec...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            OpenResult.Opened
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no app for $finalMime")
            OpenResult.NoViewer(sourceUrl)
        } catch (e: Exception) {
            Log.e(TAG, "launchViewer failed", e)
            OpenResult.Error("Ouverture impossible : ${e::class.java.simpleName}")
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)

    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun guessMime(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "csv" -> "text/csv"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    sealed class OpenResult {
        object Opened : OpenResult()
        data class NoViewer(val sourceUrl: String) : OpenResult()
        data class Error(val message: String) : OpenResult()
    }
}
