package com.diabeto.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diabeto.data.model.PrioriteSuivi
import com.diabeto.domain.EvaluationSuivi
import com.diabeto.domain.MesureHbA1c
import com.diabeto.domain.MesureTension
import com.diabeto.domain.ReglesTension
import kotlinx.coroutines.launch

private enum class Filtre(val libelle: String) { TOUS("Tous"), A_RISQUE("À revoir en priorité"), PERDUS("Perdus de vue") }

@Composable
fun EcranPatients(etat: EtatApp, patientOuvert: String?, onOuvrir: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    if (!etat.dejaCharge) {
        if (etat.erreur != null) Message(etat.erreur!!) { scope.launch { etat.charger() } }
        else Chargement("Calcul du suivi des patients…")
        return
    }
    val ouvert = etat.patients.firstOrNull { it.uid == patientOuvert }
    if (ouvert != null) FichePatient(etat, ouvert) { onOuvrir(null) }
    else ListePatients(etat, onOuvrir)
}

private val colonnes = listOf(0.18f, 0.13f, 0.11f, 0.10f, 0.08f, 0.09f, 0.09f, 0.22f)

@Composable
private fun ListePatients(etat: EtatApp, onOuvrir: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var filtre by remember { mutableStateOf(Filtre.TOUS) }
    var recherche by remember { mutableStateOf("") }
    val lignes = etat.patients
    val visibles = lignes.filter {
        when (filtre) {
            Filtre.TOUS -> true
            Filtre.A_RISQUE -> it.resultat.priorite == PrioriteSuivi.HAUTE
            Filtre.PERDUS -> it.resultat.perduDeVue
        } && (recherche.isBlank() || it.nom.contains(recherche.trim(), ignoreCase = true))
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Patients suivis", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Patients liés à vous directement et patients de votre établissement. Cliquez sur un patient pour voir sa courbe.",
                    color = Color.Gray, fontSize = 13.sp)
            }
            OutlinedButton(onClick = { scope.launch { etat.charger() } }, enabled = !etat.chargement) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp))
                Text(if (etat.chargement) "Mise à jour…" else "Actualiser")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filtre.entries.forEach { f -> FilterChip(filtre == f, { filtre = f }, label = { Text(f.libelle) }) }
            Spacer(Modifier.weight(1f))
            OutlinedTextField(recherche, { recherche = it.take(40) }, placeholder = { Text("Rechercher un nom") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.widthIn(max = 280.dp))
        }
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                listOf("Patient", "Priorité", "Dernière mesure", "Moyenne 30 j", "HbA1c", "Tension 30 j", "Suivi par", "Pourquoi").forEachIndexed { i, t ->
                    Text(t, Modifier.weight(colonnes[i]), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Gray)
                }
            }
            HorizontalDivider()
            if (visibles.isEmpty()) Text(
                if (lignes.isEmpty()) "Aucun patient suivi. Donnez le code patient de l'établissement à vos patients, " +
                    "ou acceptez leurs demandes de partage dans l'app mobile." else "Aucun patient dans ce filtre.",
                Modifier.padding(20.dp), color = Color.Gray
            )
            LazyColumn {
                items(visibles, key = { it.uid }) { p ->
                    LignePatient(p) { onOuvrir(p.uid) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LignePatient(p: PatientSuivi, onClic: () -> Unit) {
    val r = p.resultat
    Row(Modifier.fillMaxWidth().clickable(onClick = onClic).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(p.nom.ifBlank { "Patient" }, Modifier.weight(colonnes[0]), fontWeight = FontWeight.Medium)
        PointPriorite(r.priorite, Modifier.weight(colonnes[1]))
        Text(r.derniereMesure?.jour() ?: "—", Modifier.weight(colonnes[2]), fontSize = 13.sp,
            color = if (r.perduDeVue) Orange else Color.Unspecified)
        Text(r.moyenne30j?.let { "${it.toInt()} mg/dL" } ?: "—", Modifier.weight(colonnes[3]), fontSize = 13.sp)
        Text(r.hba1c?.let { "${EvaluationSuivi.unChiffre(it.valeur)} %" + if (it.estimee) " (est.)" else "" } ?: "—",
            Modifier.weight(colonnes[4]), fontSize = 13.sp)
        Text(r.tensionMoyenne30j?.let { "${it.first}/${it.second}" } ?: "—", Modifier.weight(colonnes[5]), fontSize = 13.sp,
            color = r.tensionMoyenne30j?.let { ReglesTension.categorie(it.first, it.second).couleur() } ?: Color.Unspecified)
        Text(p.origine.libelle, Modifier.weight(colonnes[6]), fontSize = 12.sp, color = Color.Gray)
        Text(r.raisons.joinToString(" · ").ifBlank { "—" }, Modifier.weight(colonnes[7]), fontSize = 12.sp, color = Color.DarkGray)
    }
}

// ── Fiche d'un patient : courbe, HbA1c, dernieres mesures ───────────────

private val periodes = listOf(7, 30, 90)

@Composable
private fun FichePatient(etat: EtatApp, p: PatientSuivi, onRetour: () -> Unit) {
    var jours by remember { mutableIntStateOf(30) }
    var mesures by remember(p.uid) { mutableStateOf(p.mesures) }
    var hba1c by remember(p.uid) { mutableStateOf<List<MesureHbA1c>>(emptyList()) }
    var tensions by remember(p.uid) { mutableStateOf(p.tensions) }
    var chargement by remember(p.uid) { mutableStateOf(true) }
    LaunchedEffect(p.uid) {
        // Plus de mesures que la liste (3 mois) pour la courbe 90 jours
        val toutes = etat.patientsService.mesures(p.uid, 600)
        if (toutes.isNotEmpty()) mesures = toutes
        hba1c = etat.patientsService.hba1c(p.uid)
        etat.patientsService.tensions(p.uid, 300).takeIf { it.isNotEmpty() }?.let { tensions = it }
        chargement = false
    }
    val r = p.resultat
    val fin = System.currentTimeMillis()
    val debut = fin - jours * 86_400_000L
    val periode = mesures.filter { it.date.ms() >= debut }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Column(Modifier.weight(1f)) {
                Text(p.nom.ifBlank { "Patient" }, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(p.origine.libelle, color = Color.Gray, fontSize = 13.sp)
            }
            Pastille(r.priorite.libelle, r.priorite.couleur())
        }
        if (r.raisons.isNotEmpty()) Text(r.raisons.joinToString(" · "), fontSize = 13.sp, color = Color.DarkGray)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Compteur("Dernière mesure", r.derniereMesure?.jour() ?: "—", Modifier.weight(1f),
                if (r.perduDeVue) Orange else Indigo)
            Compteur("Moyenne 30 j", r.moyenne30j?.let { "${it.toInt()} mg/dL" } ?: "—", Modifier.weight(1f))
            Compteur("Mesures 30 j", r.nbMesures30j.toString(), Modifier.weight(1f))
            Compteur("Hypos 30 j (< 70)", r.nbHypos30j.toString(), Modifier.weight(1f), if (r.nbHypos30j > 0) Rouge else Indigo)
            Compteur("HbA1c", r.hba1c?.let { EvaluationSuivi.unChiffre(it.valeur) + " %" } ?: "—", Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Courbe de glycémie", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    if (chargement) CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                    periodes.forEach { j -> FilterChip(jours == j, { jours = j }, label = { Text("$j jours") }) }
                }
                if (periode.isNotEmpty()) {
                    val dansCible = periode.count { it.valeur in 70.0..180.0 }
                    Text("${periode.size} mesures · moyenne ${periode.map { it.valeur }.average().toInt()} mg/dL · " +
                        "${dansCible * 100 / periode.size} % dans la cible 70-180", fontSize = 13.sp, color = Color.Gray)
                }
                Spacer(Modifier.height(8.dp))
                if (periode.isEmpty()) Text("Aucune mesure sur cette période.", color = Color.Gray, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp))
                else CourbeGlycemie(periode.map { PointCourbe(it.date.ms(), it.valeur) }, debut, fin,
                    Modifier.fillMaxWidth().height(260.dp))
            }
        }

        CarteTension(tensions, debut, fin, jours)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("HbA1c", fontWeight = FontWeight.SemiBold)
                    if (hba1c.isEmpty()) Text(if (chargement) "Chargement…" else "Aucune HbA1c enregistrée.",
                        color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    hba1c.sortedByDescending { it.date }.forEach { h ->
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Row {
                            Text(h.date.texte(), Modifier.width(110.dp), fontSize = 13.sp)
                            Text("${EvaluationSuivi.unChiffre(h.valeur)} %", Modifier.width(70.dp), fontWeight = FontWeight.Medium)
                            if (h.estimee) Text("estimée", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Dernières mesures", fontWeight = FontWeight.SemiBold)
                    if (mesures.isEmpty()) Text("Aucune mesure.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    mesures.sortedByDescending { it.date }.take(15).forEach { m ->
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Row {
                            Text("${m.date.jour()} ${m.date.heure()}", Modifier.width(150.dp), fontSize = 13.sp)
                            Text("${m.valeur.toInt()} mg/dL", Modifier.width(90.dp), fontWeight = FontWeight.Medium,
                                color = couleurGlycemie(m.valeur))
                            Text(m.contexte.lowercase().replace('_', ' '), fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
        Text("Aide au suivi, pas un diagnostic : la décision reste au soignant.", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun CarteTension(tensions: List<MesureTension>, debut: Long, fin: Long, jours: Int) {
    val periode = tensions.filter { it.date.ms() >= debut }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Tension artérielle ($jours jours)", fontWeight = FontWeight.SemiBold)
            if (tensions.isEmpty()) {
                Text("Aucune mesure de tension partagée.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                return@Column
            }
            val derniere = tensions.maxBy { it.date }
            val cat = ReglesTension.categorie(derniere.systolique, derniere.diastolique)
            val moy = ReglesTension.moyenne(periode)
            Text(
                "Dernière : ${derniere.systolique}/${derniere.diastolique} mmHg le ${derniere.date.jour()} (${cat.libelle})" +
                    (moy?.let { " · moyenne ${it.first}/${it.second} sur ${periode.size} mesures" } ?: ""),
                fontSize = 13.sp, color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            if (periode.isEmpty()) Text("Aucune mesure sur cette période.", color = Color.Gray, fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 16.dp))
            else CourbeTension(periode.map { Triple(it.date.ms(), it.systolique, it.diastolique) }, debut, fin,
                Modifier.fillMaxWidth().height(200.dp))
            Text("Indigo : haut (systolique) · vert : bas (diastolique) · pointillés : 140 et 90 mmHg",
                fontSize = 12.sp, color = Color.Gray)
        }
    }
}
