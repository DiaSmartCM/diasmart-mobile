package com.diabeto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.diabeto.report.FormatExport
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.model.EntreeDossier
import com.diabeto.data.model.SectionDossier
import com.diabeto.data.model.TypeEntree
import com.diabeto.data.repository.DossierRepository
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.DossierUiState
import com.diabeto.ui.viewmodel.DossierViewModel

/**
 * Dossier numerique patient.
 *
 * Cote medecin, `synthese` affiche les donnees que le patient partage deja
 * (profil, glycemies, repas) en premier onglet, puis viennent les six sections
 * que le medecin remplit. Cote patient, `synthese` est nul : il ne voit que les
 * fiches que son medecin a rendues visibles, en lecture seule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DossierPatientScreen(
    nomAffiche: String,
    onNavigateBack: () -> Unit,
    synthese: (@Composable (Modifier) -> Unit)? = null,
    viewModel: DossierViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.effacerMessage()
        }
    }

    val decalage = if (synthese != null) 1 else 0
    val onglets = buildList {
        if (synthese != null) add("Synthèse")
        SectionDossier.entries.forEach { add(it.libelle) }
    }
    var onglet by rememberSaveable { mutableIntStateOf(0) }
    val section = SectionDossier.entries.getOrNull(onglet - decalage)

    var choixType by remember { mutableStateOf(false) }
    var edition by remember { mutableStateOf<EntreeDossier?>(null) }
    var exportOuvert by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(ui.fichierExporte) {
        val fichier = ui.fichierExporte ?: return@LaunchedEffect
        val format = ui.formatExporte ?: FormatExport.PDF
        partagerFichier(context, fichier, format)
        viewModel.exportPartage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            nomAffiche.ifBlank { "Dossier" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (ui.commePatient) "Mon dossier médical" else "Dossier numérique du patient",
                            fontSize = 11.sp,
                            color = OnSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                actions = {
                    if (ui.exportEnCours) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(22.dp),
                            strokeWidth = 2.dp,
                            color = Primary
                        )
                    } else {
                        IconButton(onClick = { exportOuvert = true }, enabled = !ui.chargement) {
                            Icon(Icons.Default.Share, "Exporter le dossier", tint = Primary)
                        }
                    }
                },
                colors = diaSmartTopAppBarColors()
            )
        },
        floatingActionButton = {
            if (!ui.commePatient && section != null) {
                ExtendedFloatingActionButton(
                    onClick = { choixType = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Ajouter") },
                    containerColor = Primary,
                    contentColor = Color.White
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = onglet,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Primary
            ) {
                onglets.forEachIndexed { i, titre ->
                    Tab(
                        selected = onglet == i,
                        onClick = { onglet = i },
                        text = {
                            Text(titre, fontWeight = if (onglet == i) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                }
            }
            if (section == null) {
                synthese?.invoke(Modifier.fillMaxSize())
            } else {
                SectionContenu(
                    section = section,
                    ui = ui,
                    onImporterIdentite = viewModel::importerIdentite,
                    onModifier = { edition = it },
                    onSupprimer = viewModel::supprimer,
                    onVisibilite = viewModel::changerVisibilite
                )
            }
        }
    }

    if (choixType && section != null) {
        ChoixTypeDialog(
            section = section,
            onChoix = { type ->
                choixType = false
                edition = EntreeDossier(type = type, date = DossierRepository.aujourdhui())
            },
            onDismiss = { choixType = false }
        )
    }

    edition?.let { fiche ->
        FicheDialog(
            initiale = fiche,
            enregistrement = ui.enregistrement,
            onEnregistrer = {
                viewModel.enregistrer(it)
                edition = null
            },
            onDismiss = { edition = null }
        )
    }

    if (exportOuvert) {
        ExportDialog(
            commePatient = ui.commePatient,
            nbPrivees = ui.entrees.count { !it.visiblePatient },
            onExporter = { format, inclurePrivees ->
                exportOuvert = false
                viewModel.exporter(format, inclurePrivees)
            },
            onDismiss = { exportOuvert = false }
        )
    }
}

@Composable
private fun ExportDialog(
    commePatient: Boolean,
    nbPrivees: Int,
    onExporter: (FormatExport, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var format by remember { mutableStateOf(FormatExport.PDF) }
    // Par defaut on sort la version partageable : une note privee envoyee par
    // megarde a un confrere ou au patient ne se rattrape pas.
    var inclurePrivees by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Share, null, tint = Primary) },
        title = { Text("Exporter le dossier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Format du fichier", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                FormatExport.entries.forEach { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = format == f, onClick = { format = f }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = format == f, onClick = { format = f })
                        Text(f.libelle)
                    }
                }
                if (!commePatient) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Inclure les notes privées", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                if (inclurePrivees) "Copie complète, pour votre usage ou un confrère."
                                else "Seules les fiches visibles par le patient ($nbPrivees note(s) privée(s) exclue(s)).",
                                fontSize = 12.sp,
                                color = OnSurfaceVariant
                            )
                        }
                        Switch(checked = inclurePrivees, onCheckedChange = { inclurePrivees = it })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Le fichier contient des données médicales : partagez-le uniquement avec des personnes autorisées.",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onExporter(format, inclurePrivees) }) {
                Text("Exporter et partager", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/**
 * Ouvre le menu de partage Android (WhatsApp, e-mail, Drive, enregistrement…).
 * Le ClipData est indispensable : sans lui, les applications recues depuis le
 * selecteur n'obtiennent pas le droit de lecture sur l'URI du FileProvider.
 */
private fun partagerFichier(context: Context, fichier: File, format: FormatExport) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", fichier)
        val envoi = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fichier.nameWithoutExtension.replace('_', ' '))
            clipData = ClipData.newRawUri(fichier.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(envoi, "Partager le dossier").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(context, "Partage impossible : ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun SectionContenu(
    section: SectionDossier,
    ui: DossierUiState,
    onImporterIdentite: () -> Unit,
    onModifier: (EntreeDossier) -> Unit,
    onSupprimer: (EntreeDossier) -> Unit,
    onVisibilite: (EntreeDossier, Boolean) -> Unit
) {
    if (ui.chargement) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }
    val fiches = ui.entrees.filter { it.type.section == section }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!ui.commePatient && section == SectionDossier.ADMINISTRATIF &&
            fiches.none { it.type == TypeEntree.IDENTITE }
        ) {
            item {
                OutlinedButton(onClick = onImporterIdentite, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Importer l'identité depuis le profil du patient")
                }
            }
        }

        if (fiches.isEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant.copy(alpha = 0.3f))
                ) {
                    Text(
                        if (ui.commePatient) "Votre médecin n'a rien partagé dans cette partie pour l'instant."
                        else "Aucune fiche. Touchez « Ajouter » pour en créer une.",
                        modifier = Modifier.padding(16.dp),
                        color = OnSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        }

        items(fiches, key = { it.id }) { fiche ->
            FicheCard(
                fiche = fiche,
                lectureSeule = ui.commePatient,
                onModifier = { onModifier(fiche) },
                onSupprimer = { onSupprimer(fiche) },
                onVisibilite = { onVisibilite(fiche, it) }
            )
        }

        if (section == SectionDossier.SUIVI && !ui.commePatient) {
            item {
                Text("Rendez-vous", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (ui.rendezVous.isEmpty()) {
                item { Text("Aucun rendez-vous avec ce patient.", color = OnSurfaceVariant, fontSize = 13.sp) }
            } else {
                items(ui.rendezVous) { rdv -> RendezVousCarte(rdv) }
            }
        }
    }
}

@Composable
private fun FicheCard(
    fiche: EntreeDossier,
    lectureSeule: Boolean,
    onModifier: () -> Unit,
    onSupprimer: () -> Unit,
    onVisibilite: (Boolean) -> Unit
) {
    var confirmer by remember { mutableStateOf(false) }
    val cleTitre = fiche.type.champs.firstOrNull { !fiche.champs[it.cle].isNullOrBlank() }?.cle

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fiche.type.libelle.uppercase(),
                    fontSize = 11.sp,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (fiche.date.isNotBlank()) {
                    Text(fiche.date, fontSize = 12.sp, color = OnSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(fiche.titre, fontWeight = FontWeight.Bold, fontSize = 15.sp)

            fiche.type.champs.filter { it.cle != cleTitre }.forEach { champ ->
                val valeur = fiche.champs[champ.cle]
                if (!valeur.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(champ.libelle, fontSize = 11.sp, color = OnSurfaceVariant)
                    Text(valeur, fontSize = 14.sp)
                }
            }

            if (lectureSeule) {
                if (fiche.auteurNom.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Par Dr ${fiche.auteurNom}", fontSize = 11.sp, color = OnSurfaceVariant)
                }
            } else {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (fiche.visiblePatient) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        null,
                        tint = if (fiche.visiblePatient) StatusGreen else OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (fiche.visiblePatient) "Visible par le patient" else "Privée",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = fiche.visiblePatient, onCheckedChange = onVisibilite)
                    IconButton(onClick = onModifier) {
                        Icon(Icons.Default.Edit, "Modifier", tint = Primary)
                    }
                    IconButton(onClick = { confirmer = true }) {
                        Icon(Icons.Default.Delete, "Supprimer", tint = StatusRedDark)
                    }
                }
            }
        }
    }

    if (confirmer) {
        AlertDialog(
            onDismissRequest = { confirmer = false },
            title = { Text("Supprimer cette fiche ?") },
            text = { Text("« ${fiche.titre} » sera retirée du dossier. Cette action est définitive.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmer = false
                    onSupprimer()
                }) { Text("Supprimer", color = StatusRedDark, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { confirmer = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun RendezVousCarte(rdv: Map<String, Any?>) {
    val titre = (rdv["titre"] as? String).orEmpty().ifBlank { "Rendez-vous" }
    val date = rdv["dateHeure"]?.toString().orEmpty().take(16).replace("T", " ")
    val lieu = (rdv["lieu"] as? String).orEmpty()
    val statut = (rdv["status"] as? String).orEmpty()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CalendarToday, null, tint = Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(titre, fontWeight = FontWeight.SemiBold)
                val detail = listOf(date, lieu).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotBlank()) Text(detail, fontSize = 12.sp, color = OnSurfaceVariant)
            }
            if (statut.isNotBlank()) {
                Text(statut.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = OnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChoixTypeDialog(
    section: SectionDossier,
    onChoix: (TypeEntree) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter : ${section.libelle}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TypeEntree.deSection(section).forEach { type ->
                    TextButton(onClick = { onChoix(type) }, modifier = Modifier.fillMaxWidth()) {
                        Text(type.libelle, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun FicheDialog(
    initiale: EntreeDossier,
    enregistrement: Boolean,
    onEnregistrer: (EntreeDossier) -> Unit,
    onDismiss: () -> Unit
) {
    var date by remember(initiale) { mutableStateOf(initiale.date) }
    val valeurs = remember(initiale) { mutableStateMapOf<String, String>().apply { putAll(initiale.champs) } }
    var visible by remember(initiale) { mutableStateOf(initiale.visiblePatient) }
    val rempli = valeurs.values.any { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(initiale.type.libelle) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (JJ/MM/AAAA)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                initiale.type.champs.forEach { champ ->
                    OutlinedTextField(
                        value = valeurs[champ.cle].orEmpty(),
                        onValueChange = { valeurs[champ.cle] = it },
                        label = { Text(champ.libelle) },
                        singleLine = !champ.long,
                        minLines = if (champ.long) 2 else 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Visible par le patient", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (visible) "Le patient verra cette fiche dans son dossier."
                            else "Note de travail : le patient ne la verra pas.",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant
                        )
                    }
                    Switch(checked = visible, onCheckedChange = { visible = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = rempli && !enregistrement,
                onClick = {
                    onEnregistrer(
                        initiale.copy(
                            date = date.trim(),
                            champs = valeurs.filterValues { it.isNotBlank() }.mapValues { it.value.trim() },
                            visiblePatient = visible
                        )
                    )
                }
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
