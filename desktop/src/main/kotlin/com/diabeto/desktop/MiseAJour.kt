package com.diabeto.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * Mise a jour de la version PC depuis la derniere release GitHub.
 * Windows : telecharge le .msi et le lance ; il remplace l'ancienne version
 * (meme upgradeUuid, numero plus grand) sans desinstaller. Mac : ouvre le .dmg.
 */
object MiseAJour {
    private const val DEPOT = "DiaSmartCM/diasmart-mobile"

    data class Disponible(val version: String, val url: String, val nomFichier: String)

    /** Numero de cette version (ecrit a la construction), null en developpement. */
    val versionActuelle: String? =
        MiseAJour::class.java.getResource("/diasmart-version.txt")?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    private val extension = if (windows) ".msi" else ".dmg"
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL).build()

    /** -1, 0 ou 1 : compare "2.1.106" et "2.1.107" chiffre par chiffre. */
    fun comparer(a: String, b: String): Int {
        val x = a.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /** La nouvelle version a installer, ou null (deja a jour, hors ligne...). */
    suspend fun verifier(): Disponible? = withContext(Dispatchers.IO) {
        val actuelle = versionActuelle ?: return@withContext null
        runCatching {
            val req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/$DEPOT/releases/latest"))
                .timeout(Duration.ofSeconds(20)).header("Accept", "application/vnd.github+json").GET().build()
            val rep = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (rep.statusCode() != 200) return@runCatching null
            val o = Json.parseToJsonElement(rep.body()).jsonObject
            val version = o["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return@runCatching null
            if (comparer(version, actuelle) <= 0) return@runCatching null
            // Le .msi / .dmg arrive quelques minutes apres l'APK : sans lui, rien a proposer.
            (o["assets"] as? JsonArray).orEmpty().map { it.jsonObject }.firstNotNullOfOrNull { a ->
                val nom = a["name"]?.jsonPrimitive?.content ?: return@firstNotNullOfOrNull null
                val url = a["browser_download_url"]?.jsonPrimitive?.content ?: return@firstNotNullOfOrNull null
                if (nom.endsWith(extension, ignoreCase = true)) Disponible(version, url, nom) else null
            }
        }.getOrNull()
    }

    /**
     * Windows : telecharge puis lance l'installation et ferme DiaSmart.
     * Mac : ouvre le telechargement dans le navigateur.
     */
    suspend fun installer(d: Disponible, progres: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (!windows) {
            Desktop.getDesktop().browse(URI.create(d.url))
            return@withContext
        }
        val req = HttpRequest.newBuilder(URI.create(d.url)).timeout(Duration.ofMinutes(30)).GET().build()
        val rep = try {
            http.send(req, HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: java.io.IOException) {
            throw Exception("Pas de connexion Internet. Réessayez plus tard.")
        }
        if (rep.statusCode() != 200) throw Exception("Téléchargement impossible (${rep.statusCode()}).")
        val total = rep.headers().firstValueAsLong("Content-Length").orElse(-1L)
        val dossier = File(System.getProperty("java.io.tmpdir"), "DiaSmart-maj").apply { mkdirs() }
        val partiel = File(dossier, d.nomFichier + ".part")
        rep.body().use { entree ->
            partiel.outputStream().use { sortie ->
                val tampon = ByteArray(64 * 1024)
                var lu = 0L
                while (true) {
                    val n = entree.read(tampon)
                    if (n < 0) break
                    sortie.write(tampon, 0, n)
                    lu += n
                    if (total > 0) progres((lu.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        if (total > 0 && partiel.length() != total) throw Exception("Téléchargement incomplet. Réessayez.")
        val msi = File(dossier, d.nomFichier)
        Files.move(partiel.toPath(), msi.toPath(), StandardCopyOption.REPLACE_EXISTING)
        // /passive : barre de progression seulement, pas de questions.
        // L'installeur remplace l'ancienne version (meme upgradeUuid).
        ProcessBuilder("msiexec", "/i", msi.absolutePath, "/passive").start()
        kotlin.system.exitProcess(0)
    }
}
