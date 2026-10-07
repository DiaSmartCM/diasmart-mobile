package com.diabeto.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private enum class FiltreRdv(val libelle: String) {
    A_VENIR("À venir"), EN_ATTENTE("Demandes en attente"), PASSES("Passés"), REFUSES("Refusés")
}

@Composable
fun EcranRendezVous(etat: EtatApp) {
    val scope = rememberCoroutineScope()
    var filtre by remember { mutableStateOf(FiltreRdv.A_VENIR) }
    var reponseA by remember { mutableStateOf<Pair<DemandeRdv, Boolean>?>(null) }  // (demande, accepter ?)
    var erreur by remember { mutableStateOf<String?>(null) }
    if (!etat.dejaCharge) {
        if (etat.erreur != null) Message(etat.erreur!!) { scope.launch { etat.charger() } }
        else Chargement("Chargement des rendez-vous…")
        return
    }
    val maintenant = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    fun futur(r: DemandeRdv) = (r.date ?: maintenant) >= maintenant
    val liste = when (filtre) {
        FiltreRdv.A_VENIR -> etat.rdv.filter { it.statut == "ACCEPTED" && futur(it) }
        FiltreRdv.EN_ATTENTE -> etat.rdv.filter { it.statut == "PENDING" }
        FiltreRdv.PASSES -> etat.rdv.filter { it.statut == "ACCEPTED" && !futur(it) }.reversed()
        FiltreRdv.REFUSES -> etat.rdv.filter { it.statut == "REJECTED" }.reversed()
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Rendez-vous", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Demandes envoyées par vos patients depuis l'app mobile. Le patient voit votre réponse sur son téléphone.",
                    color = Color.Gray, fontSize = 13.sp)
            }
            OutlinedButton(onClick = { scope.launch { etat.charger() } }, enabled = !etat.chargement) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp))
                Text(if (etat.chargement) "Mise à jour…" else "Actualiser")
            }
        }
        Spacer(Modifier.height(12.dp))
        Onglets(FiltreRdv.entries, filtre, { f ->
            f.libelle + if (f == FiltreRdv.EN_ATTENTE) " (${etat.rdv.count { it.statut == "PENDING" }})" else ""
        }) { filtre = it }
        erreur?.let { Text(it, color = Rouge, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(8.dp))
        val defil = rememberLazyListState()
        Column(Modifier.fillMaxSize().background(Color.White).border(1.dp, Bordure)) {
            if (liste.isEmpty()) Text("Aucun rendez-vous ici.", Modifier.padding(20.dp), color = Color.Gray)
            LazyColumn(Modifier.fillMaxSize().defilementClavier(defil), state = defil) {
                items(liste, key = { it.id }) { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(150.dp)) {
                            Text(r.date?.jour() ?: "—", fontWeight = FontWeight.Medium)
                            Text((r.date?.heure() ?: "") + " · ${r.dureeMinutes} min", fontSize = 12.sp, color = Color.Gray)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(r.patientNom.ifBlank { "Patient" }, fontWeight = FontWeight.Medium)
                            Text(listOf(libelleType(r.type), r.motif).filter { it.isNotBlank() }.joinToString(" · "),
                                fontSize = 13.sp, color = Color.DarkGray)
                            if (r.reponse.isNotBlank()) Text("Votre réponse : ${r.reponse}", fontSize = 12.sp, color = Color.Gray)
                        }
                        when (r.statut) {
                            "PENDING" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { reponseA = r to false }) { Text("Refuser") }
                                Button(onClick = { reponseA = r to true }) { Text("Accepter") }
                            }
                            "ACCEPTED" -> Pastille("Confirmé", Vert)
                            "REJECTED" -> Pastille("Refusé", Rouge)
                            else -> Pastille(r.statut, Color.Gray)
                        }
                    }
                    HorizontalDivider(color = Bordure)
                }
            }
        }
    }

    reponseA?.let { (d, accepter) ->
        var message by remember(d.id) { mutableStateOf("") }
        var enCours by remember(d.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!enCours) reponseA = null },
            title = { Text(if (accepter) "Accepter ce rendez-vous ?" else "Refuser ce rendez-vous ?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${d.patientNom.ifBlank { "Patient" }} · ${d.date?.let { "${it.jour()} à ${it.heure()}" } ?: ""}")
                    OutlinedTextField(message, { message = it.take(300) },
                        label = { Text(if (accepter) "Message au patient (facultatif)" else "Raison ou autre date proposée (facultatif)") },
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        enCours = true; erreur = null
                        scope.launch {
                            try {
                                if (accepter) etat.rdvService.accepter(d, etat.profil.uid, message)
                                else etat.rdvService.refuser(d, message)
                                reponseA = null
                                etat.charger()
                            } catch (e: Exception) {
                                erreur = e.message ?: "Réponse impossible."
                                reponseA = null
                            } finally { enCours = false }
                        }
                    },
                    enabled = !enCours,
                    colors = if (accepter) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = Rouge)
                ) { Text(if (accepter) "Accepter" else "Refuser") }
            },
            dismissButton = { TextButton(onClick = { reponseA = null }, enabled = !enCours) { Text("Annuler") } }
        )
    }
}

private fun libelleType(t: String) = when (t.uppercase()) {
    "CONSULTATION" -> "Consultation"
    "SUIVI" -> "Suivi"
    "URGENCE" -> "Urgence"
    "TELECONSULTATION" -> "Téléconsultation"
    "EXAMEN" -> "Examen"
    else -> t.lowercase().replaceFirstChar { it.uppercase() }
}
