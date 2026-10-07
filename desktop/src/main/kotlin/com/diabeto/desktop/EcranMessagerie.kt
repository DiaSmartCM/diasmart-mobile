package com.diabeto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Messagerie avec les patients : memes conversations que l'app mobile
 * (le patient lit et repond depuis son telephone, et y recoit une notification).
 * [ouvrirAvec] : uid d'un patient dont on veut ouvrir (ou creer) la conversation.
 */
@Composable
fun EcranMessagerie(etat: EtatApp, ouvrirAvec: String?, onOuvert: () -> Unit, onDossier: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val service = etat.messagerieService
    var choisie by remember { mutableStateOf<String?>(null) }
    var recherche by remember { mutableStateOf("") }
    var nouveau by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }

    // Liste des conversations, rafraichie regulierement
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { etat.conversations = service.conversations(etat.profil.uid) }
                .onFailure { erreur = it.message ?: "Messagerie indisponible." }
            delay(20_000)
        }
    }
    // Ouverture depuis « Message » (liste des patients ou dossier)
    LaunchedEffect(ouvrirAvec) {
        val uid = ouvrirAvec ?: return@LaunchedEffect
        val p = etat.patients.firstOrNull { it.uid == uid }
        runCatching { service.ouvrirAvec(uid, p?.nomAffiche ?: "Patient", etat.profil) }
            .onSuccess { c ->
                if (etat.conversations.none { it.id == c.id }) etat.conversations = listOf(c) + etat.conversations
                choisie = c.id
            }
            .onFailure { erreur = it.message ?: "Conversation impossible." }
        onOuvert()
    }

    val texte = recherche.trim()
    val liste = etat.conversations.filter { texte.isEmpty() || it.patientNom.contains(texte, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Messagerie", Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A3F55))
            Button(onClick = { nouveau = true }) { Icon(Icons.Default.Add, null); EspaceL(6); Text("Nouveau message") }
        }
        Espace(10)
        Row(Modifier.weight(1f).fillMaxWidth().background(Color.White).border(1.dp, Bordure)) {
            // ── Colonne de gauche : cartes des conversations
            Column(Modifier.width(320.dp).fillMaxHeight().background(Color.White)) {
                OutlinedTextField(recherche, { recherche = it.take(40) }, placeholder = { Text("Rechercher un patient", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(12.dp))
                val etatListe = rememberLazyListState()
                LazyColumn(Modifier.weight(1f).defilementClavier(etatListe, focusAuDepart = false), state = etatListe,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(liste, key = { it.id }) { c ->
                        CarteConversation(c, photoDe(etat, c.patientId), c.id == choisie) {
                            choisie = c.id
                            erreur = null
                        }
                    }
                }
                if (liste.isEmpty()) Text(
                    if (etat.conversations.isEmpty()) "Aucune conversation. « Nouveau message » pour écrire à un patient."
                    else "Aucun résultat.", Modifier.padding(16.dp), fontSize = 13.sp, color = Color.Gray)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Bordure))
            // ── Conversation ouverte
            val c = etat.conversations.firstOrNull { it.id == choisie }
            Box(Modifier.weight(1f).fillMaxHeight().background(FondFil)) {
                if (c == null) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Choisissez une conversation à gauche,", color = Color.Gray)
                    Text("ou cliquez sur « Nouveau message ».", color = Color.Gray)
                    erreur?.let { Espace(8); Text(it, color = Rouge, fontSize = 13.sp) }
                }
                else FilConversation(etat, c, onDossier)
            }
        }
    }

    if (nouveau) DialogueChoixPatient(etat, onFermer = { nouveau = false }) { p ->
        nouveau = false
        scope.launch {
            runCatching { service.ouvrirAvec(p.uid, p.nomAffiche, etat.profil) }
                .onSuccess { cv ->
                    if (etat.conversations.none { it.id == cv.id }) etat.conversations = listOf(cv) + etat.conversations
                    choisie = cv.id
                }
                .onFailure { erreur = it.message ?: "Conversation impossible." }
        }
    }
}

/** Fond clair (lavande) de la zone des messages. */
private val FondFil = Color(0xFFEEF0FA)

/** Photo de profil du patient (vide s'il n'en a pas ou s'il n'est plus suivi). */
private fun photoDe(etat: EtatApp, uid: String) = etat.patients.firstOrNull { it.uid == uid }?.identite?.photo ?: ""

/** Carte d'une conversation : avatar, nom, heure, apercu du dernier message, nombre de messages non lus. */
@Composable
private fun CarteConversation(c: Conversation, photo: String, active: Boolean, onClic: () -> Unit) {
    val aujourdhui = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val quand = c.dernierMessageAt?.let { if (it.date == aujourdhui) it.heure() else it.jour().take(5) } ?: ""
    Row(Modifier.fillMaxWidth().background(if (active) Indigo else Color(0xFFF3F4FA)).clickable(onClick = onClic).padding(12.dp)) {
        Avatar(c.patientNom.ifBlank { "Patient" }, photo, 42, contour = active)
        EspaceL(10)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.patientNom.ifBlank { "Patient" }, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    color = if (active) Color.White else Color(0xFF22252F), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(quand, fontSize = 11.sp, color = if (active) Color.White.copy(alpha = 0.85f) else Color.Gray)
            }
            Espace(3)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(c.dernierMessage.ifBlank { "Nouvelle conversation" }, Modifier.weight(1f), fontSize = 12.sp,
                    color = if (active) Color.White.copy(alpha = 0.9f) else Color.DarkGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (c.nonLusMedecin > 0) {
                    EspaceL(6)
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Vert), contentAlignment = Alignment.Center) {
                        Text(c.nonLusMedecin.coerceAtMost(99).toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilConversation(etat: EtatApp, c: Conversation, onDossier: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val service = etat.messagerieService
    var messages by remember(c.id) { mutableStateOf<List<MessageChat>?>(null) }
    var saisie by remember(c.id) { mutableStateOf("") }
    var envoi by remember(c.id) { mutableStateOf(false) }
    var erreur by remember(c.id) { mutableStateOf<String?>(null) }
    val defil = rememberLazyListState()
    val photo = photoDe(etat, c.patientId)

    suspend fun recharger() {
        runCatching { service.messages(c.id) }
            .onSuccess { messages = it; erreur = null }
            .onFailure { erreur = it.message ?: "Messages indisponibles." }
    }
    LaunchedEffect(c.id) {
        service.marquerLu(c)
        etat.conversations = etat.conversations.map { if (it.id == c.id) it.copy(nonLusMedecin = 0) else it }
        while (true) { recharger(); delay(8_000) }
    }
    LaunchedEffect(messages?.size) {
        val n = messages?.size ?: 0
        if (n > 0) defil.scrollToItem(n - 1)
    }
    fun envoyer() {
        val t = saisie.trim()
        if (t.isEmpty() || envoi) return
        envoi = true
        scope.launch {
            try {
                // Compteur a jour avant d'ecrire (le patient a pu lire entre-temps)
                val actuelle = runCatching { service.conversations(etat.profil.uid).firstOrNull { it.id == c.id } }.getOrNull() ?: c
                service.envoyer(actuelle, etat.profil, t)
                saisie = ""
                recharger()
                etat.conversations = etat.conversations.map {
                    if (it.id == c.id) it.copy(dernierMessage = t.take(500), nonLusPatient = actuelle.nonLusPatient + 1) else it
                }
            } catch (e: Exception) {
                erreur = e.message ?: "Envoi impossible."
            } finally { envoi = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // En-tete : avatar et nom du patient
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(c.patientNom.ifBlank { "Patient" }, photo, 42)
            Column(Modifier.weight(1f)) {
                Text(c.patientNom.ifBlank { "Patient" }, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Répond depuis son application DiaSmart. Pour une urgence, appelez-le.", fontSize = 12.sp, color = Color.Gray,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (etat.patients.any { it.uid == c.patientId }) OutlinedButton(onClick = { onDossier(c.patientId) }) { Text("Ouvrir le dossier") }
        }
        HorizontalDivider(color = Bordure)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val liste = messages
            if (liste == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else if (liste.isEmpty()) Text("Aucun message pour l'instant. Écrivez le premier ci-dessous.",
                Modifier.align(Alignment.Center), color = Color.Gray)
            else {
                // Mes derniers messages pas encore lus par le patient (son compteur de non lus)
                val miens = liste.indices.filter { liste[it].envoyeurId == etat.profil.uid }
                val nonLus = miens.takeLast(c.nonLusPatient.coerceAtLeast(0)).toSet()
                LazyColumn(Modifier.fillMaxSize().defilementClavier(defil, focusAuDepart = false), state = defil,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(liste.size) { i ->
                        val m = liste[i]
                        val jour = m.date?.date
                        if (i == 0 || liste[i - 1].date?.date != jour) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center) {
                            Text(jour?.texte() ?: "", Modifier.background(Color.White).padding(horizontal = 10.dp, vertical = 3.dp),
                                fontSize = 11.sp, color = Color.Gray)
                        }
                        val moi = m.envoyeurId == etat.profil.uid
                        Bulle(m, moi, lu = i !in nonLus, nom = c.patientNom.ifBlank { "Patient" }, photo = photo)
                    }
                }
            }
        }
        erreur?.let { Text(it, Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 4.dp),
            color = Rouge, fontSize = 12.sp) }
        // Saisie en bas
        Row(Modifier.fillMaxWidth().background(Color.White).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(saisie, { saisie = it.take(4000) },
                placeholder = { Text("Écrire un message… (Entrée pour envoyer, Maj + Entrée pour aller à la ligne)", color = Color.Gray) },
                maxLines = 5,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).onPreviewKeyEvent { ev ->
                    if (ev.key == Key.Enter && !ev.isShiftPressed) {
                        if (ev.type == KeyEventType.KeyDown) envoyer()
                        true
                    } else false
                })
            Button(onClick = { envoyer() }, modifier = Modifier.size(52.dp), enabled = saisie.isNotBlank() && !envoi,
                contentPadding = PaddingValues(0.dp)) {
                if (envoi) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.AutoMirrored.Filled.Send, "Envoyer")
            }
        }
    }
}

/** Bulle rectangulaire avec une petite pointe en bas, du cote de celui qui ecrit. */
private class FormeBulle(private val aDroite: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = with(density) { POINTE.toPx() }
        val w = size.width
        val h = size.height
        val bas = h - t
        val chemin = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            if (aDroite) { lineTo(w, h); lineTo(w - 1.6f * t, bas); lineTo(0f, bas) }
            else { lineTo(w, bas); lineTo(1.6f * t, bas); lineTo(0f, h) }
            close()
        }
        return Outline.Generic(chemin)
    }
}

private val POINTE = 8.dp
private val BULLE_MOI = FormeBulle(aDroite = true)
private val BULLE_PATIENT = FormeBulle(aDroite = false)

@Composable
private fun Bulle(m: MessageChat, moi: Boolean, lu: Boolean, nom: String, photo: String) {
    val forme = if (moi) BULLE_MOI else BULLE_PATIENT
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (moi) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom) {
        if (!moi) { Avatar(nom, photo, 30, modifier = Modifier.padding(bottom = 16.dp)); EspaceL(8) }
        Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = if (moi) Alignment.End else Alignment.Start) {
            Column(
                Modifier.background(if (moi) Indigo else Color.White, forme)
                    .then(if (moi) Modifier else Modifier.border(1.dp, Color(0xFFE1E4F0), forme))
                    .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 9.dp + POINTE)
            ) {
                // Texte copiable (code, conseil...) : selection visible meme sur fond indigo
                if (m.contenu.isNotBlank()) CompositionLocalProvider(LocalTextSelectionColors provides
                    if (moi) TextSelectionColors(Color.White, Color.White.copy(alpha = 0.4f)) else LocalTextSelectionColors.current) {
                    Copiable { Text(m.contenu, fontSize = 14.sp, color = if (moi) Color.White else Color(0xFF22252F)) }
                }
                if (m.pieceJointeNom.isNotBlank()) Text("📎 ${m.pieceJointeNom}",
                    Modifier.clickable(enabled = m.pieceJointeUrl.startsWith("https://")) {
                        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI.create(m.pieceJointeUrl)) }
                    }, fontSize = 13.sp, color = if (moi) Color.White else Indigo, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.date?.heure() ?: "", fontSize = 10.sp, color = Color.Gray)
                // Coches : ✓ envoye, ✓✓ (indigo) lu par le patient
                if (moi) { EspaceL(4); Text(if (lu) "✓✓" else "✓", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (lu) Indigo else Color.Gray) }
            }
        }
    }
}

/** Choisir le patient a qui ecrire (liste des patients suivis). */
@Composable
fun DialogueChoixPatient(etat: EtatApp, titre: String = "Écrire à un patient", onFermer: () -> Unit, onChoix: (PatientSuivi) -> Unit) {
    var recherche by remember { mutableStateOf("") }
    val t = recherche.trim()
    val liste = etat.patients.filter { it.uid !in etat.archives }
        .filter { t.isEmpty() || it.nomAffiche.contains(t, ignoreCase = true) }
        .sortedBy { it.nomAffiche.lowercase() }
    AlertDialog(
        onDismissRequest = onFermer,
        confirmButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
        title = { Text(titre) },
        text = {
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(recherche, { recherche = it.take(40) }, placeholder = { Text("Nom du patient") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (liste.isEmpty()) Text("Aucun patient.", color = Color.Gray, fontSize = 13.sp)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(liste, key = { it.uid }) { p ->
                        Row(Modifier.fillMaxWidth().clickable { onChoix(p) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.nomAffiche, p.identite.photo, 32); EspaceL(10)
                            Text(p.nomAffiche, Modifier.weight(1f), fontSize = 14.sp)
                            PointPriorite(p.resultat.priorite)
                        }
                        HorizontalDivider(color = Bordure)
                    }
                }
            }
        }
    )
}
