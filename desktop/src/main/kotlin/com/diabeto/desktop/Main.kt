package com.diabeto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.diabeto.data.model.Affiliation
import com.diabeto.data.model.Etablissement
import com.diabeto.data.model.MembreEtablissement
import com.diabeto.data.model.PrioriteSuivi
import com.diabeto.data.model.RoleEtablissement
import com.diabeto.domain.CategorieTension
import com.diabeto.domain.EvaluationSuivi
import com.diabeto.domain.ReglesGlycemie
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

val Indigo = Color(0xFF6771E4)
val Rouge = Color(0xFFD32F2F)
val Orange = Color(0xFFF57C00)
val Vert = Color(0xFF2E7D32)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "DiaSmart",
        icon = painterResource("icone.png"),
        state = rememberWindowState(width = 1280.dp, height = 820.dp)
    ) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Indigo, secondary = Indigo)) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFFF6F7FB)) { AppDiaSmart() }
        }
    }
}

@Composable
private fun AppDiaSmart() {
    val fb = remember { FirebaseRest() }
    var profil by remember { mutableStateOf<Profil?>(null) }
    val p = profil
    if (p == null) EcranConnexion(fb) { profil = it }
    else EcranPrincipal(remember(p.uid) { EtatApp(fb, p) }) { fb.deconnexion(); profil = null }
}

// ── Donnees partagees par tous les ecrans ────────────────────────────────

class EtatApp(val fb: FirebaseRest, val profil: Profil) {
    val etabService = ServiceEtablissement(fb)
    val patientsService = ServicePatients(fb)
    val rdvService = ServiceRdv(fb)

    var chargement by mutableStateOf(false)
    var erreur by mutableStateOf<String?>(null)
    var affiliation by mutableStateOf<Affiliation?>(null)
    var etablissement by mutableStateOf<Etablissement?>(null)
    var membres by mutableStateOf<List<MembreEtablissement>>(emptyList())
    var patients by mutableStateOf<List<PatientSuivi>>(emptyList())
    var rdv by mutableStateOf<List<DemandeRdv>>(emptyList())
    var dejaCharge by mutableStateOf(false)

    /** Membre de l'equipe d'un etablissement (admin ou soignant), sinon null. */
    val equipe: Affiliation? get() = affiliation?.takeIf { it.role != RoleEtablissement.PATIENT }

    suspend fun charger() {
        if (chargement) return
        chargement = true; erreur = null
        try {
            val aff = etabService.monAffiliation(profil.uid)
            affiliation = aff
            val etabId = aff?.takeIf { it.role != RoleEtablissement.PATIENT }?.etablissementId
            val inscrits = if (etabId != null) {
                etablissement = etabService.etablissement(etabId)
                membres = runCatching { etabService.membres(etabId) }.getOrDefault(emptyList())
                runCatching { etabService.patients(etabId) }.getOrDefault(emptyList())
            } else {
                etablissement = null; membres = emptyList(); emptyList()
            }
            rdv = runCatching { rdvService.demandes(profil.uid) }.getOrDefault(rdv)
            patients = patientsService.patientsSuivis(profil.uid, inscrits)
            dejaCharge = true
        } catch (e: Exception) {
            erreur = e.message ?: "Chargement impossible"
        } finally {
            chargement = false
        }
    }
}

// ── Ecran principal ──────────────────────────────────────────────────────

enum class Onglet(val libelle: String) {
    TABLEAU("Tableau de bord"), PATIENTS("Patients"), RDV("Rendez-vous"), ETABLISSEMENT("Établissement"), OUTILS("Outils")
}

@Composable
private fun EcranPrincipal(etat: EtatApp, onDeconnexion: () -> Unit) {
    var onglet by remember { mutableStateOf(Onglet.TABLEAU) }
    var patientOuvert by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(etat) { etat.charger() }

    Row(Modifier.fillMaxSize()) {
        NavigationRail {
            Spacer(Modifier.height(12.dp))
            Text("DiaSmart", fontWeight = FontWeight.Bold, color = Indigo, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Onglet.entries.forEach { o ->
                NavigationRailItem(onglet == o, { onglet = o; if (o == Onglet.PATIENTS) patientOuvert = null },
                    icon = { Icon(o.icone(), null) }, label = { Text(o.libelle, fontSize = 11.sp) })
            }
            Spacer(Modifier.weight(1f))
            NavigationRailItem(false, onDeconnexion,
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }, label = { Text("Quitter", fontSize = 11.sp) })
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            when (onglet) {
                Onglet.TABLEAU -> EcranTableauDeBord(etat,
                    onOuvrirPatient = { patientOuvert = it; onglet = Onglet.PATIENTS },
                    onAller = { onglet = it })
                Onglet.PATIENTS -> EcranPatients(etat, patientOuvert) { patientOuvert = it }
                Onglet.RDV -> EcranRendezVous(etat)
                Onglet.ETABLISSEMENT -> EcranEtablissement(etat)
                Onglet.OUTILS -> EcranOutils()
            }
        }
    }
}

private fun Onglet.icone() = when (this) {
    Onglet.TABLEAU -> Icons.Default.Home
    Onglet.PATIENTS -> Icons.Default.Person
    Onglet.RDV -> Icons.Default.DateRange
    Onglet.ETABLISSEMENT -> Icons.Default.Place
    Onglet.OUTILS -> Icons.Default.Build
}

// ── Petits elements communs ──────────────────────────────────────────────

@Composable
fun Chargement(texte: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(texte)
        }
    }
}

@Composable
fun Message(texte: String, onReessayer: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(texte, fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onReessayer) { Text("Réessayer") }
        }
    }
}

@Composable
fun Compteur(titre: String, valeur: String, modifier: Modifier = Modifier, couleur: Color = Indigo) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(valeur, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = couleur)
            Text(titre, fontSize = 13.sp, color = Color.Gray)
        }
    }
}

@Composable
fun Pastille(texte: String, couleur: Color) {
    Text(texte, fontSize = 12.sp, color = couleur, fontWeight = FontWeight.Medium,
        modifier = Modifier.background(couleur.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
fun PointPriorite(p: PrioriteSuivi, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(p.couleur(), RoundedCornerShape(50)))
        Spacer(Modifier.width(6.dp))
        Text(p.libelle, fontSize = 13.sp)
    }
}

fun PrioriteSuivi.couleur() = when (this) {
    PrioriteSuivi.HAUTE -> Rouge
    PrioriteSuivi.MOYENNE -> Orange
    PrioriteSuivi.BASSE -> Vert
    PrioriteSuivi.INCONNUE -> Color.Gray
}

fun CategorieTension.couleur() = when (this) {
    CategorieTension.BASSE -> Color(0xFF1E88E5)
    CategorieTension.NORMALE -> Vert
    CategorieTension.NORMALE_HAUTE -> Color(0xFFF9A825)
    CategorieTension.HTA_1 -> Orange
    CategorieTension.HTA_2 -> Rouge
    CategorieTension.TRES_ELEVEE -> Color(0xFF8E0000)
}

fun couleurGlycemie(v: Double) = when {
    v < 70 -> Rouge
    v > 180 -> Orange
    else -> Vert
}

fun LocalDate.texte() = "%02d/%02d/%d".format(dayOfMonth, monthNumber, year)
fun LocalDateTime.jour() = "%02d/%02d/%d".format(dayOfMonth, monthNumber, year)
fun LocalDateTime.heure() = "%02dh%02d".format(hour, minute)

// ── Outils ───────────────────────────────────────────────────────────────

@Composable
private fun EcranOutils() {
    var hba1c by remember { mutableStateOf("") }
    var glycemie by remember { mutableStateOf("") }
    Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Outils", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("HbA1c → glycémie moyenne estimée (ADAG)", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(hba1c, { hba1c = it.take(5) }, label = { Text("HbA1c (%)") }, singleLine = true)
            hba1c.replace(',', '.').toDoubleOrNull()?.takeIf { it in 3.0..20.0 }?.let { v ->
                Text("≈ ${ReglesGlycemie.glycemieMoyenneDepuisHbA1c(v).toInt()} mg/dL · " +
                    ReglesGlycemie.interpreterHbA1c(v).getDisplayName())
            }
        } }
        Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Statut d'une glycémie", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(glycemie, { glycemie = it.take(5) }, label = { Text("Glycémie (mg/dL)") }, singleLine = true)
            glycemie.replace(',', '.').toDoubleOrNull()?.takeIf { it in 10.0..800.0 }?.let { v ->
                Text("${ReglesGlycemie.statutGlycemie(v)} · HbA1c équivalente ≈ ${EvaluationSuivi.unChiffre(ReglesGlycemie.hba1cDepuisGlycemieMoyenne(v))} %")
            }
        } }
    }
}
