package com.diabeto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Send
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPatient) "Envoyer un rapport" else "Compte-rendu / Ordonnance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

            if (isPatient) PatientReportSection(state, viewModel)
            else DoctorReportSection(state, viewModel)

            // Destinataire + canaux (commun)
            RecipientSection(state, viewModel, isPatient)

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
    isPatient: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (isPatient) "Envoyer a un medecin" else "Envoyer au patient",
                fontWeight = FontWeight.SemiBold
            )
            if (state.recipients.isEmpty()) {
                Text(
                    if (isPatient)
                        "Aucun medecin lie. Utilisez l'onglet 'Medecin' pour autoriser un medecin a vous suivre."
                    else
                        "Aucun patient lie pour le moment.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        readOnly = true,
                        value = state.selectedRecipient?.nomComplet ?: "Selectionner...",
                        onValueChange = {},
                        label = { Text(if (isPatient) "Medecin destinataire" else "Patient destinataire") },
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
        }
    }
}

@Composable
private fun HistorySection(state: com.diabeto.ui.viewmodel.ReportUiState) {
    if (state.history.isEmpty()) return
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
