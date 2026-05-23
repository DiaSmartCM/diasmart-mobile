package com.diabeto.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.model.UserRole
import com.diabeto.data.repository.ReportRepository
import com.diabeto.ui.viewmodel.AuthViewModel
import com.diabeto.ui.viewmodel.ReportViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    onNavigateToDataSharing: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    viewModel: ReportViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val role = authState.userProfile?.role ?: UserRole.PATIENT
    val isPatient = role == UserRole.PATIENT
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(role) {
        if (isPatient) viewModel.loadAsPatient() else viewModel.loadAsDoctor()
    }
    LaunchedEffect(state.info) {
        state.info?.let {
            snackbar.showSnackbar(it)
            viewModel.clearInfo()
        }
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.pendingWhatsappShare) {
        state.pendingWhatsappShare?.let { p ->
            com.diabeto.util.WhatsAppShare.share(
                context = ctx,
                phone = p.phone,
                fileName = p.fileName,
                downloadUrl = p.downloadUrl,
                introMessage = p.intro
            )
            viewModel.consumePendingWhatsappShare()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPatient) "Envoyer un rapport" else "Compte-rendu / Ordonnance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        // v2.1.58 : breadcrumb Crashlytics
        LaunchedEffect(Unit) { com.diabeto.monitoring.CrashlyticsLogger.setScreen("ReportsScreen") }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // v2.1.46 : tooltip contextuel Reports
            val onboardingVm: com.diabeto.ui.viewmodel.OnboardingViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val reportsSeen by onboardingVm.reportsSeen.collectAsStateWithLifecycle()
            com.diabeto.ui.components.ContextualTooltip(
                visible = !reportsSeen,
                title = "Envoyer un rapport",
                message = if (isPatient) "Selectionne ton medecin destinataire, choisis la periode, puis genere ton rapport PDF. 3 canaux d'envoi : messagerie in-app, email, WhatsApp." else "Genere une ordonnance ou un compte-rendu personnalise et envoie-le directement au patient.",
                onDismiss = onboardingVm::dismissReports
            )
            // Bandeau d'erreur persistant (pas un simple snackbar) pour qu'on
            // puisse lire/recopier le message complet en cas de bug.
            state.error?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("⚠ Erreur", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(msg, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = viewModel::clearMessages) {
                            Text("Masquer")
                        }
                    }
                }
            }

            if (isPatient) {
                PatientReportSection(state, viewModel)
                ExportCsvSection(state, viewModel)
            } else {
                DoctorReportSection(state, viewModel)
            }

            // Destinataire + canaux (commun)
            RecipientSection(state, viewModel, isPatient, onGoToDataSharing = onNavigateToDataSharing)

            // Partage d'un fichier local (PDF, image, Word, Excel...) deja
            // present sur le telephone — alternative au PDF auto-genere.
            ShareLocalFileSection(state, viewModel, isPatient)

            // Bouton Generer
            Button(
                onClick = {
                    if (isPatient) viewModel.generateAndSendPatient()
                    else viewModel.generateAndSendDoctor()
                },
                enabled = !state.isGenerating && !state.isSending,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isGenerating || state.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(if (state.isGenerating) "Generation du PDF..." else "Envoi en cours...")
                } else {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Generer et envoyer le PDF")
                }
            }

            // Historique
            HistorySection(state)
        }
    }
}

@Composable
private fun PatientReportSection(state: com.diabeto.ui.viewmodel.ReportUiState, vm: ReportViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Periode du rapport", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.period == ReportRepository.PERIOD_7D,
                    onClick = { vm.setPeriod(ReportRepository.PERIOD_7D) },
                    label = { Text("7 jours") }
                )
                FilterChip(
                    selected = state.period == ReportRepository.PERIOD_30D,
                    onClick = { vm.setPeriod(ReportRepository.PERIOD_30D) },
                    label = { Text("30 jours") }
                )
                FilterChip(
                    selected = state.period == ReportRepository.PERIOD_CUSTOM,
                    onClick = { vm.setPeriod(ReportRepository.PERIOD_CUSTOM) },
                    label = { Text("Au choix") }
                )
            }
            Text(
                "Le rapport contient : glycemies (resume + tableau), repas Rolly IA, " +
                    "medicaments, journal humeur/sommeil, pas du podometre.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DoctorReportSection(state: com.diabeto.ui.viewmodel.ReportUiState, vm: ReportViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Compte-rendu de consultation", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.compteRendu,
                onValueChange = vm::setCompteRendu,
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                placeholder = { Text("Anamnese, examen clinique, conclusions...") },
                minLines = 4
            )
            Divider()
            Text("Ordonnance", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.ordonnance,
                onValueChange = vm::setOrdonnance,
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                placeholder = { Text("Medicaments prescrits, dosage, posologie...") },
                minLines = 4
            )
            Divider()
            Text("Recommandations / suivi (optionnel)", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.recommandations,
                onValueChange = vm::setRecommandations,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Hygiene de vie, prochain RDV...") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipientSection(
    state: com.diabeto.ui.viewmodel.ReportUiState,
    vm: ReportViewModel,
    isPatient: Boolean,
    onGoToDataSharing: () -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (isPatient) "Envoyer a un medecin" else "Envoyer au patient",
                fontWeight = FontWeight.SemiBold
            )
            if (state.recipients.isEmpty()) {
                // v2.1.41 — empty state actionnable avec CTA explicite
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PersonOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isPatient) "Aucun medecin lie" else "Aucun patient lie",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            if (isPatient)
                                "Pour envoyer un rapport, autorise d'abord un medecin a acceder a tes donnees."
                            else
                                "Aucun patient n'a partage ses donnees avec toi. Demande-leur d'activer le partage depuis leur app.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isPatient) {
                            Button(
                                onClick = onGoToDataSharing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Lier un medecin")
                            }
                        }
                    }
                }
            } else {
                // v2.1.41 — destinataire selectionne MIS EN AVANT (Card prominent)
                val sel = state.selectedRecipient
                if (sel != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    sel.nomComplet.take(1).uppercase().ifBlank { "?" },
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sel.nomComplet.ifBlank { "(sans nom)" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (sel.email.isNotBlank()) {
                                    Text(
                                        sel.email,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                    )
                                }
                                Text(
                                    if (isPatient) "Medecin destinataire" else "Patient destinataire",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                // Selecteur (si plusieurs destinataires)
                if (state.recipients.size > 1) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            readOnly = true,
                            value = "Changer (${state.recipients.size} disponibles)",
                            onValueChange = {},
                            label = { Text("Selectionner un autre destinataire") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            state.recipients.forEach { r ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(r.nomComplet, fontWeight = FontWeight.Medium)
                                            Text(r.email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = { vm.selectRecipient(r); expanded = false }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            // Toggles canaux
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.sendByMessagerie, onCheckedChange = vm::setSendByMessagerie)
                Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Envoyer dans la messagerie in-app")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.sendByEmail, onCheckedChange = vm::setSendByEmail)
                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Envoyer par email")
            }
            if (state.sendByEmail) {
                OutlinedTextField(
                    value = state.emailOverride,
                    onValueChange = vm::setEmailOverride,
                    label = { Text("Adresse email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("destinataire@example.com") }
                )
            }
            // ── WhatsApp ──
            val recipientPhone = state.selectedRecipient?.telephone.orEmpty()
            val canWhatsapp = recipientPhone.isNotBlank() &&
                com.diabeto.util.WhatsAppShare.isLikelyPhoneNumber(recipientPhone)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.sendByWhatsapp,
                    onCheckedChange = vm::setSendByWhatsapp,
                    enabled = canWhatsapp
                )
                Icon(
                    androidx.compose.material.icons.Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (canWhatsapp)
                        "Envoyer sur WhatsApp (${recipientPhone})"
                    else
                        "WhatsApp indisponible (numero du destinataire absent)",
                    fontSize = 13.sp,
                    color = if (canWhatsapp) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Permet a l'utilisateur de choisir un fichier local (PDF, image, Word,
 * Excel, etc.) deja present sur son telephone et de l'envoyer via la
 * messagerie. Reutilise le destinataire selectionne dans RecipientSection.
 */
@Composable
private fun ShareLocalFileSection(
    state: com.diabeto.ui.viewmodel.ReportUiState,
    vm: ReportViewModel,
    isPatient: Boolean
) {
    var commentaire by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // On persiste l'autorisation de lecture pour ne pas perdre l'acces
            // si l'utilisateur quitte la screen et revient.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            pickedUri = uri
            pickedName = context.contentResolver
                .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isPatient) "Joindre un fichier (resultats, ordonnance, photo...)"
                    else "Joindre un fichier (ordonnance, examens, photo...)",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "PDF, image, Word, Excel, ou n'importe quel fichier deja present sur le telephone. Limite : 3 MB.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    launcher.launch(arrayOf(
                        "application/pdf",
                        "image/*",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "text/csv",
                        "text/plain",
                        "*/*"
                    ))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (pickedUri == null) "Choisir un fichier" else "Changer de fichier")
            }
            pickedName?.let { name ->
                Text(
                    "📎 $name",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            OutlinedTextField(
                value = commentaire,
                onValueChange = { commentaire = it },
                label = { Text("Commentaire (optionnel)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val uri = pickedUri ?: return@Button
                    vm.shareLocalFile(uri, commentaire)
                },
                enabled = pickedUri != null
                    && state.selectedRecipient != null
                    && !state.isSending
                    && !state.isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Envoi...")
                } else {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isPatient) "Envoyer ce fichier au medecin"
                        else "Envoyer ce fichier au patient"
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportCsvSection(
    state: com.diabeto.ui.viewmodel.ReportUiState,
    vm: ReportViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(state.exportCsvData) {
        state.exportCsvData?.let { csv ->
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_TEXT, csv)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "DiaSmart — Export glycemie CSV")
            }
            try {
                context.startActivity(
                    android.content.Intent.createChooser(intent, "Exporter CSV via...").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {}
            vm.clearExportCsv()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Exporter mes donnees (CSV)", fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Exporte vos glycemies et HbA1c au format CSV — compatible Excel, LibreOffice, Google Sheets.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = vm::exportCsv,
                enabled = !state.isExportingCsv,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isExportingCsv) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Preparation...")
                } else {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Exporter et partager CSV")
                }
            }
        }
    }
}

@Composable
private fun HistorySection(state: com.diabeto.ui.viewmodel.ReportUiState) {
    if (state.history.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Historique", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            val df = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }
            state.history.take(20).forEach { r ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(enabled = r.fileUrl.isNotBlank()) {
                            scope.launch {
                                val result = com.diabeto.util.FileOpener.openOrCache(
                                    context = context,
                                    url = r.fileUrl,
                                    fileName = r.fileName.ifBlank { "rapport.pdf" },
                                    mimeType = "application/pdf"
                                )
                                if (result !is com.diabeto.util.FileOpener.OpenResult.Opened) {
                                    com.diabeto.util.FileOpener.openInBrowser(context, r.fileUrl)
                                }
                            }
                        }
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(r.title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(
                                df.format(Date(r.createdAt.toDate().time)),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (r.recipientNom.isNotBlank()) {
                            Text("→ ${r.recipientNom}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (r.channels.isNotEmpty()) {
                            Text(
                                "Canaux : ${r.channels.joinToString(", ")}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
