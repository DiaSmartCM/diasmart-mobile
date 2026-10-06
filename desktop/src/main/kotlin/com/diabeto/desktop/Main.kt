package com.diabeto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.key
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
import com.diabeto.domain.ReglesTension
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
    // Apres une connexion par email : choisir le mot de passe de ce PC
    var creerMdpLocal by remember { mutableStateOf(false) }
    // Session gardee sur ce PC : on l'ouvre avec le mot de passe local
    var enregistree by remember { mutableStateOf(SessionPc.lire()) }
    var messageConnexion by remember { mutableStateOf<String?>(null) }
    val p = profil
    val e = enregistree
    when {
        p != null && creerMdpLocal -> EcranCreerMotDePasseLocal(fb, p) { creerMdpLocal = false; enregistree = SessionPc.lire() }
        p != null -> EcranPrincipal(remember(p.uid) { EtatApp(fb, p) }) {
            // Verrouiller : la session reste sur ce PC, le mot de passe local la rouvre
            fb.deconnexion(); profil = null; enregistree = SessionPc.lire()
        }
        e != null -> EcranDeverrouillage(fb, e,
            onOuvert = { profil = it },
            onAutreCompte = { messageConnexion = it; enregistree = null })
        else -> key(messageConnexion) {
            EcranConnexion(fb, messageConnexion) { profil = it; creerMdpLocal = true; messageConnexion = null }
        }
    }
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
            runCatching { SessionPc.retenirEtablissement(etablissement?.nom) }
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
                icon = { Icon(Icons.Default.Lock, null) }, label = { Text("Verrouiller", fontSize = 11.sp) })
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
    CategorieTension.OBJECTIF, CategorieTension.OBJECTIF_AGE, CategorieTension.OBJECTIF_PERSO -> Vert
    CategorieTension.AU_DESSUS_OBJECTIF -> Color(0xFFF9A825)
    CategorieTension.HTA_DOMICILE -> Orange
    CategorieTension.URGENCE -> Color(0xFFC62828)
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
    Column(Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
        OutilTension()
        OutilOrthostatique()
    }
}

private fun entier(s: String) = s.trim().toIntOrNull()

@Composable
private fun ChampNombre(valeur: String, onChange: (String) -> Unit, libelle: String, modifier: Modifier = Modifier) =
    OutlinedTextField(valeur, { onChange(it.filter(Char::isDigit).take(3)) }, label = { Text(libelle) }, singleLine = true, modifier = modifier)

/** Tension arterielle : categorie (reperes ADA 2025 / ESC 2024), pression pulsee, PAM. */
@Composable
private fun OutilTension() {
    var pas by remember { mutableStateOf("") }
    var pad by remember { mutableStateOf("") }
    var fc by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tension artérielle (TA)", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampNombre(pas, { pas = it }, "PAS (mmHg)", Modifier.weight(1f))
            ChampNombre(pad, { pad = it }, "PAD (mmHg)", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampNombre(fc, { fc = it }, "FC (bpm, facultatif)", Modifier.weight(1f))
            ChampNombre(age, { age = it }, "Âge (facultatif)", Modifier.weight(1f))
        }
        val s = entier(pas); val d = entier(pad)
        if (s != null && d != null) {
            if (!ReglesTension.valide(s, d)) {
                Text("Valeurs à vérifier (la PAS doit être plus haute que la PAD).", color = Color.Gray, fontSize = 13.sp)
            } else {
                val c = ReglesTension.categorie(s, d, entier(age))
                Pastille(c.libelle, c.couleur())
                val pp = ReglesTension.pressionPulsee(s, d)
                Text("Pression pulsée : $pp mmHg" + if (ReglesTension.pressionPulseeElevee(s, d)) " (> 60 : rigidité artérielle possible)" else "")
                Text("Pression artérielle moyenne (PAM) : ${ReglesTension.pam(s, d)} mmHg")
                entier(fc)?.takeIf { it >= 100 }?.let {
                    Text("FC $it bpm au repos : tachycardie. Si elle persiste, penser à une neuropathie autonome.", color = Orange, fontSize = 13.sp)
                }
            }
        }
        Text("Repères : objectif < 130/80 si toléré ; HTA au cabinet ≥ 130/80 (ADA) ou ≥ 140/90 (ESC), à confirmer sur 2 consultations ; " +
            "automesure ≥ 135/85 ; urgence ≥ 180/110. Objectif assoupli (PAS 130-139) à partir de 65 ans. " +
            "IEC ou ARA2 à privilégier en cas d'albuminurie. Objectifs individuels fixés par le médecin traitant.",
            fontSize = 12.sp, color = Color.Gray)
    } }
}

/** Hypotension orthostatique : couche puis debout (1 et 3 min). */
@Composable
private fun OutilOrthostatique() {
    var pasC by remember { mutableStateOf("") }
    var padC by remember { mutableStateOf("") }
    var pasD by remember { mutableStateOf("") }
    var padD by remember { mutableStateOf("") }
    Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Test d'hypotension orthostatique", fontWeight = FontWeight.SemiBold)
        Text("Mesure couché après 5 min de repos, puis debout à 1 et 3 min : entrez la plus basse.", fontSize = 13.sp, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampNombre(pasC, { pasC = it }, "PAS couché", Modifier.weight(1f))
            ChampNombre(padC, { padC = it }, "PAD couché", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampNombre(pasD, { pasD = it }, "PAS debout", Modifier.weight(1f))
            ChampNombre(padD, { padD = it }, "PAD debout", Modifier.weight(1f))
        }
        val sc = entier(pasC); val dc = entier(padC); val sd = entier(pasD); val dd = entier(padD)
        if (sc != null && dc != null && sd != null && dd != null && ReglesTension.valide(sc, dc) && ReglesTension.valide(sd, dd)) {
            val baisseS = sc - sd; val baisseD = dc - dd
            Text("Baisse : PAS $baisseS mmHg, PAD $baisseD mmHg")
            if (baisseS >= 20 || baisseD >= 10) Pastille("Hypotension orthostatique (baisse PAS ≥ 20 ou PAD ≥ 10)", Rouge)
            else Pastille("Pas d'hypotension orthostatique", Vert)
        }
    } }
}
