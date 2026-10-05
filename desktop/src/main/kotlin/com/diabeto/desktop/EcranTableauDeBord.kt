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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diabeto.data.model.PrioriteSuivi
import com.diabeto.data.model.RoleEtablissement
import com.diabeto.domain.EvaluationSuivi
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

private const val JOUR_MS = 86_400_000L

@Composable
fun EcranTableauDeBord(etat: EtatApp, onOuvrirPatient: (String) -> Unit, onAller: (Onglet) -> Unit) {
    val scope = rememberCoroutineScope()
    if (!etat.dejaCharge) {
        if (etat.erreur != null) Message(etat.erreur!!) { scope.launch { etat.charger() } }
        else Chargement("Chargement de vos patients…")
        return
    }
    val fuseau = TimeZone.currentSystemDefault()
    val maintenant = Clock.System.now().toLocalDateTime(fuseau)
    val patients = etat.patients
    val aVenir = etat.rdv.filter { it.statut == "ACCEPTED" && (it.date ?: maintenant) >= maintenant }
    val enAttente = etat.rdv.filter { it.statut == "PENDING" }
    val prioritaires = patients.filter { it.resultat.priorite == PrioriteSuivi.HAUTE }

    // Courbe de suivi : moyenne par jour de toutes les mesures des patients (30 j)
    val finMs = System.currentTimeMillis()
    val debutMs = finMs - 30 * JOUR_MS
    val moyennesJour = remember(patients) {
        patients.flatMap { it.mesures }
            .filter { it.date.ms() >= debutMs }
            .groupBy { it.date.date }
            .map { (jour, l) -> PointCourbe(jour.atStartOfDayIn(fuseau).toEpochMilliseconds() + JOUR_MS / 2, l.map { it.valeur }.average()) }
    }
    val mesures30 = patients.flatMap { it.mesures }.filter { it.date.ms() >= debutMs }
    val dansCible = mesures30.count { it.valeur in 70.0..180.0 }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bonjour ${etat.profil.nomComplet}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                val e = etat.etablissement
                Text(
                    if (e != null) "${e.nom}${if (e.ville.isNotBlank()) " · ${e.ville}" else ""} · " +
                        (if (etat.equipe?.role == RoleEtablissement.ADMIN) "Administrateur" else "Soignant")
                    else "Aucun établissement : vos patients liés directement s'affichent ici.",
                    color = Color.Gray
                )
            }
            OutlinedButton(onClick = { scope.launch { etat.charger() } }, enabled = !etat.chargement) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp))
                Text(if (etat.chargement) "Mise à jour…" else "Actualiser")
            }
        }
        etat.erreur?.let { Text(it, color = Rouge, fontSize = 13.sp) }

        if (etat.equipe == null) {
            Card(colors = CardDefaults.cardColors(containerColor = Indigo.copy(alpha = 0.08f))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Créez l'espace de votre structure, ou rejoignez-la si elle a déjà un compte DiaSmart (code soignant).",
                        Modifier.weight(1f), fontSize = 14.sp)
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { onAller(Onglet.ETABLISSEMENT) }) { Text("Établissement") }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Compteur("Patients suivis", patients.size.toString(), Modifier.weight(1f))
            Compteur("À revoir en priorité", prioritaires.size.toString(), Modifier.weight(1f), Rouge)
            Compteur("Perdus de vue", patients.count { it.resultat.perduDeVue }.toString(), Modifier.weight(1f), Orange)
            Compteur("Tension ≥ 140/90", patients.count { p ->
                p.resultat.tensionMoyenne30j?.let { it.first >= 140 || it.second >= 90 } == true
            }.toString(), Modifier.weight(1f), Orange)
            Compteur("RDV à venir", aVenir.size.toString(), Modifier.weight(1f))
            Compteur("Demandes de RDV", enAttente.size.toString(), Modifier.weight(1f), if (enAttente.isNotEmpty()) Orange else Indigo)
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Courbe de suivi : glycémie moyenne de vos patients par jour (30 jours)",
                        Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    if (mesures30.isNotEmpty()) Text(
                        "${mesures30.size} mesures · ${dansCible * 100 / mesures30.size} % dans la cible 70-180",
                        fontSize = 13.sp, color = Color.Gray)
                }
                Spacer(Modifier.height(8.dp))
                if (moyennesJour.isEmpty()) Text("Aucune mesure ces 30 derniers jours.", color = Color.Gray, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp))
                else CourbeGlycemie(moyennesJour, debutMs, finMs, Modifier.fillMaxWidth().height(220.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Patients à revoir en priorité", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = { onAller(Onglet.PATIENTS) }) { Text("Tous les patients") }
                    }
                    if (prioritaires.isEmpty()) Text("Aucun pour l'instant.", color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp))
                    prioritaires.take(6).forEach { p ->
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Column(Modifier.fillMaxWidth().clickable { onOuvrirPatient(p.uid) }.padding(vertical = 2.dp)) {
                            Text(p.nom.ifBlank { "Patient" }, fontWeight = FontWeight.Medium)
                            Text(p.resultat.raisons.joinToString(" · ").ifBlank { "—" }, fontSize = 12.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Prochains rendez-vous", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = { onAller(Onglet.RDV) }) { Text("Tous les RDV") }
                    }
                    if (enAttente.isNotEmpty()) Text("${enAttente.size} demande(s) en attente de réponse.",
                        color = Orange, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    if (aVenir.isEmpty()) Text("Aucun rendez-vous confirmé à venir.", color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp))
                    aVenir.take(6).forEach { r ->
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Row {
                            Text(r.date?.let { "${it.jour()} ${it.heure()}" } ?: "—", Modifier.width(130.dp), fontSize = 13.sp)
                            Column {
                                Text(r.patientNom.ifBlank { "Patient" }, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                if (r.motif.isNotBlank()) Text(r.motif, fontSize = 12.sp, color = Color.DarkGray)
                            }
                        }
                    }
                }
            }
        }
        Text("Aide au suivi, pas un diagnostic : la décision reste au soignant. Perdu de vue = aucune mesure depuis " +
            "${EvaluationSuivi.JOURS_PERDU_DE_VUE} jours.", fontSize = 12.sp, color = Color.Gray)
    }
}
