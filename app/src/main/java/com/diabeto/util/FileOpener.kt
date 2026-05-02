package com.diabeto.util

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
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
    private const val DIASMART_SUBDIR = "DiaSmart"
    private const val PREFS_DOWNLOADS = "diasmart_downloads_map"

    /** URLs en cours de telechargement — evite les enqueue multiples si l'utilisateur retape. */
    private val pendingDownloads = mutableSetOf<String>()

    private fun getCachedPath(context: Context, url: String): String? =
        context.getSharedPreferences(PREFS_DOWNLOADS, Context.MODE_PRIVATE).getString(url, null)

    private fun saveCachedPath(context: Context, url: String, path: String) {
        context.getSharedPreferences(PREFS_DOWNLOADS, Context.MODE_PRIVATE)
            .edit().putString(url, path).apply()
    }

    private fun removeCachedPath(context: Context, url: String) {
        context.getSharedPreferences(PREFS_DOWNLOADS, Context.MODE_PRIVATE)
            .edit().remove(url).apply()
    }

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
     * Comportement par defaut au tap sur une piece jointe :
     *  - 1er tap : enqueue DownloadManager → fichier ecrit dans
     *    /storage/emulated/0/Download/DiaSmart/{nom}, notification systeme
     *    (l'utilisateur peut taper la notif pour ouvrir avec sa visionneuse).
     *  - 2eme tap (et suivants) : detecte le fichier deja telecharge → ouvre
     *    directement avec FileProvider sans re-telecharger.
     *
     * Avantages vs cacheDir :
     *  - persiste apres redemarrage / reinstallation app
     *  - visible dans le gestionnaire de fichiers (Files, Documents...)
     *  - accessible offline indefiniment
     *  - aucune permission speciale requise (DownloadManager systeme)
     *
     * Une SharedPreferences URL → chemin local conserve la trace pour
     * qu'on retrouve le fichier instantanement au prochain tap.
     */
    fun downloadOrOpen(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String?
    ) {
        if (url.isBlank()) {
            toast(context, "URL invalide")
            return
        }
        val safeName = sanitizeFileName(fileName.ifBlank { "fichier" })
        val mime = (mimeType?.takeIf { it.isNotBlank() } ?: guessMime(safeName))

        // 1) Determiner le dossier cible selon la version Android :
        //  - API >= 29 : public Downloads (pas de permission necessaire avec
        //    setDestinationInExternalPublicDir + DownloadManager)
        //  - API <= 28 : public Downloads exige WRITE_EXTERNAL_STORAGE,
        //    qui peut etre refusee par l'utilisateur ou non encore accordee.
        //    On retombe sur le dossier external-files de l'app
        //    (Android/data/com.diabeto/files/Download/DiaSmart/), qui ne
        //    necessite aucune permission, est visible dans Files et persiste.
        val usePublicDownloads = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

        val targetDir = if (usePublicDownloads) {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                DIASMART_SUBDIR
            )
        } else {
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                DIASMART_SUBDIR
            )
        }.apply { mkdirs() }
        val target = File(targetDir, safeName)

        // 1a) Mapping URL → path memorise
        val cachedPath = getCachedPath(context, url)
        if (cachedPath != null) {
            val cached = File(cachedPath)
            if (cached.exists() && cached.length() > 0) {
                Log.d(TAG, "open via cache map: $cachedPath")
                openLocalFile(context, cached, mime)
                return
            }
            removeCachedPath(context, url)
        }

        // 1b) Verification directe sur disque (fichier present sans mapping,
        //     p. ex. apres un download precedent qui n'a pas eu le temps de
        //     persister la map, ou apres reinstallation app)
        if (target.exists() && target.length() > 0) {
            Log.d(TAG, "open via direct path: ${target.absolutePath}")
            saveCachedPath(context, url, target.absolutePath)
            openLocalFile(context, target, mime)
            return
        }

        // 2) Pas encore telecharge — eviter la duplication si deja en cours
        synchronized(pendingDownloads) {
            if (pendingDownloads.contains(url)) {
                toast(context, "Telechargement en cours — voir la notification")
                return
            }
            pendingDownloads.add(url)
        }
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(safeName)
                setDescription("DiaSmart — fichier joint")
                setMimeType(mime)
                if (usePublicDownloads) {
                    // public Downloads : visible immediatement dans Files
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "$DIASMART_SUBDIR/$safeName"
                    )
                } else {
                    // external-files de l'app : pas de permission requise
                    // (Android/data/<package>/files/Download/DiaSmart/<file>)
                    setDestinationUri(Uri.fromFile(target))
                }
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            dm.enqueue(req)
            // On enregistre le chemin attendu : si l'utilisateur tape a nouveau
            // apres la fin du telechargement, openOrCache trouvera le fichier.
            saveCachedPath(context, url, target.absolutePath)
            val locationLabel = if (usePublicDownloads)
                "Downloads/DiaSmart"
            else
                "stockage app (Android/data/${context.packageName}/files/Download/DiaSmart)"
            toast(
                context,
                "Telechargement vers $locationLabel — voir la notification"
            )
        } catch (e: Exception) {
            Log.e(TAG, "downloadOrOpen failed", e)
            synchronized(pendingDownloads) { pendingDownloads.remove(url) }
            toast(context, "Echec : ${e::class.java.simpleName}: ${e.message ?: ""}")
        }
    }

    private fun openLocalFile(context: Context, file: File, mimeType: String) {
        val authority = "${context.packageName}.provider"
        val uri = try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider failed for ${file.absolutePath}", e)
            toast(context, "Cache inaccessible (${e::class.java.simpleName})")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(
                Intent.createChooser(intent, "Ouvrir avec...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: ActivityNotFoundException) {
            toast(context, "Aucune application pour ouvrir ce type de fichier")
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
