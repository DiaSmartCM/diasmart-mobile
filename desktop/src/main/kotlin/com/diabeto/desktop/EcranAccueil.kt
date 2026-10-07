package com.diabeto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diabeto.data.model.PrioriteSuivi
import kotlinx.coroutines.launch

private val iconesParcours: List<ImageVector> = listOf(
    Icons.Default.Info, Icons.Default.DateRange, Icons.Default.Warning, Icons.Default.ShoppingCart,
    Icons.Default.Person, Icons.Default.Favorite, Icons.Default.Notifications, Icons.Default.CheckCircle
)

/**
 * Accueil : raccourcis du jour puis « Tous les parcours » d'education,
 * des fiches simples a lire avec le patient ou a lui envoyer par message.
 */
@Composable
fun EcranAccueil(etat: EtatApp, onAller: (Onglet) -> Unit) {
    var fiche by remember { mutableStateOf<Pair<Int, Fiche>?>(null) }
    val f = fiche
    if (f != null) {
        LectureFiche(etat, PARCOURS[f.first], f.second) { fiche = null }
        return
    }
    val defil = rememberScrollState()
    Column(Modifier.fillMaxSize().defilementClavier(defil), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Accueil", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A3F55))
        Text("Bonjour ${etat.profil.nomComplet}", fontSize = 15.sp, color = Color.DarkGray)
        // Raccourcis
        val actifs = etat.patients.filter { it.uid !in etat.archives }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Raccourci("Mes patients", actifs.size.toString(), Indigo, Modifier.weight(1f)) { onAller(Onglet.PATIENTS) }
            Raccourci("À revoir en priorité", actifs.count { it.resultat.priorite == PrioriteSuivi.HAUTE }.toString(), Rouge,
                Modifier.weight(1f)) { onAller(Onglet.PATIENTS) }
            Raccourci("Messages non lus", etat.conversations.sumOf { it.nonLusMedecin }.toString(), Orange,
                Modifier.weight(1f)) { onAller(Onglet.MESSAGES) }
            Raccourci("Demandes de rendez-vous", etat.rdv.count { it.statut == "PENDING" }.toString(), IndigoFonce,
                Modifier.weight(1f)) { onAller(Onglet.RDV) }
        }
        Espace(4)
        Text("Tous les parcours", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A3F55))
        Text("Fiches d'éducation à lire avec vos patients ou à leur envoyer par la messagerie. Cliquez sur une fiche pour l'ouvrir.",
            fontSize = 13.sp, color = Color.Gray)
        PARCOURS.withIndex().chunked(3).forEach { rangee ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rangee.forEach { (i, p) -> CarteParcours(i, p, Modifier.weight(1f).fillMaxHeight()) { fiche = i to it } }
                repeat(3 - rangee.size) { Box(Modifier.weight(1f)) }
            }
        }
        Text(AVERTISSEMENT_PARCOURS, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun Raccourci(titre: String, valeur: String, couleur: Color, modifier: Modifier, onClic: () -> Unit) {
    Row(modifier.background(Color.White).border(1.dp, Bordure).clickable(onClick = onClic)) {
        Box(Modifier.size(4.dp, 64.dp).background(couleur))
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(valeur, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = couleur)
            Text(titre.uppercase(), fontSize = 11.sp, color = Color.Gray, letterSpacing = 0.4.sp)
        }
    }
}

@Composable
private fun CarteParcours(index: Int, p: Parcours, modifier: Modifier, onFiche: (Fiche) -> Unit) {
    Panneau(p.titre, modifier) {
        Box(Modifier.fillMaxWidth().height(110.dp).background(EnteteTableau), contentAlignment = Alignment.Center) {
            Icon(iconesParcours[index % iconesParcours.size], null, Modifier.size(64.dp), tint = Indigo)
        }
        Espace(4)
        p.fiches.forEach { f ->
            Row(Modifier.fillMaxWidth().border(1.dp, Bordure).clickable { onFiche(f) }.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(16.dp), tint = Indigo)
                EspaceL(8)
                Text(f.titre, fontSize = 13.sp, color = Color(0xFF22252F))
            }
        }
    }
}

@Composable
private fun LectureFiche(etat: EtatApp, p: Parcours, f: Fiche, onRetour: () -> Unit) {
    val scope = rememberCoroutineScope()
    var choix by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val defil = rememberScrollState()
    Column(Modifier.fillMaxSize().defilementClavier(defil), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); EspaceL(6); Text("Tous les parcours") }
            Text(p.titre, Modifier.weight(1f), fontSize = 14.sp, color = Color.Gray)
            Button(onClick = { choix = true }) { Icon(Icons.AutoMirrored.Filled.Send, null); EspaceL(6); Text("Envoyer à un patient") }
        }
        message?.let { Text(it, fontSize = 13.sp, color = if (it.startsWith("Envoyé")) Vert else Rouge) }
        Panneau(f.titre, Modifier.widthIn(max = 820.dp).fillMaxWidth()) {
            f.paragraphes.forEachIndexed { i, para ->
                if (i > 0) HorizontalDivider(color = Bordure)
                Text(para, fontSize = 15.sp, lineHeight = 22.sp, color = Color(0xFF22252F), modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        Text(AVERTISSEMENT_PARCOURS, fontSize = 12.sp, color = Color.Gray)
        // Autres fiches du meme parcours
        val autres = p.fiches.filter { it != f }
        if (autres.isNotEmpty()) Text("Dans le même parcours : " + autres.joinToString(" · ") { it.titre }, fontSize = 12.sp, color = Color.Gray)
    }
    if (choix) DialogueChoixPatient(etat, "Envoyer « ${f.titre} » à…", onFermer = { choix = false }) { patient ->
        choix = false
        scope.launch {
            message = runCatching {
                val c = etat.messagerieService.ouvrirAvec(patient.uid, patient.nomAffiche, etat.profil)
                etat.messagerieService.envoyer(c, etat.profil, f.texteMessage)
                "Envoyé à ${patient.nomAffiche} : il le lit dans la messagerie de son application."
            }.getOrElse { "Envoi impossible : ${it.message}" }
        }
    }
}
