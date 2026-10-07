package com.diabeto.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.model.Etablissement
import com.diabeto.data.model.MembreEtablissement
import com.diabeto.data.model.PrioriteSuivi
import com.diabeto.data.model.RoleEtablissement
import com.diabeto.data.model.SuiviPatient
import com.diabeto.data.model.TypeCode
import com.diabeto.data.repository.EtablissementRepository
import com.diabeto.ui.viewmodel.EtablissementUiState
import com.diabeto.ui.viewmodel.EtablissementViewModel
import com.diabeto.ui.viewmodel.FiltreSuivi
import java.io.File
import java.time.format.DateTimeFormatter

/**
 * Espace etablissement (offre B2B2C).
 *
 *  - Patient : rejoint un centre avec le code patient, ou le quitte.
 *  - Soignant sans centre : cree un centre (il en devient l'administrateur)
 *    ou rejoint un centre avec le code soignant.
 *  - Soignant du centre : tableau de bord (patients a risque, HbA1c, perdus
 *    de vue), equipe, codes d'invitation. L'administrateur genere en plus le
 *    rapport anonymise pour le payeur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtablissementScreen(
    onNavigateBack: () -> Unit,
    viewModel: EtablissementViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(ui.message) {
        ui.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    LaunchedEffect(ui.rapportPdf) {
        ui.rapportPdf?.let { partagerPdf(context, it); viewModel.rapportPartage() }
    }
    LaunchedEffect(Unit) { com.diabeto.monitoring.CrashlyticsLogger.setScreen("EtablissementScreen") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.etablissement?.nom ?: ui.affiliation?.etablissementNom ?: "Etablissement de sante") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
                actions = {
                    if (ui.etablissement != null) {
                        IconButton(onClick = { viewModel.actualiserSuivi() }) { Icon(Icons.Default.Refresh, "Actualiser") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val aff = ui.affiliation
            when {
                ui.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                aff == null && ui.estSoignant -> SoignantSansCentre(ui, viewModel)
                aff == null -> PatientSansCentre(ui, viewModel)
                aff.role == RoleEtablissement.PATIENT -> PatientInscritVue(aff.etablissementNom, onQuitter = viewModel::quitter)
                ui.etablissement != null -> EspaceSoignant(ui, viewModel)
                else -> Text("Etablissement introuvable", Modifier.align(Alignment.Center))
            }
        }
    }

    ui.codeAConfirmer?.let { info ->
        val pourPatient = info.type == TypeCode.PATIENT
        AlertDialog(
            onDismissRequest = viewModel::annulerCode,
            title = { Text("Rejoindre ${info.etablissementNom} ?") },
            text = {
                Text(
                    if (pourPatient)
                        "Les soignants de cet etablissement pourront LIRE tes glycemies et tes HbA1c pour mieux te suivre. " +
                            "Ils ne peuvent rien modifier. Tu peux quitter l'etablissement a tout moment depuis cette page."
                    else
                        "Tu rejoins l'equipe soignante. Tu verras les patients inscrits et leurs indicateurs de suivi."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmerRejoindre, enabled = !ui.enCours) {
                    Text(if (pourPatient) "J'accepte et je rejoins" else "Rejoindre")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::annulerCode) { Text("Annuler") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  PATIENT
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun PatientSansCentre(ui: EtablissementUiState, vm: EtablissementViewModel) {
    var code by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EtabBandeau(
            "Ton centre de sante te suit avec DiaSmart",
            "Si ton hopital, ta clinique ou ton centre t'a donne un code DiaSmart, tape-le ici. " +
                "Ses soignants verront tes glycemies et tes HbA1c (lecture seule)."
        )
        CodeField(code, onChange = { code = it }, label = "Code de l'etablissement")
        Button(
            onClick = { vm.verifierCode(code) },
            enabled = !ui.enCours && EtablissementRepository.normaliserCode(code).length == 8,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continuer") }
    }
}

@Composable
private fun PatientInscritVue(nom: String, onQuitter: () -> Unit) {
    var confirmer by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EtabBandeau(
            "Tu es suivi par $nom",
            "Les soignants de cet etablissement lisent tes glycemies et tes HbA1c. Ils ne peuvent rien modifier."
        )
        OutlinedButton(onClick = { confirmer = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(8.dp)); Text("Quitter l'etablissement")
        }
    }
    if (confirmer) {
        AlertDialog(
            onDismissRequest = { confirmer = false },
            title = { Text("Quitter $nom ?") },
            text = { Text("Ses soignants ne verront plus tes nouvelles mesures.") },
            confirmButton = { Button(onClick = { confirmer = false; onQuitter() }) { Text("Quitter") } },
            dismissButton = { TextButton(onClick = { confirmer = false }) { Text("Annuler") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  SOIGNANT SANS CENTRE : creer ou rejoindre
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SoignantSansCentre(ui: EtablissementUiState, vm: EtablissementViewModel) {
    var nom by remember { mutableStateOf("") }
    var ville by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            EtabBandeau(
                "Espace etablissement",
                "Un hopital, une clinique ou un centre suit ses patients diabetiques a plusieurs soignants. " +
                    "Celui qui cree l'espace en est l'administrateur."
            )
        }
        item { Text("Creer l'espace de mon etablissement", fontWeight = FontWeight.Bold) }
        item {
            OutlinedTextField(nom, { nom = it.take(EtablissementRepository.NOM_MAX) }, label = { Text("Nom de l'etablissement") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(ville, { ville = it.take(EtablissementRepository.VILLE_MAX) }, label = { Text("Ville") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Button(onClick = { vm.creer(nom, ville) }, enabled = !ui.enCours && nom.trim().length >= EtablissementRepository.NOM_MIN,
                modifier = Modifier.fillMaxWidth()) { Text("Creer et devenir administrateur") }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { Text("Rejoindre une equipe existante", fontWeight = FontWeight.Bold) }
        item { CodeField(code, onChange = { code = it }, label = "Code soignant (donne par l'administrateur)") }
        item {
            OutlinedButton(
                onClick = { vm.verifierCode(code) },
                enabled = !ui.enCours && EtablissementRepository.normaliserCode(code).length == 8,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Rejoindre") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  SOIGNANT DU CENTRE : tableau de bord, equipe, invitations
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun EspaceSoignant(ui: EtablissementUiState, vm: EtablissementViewModel) {
    var onglet by rememberSaveable { mutableIntStateOf(0) }
    val etab = ui.etablissement ?: return
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = onglet) {
            listOf("Tableau de bord", "Equipe", "Invitations").forEachIndexed { i, t ->
                Tab(selected = onglet == i, onClick = { onglet = i }, text = { Text(t, fontSize = 13.sp) })
            }
        }
        when (onglet) {
            0 -> TableauDeBord(ui, vm)
            1 -> Equipe(ui, vm)
            else -> Invitations(etab, ui.estAdmin, vm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableauDeBord(ui: EtablissementUiState, vm: EtablissementViewModel) {
    val context = LocalContext.current
    var aRetirer by remember { mutableStateOf<SuiviPatient?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Compteur("Inscrits", ui.suivis.size, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Compteur("A risque", ui.suivis.count { it.priorite == PrioriteSuivi.HAUTE }, Color(0xFFD32F2F), Modifier.weight(1f))
                Compteur("Perdus de vue", ui.suivis.count { it.perduDeVue }, Color(0xFFF57C00), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FiltreSuivi.entries.forEach { f ->
                    FilterChip(
                        selected = ui.filtre == f,
                        onClick = { vm.setFiltre(f) },
                        label = {
                            Text(when (f) {
                                FiltreSuivi.TOUS -> "Tous"
                                FiltreSuivi.A_RISQUE -> "A risque"
                                FiltreSuivi.PERDUS_DE_VUE -> "Perdus de vue"
                            })
                        }
                    )
                }
            }
        }
        if (ui.estAdmin) {
            item {
                OutlinedButton(
                    onClick = { vm.genererRapport(context) },
                    enabled = !ui.enCours && !ui.chargementSuivi,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(8.dp))
                    Text("Rapport pour le payeur (PDF anonymise)")
                }
            }
        }
        if (ui.chargementSuivi) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() } }
        } else if (ui.suivisFiltres.isEmpty()) {
            item {
                Text(
                    if (ui.suivis.isEmpty()) "Aucun patient inscrit. Donne le code patient (onglet Invitations) a tes patients."
                    else "Aucun patient dans ce filtre.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!ui.chargementSuivi) {
            items(ui.suivisFiltres, key = { it.uid }) { s ->
                CartePatient(s, peutRetirer = ui.estAdmin, onRetirer = { aRetirer = s })
            }
        }
        item {
            Text(
                "La priorite est une aide pour savoir qui recontacter en premier, pas un diagnostic. " +
                    "Perdu de vue : aucune mesure depuis ${EtablissementRepository.JOURS_PERDU_DE_VUE} jours.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    aRetirer?.let { s ->
        AlertDialog(
            onDismissRequest = { aRetirer = null },
            title = { Text("Retirer ${s.nom} ?") },
            text = { Text("L'equipe ne verra plus ses mesures. Il pourra se reinscrire avec le code patient.") },
            confirmButton = { Button(onClick = { vm.retirerPatient(s.uid); aRetirer = null }) { Text("Retirer") } },
            dismissButton = { TextButton(onClick = { aRetirer = null }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun Compteur(libelle: String, n: Int, couleur: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = couleur.copy(alpha = 0.10f), modifier = modifier) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(n.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = couleur)
            Text(libelle, fontSize = 11.sp)
        }
    }
}

private val formatDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
private fun CartePatient(s: SuiviPatient, peutRetirer: Boolean, onRetirer: () -> Unit) {
    val couleur = when (s.priorite) {
        PrioriteSuivi.HAUTE -> Color(0xFFD32F2F)
        PrioriteSuivi.MOYENNE -> Color(0xFFF57C00)
        PrioriteSuivi.BASSE -> Color(0xFF2E7D32)
        PrioriteSuivi.INCONNUE -> Color(0xFF757575)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.nom.ifBlank { "Patient" }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = couleur.copy(alpha = 0.15f)) {
                    Text(s.priorite.libelle, color = couleur, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                if (peutRetirer) {
                    IconButton(onClick = onRetirer) { Icon(Icons.Default.PersonRemove, "Retirer", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (s.perduDeVue) {
                Text("Perdu de vue", color = Color(0xFFF57C00), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            val hba1c = s.derniereHbA1c?.let {
                String.format(java.util.Locale.FRANCE, "%.1f %%", it) +
                    (s.dateHbA1c?.let { d -> " le ${d.format(formatDate)}" } ?: "") +
                    (if (s.hba1cEstimee) " (estimee)" else "")
            } ?: "aucune"
            Text("HbA1c : $hba1c", fontSize = 13.sp)
            Text(
                "Moyenne 30 j : ${s.moyenne30j?.let { "${it.toInt()} mg/dL (${s.nbMesures30j} mesures)" } ?: "pas de mesure"}",
                fontSize = 13.sp
            )
            Text(
                "Derniere mesure : ${s.derniereMesure?.toLocalDate()?.format(formatDate) ?: "jamais"}",
                fontSize = 13.sp
            )
            if (s.derniereTension != null) {
                Text(
                    "Tension : ${s.derniereTension} mmHg" +
                        (s.tensionMoyenne30j?.let { " (moyenne 30 j $it)" } ?: ""),
                    fontSize = 13.sp
                )
            }
            if (s.raisons.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(s.raisons.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Equipe(ui: EtablissementUiState, vm: EtablissementViewModel) {
    var aRetirer by remember { mutableStateOf<MembreEtablissement?>(null) }
    var quitter by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ui.membres, key = { it.uid }) { m ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (m.role == RoleEtablissement.ADMIN) Icons.Default.AdminPanelSettings else Icons.Default.MedicalServices, null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.nom.ifBlank { "Soignant" }, fontWeight = FontWeight.SemiBold)
                        Text(if (m.role == RoleEtablissement.ADMIN) "Administrateur" else "Soignant", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (ui.estAdmin && m.role != RoleEtablissement.ADMIN) {
                        IconButton(onClick = { aRetirer = m }) { Icon(Icons.Default.PersonRemove, "Retirer") }
                    }
                }
            }
        }
        if (!ui.estAdmin) {
            item {
                OutlinedButton(onClick = { quitter = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Quitter l'equipe")
                }
            }
        }
    }
    aRetirer?.let { m ->
        AlertDialog(
            onDismissRequest = { aRetirer = null },
            title = { Text("Retirer ${m.nom} de l'equipe ?") },
            text = { Text("Il ne verra plus les patients de l'etablissement.") },
            confirmButton = { Button(onClick = { vm.retirerMembre(m.uid); aRetirer = null }) { Text("Retirer") } },
            dismissButton = { TextButton(onClick = { aRetirer = null }) { Text("Annuler") } }
        )
    }
    if (quitter) {
        AlertDialog(
            onDismissRequest = { quitter = false },
            title = { Text("Quitter l'equipe ?") },
            text = { Text("Tu ne verras plus les patients de l'etablissement.") },
            confirmButton = { Button(onClick = { quitter = false; vm.quitter() }) { Text("Quitter") } },
            dismissButton = { TextButton(onClick = { quitter = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun Invitations(etab: Etablissement, estAdmin: Boolean, vm: EtablissementViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CarteCode(
                titre = "Code patient",
                explication = "A donner aux patients : dans DiaSmart, Parametres > Etablissement de sante, ils tapent ce code.",
                code = etab.codePatient,
                messagePartage = "Bonjour, ${etab.nom} vous suit avec l'application DiaSmart. " +
                    "Installez DiaSmart, puis dans Parametres > Etablissement de sante, tapez le code : ${etab.codePatient}",
                peutRegenerer = estAdmin,
                onRegenerer = { vm.regenererCode(TypeCode.PATIENT) }
            )
        }
        if (estAdmin) {
            item {
                CarteCode(
                    titre = "Code soignant",
                    explication = "A donner seulement aux soignants de ton equipe (compte medecin DiaSmart).",
                    code = etab.codeSoignant,
                    messagePartage = "Rejoins l'equipe ${etab.nom} sur DiaSmart : Parametres > Etablissement de sante, " +
                        "code soignant : ${etab.codeSoignant}",
                    peutRegenerer = true,
                    onRegenerer = { vm.regenererCode(TypeCode.SOIGNANT) }
                )
            }
        }
    }
}

@Composable
private fun CarteCode(
    titre: String,
    explication: String,
    code: String,
    messagePartage: String,
    peutRegenerer: Boolean,
    onRegenerer: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var confirmer by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(titre, fontWeight = FontWeight.Bold)
            Text(explication, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(code.chunked(4).joinToString(" "), fontSize = 28.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(code))
                    Toast.makeText(context, "Code copie", Toast.LENGTH_SHORT).show()
                }) { Text("Copier") }
                Button(onClick = { partagerTexte(context, messagePartage) }) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Partager")
                }
                if (peutRegenerer) {
                    TextButton(onClick = { confirmer = true }) { Text("Nouveau code") }
                }
            }
        }
    }
    if (confirmer) {
        AlertDialog(
            onDismissRequest = { confirmer = false },
            title = { Text("Creer un nouveau code ?") },
            text = { Text("L'ancien code ne marchera plus. Les personnes deja inscrites restent inscrites.") },
            confirmButton = { Button(onClick = { confirmer = false; onRegenerer() }) { Text("Nouveau code") } },
            dismissButton = { TextButton(onClick = { confirmer = false }) { Text("Annuler") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  Elements communs
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun EtabBandeau(titre: String, texte: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalHospital, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(titre, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(6.dp))
            Text(texte, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        // On peut coller tout le message recu : seul le code est garde
        onValueChange = { onChange(EtablissementRepository.codeDepuisCollage(it).uppercase().take(12)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun partagerTexte(context: Context, texte: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, texte)
    }
    context.startActivity(Intent.createChooser(intent, "Partager le code"))
}

private fun partagerPdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "DiaSmart : rapport de resultats")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Envoyer le rapport"))
}
