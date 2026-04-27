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

/**
 * Ouvre ou telecharge un PDF distant (URL Supabase publique).
 *
 * L'ancienne implementation (download OkHttp + FileProvider + chooser MIME)
 * echouait silencieusement sur les telephones bas de gamme :
 *  - aucune app native pour application/pdf → chooser vide
 *  - SSL/timeout dans OkHttp → exception sans message
 *  - FileProvider strict authorities → ActivityNotFoundException
 *
 * Nouvelle approche, deux primitives systeme robustes (chacune autonome) :
 *  - **openInBrowser**  : Intent.ACTION_VIEW sur l'URL HTTPS. Tous les
 *    Android ont au moins un navigateur, qui sait afficher/proposer le PDF.
 *  - **downloadToDownloads** : DownloadManager systeme → /Downloads, avec
 *    notification cliquable pour ouvrir le fichier. Pas de FileProvider,
 *    pas de cache app-private, pas de chooser fragile.
 *
 * L'UI (chat) appelle ces fonctions directement via un dialog donnant le
 * choix a l'utilisateur — plus simple que la decision automatique.
 */
object PdfOpener {

    private const val TAG = "PdfOpener"

    /** Ouvre l'URL dans le navigateur par defaut (Chrome rend les PDF nativement). */
    fun openInBrowser(context: Context, url: String) {
        if (url.isBlank()) {
            toast(context, "URL invalide")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no browser", e)
            toast(context, "Aucun navigateur installe")
        } catch (e: Exception) {
            Log.e(TAG, "openInBrowser failed", e)
            toast(context, "Echec ouverture : ${e::class.java.simpleName}")
        }
    }

    /**
     * Telechargement systeme via DownloadManager. Le PDF arrive dans
     * /Downloads avec une notification cliquable pour l'ouvrir. Aucune
     * permission speciale requise sur Android Q+ pour ce dossier public.
     */
    fun downloadToDownloads(context: Context, url: String, fileName: String?) {
        if (url.isBlank()) {
            toast(context, "URL invalide")
            return
        }
        val safeName = (fileName?.takeIf { it.isNotBlank() } ?: "rapport.pdf")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(120)
            .let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }

        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(safeName)
                setDescription("Telechargement du rapport DiaSmart")
                setMimeType("application/pdf")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            dm.enqueue(req)
            toast(context, "Telechargement lance — voir la notification")
        } catch (e: Exception) {
            Log.e(TAG, "downloadToDownloads failed", e)
            toast(context, "Echec telechargement : ${e::class.java.simpleName}: ${e.message ?: "(?)"}")
        }
    }

    /** Copie l'URL dans le presse-papier (fallback ultime). */
    fun copyLink(context: Context, url: String) {
        if (url.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("URL", url))
        toast(context, "Lien copie dans le presse-papier")
    }

    private fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }
}
