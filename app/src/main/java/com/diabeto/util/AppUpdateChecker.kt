package com.diabeto.util

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume

/**
 * Vérifie, télécharge et installe les mises à jour automatiquement.
 *
 * Flux :
 * 1. SplashScreen appelle checkAndAutoUpdate()
 * 2. Compare versionCode locale vs Firestore app_config/latest_version
 * 3. Télécharge le ZIP/APK en arrière-plan
 * 4. Extrait l'APK du ZIP si nécessaire
 * 5. Installe via PackageInstaller API (quasi-silencieux sur Android 12+)
 *
 * Sur Android 12+ : l'installation se fait automatiquement sans interaction
 * Sur Android < 12 : un dialog système minimal apparaît (un seul tap "Installer")
 *
 * L'APK est installé PAR-DESSUS l'app existante (même clé de signature),
 * pas besoin de désinstaller.
 */
data class AppUpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val changelog: String = "",
    val forceUpdate: Boolean = false
)

class AppUpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val COLLECTION = "app_config"
        private const val DOCUMENT = "latest_version"
        const val ACTION_INSTALL_COMPLETE = "com.diabeto.INSTALL_COMPLETE"
    }

    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Dossier de travail des mises a jour.
     *
     * v2.1.90 : ce n'est plus /storage/emulated/0/Download.
     *
     * Ce dossier public exigeait WRITE_EXTERNAL_STORAGE, une permission
     * dangereuse qui doit etre accordee a l'execution sur Android 6 a 9 — ce
     * que l'application ne demandait jamais. DownloadManager refusait donc
     * d'ecrire, avec le message « No permission to write to
     * /storage/emulated/0/Download/DiaSmart-update.apk ». Sur Android 10 et
     * au-dela, la permission ne s'applique plus du tout et c'est le stockage
     * cloisonne qui bloquait la relecture du fichier telecharge.
     *
     * Le dossier prive de l'application n'a jamais eu besoin d'aucune
     * permission, sur aucune version d'Android. Et comme l'APK est lu par
     * notre propre processus pour etre pousse dans PackageInstaller, rien
     * n'exige qu'il soit visible par les autres applications.
     */
    private fun dossierMiseAJour(): File? =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir

    /**
     * Vérifie si une mise à jour est disponible.
     */
    suspend fun checkForUpdate(): AppUpdateInfo? {
        return try {
            val doc = firestore.collection(COLLECTION)
                .document(DOCUMENT)
                .get()
                .await()

            if (!doc.exists()) return null

            val remoteVersionCode = (doc.getLong("versionCode") ?: 0).toInt()
            val currentVersionCode = getCurrentVersionCode()

            Log.d(TAG, "Version locale: $currentVersionCode, distante: $remoteVersionCode")

            if (remoteVersionCode > currentVersionCode) {
                AppUpdateInfo(
                    versionCode = remoteVersionCode,
                    versionName = doc.getString("versionName") ?: "",
                    apkUrl = doc.getString("apkUrl") ?: "",
                    changelog = doc.getString("changelog") ?: "",
                    forceUpdate = doc.getBoolean("forceUpdate") ?: false
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Erreur vérification MAJ", e)
            null
        }
    }

    /**
     * Vérifie, télécharge et installe la mise à jour automatiquement.
     * Appelé depuis SplashScreen en arrière-plan.
     *
     * @return true si une mise à jour a été détectée et l'installation lancée
     */
    suspend fun checkAndAutoUpdate(): Boolean {
        val update = checkForUpdate() ?: return false
        if (update.apkUrl.isBlank()) return false

        Log.d(TAG, "Mise à jour disponible: ${update.versionName}")

        // Stocker les infos pour le dialogue Dashboard (fallback)
        val prefs = context.getSharedPreferences("diasmart_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("pending_update_version", update.versionName)
            .putString("pending_update_url", update.apkUrl)
            .putString("pending_update_changelog", update.changelog)
            .putBoolean("pending_update_force", update.forceUpdate)
            .apply()

        // Vérifier si on peut installer depuis des sources inconnues
        if (!canInstallFromUnknownSources()) {
            Log.w(TAG, "Installation depuis sources inconnues non autorisée")
            return false // Le dialogue Dashboard gérera le fallback
        }

        try {
            // Télécharger le fichier
            val apkFile = downloadUpdate(update.apkUrl, update.versionName)
            if (apkFile == null) {
                Log.e(TAG, "Échec du téléchargement")
                return false
            }

            // Installer via PackageInstaller (silencieux sur Android 12+)
            installWithPackageInstaller(apkFile)

            // Nettoyer les prefs car l'installation est lancée
            prefs.edit()
                .remove("pending_update_version")
                .remove("pending_update_url")
                .remove("pending_update_changelog")
                .remove("pending_update_force")
                .apply()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur auto-update", e)
            return false
        }
    }

    /**
     * Vérifie si l'app a la permission d'installer depuis des sources inconnues
     */
    private fun canInstallFromUnknownSources(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1
        }
    }

    /**
     * Ouvre les paramètres pour activer "Sources inconnues" pour cette app
     */
    fun openInstallPermissionSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur ouverture paramètres", e)
        }
    }

    /**
     * Télécharge le fichier (APK ou ZIP), extrait si nécessaire.
     * @return le fichier APK prêt à installer, ou null en cas d'erreur
     */
    private suspend fun downloadUpdate(url: String, versionName: String): File? {
        return try {
            val isZip = url.lowercase().endsWith(".zip")
            val downloadFileName = if (isZip) "DiaSmart-update.zip" else "DiaSmart-update.apk"

            // Nettoyer les anciens fichiers
            cleanOldFiles()

            // Télécharger via DownloadManager
            val downloadedFile = downloadFile(url, downloadFileName) ?: return null

            if (isZip) {
                // Extraire l'APK du ZIP
                val apkFile = extractApkFromZip(downloadedFile, versionName)
                downloadedFile.delete() // Supprimer le ZIP
                apkFile
            } else {
                downloadedFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur téléchargement", e)
            null
        }
    }

    /**
     * Télécharge un fichier via DownloadManager et attend la fin.
     */
    private suspend fun downloadFile(url: String, fileName: String): File? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val downloadsDir = dossierMiseAJour()
                if (downloadsDir == null) {
                    Log.e(TAG, "Aucun dossier de travail disponible")
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
                val targetFile = File(downloadsDir, fileName)
                if (targetFile.exists()) targetFile.delete()

                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle("Mise à jour DiaSmart")
                    .setDescription("Téléchargement en cours...")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(
                        context, Environment.DIRECTORY_DOWNLOADS, fileName
                    )
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)

                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val downloadId = dm.enqueue(request)

                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (id == downloadId) {
                            try { context.unregisterReceiver(this) } catch (_: Exception) {}

                            // Vérifier que le fichier existe
                            val file = File(downloadsDir, fileName)
                            if (file.exists() && file.length() > 0) {
                                Log.d(TAG, "Téléchargé: ${file.absolutePath} (${file.length()} bytes)")
                                continuation.resume(file)
                            } else {
                                Log.e(TAG, "Fichier téléchargé introuvable ou vide")
                                continuation.resume(null)
                            }
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(
                        receiver,
                        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context.registerReceiver(
                        receiver,
                        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                    )
                }

                continuation.invokeOnCancellation {
                    try {
                        dm.remove(downloadId)
                        context.unregisterReceiver(receiver)
                    } catch (_: Exception) {}
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erreur lancement téléchargement", e)
                continuation.resume(null)
            }
        }
    }

    /**
     * Extrait le premier fichier .apk trouvé dans un ZIP.
     */
    private fun extractApkFromZip(zipFile: File, versionName: String): File? {
        return try {
            val downloadsDir = dossierMiseAJour() ?: return null
            val targetApk = File(downloadsDir, "DiaSmart-$versionName.apk")

            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".apk")) {
                        FileOutputStream(targetApk).use { fos ->
                            zis.copyTo(fos)
                        }
                        Log.d(TAG, "APK extrait: ${targetApk.absolutePath} (${targetApk.length()} bytes)")
                        return targetApk
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            Log.e(TAG, "Aucun .apk trouvé dans le ZIP")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Erreur extraction ZIP", e)
            null
        }
    }

    /**
     * Installe l'APK via PackageInstaller API.
     *
     * - Sur Android 12+ (API 31+) : avec USER_ACTION_NOT_REQUIRED,
     *   l'installation est silencieuse si l'app se met à jour elle-même.
     * - Sur Android < 12 : affiche un dialog système minimaliste.
     */
    private fun installWithPackageInstaller(apkFile: File) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setSize(apkFile.length())

                // Sur Android 12+, demander une installation sans action utilisateur
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }

                // Nom du package pour mise à jour
                setAppPackageName(context.packageName)
            }

            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)

            // Copier l'APK dans la session
            session.openWrite("DiaSmart.apk", 0, apkFile.length()).use { outputStream ->
                FileInputStream(apkFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                session.fsync(outputStream)
            }

            // Créer le PendingIntent pour le résultat de l'installation
            val intent = Intent(ACTION_INSTALL_COMPLETE).apply {
                setPackage(context.packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            // Lancer l'installation
            session.commit(pendingIntent.intentSender)
            Log.d(TAG, "Installation PackageInstaller lancée (session $sessionId)")

            // Supprimer le fichier APK après l'installation
            apkFile.delete()

        } catch (e: Exception) {
            Log.e(TAG, "Erreur PackageInstaller", e)
            // Fallback: ne rien faire, le Dialog Dashboard gérera
        }
    }

    /**
     * Méthode legacy pour le bouton Dashboard - télécharge et installe.
     */
    fun downloadAndInstall(apkUrl: String, versionName: String) {
        try {
            val isZip = apkUrl.lowercase().endsWith(".zip")
            val downloadFileName = if (isZip) "DiaSmart-update.zip" else "DiaSmart-update.apk"

            cleanOldFiles()

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("DiaSmart $versionName")
                .setDescription("Téléchargement de la mise à jour...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    context, Environment.DIRECTORY_DOWNLOADS, downloadFileName
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            Toast.makeText(context, "Téléchargement de la mise à jour...", Toast.LENGTH_LONG).show()

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        try { context.unregisterReceiver(this) } catch (_: Exception) {}

                        val downloadsDir = dossierMiseAJour()
                        val downloadedFile = File(downloadsDir, downloadFileName)

                        val apkFile = if (isZip) {
                            val extracted = extractApkFromZip(downloadedFile, versionName)
                            downloadedFile.delete()
                            extracted
                        } else {
                            downloadedFile
                        }

                        if (apkFile != null && apkFile.exists()) {
                            installWithPackageInstaller(apkFile)
                        } else {
                            Toast.makeText(
                                context,
                                "Le telechargement n'est pas alle au bout. " +
                                    "Verifie ta connexion et reessaie.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur téléchargement", e)
            // Le detail technique part dans les logs ; l'utilisateur recoit une
            // phrase qui lui dit quoi faire. C'est ce Toast qui affichait
            // « Neither user 10215 nor current process has android.permission… ».
            Toast.makeText(
                context,
                MessageErreur.lisible(e, "telecharger la mise a jour"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun cleanOldFiles() {
        try {
            val dir = dossierMiseAJour() ?: return
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("DiaSmart-") &&
                    (file.name.endsWith(".apk") || file.name.endsWith(".zip"))) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    fun getCurrentVersionCode(): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) { 0 }
    }
}
