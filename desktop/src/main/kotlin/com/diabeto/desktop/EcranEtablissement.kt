package com.diabeto.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.diabeto.data.model.RoleEtablissement
import com.diabeto.data.model.TypeCode
import com.diabeto.util.ReglesEssais
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EcranEtablissement(etat: EtatApp) {
    val scope = rememberCoroutineScope()
    if (!etat.dejaCharge) {
        if (etat.erreur != null) Message(etat.erreur!!) { scope.launch { etat.charger() } }
        else Chargement("Chargement…")
        return
    }
    val aff = etat.equipe
    val e = etat.etablissement
    if (aff == null || e == null) { SansEtablissement(etat); return }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(e.nom, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(listOf(e.ville, if (aff.role == RoleEtablissement.ADMIN) "Vous êtes administrateur" else "Vous êtes soignant")
            .filter { it.isNotBlank() }.joinToString(" · "), color = Color.Gray)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Code patient", fontWeight = FontWeight.SemiBold)
                    Text(e.codePatient, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Indigo)
                    Text("À donner à vos patients : dans l'app, carte « Mon centre de santé » sur l'accueil.",
                        fontSize = 13.sp, color = Color.Gray)
                }
            }
            if (aff.role == RoleEtablissement.ADMIN) Card(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Code soignant", fontWeight = FontWeight.SemiBold)
                    Text(e.codeSoignant, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Indigo)
                    Text("À donner seulement à vos collègues : ils rejoignent l'équipe avec ce code (sur PC ou mobile).",
                        fontSize = 13.sp, color = Color.Gray)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Équipe (${etat.membres.size})", fontWeight = FontWeight.SemiBold)
                etat.membres.forEach { m ->
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.nom.ifBlank { "Soignant" }, Modifier.weight(1f))
                        Pastille(if (m.role == RoleEtablissement.ADMIN) "Administrateur" else "Soignant",
                            if (m.role == RoleEtablissement.ADMIN) Indigo else Color.Gray)
                    }
                }
            }
        }
        Text("Retirer un membre ou un patient, changer les codes et le rapport pour le payeur se font dans l'app mobile " +
            "(administrateur).", fontSize = 12.sp, color = Color.Gray)
    }
}

/** Pas encore d'etablissement : le creer, ou rejoindre celui de sa structure. */
@Composable
private fun SansEtablissement(etat: EtatApp) {
    val scope = rememberCoroutineScope()
    val limiteur = remember { LimiteurPc("code_etablissement_pc") }
    var nom by remember { mutableStateOf("") }
    var ville by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var trouve by remember { mutableStateOf<ServiceEtablissement.InfoCode?>(null) }
    var erreurCreer by remember { mutableStateOf<String?>(null) }
    var erreurCode by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); maintenant = System.currentTimeMillis() } }
    val attente = maintenant.let { limiteur.attenteRestanteMs() }

    fun lancer(surErreur: (String) -> Unit, action: suspend () -> Unit) {
        if (enCours) return
        enCours = true
        scope.launch {
            try { action() } catch (ex: Exception) { surErreur(ex.message ?: "Une erreur est survenue.") }
            finally { enCours = false }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Établissement", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Votre structure (hôpital, centre de santé, cabinet) n'a qu'un seul espace DiaSmart. " +
            "Le premier soignant le crée et devient administrateur ; les autres le rejoignent avec le code soignant.",
            color = Color.Gray, fontSize = 14.sp, modifier = Modifier.widthIn(max = 900.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ma structure a déjà un espace", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Entrez le code soignant donné par l'administrateur.", fontSize = 13.sp, color = Color.Gray)
                    val info = trouve
                    if (info == null) {
                        OutlinedTextField(code, { code = ServiceEtablissement.normaliserCode(it); erreurCode = null },
                            label = { Text("Code soignant (8 caractères)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                if (limiteur.attenteRestanteMs() > 0) { erreurCode = limiteur.messageBlocage(); return@Button }
                                erreurCode = null
                                lancer({ erreurCode = it }) {
                                    val r = etat.etabService.verifierCode(code)
                                    when {
                                        r == null -> {
                                            limiteur.echec(); maintenant = System.currentTimeMillis()
                                            erreurCode = limiteur.messageApresEchec("Code inconnu ou désactivé.")
                                        }
                                        r.type == TypeCode.PATIENT -> erreurCode =
                                            "C'est le code patient. Demandez le code soignant à l'administrateur."
                                        else -> { limiteur.reussite(); trouve = r }
                                    }
                                }
                            },
                            enabled = !enCours && attente == 0L && code.length == 8
                        ) { Text("Vérifier le code") }
                    } else {
                        Text("Rejoindre « ${info.etablissementNom} » comme soignant ?", fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { trouve = null }, enabled = !enCours) { Text("Annuler") }
                            Button(onClick = {
                                lancer({ erreurCode = it }) {
                                    etat.etabService.rejoindreCommeSoignant(etat.profil.uid, etat.profil, info)
                                    etat.charger()
                                }
                            }, enabled = !enCours) { Text("Rejoindre") }
                        }
                    }
                    if (attente > 0) Text("Trop d'essais : réessayez dans ${ReglesEssais.formaterAttente(attente)}.",
                        color = Orange, fontSize = 13.sp)
                    erreurCode?.let { Text(it, color = Rouge, fontSize = 13.sp) }
                }
            }
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Créer l'espace de ma structure", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Vous serez administrateur : vous recevrez un code patient et un code soignant.",
                        fontSize = 13.sp, color = Color.Gray)
                    OutlinedTextField(nom, { nom = it.take(80) }, label = { Text("Nom de l'établissement") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ville, { ville = it.take(60) }, label = { Text("Ville") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        erreurCreer = null
                        lancer({ erreurCreer = it }) {
                            etat.etabService.creer(etat.profil.uid, etat.profil, nom, ville)
                            etat.charger()
                        }
                    }, enabled = !enCours && nom.trim().length >= 2) { Text("Créer l'espace") }
                    erreurCreer?.let { Text(it, color = Rouge, fontSize = 13.sp) }
                }
            }
        }
        Spacer(Modifier.width(0.dp))
    }
}
