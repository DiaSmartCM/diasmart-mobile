package com.diabeto.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Deux lettres pour l'avatar : initiales du prenom et du nom (« Rolande Ngongang » -> « RN »). */
fun initiales(nom: String): String =
    nom.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }

private val photos = ConcurrentHashMap<String, ImageBitmap>()
private val http: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build() }

/** Photo du profil : data URL base64 (photo prise dans l'app) ou lien https (compte Google). Null si illisible. */
private suspend fun chargerPhoto(photo: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val octets = if (photo.startsWith("data:")) Base64.getMimeDecoder().decode(photo.substringAfter("base64,"))
        else {
            val req = HttpRequest.newBuilder(URI(photo)).timeout(Duration.ofSeconds(10)).GET().build()
            http.send(req, HttpResponse.BodyHandlers.ofByteArray()).takeIf { it.statusCode() == 200 }?.body()
        } ?: return@runCatching null
        org.jetbrains.skia.Image.makeFromEncoded(octets).toComposeImageBitmap()
    }.getOrNull()?.also { photos[photo] = it }
}

/** Cercle avec la photo du patient, ou ses initiales s'il n'en a pas. */
@Composable
fun Avatar(nom: String, photo: String = "", taille: Int = 38, contour: Boolean = false, modifier: Modifier = Modifier) {
    val valide = photo.startsWith("data:image") || photo.startsWith("https://")
    val image by produceState(if (valide) photos[photo] else null, photo) {
        if (valide && value == null) value = chargerPhoto(photo)
    }
    Box(
        modifier.size(taille.dp).clip(CircleShape).background(Indigo)
            .then(if (contour) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val img = image
        if (img != null) Image(img, contentDescription = nom, modifier = Modifier.size(taille.dp), contentScale = ContentScale.Crop)
        else Text(initiales(nom), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (taille * 0.38f).sp)
    }
}
