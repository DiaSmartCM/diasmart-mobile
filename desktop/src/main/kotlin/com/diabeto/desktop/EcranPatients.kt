package com.diabeto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diabeto.data.model.PrioriteSuivi
import com.diabeto.domain.EvaluationSuivi
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
    val liste = rememberLazyListState()
    val lignes = etat.patients
    val visibles = lignes.filter {
        when (filtre) {
            Filtre.TOUS -> true
            Filtre.A_RISQUE -> it.resultat.priorite == PrioriteSuivi.HAUTE
            Filtre.PERDUS -> it.resultat.perduDeVue
        } && (recherche.isBlank() || it.nom.contains(recherche.trim(), ignoreCase = true))
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Patients suivis", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Patients liés à vous directement et patients de votre établissement. Cliquez sur un patient pour ouvrir sa fiche. " +
                    "Flèches ↑ ↓ pour faire défiler.", color = Color.Gray, fontSize = 13.sp)
            }
            Badge("Total patients : ${lignes.size}", Indigo)
            Badge("Prioritaires : ${lignes.count { it.resultat.priorite == PrioriteSuivi.HAUTE }}", Rouge)
            Badge("Perdus de vue : ${lignes.count { it.resultat.perduDeVue }}", Orange)
        }
        Espace(12)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f)) {
                Onglets(Filtre.entries, filtre, { f ->
                    f.libelle + " (" + lignes.count { p ->
                        when (f) {
                            Filtre.TOUS -> true
                            Filtre.A_RISQUE -> p.resultat.priorite == PrioriteSuivi.HAUTE
                            Filtre.PERDUS -> p.resultat.perduDeVue
                        }
                    } + ")"
                }) { filtre = it }
            }
            OutlinedTextField(recherche, { recherche = it.take(40) }, placeholder = { Text("Rechercher un nom") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.widthIn(max = 260.dp))
            OutlinedButton(onClick = { scope.launch { etat.charger() } }, enabled = !etat.chargement) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp))
                Text(if (etat.chargement) "Mise à jour…" else "Actualiser")
            }
        }
        Espace(8)
        Column(Modifier.fillMaxSize().background(Color.White).border(1.dp, Bordure)) {
            EnteteLigne(listOf("Patient", "Priorité", "Dernière mesure", "Moyenne 30 j", "HbA1c", "Tension 30 j", "Suivi par", "Pourquoi")
                .zip(colonnes))
            if (visibles.isEmpty()) Text(
                if (lignes.isEmpty()) "Aucun patient suivi. Donnez le code patient de l'établissement à vos patients, " +
                    "ou acceptez leurs demandes de partage dans l'app mobile." else "Aucun patient dans ce filtre.",
                Modifier.padding(20.dp), color = Color.Gray
            )
            LazyColumn(Modifier.fillMaxSize().defilementClavier(liste), state = liste) {
                items(visibles, key = { it.uid }) { p ->
                    LignePatient(p) { onOuvrir(p.uid) }
                    HorizontalDivider(color = Bordure)
                }
            }
        }
    }
}

@Composable
private fun LignePatient(p: PatientSuivi, onClic: () -> Unit) {
    val r = p.resultat
    Row(Modifier.fillMaxWidth().clickable(onClick = onClic).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(p.nom.ifBlank { "Patient" }, Modifier.weight(colonnes[0]), fontWeight = FontWeight.Medium, color = Indigo)
        PointPriorite(r.priorite, Modifier.weight(colonnes[1]))
        Text(r.derniereMesure?.jour() ?: "—", Modifier.weight(colonnes[2]), fontSize = 13.sp,
            color = if (r.perduDeVue) Orange else Color.Unspecified)
        Text(r.moyenne30j?.let { "${it.toInt()} mg/dL" } ?: "—", Modifier.weight(colonnes[3]), fontSize = 13.sp,
            color = r.moyenne30j?.let { couleurGlycemie(it) } ?: Color.Unspecified)
        Text(r.hba1c?.let { "${EvaluationSuivi.unChiffre(it.valeur)} %" + if (it.estimee) " (est.)" else "" } ?: "—",
            Modifier.weight(colonnes[4]), fontSize = 13.sp)
        Text(r.tensionMoyenne30j?.let { "${it.first}/${it.second}" } ?: "—", Modifier.weight(colonnes[5]), fontSize = 13.sp,
            color = r.tensionMoyenne30j?.let { ReglesTension.categorie(it.first, it.second).couleur() } ?: Color.Unspecified)
        Text(p.origine.libelle, Modifier.weight(colonnes[6]), fontSize = 12.sp, color = Color.Gray)
        Text(r.raisons.joinToString(" · ").ifBlank { "—" }, Modifier.weight(colonnes[7]), fontSize = 12.sp, color = Color.DarkGray)
    }
}
