package com.diabeto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.diabeto.data.model.Affiliation
import com.diabeto.data.model.Etablissement
import com.diabeto.data.model.PrioriteSuivi
import com.diabeto.data.model.RoleEtablissement
import com.diabeto.domain.EvaluationSuivi
import com.diabeto.domain.ReglesGlycemie
import kotlinx.coroutines.launch

private val Indigo = Color(0xFF6771E4)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "DiaSmart",
        icon = painterResource("icone.png"),
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Indigo, secondary = Indigo)) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFFF6F7FB)) { AppDiaSmart() }
        }
    }
}

@Composable
private fun AppDiaSmart() {
    val fb = remember { FirebaseRest() }
    var session by remember { mutableStateOf<FirebaseRest.Session?>(null) }
    val s = session
    if (s == null) EcranConnexion(fb) { session = it }
    else EcranPrincipal(fb, s) { fb.deconnexion(); session = null }
}

// ── Connexion ────────────────────────────────────────────────────────────

@Composable
private fun EcranConnexion(fb: FirebaseRest, onConnecte: (FirebaseRest.Session) -> Unit) {
    var email by remember { mutableStateOf("") }
    var mdp by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun valider() {
        if (email.isBlank() || mdp.isBlank() || enCours) return
        enCours = true; erreur = null
        scope.launch {
            try { onConnecte(fb.connexion(email, mdp)) }
            catch (e: Exception) { erreur = e.message ?: "Connexion impossible" }
            finally { enCours = false }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 420.dp)) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("DiaSmart", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Indigo)
                Text("Version PC pour les soignants. Connectez-vous avec le meme compte que sur l'application mobile.",
                    fontSize = 14.sp, color = Color.DarkGray)
                OutlinedTextField(email, { email = it.take(120) }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(mdp, { mdp = it.take(128) }, label = { Text("Mot de passe") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                erreur?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                Button(onClick = { valider() }, enabled = !enCours, modifier = Modifier.fillMaxWidth()) {
                    if (enCours) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Se connecter")
                }
            }
        }
    }
}

// ── Ecran principal ──────────────────────────────────────────────────────

private enum class Onglet { ETABLISSEMENT, OUTILS }

@Composable
private fun EcranPrincipal(fb: FirebaseRest, session: FirebaseRest.Session, onDeconnexion: () -> Unit) {
    var onglet by remember { mutableStateOf(Onglet.ETABLISSEMENT) }
    Row(Modifier.fillMaxSize()) {
        NavigationRail {
            Spacer(Modifier.height(12.dp))
            NavigationRailItem(onglet == Onglet.ETABLISSEMENT, { onglet = Onglet.ETABLISSEMENT },
                icon = { Icon(Icons.Default.Home, null) }, label = { Text("Établissement") })
            NavigationRailItem(onglet == Onglet.OUTILS, { onglet = Onglet.OUTILS },
                icon = { Icon(Icons.Default.Build, null) }, label = { Text("Outils") })
            Spacer(Modifier.weight(1f))
            NavigationRailItem(false, onDeconnexion,
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }, label = { Text("Quitter") })
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            when (onglet) {
                Onglet.ETABLISSEMENT -> EcranEtablissement(fb, session)
                Onglet.OUTILS -> EcranOutils()
            }
        }
    }
}

// ── Espace etablissement ─────────────────────────────────────────────────

private enum class Filtre(val libelle: String) { TOUS("Tous"), A_RISQUE("À revoir en priorité"), PERDUS("Perdus de vue") }

@Composable
private fun EcranEtablissement(fb: FirebaseRest, session: FirebaseRest.Session) {
    val depot = remember { DepotEtablissement(fb) }
    var chargement by remember { mutableStateOf(true) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var affiliation by remember { mutableStateOf<Affiliation?>(null) }
    var etab by remember { mutableStateOf<Etablissement?>(null) }
    var lignes by remember { mutableStateOf<List<LigneSuivi>>(emptyList()) }
    var filtre by remember { mutableStateOf(Filtre.TOUS) }
    var actualiser by remember { mutableStateOf(0) }

    LaunchedEffect(actualiser) {
        chargement = true; erreur = null
        try {
            val aff = depot.monAffiliation(session.uid)
            affiliation = aff
            if (aff != null && aff.role != RoleEtablissement.PATIENT) {
                etab = depot.etablissement(aff.etablissementId)
                lignes = depot.suivi(aff.etablissementId)
            }
        } catch (e: Exception) {
            erreur = e.message ?: "Chargement impossible"
        } finally { chargement = false }
    }

    val aff = affiliation
    val e = etab
    when {
        chargement && lignes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Calcul du suivi des patients…")
            }
        }
        erreur != null && lignes.isEmpty() -> Message(erreur!!) { actualiser++ }
        aff == null -> Message("Ce compte n'est inscrit dans aucun établissement. Créez l'espace ou rejoignez une équipe " +
            "depuis l'application mobile (carte « Établissement » sur l'accueil).") { actualiser++ }
        aff.role == RoleEtablissement.PATIENT -> Message("La version PC est réservée aux soignants pour l'instant. " +
            "Vous êtes inscrit comme patient à ${aff.etablissementNom}.") { actualiser++ }
        e == null -> Message("Établissement introuvable.") { actualiser++ }
        else -> Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.nom, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(listOf(e.ville, if (aff.role == RoleEtablissement.ADMIN) "Administrateur" else "Soignant")
                        .filter { it.isNotBlank() }.joinToString(" · "), color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Code patient : ${e.codePatient}", fontWeight = FontWeight.SemiBold)
                    if (aff.role == RoleEtablissement.ADMIN) Text("Code soignant : ${e.codeSoignant}", color = Color.Gray, fontSize = 13.sp)
                }
                Spacer(Modifier.width(16.dp))
                OutlinedButton(onClick = { actualiser++ }, enabled = !chargement) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(if (chargement) "Mise à jour…" else "Actualiser")
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Compteur("Patients", lignes.size.toString())
                Compteur("À revoir en priorité", lignes.count { it.resultat.priorite == PrioriteSuivi.HAUTE }.toString())
                Compteur("Perdus de vue", lignes.count { it.resultat.perduDeVue }.toString())
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filtre.entries.forEach { f -> FilterChip(filtre == f, { filtre = f }, label = { Text(f.libelle) }) }
            }
            Spacer(Modifier.height(8.dp))
            val visibles = when (filtre) {
                Filtre.TOUS -> lignes
                Filtre.A_RISQUE -> lignes.filter { it.resultat.priorite == PrioriteSuivi.HAUTE }
                Filtre.PERDUS -> lignes.filter { it.resultat.perduDeVue }
            }
            Card(Modifier.fillMaxSize()) {
                EnTeteTableau()
                HorizontalDivider()
                if (visibles.isEmpty()) Text(
                    if (lignes.isEmpty()) "Aucun patient inscrit. Donnez le code patient à vos patients." else "Aucun patient dans ce filtre.",
                    Modifier.padding(20.dp), color = Color.Gray
                )
                LazyColumn { items(visibles, key = { it.patient.uid }) { LignePatient(it); HorizontalDivider() } }
            }
            Text("Aide au suivi, pas un diagnostic : la décision reste au soignant. Perdu de vue = aucune mesure depuis " +
                "${EvaluationSuivi.JOURS_PERDU_DE_VUE} jours.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Message(texte: String, onReessayer: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(texte, fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onReessayer) { Text("Réessayer") }
        }
    }
}

@Composable
private fun Compteur(titre: String, valeur: String) {
    Card { Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(valeur, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Indigo)
        Text(titre, fontSize = 13.sp, color = Color.Gray)
    } }
}

private val colonnes = listOf(0.22f, 0.16f, 0.12f, 0.12f, 0.10f, 0.28f)

@Composable
private fun EnTeteTableau() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        listOf("Patient", "Priorité", "Dernière mesure", "Moyenne 30 j", "HbA1c", "Pourquoi").forEachIndexed { i, t ->
            Text(t, Modifier.weight(colonnes[i]), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun LignePatient(l: LigneSuivi) {
    val r = l.resultat
    val couleur = when (r.priorite) {
        PrioriteSuivi.HAUTE -> Color(0xFFD32F2F)
        PrioriteSuivi.MOYENNE -> Color(0xFFF57C00)
        PrioriteSuivi.BASSE -> Color(0xFF2E7D32)
        PrioriteSuivi.INCONNUE -> Color.Gray
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(l.patient.nom.ifBlank { "Patient" }, Modifier.weight(colonnes[0]), fontWeight = FontWeight.Medium)
        Row(Modifier.weight(colonnes[1]), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(couleur, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(r.priorite.libelle, fontSize = 13.sp)
        }
        Text(r.derniereMesure?.let { "%02d/%02d/%d".format(it.dayOfMonth, it.monthNumber, it.year) } ?: "—",
            Modifier.weight(colonnes[2]), fontSize = 13.sp, color = if (r.perduDeVue) Color(0xFFF57C00) else Color.Unspecified)
        Text(r.moyenne30j?.let { "${it.toInt()} mg/dL" } ?: "—", Modifier.weight(colonnes[3]), fontSize = 13.sp)
        Text(r.hba1c?.let { "${EvaluationSuivi.unChiffre(it.valeur)} %" + if (it.estimee) " (est.)" else "" } ?: "—",
            Modifier.weight(colonnes[4]), fontSize = 13.sp)
        Text(r.raisons.joinToString(" · ").ifBlank { "—" }, Modifier.weight(colonnes[5]), fontSize = 12.sp, color = Color.DarkGray)
    }
}

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
