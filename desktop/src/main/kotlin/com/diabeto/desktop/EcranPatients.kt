package com.diabeto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diabeto.data.model.PrioriteSuivi
import com.diabeto.domain.EvaluationSuivi
import com.diabeto.domain.ReglesTension
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Onglets de « Mes patients ». Archives : masques des autres onglets, pour ce soignant seulement. */
private enum class Filtre(val libelle: String) {
    TOUS("Tous"), SUIVIS("Suivis"), A_RISQUE("À revoir en priorité"), PERDUS("Perdus de vue"), ARCHIVES("Archivés")
}

private fun Filtre.garde(p: PatientSuivi, archives: Set<String>): Boolean {
    val archive = p.uid in archives
    return when (this) {
        Filtre.TOUS -> !archive
        Filtre.SUIVIS -> !archive && !p.resultat.perduDeVue && p.resultat.derniereMesure != null
        Filtre.A_RISQUE -> !archive && p.resultat.priorite == PrioriteSuivi.HAUTE
        Filtre.PERDUS -> !archive && p.resultat.perduDeVue
        Filtre.ARCHIVES -> archive
    }
}

@Composable
fun EcranPatients(
    etat: EtatApp,
    patientOuvert: String?,
    ongletFiche: OngletFiche,
    onOuvrir: (String?, OngletFiche) -> Unit,
    onEcrire: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    if (!etat.dejaCharge) {
        if (etat.erreur != null) Message(etat.erreur!!) { scope.launch { etat.charger() } }
        else Chargement("Calcul du suivi des patients…")
        return
    }
    val ouvert = etat.patients.firstOrNull { it.uid == patientOuvert }
    if (ouvert != null) FichePatient(etat, ouvert, ongletFiche, onRetour = { onOuvrir(null, OngletFiche.VUE) }, onEcrire = { onEcrire(ouvert.uid) })
    else ListePatients(etat, onOuvrir, onEcrire)
}

// Nom, Prenom, Suivi, Sexe, Age, Type, Priorite, Derniere mesure, Moy. 30 j, HbA1c, TA 30 j, Dossier, Carnet, Actions
private val colonnes = listOf(1.1f, 1.1f, 0.45f, 0.65f, 0.45f, 1.05f, 0.95f, 0.95f, 0.8f, 0.65f, 0.65f, 0.75f, 0.7f, 1.1f)

@Composable
private fun ListePatients(etat: EtatApp, onOuvrir: (String?, OngletFiche) -> Unit, onEcrire: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var filtre by remember { mutableStateOf(Filtre.TOUS) }
    var recherche by remember { mutableStateOf("") }
    var ajout by remember { mutableStateOf(false) }
    val liste = rememberLazyListState()
    val lignes = etat.patients
    val archives = etat.archives
    val texte = recherche.trim()
    val visibles = lignes.filter {
        filtre.garde(it, archives) && (texte.isEmpty() || listOf(it.nom, it.prenom, it.nomFamille, it.identite.email)
            .any { champ -> champ.contains(texte, ignoreCase = true) })
    }
    val actifs = lignes.filter { it.uid !in archives }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mes patients", Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A3F55))
            OutlinedButton(onClick = { scope.launch { etat.charger() } }, enabled = !etat.chargement) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp))
                Text(if (etat.chargement) "Mise à jour…" else "Actualiser")
            }
        }
        Espace(8)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ajout = true }) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Ajouter un patient")
            }
            Badge("Total patients : ${actifs.size}", IndigoFonce)
            Badge("Patients prioritaires : ${actifs.count { it.resultat.priorite == PrioriteSuivi.HAUTE }}", Rouge)
            Badge("Perdus de vue : ${actifs.count { it.resultat.perduDeVue }}", Orange)
            Badge("Archivés : ${lignes.count { it.uid in archives }}", Color.Gray)
        }
        Espace(12)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                Onglets(Filtre.entries, filtre, { f -> "${f.libelle} (${lignes.count { f.garde(it, archives) }})" }) { filtre = it }
            }
            OutlinedTextField(recherche, { recherche = it.take(40) }, placeholder = { Text("Nom, prénom ou email") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.widthIn(max = 280.dp))
        }
        Espace(8)
        Column(Modifier.weight(1f).fillMaxWidth().background(Color.White).border(1.dp, Bordure)) {
            EnteteLigne(listOf("Nom", "Prénom", "Suivi", "Sexe", "Âge", "Type de diabète", "Priorité", "Dernière mesure",
                "Moy. 30 j", "HbA1c", "TA 30 j", "Dossier", "Carnet", "").zip(colonnes))
            if (visibles.isEmpty()) Text(
                when {
                    lignes.isEmpty() -> "Aucun patient suivi. Cliquez sur « Ajouter un patient » pour savoir comment en ajouter."
                    filtre == Filtre.ARCHIVES -> "Aucun patient archivé. « Archiver » range un patient ici sans supprimer ses données."
                    else -> "Aucun patient dans cet onglet."
                },
                Modifier.padding(20.dp), color = Color.Gray
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth().defilementClavier(liste), state = liste) {
                items(visibles, key = { it.uid }) { p ->
                    LignePatient(p, p.uid in archives,
                        onDossier = { onOuvrir(p.uid, OngletFiche.VUE) },
                        onCarnet = { onOuvrir(p.uid, OngletFiche.CARNET) },
                        onEcrire = { onEcrire(p.uid) },
                        onArchiver = { scope.launch { etat.basculerArchive(p.uid) } })
                    HorizontalDivider(color = Bordure)
                }
            }
        }
        Espace(6)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${visibles.size} patient(s) correspondant aux filtres", Modifier.weight(1f), fontSize = 12.sp, color = Color.DarkGray)
            Text("Suivi : carré vert = mesure ces 7 derniers jours · Survolez la priorité pour voir pourquoi · " +
                "Aide au suivi, pas un diagnostic.", fontSize = 12.sp, color = Color.Gray)
        }
    }
    if (ajout) DialogueAjout(etat) { ajout = false }
    etat.erreurArchive?.let { e ->
        AlertDialog(onDismissRequest = { etat.erreurArchive = null }, confirmButton = {
            TextButton(onClick = { etat.erreurArchive = null }) { Text("OK") }
        }, title = { Text("Archivage impossible") }, text = { Text(e) })
    }
}

@Composable
private fun LignePatient(
    p: PatientSuivi, archive: Boolean,
    onDossier: () -> Unit, onCarnet: () -> Unit, onEcrire: () -> Unit, onArchiver: () -> Unit
) {
    val r = p.resultat
    val c = p.clinique
    val aujourdhui = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val recent = r.derniereMesure?.let { System.currentTimeMillis() - it.ms() < 7 * 86_400_000L } == true
    Row(Modifier.fillMaxWidth().clickable(onClick = onDossier).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(p.nomFamille.ifBlank { "—" }, Modifier.weight(colonnes[0]), fontWeight = FontWeight.Medium, color = Indigo,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(p.prenom.ifBlank { "—" }, Modifier.weight(colonnes[1]), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(colonnes[2])) {
            Box(Modifier.size(10.dp).then(if (recent) Modifier.background(Vert) else Modifier.border(1.dp, Color.Gray)))
        }
        Text(c?.sexeTexte ?: "—", Modifier.weight(colonnes[3]), fontSize = 13.sp)
        Text(ageEn(c?.dateNaissance, aujourdhui)?.toString() ?: "—", Modifier.weight(colonnes[4]), fontSize = 13.sp)
        Box(Modifier.weight(colonnes[5])) { BadgeType(c?.typeDiabete ?: "") }
        Box(Modifier.weight(colonnes[6])) {
            Infobulle(r.raisons.joinToString("\n").ifBlank { "Rien de particulier" }) { PointPriorite(r.priorite) }
        }
        Text(r.derniereMesure?.jour() ?: "—", Modifier.weight(colonnes[7]), fontSize = 13.sp,
            color = if (r.perduDeVue) Orange else Color.Unspecified)
        Text(r.moyenne30j?.let { "${it.toInt()} mg/dL" } ?: "—", Modifier.weight(colonnes[8]), fontSize = 13.sp,
            color = r.moyenne30j?.let { couleurGlycemie(it) } ?: Color.Unspecified)
        Text(r.hba1c?.let { "${EvaluationSuivi.unChiffre(it.valeur)} %" } ?: "—", Modifier.weight(colonnes[9]), fontSize = 13.sp)
        Text(r.tensionMoyenne30j?.let { "${it.first}/${it.second}" } ?: "—", Modifier.weight(colonnes[10]), fontSize = 13.sp,
            color = r.tensionMoyenne30j?.let { ReglesTension.categorie(it.first, it.second).couleur() } ?: Color.Unspecified)
        Box(Modifier.weight(colonnes[11])) { MiniBouton("Dossier", onClic = onDossier) }
        Box(Modifier.weight(colonnes[12])) { MiniBouton("Carnet", onClic = onCarnet) }
        Row(Modifier.weight(colonnes[13]), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Infobulle("Écrire au patient") { MiniBouton("Message", onClic = onEcrire) }
            Infobulle(if (archive) "Remettre dans la liste" else "Ranger dans « Archivés » (les données restent)") {
                MiniBouton(if (archive) "Restaurer" else "Archiver", Color.Gray, onArchiver)
            }
        }
    }
}

@Composable
private fun BadgeType(code: String) {
    val (texte, couleur) = when (code) {
        "TYPE_1" -> "TYPE 1" to Orange
        "TYPE_2" -> "TYPE 2" to Indigo
        "GESTATIONNEL" -> "GESTATIONNEL" to Color(0xFF8E24AA)
        "PRE_DIABETE" -> "PRÉDIABÈTE" to Color(0xFF00897B)
        else -> "" to Color.Unspecified
    }
    if (texte.isEmpty()) Text("—", fontSize = 13.sp)
    else Text(texte, Modifier.background(couleur).padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White,
        fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

/** Comment ajouter un patient : code patient de l'etablissement, ou partage depuis l'app. */
@Composable
private fun DialogueAjout(etat: EtatApp, onFermer: () -> Unit) {
    AlertDialog(
        onDismissRequest = onFermer,
        confirmButton = { Button(onClick = onFermer) { Text("Compris") } },
        title = { Text("Ajouter un patient à mon suivi") },
        text = {
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val e = etat.etablissement
                if (e != null) {
                    Text("1. Donnez au patient le code patient de ${e.nom} :", fontSize = 14.sp)
                    CodeACopier(e.codePatient)
                    Text("Dans son application DiaSmart, il ouvre « Mon centre de santé », entre ce code et accepte le partage. " +
                        "Il apparaît alors ici pour toute l'équipe.", fontSize = 13.sp, color = Color.DarkGray)
                    Text("2. Ou bien le patient vous partage ses données directement depuis l'onglet « Médecin » " +
                        "de son application.", fontSize = 13.sp, color = Color.DarkGray)
                } else {
                    Text("Le patient vous partage ses données depuis l'onglet « Médecin » de son application DiaSmart : " +
                        "il apparaît alors dans cette liste.", fontSize = 13.sp, color = Color.DarkGray)
                    Text("Avec un établissement (onglet « Établissement »), vous obtenez aussi un code patient à donner " +
                        "à tous vos patients.", fontSize = 13.sp, color = Color.DarkGray)
                }
            }
        }
    )
}
