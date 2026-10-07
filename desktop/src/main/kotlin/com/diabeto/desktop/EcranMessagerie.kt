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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            // ── Colonne de gauche : conversations
            Column(Modifier.width(300.dp).fillMaxHeight().background(Color(0xFFF7F8FB))) {
                Row(Modifier.fillMaxWidth().background(Indigo).padding(10.dp)) {
                    OutlinedTextField(recherche, { recherche = it.take(40) }, placeholder = { Text("Rechercher", color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().background(Color.White))
                }
                val etatListe = rememberLazyListState()
                LazyColumn(Modifier.weight(1f).defilementClavier(etatListe, focusAuDepart = false), state = etatListe) {
                    items(liste, key = { it.id }) { c ->
                        LigneConversation(c, c.id == choisie) {
                            choisie = c.id
                            erreur = null
                        }
                        HorizontalDivider(color = Bordure)
                    }
                }
                if (liste.isEmpty()) Text(
                    if (etat.conversations.isEmpty()) "Aucune conversation. « Nouveau message » pour écrire à un patient."
                    else "Aucun résultat.", Modifier.padding(16.dp), fontSize = 13.sp, color = Color.Gray)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Bordure))
            // ── Conversation ouverte
            val c = etat.conversations.firstOrNull { it.id == choisie }
            Box(Modifier.weight(1f).fillMaxHeight()) {
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

@Composable
private fun Initiales(nom: String, taille: Int = 38) {
    val ini = nom.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    Box(Modifier.size(taille.dp).background(Indigo), contentAlignment = Alignment.Center) {
        Text(ini, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (taille / 2.6).sp)
    }
}

@Composable
private fun LigneConversation(c: Conversation, active: Boolean, onClic: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (active) Color.White else Color.Transparent).clickable(onClick = onClic)
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(3.dp, 38.dp).background(if (active) Indigo else Color.Transparent))
        EspaceL(6)
        Initiales(c.patientNom)
        EspaceL(10)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.patientNom.ifBlank { "Patient" }, Modifier.weight(1f), fontWeight = if (c.nonLusMedecin > 0) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.dernierMessageAt?.let { it.jour().take(5) } ?: "", fontSize = 11.sp, color = Color.Gray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.dernierMessage.ifBlank { "Nouvelle conversation" }, Modifier.weight(1f), fontSize = 12.sp, color = Color.DarkGray,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (c.nonLusMedecin > 0) Text(c.nonLusMedecin.toString(), Modifier.background(Rouge).padding(horizontal = 6.dp, vertical = 1.dp),
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        Row(Modifier.fillMaxWidth().border(1.dp, Bordure).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Initiales(c.patientNom, 34)
            Text(c.patientNom.ifBlank { "Patient" }, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (etat.patients.any { it.uid == c.patientId }) OutlinedButton(onClick = { onDossier(c.patientId) }) { Text("Ouvrir le dossier") }
        }
        Text("Le patient lit vos messages et vous répond dans son application DiaSmart. Pour une urgence, appelez-le.",
            Modifier.fillMaxWidth().background(EnteteTableau).padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 12.sp, color = IndigoFonce)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val liste = messages
            if (liste == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else if (liste.isEmpty()) Text("Aucun message pour l'instant. Écrivez le premier ci-dessous.",
                Modifier.align(Alignment.Center), color = Color.Gray)
            else LazyColumn(Modifier.fillMaxSize().defilementClavier(defil, focusAuDepart = false).padding(horizontal = 16.dp),
                state = defil, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(liste.size) { i ->
                    val m = liste[i]
                    val jour = m.date?.date
                    if (i == 0 || liste[i - 1].date?.date != jour) Text(jour?.texte() ?: "",
                        Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = Color.Gray)
                    Bulle(m, m.envoyeurId == etat.profil.uid)
                }
            }
        }
        erreur?.let { Text(it, Modifier.padding(horizontal = 14.dp), color = Rouge, fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth().border(1.dp, Bordure).padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(saisie, { saisie = it.take(4000) },
                placeholder = { Text("Votre message… (Entrée pour envoyer, Maj + Entrée pour aller à la ligne)") },
                maxLines = 5,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).onPreviewKeyEvent { ev ->
                    if (ev.key == Key.Enter && !ev.isShiftPressed) {
                        if (ev.type == KeyEventType.KeyDown) envoyer()
                        true
                    } else false
                })
            Button(onClick = { envoyer() }, enabled = saisie.isNotBlank() && !envoi) {
                if (envoi) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else { Icon(Icons.AutoMirrored.Filled.Send, null); EspaceL(6); Text("Envoyer") }
            }
        }
    }
}

@Composable
private fun Bulle(m: MessageChat, moi: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (moi) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = if (moi) Alignment.End else Alignment.Start) {
            Row(Modifier.height(IntrinsicSize.Min).background(if (moi) Indigo else Color(0xFFF0F1F6))) {
                if (!moi) Box(Modifier.width(3.dp).fillMaxHeight().background(Indigo))
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(m.envoyeurNom.ifBlank { if (moi) "Moi" else "Patient" }, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (moi) Color.White else IndigoFonce)
                    if (m.contenu.isNotBlank()) Text(m.contenu, fontSize = 14.sp, color = if (moi) Color.White else Color(0xFF22252F))
                    if (m.pieceJointeNom.isNotBlank()) Text("📎 ${m.pieceJointeNom}",
                        Modifier.clickable(enabled = m.pieceJointeUrl.startsWith("https://")) {
                            runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI.create(m.pieceJointeUrl)) }
                        }, fontSize = 13.sp, color = if (moi) Color.White else Indigo, fontWeight = FontWeight.Medium)
                }
            }
            Text(m.date?.heure() ?: "", fontSize = 10.sp, color = Color.Gray)
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
                            Initiales(p.nomAffiche, 30); EspaceL(10)
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
