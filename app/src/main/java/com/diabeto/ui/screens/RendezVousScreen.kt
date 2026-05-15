package com.diabeto.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.entity.RendezVousAvecPatient
import com.diabeto.data.entity.TypeRendezVous
import com.diabeto.data.model.AppointmentRequestStatus
import com.diabeto.data.model.RendezVousRequest
import com.diabeto.data.model.UserProfile
import com.diabeto.ui.components.RequiredFieldLabel
import com.diabeto.ui.components.diaSmartTextFieldColors
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.AddRendezVousState
import com.diabeto.ui.viewmodel.BookAppointmentState
import com.diabeto.ui.viewmodel.PatientOption
import com.diabeto.ui.viewmodel.RendezVousFilter
import com.diabeto.ui.viewmodel.RendezVousPatientItem
import com.diabeto.ui.viewmodel.RendezVousViewModel
import com.vanpra.composematerialdialogs.MaterialDialog
import com.vanpra.composematerialdialogs.datetime.date.datepicker
import com.vanpra.composematerialdialogs.datetime.time.timepicker
import com.vanpra.composematerialdialogs.rememberMaterialDialogState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RendezVousScreen(
    patientId: Long?,
    onNavigateBack: () -> Unit,
    onNavigateToAdd: (Long?) -> Unit,
    viewModel: RendezVousViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookState by viewModel.bookState.collectAsStateWithLifecycle()
    val addState by viewModel.addState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Demande la permission calendrier au premier affichage de l'ecran RDV
    // pour que les ajouts ulterieurs se fassent en silence.
    val calendarPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* ignored : si refuse, on retombe sur l'Intent calendrier */ }
    LaunchedEffect(Unit) {
        if (!com.diabeto.util.CalendarHelper.hasPermission(context)) {
            calendarPermLauncher.launch(arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            ))
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.addSuccess) {
        if (uiState.addSuccess) {
            snackbarHostState.showSnackbar("Rendez-vous ajouté avec succès")
            viewModel.clearAddSuccess()
        }
    }

    LaunchedEffect(uiState.bookSuccess) {
        if (uiState.bookSuccess) {
            snackbarHostState.showSnackbar("Demande de rendez-vous envoyée")
            viewModel.clearBookSuccess()
        }
    }

    // Dialogue de prise de RDV (patient)
    if (uiState.showBookDialog) {
        BookAppointmentDialog(
            state = bookState,
            availableMedecins = uiState.availableMedecins,
            onDismiss = { viewModel.toggleBookDialog(false) },
            onUpdateField = { field, value -> viewModel.updateBookField(field, value) },
            onSubmit = { viewModel.submitBookingRequest() }
        )
    }

    // Dialogue de programmation de RDV (medecin)
    if (uiState.showAddDialog) {
        ScheduleAppointmentDialog(
            state = addState,
            patientOptions = uiState.patientOptions,
            fixedPatientId = patientId,
            onDismiss = { viewModel.toggleAddDialog(false) },
            onUpdateField = { field, value -> viewModel.updateAddField(field, value) },
            onSubmit = { viewModel.addRendezVous() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isMedecin) "Rendez-vous" else "Mes rendez-vous"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = diaSmartTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.isMedecin) {
                // Médecin: programmer un RDV et l'envoyer au patient
                ExtendedFloatingActionButton(
                    onClick = { viewModel.toggleAddDialog(true) },
                    containerColor = Primary,
                    icon = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
                    text = { Text("Programmer un RDV") }
                )
            } else {
                // Patient: envoyer une demande de RDV
                ExtendedFloatingActionButton(
                    onClick = { viewModel.toggleBookDialog(true) },
                    containerColor = Primary,
                    icon = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
                    text = { Text("Prendre RDV") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Filtres
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.filter == RendezVousFilter.TOUS,
                    onClick = { viewModel.setFilter(RendezVousFilter.TOUS) },
                    label = { Text("Tous") }
                )
                FilterChip(
                    selected = uiState.filter == RendezVousFilter.A_VENIR,
                    onClick = { viewModel.setFilter(RendezVousFilter.A_VENIR) },
                    label = { Text("À venir") }
                )
                FilterChip(
                    selected = uiState.filter == RendezVousFilter.PASSES,
                    onClick = { viewModel.setFilter(RendezVousFilter.PASSES) },
                    label = { Text("Passés") }
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.isMedecin) {
                // ── VUE MÉDECIN ──────────────────────────────────
                val filteredRdv = viewModel.getFilteredRendezVous()
                val pendingRequests = uiState.appointmentRequests
                    .filter { it.status == AppointmentRequestStatus.PENDING }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Section : demandes en attente (médecin)
                    if (pendingRequests.isNotEmpty()) {
                        item {
                            Text(
                                text = "Demandes en attente (${pendingRequests.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Warning,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(
                            items = pendingRequests,
                            key = { "req-${it.id}" }
                        ) { req ->
                            MedecinRequestCard(
                                request = req,
                                onAccept = { viewModel.acceptAppointmentRequest(req.id) },
                                onReject = { viewModel.rejectAppointmentRequest(req.id) }
                            )
                        }
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = OnSurfaceVariant.copy(alpha = 0.2f)
                            )
                            Text(
                                text = "Rendez-vous programmés",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    // Liste des RDV
                    if (filteredRdv.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aucun rendez-vous programmé",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(
                            items = filteredRdv,
                            key = { it.rendezVous.id }
                        ) { rdv ->
                            MedecinRendezVousCard(
                                rdv = rdv,
                                showPatient = patientId == null,
                                onToggleConfirm = { viewModel.toggleConfirmation(rdv.rendezVous) },
                                onDelete = { viewModel.deleteRendezVous(rdv.rendezVous) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            } else {
                // ── VUE PATIENT ──────────────────────────────────
                val filteredRdv = viewModel.getFilteredPatientRendezVous()
                val myRequests = uiState.appointmentRequests

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Section : mes demandes envoyées
                    if (myRequests.isNotEmpty()) {
                        item {
                            Text(
                                text = "Mes demandes (${myRequests.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(
                            items = myRequests,
                            key = { "myreq-${it.id}" }
                        ) { req ->
                            PatientRequestCard(
                                request = req,
                                onCancel = { viewModel.cancelAppointmentRequest(req.id) }
                            )
                        }
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = OnSurfaceVariant.copy(alpha = 0.2f)
                            )
                            Text(
                                text = "Rendez-vous confirmés",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    if (filteredRdv.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (myRequests.isEmpty())
                                        "Aucun rendez-vous. Appuyez sur \"Prendre RDV\" pour en demander un."
                                    else
                                        "Aucun rendez-vous confirmé pour le moment",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    } else {
                        items(
                            items = filteredRdv,
                            key = { it.id }
                        ) { rdv ->
                            PatientRendezVousCard(rdv = rdv)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyRdvMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = "Calendrier",
                modifier = Modifier.size(64.dp),
                tint = OnSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

// ── Carte RDV pour le PATIENT (lecture seule) ────────────────────────

@Composable
private fun PatientRendezVousCard(rdv: RendezVousPatientItem) {
    val isPast = rdv.estPasse()

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPast) 0.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPast -> SurfaceVariant.copy(alpha = 0.5f)
                rdv.estConfirme -> Success.copy(alpha = 0.1f)
                else -> Surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Médecin
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Médecin",
                    modifier = Modifier.size(16.dp),
                    tint = Primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = rdv.medecinNom,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Primary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Titre
            Text(
                text = rdv.titre,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Date & heure
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = "Horaire",
                    modifier = Modifier.size(16.dp),
                    tint = if (rdv.estAujourdhui()) Error else OnSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = rdv.dateHeure.format(
                        DateTimeFormatter.ofPattern("EEEE dd MMMM à HH:mm")
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rdv.estAujourdhui()) Error else OnSurface
                )
            }

            // Lieu
            if (rdv.lieu.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Lieu",
                        modifier = Modifier.size(16.dp),
                        tint = OnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = rdv.lieu,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }

            // Type + durée + statut
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = rdv.type.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${rdv.dureeMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                // Statut de confirmation
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (rdv.estConfirme) Success.copy(alpha = 0.15f) else Warning.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (rdv.estConfirme) "Confirmé" else "En attente",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (rdv.estConfirme) Success else Warning,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Notes
            if (rdv.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = rdv.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}

// ── Carte RDV pour le MÉDECIN (avec actions) ────────────────────────

@Composable
private fun MedecinRendezVousCard(
    rdv: RendezVousAvecPatient,
    showPatient: Boolean,
    onToggleConfirm: () -> Unit,
    onDelete: () -> Unit
) {
    val isPast = rdv.rendezVous.estPasse()
    val urgencyColor = when (rdv.rendezVous.getUrgenceLevel()) {
        3 -> Error
        2 -> Warning
        else -> Success
    }
    // Dialog elegant de validation/annulation du RDV cote medecin
    var showConfirmDialog by remember { mutableStateOf(false) }
    if (showConfirmDialog) {
        val currentlyConfirmed = rdv.rendezVous.estConfirme
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(
                    imageVector = if (currentlyConfirmed) Icons.Default.EventBusy else Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint = if (currentlyConfirmed) Warning else Success,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = if (currentlyConfirmed) "Annuler la validation ?" else "Valider ce rendez-vous ?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Primary.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = rdv.patient.nomComplet,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = rdv.rendezVous.titre,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = OnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = rdv.rendezVous.dateHeure.format(
                                        DateTimeFormatter.ofPattern("EEEE dd MMMM, HH:mm")
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = if (currentlyConfirmed)
                            "Le patient sera notifie que la validation a ete retiree."
                        else
                            "Le patient sera notifie que le rendez-vous est confirme.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleConfirm()
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentlyConfirmed) Warning else Success
                    )
                ) {
                    Text(if (currentlyConfirmed) "Retirer la validation" else "Valider le RDV")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPast) 0.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPast -> SurfaceVariant.copy(alpha = 0.5f)
                rdv.rendezVous.estConfirme -> Success.copy(alpha = 0.1f)
                else -> Surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Patient (si liste globale)
            if (showPatient) {
                Text(
                    text = rdv.patient.nomComplet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rdv.rendezVous.titre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = "Horaire",
                            modifier = Modifier.size(16.dp),
                            tint = if (rdv.rendezVous.estAujourdhui()) urgencyColor else OnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = rdv.rendezVous.dateHeure.format(
                                DateTimeFormatter.ofPattern("EEEE dd MMMM à HH:mm")
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (rdv.rendezVous.estAujourdhui()) urgencyColor else OnSurface
                        )
                    }

                    if (rdv.rendezVous.lieu.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Lieu",
                                modifier = Modifier.size(16.dp),
                                tint = OnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = rdv.rendezVous.lieu,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = Primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = rdv.rendezVous.type.getDisplayName(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${rdv.rendezVous.dureeMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                }

                // Actions médecin: confirmer + supprimer
                if (!isPast) {
                    Row {
                        IconButton(onClick = { showConfirmDialog = true }) {
                            Icon(
                                imageVector = if (rdv.rendezVous.estConfirme)
                                    Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = if (rdv.rendezVous.estConfirme) "Confirmé" else "Confirmer",
                                tint = if (rdv.rendezVous.estConfirme) Success else OnSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Supprimer",
                                tint = Error
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Carte demande de RDV pour le MÉDECIN (avec accept/reject) ────────

@Composable
private fun MedecinRequestCard(
    request: RendezVousRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val dateTime = try {
        LocalDateTime.parse(request.dateHeureSouhaitee)
    } catch (e: Exception) {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Warning.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Patient",
                    modifier = Modifier.size(16.dp),
                    tint = Primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = request.patientNom,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = request.motif.ifBlank { "Demande de consultation" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = "Horaire",
                    modifier = Modifier.size(16.dp),
                    tint = OnSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = dateTime?.format(
                        DateTimeFormatter.ofPattern("EEEE dd MMMM à HH:mm")
                    ) ?: request.dateHeureSouhaitee,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = request.type.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${request.dureeMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refuser")
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Success)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accepter")
                }
            }
        }
    }
}

// ── Carte demande de RDV pour le PATIENT (avec annulation) ──────────

@Composable
private fun PatientRequestCard(
    request: RendezVousRequest,
    onCancel: () -> Unit
) {
    val dateTime = try {
        LocalDateTime.parse(request.dateHeureSouhaitee)
    } catch (e: Exception) {
        null
    }
    val (statusText, statusColor) = when (request.status) {
        AppointmentRequestStatus.PENDING -> "En attente" to Warning
        AppointmentRequestStatus.ACCEPTED -> "Acceptée" to Success
        AppointmentRequestStatus.REJECTED -> "Refusée" to Error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalHospital,
                        contentDescription = "Médecin",
                        modifier = Modifier.size(16.dp),
                        tint = Primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dr. ${request.medecinNom}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = request.motif.ifBlank { "Consultation" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = "Horaire",
                    modifier = Modifier.size(16.dp),
                    tint = OnSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = dateTime?.format(
                        DateTimeFormatter.ofPattern("EEEE dd MMMM à HH:mm")
                    ) ?: request.dateHeureSouhaitee,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface
                )
            }

            if (request.medecinReponse.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Réponse : ${request.medecinReponse}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }

            if (request.status == AppointmentRequestStatus.PENDING) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                ) {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Annuler la demande")
                }
            }
        }
    }
}

// ── Dialogue de prise de RDV (PATIENT) ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookAppointmentDialog(
    state: BookAppointmentState,
    availableMedecins: List<UserProfile>,
    onDismiss: () -> Unit,
    onUpdateField: (String, Any?) -> Unit,
    onSubmit: () -> Unit
) {
    val dateDialogState = rememberMaterialDialogState()
    val timeDialogState = rememberMaterialDialogState()

    MaterialDialog(
        dialogState = dateDialogState,
        buttons = {
            positiveButton("OK")
            negativeButton("Annuler")
        }
    ) {
        datepicker(
            initialDate = state.date,
            title = "Date souhaitée",
            allowedDateValidator = { it.isAfter(LocalDate.now().minusDays(1)) }
        ) { date ->
            onUpdateField("date", date)
        }
    }

    MaterialDialog(
        dialogState = timeDialogState,
        buttons = {
            positiveButton("OK")
            negativeButton("Annuler")
        }
    ) {
        timepicker(
            initialTime = state.heure,
            title = "Heure souhaitée"
        ) { time ->
            onUpdateField("heure", time)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Demander un rendez-vous") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Sélection du médecin
                var medecinExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = medecinExpanded,
                    onExpandedChange = { medecinExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.selectedMedecinNom.ifBlank { "" },
                        onValueChange = { },
                        label = { RequiredFieldLabel("Medecin", required = true) },
                        placeholder = {
                            Text(
                                if (availableMedecins.isEmpty())
                                    "Aucun médecin disponible"
                                else
                                    "Choisir un médecin"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(medecinExpanded) },
                        colors = diaSmartTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = medecinExpanded,
                        onDismissRequest = { medecinExpanded = false }
                    ) {
                        availableMedecins.forEach { medecin ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Dr. ${medecin.nomComplet}")
                                        if (medecin.specialite.isNotBlank()) {
                                            Text(
                                                medecin.specialite,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OnSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onUpdateField("medecin", medecin)
                                    medecinExpanded = false
                                }
                            )
                        }
                    }
                }

                // Date + heure
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        onValueChange = { },
                        label = { RequiredFieldLabel("Date", required = true) },
                        modifier = Modifier.weight(1f),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { dateDialogState.show() }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null)
                            }
                        },
                        colors = diaSmartTextFieldColors()
                    )
                    OutlinedTextField(
                        value = state.heure.format(DateTimeFormatter.ofPattern("HH:mm")),
                        onValueChange = { },
                        label = { RequiredFieldLabel("Heure", required = true) },
                        modifier = Modifier.weight(1f),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { timeDialogState.show() }) {
                                Icon(Icons.Default.Schedule, contentDescription = null)
                            }
                        },
                        colors = diaSmartTextFieldColors()
                    )
                }

                // Type
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.type.replace("_", " "),
                        onValueChange = { },
                        label = { RequiredFieldLabel("Type") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        colors = diaSmartTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        listOf(
                            "CONSULTATION",
                            "TELECONSULTATION",
                            "SUIVI",
                            "URGENCE"
                        ).forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.replace("_", " ")) },
                                onClick = {
                                    onUpdateField("type", type)
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Motif
                OutlinedTextField(
                    value = state.motif,
                    onValueChange = { onUpdateField("motif", it) },
                    label = { RequiredFieldLabel("Motif", required = true) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    colors = diaSmartTextFieldColors()
                )

                if (state.error != null) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = Error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !state.isSubmitting &&
                          state.selectedMedecinUid.isNotBlank() &&
                          state.motif.isNotBlank()
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Envoyer")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

// ── Dialogue de programmation de RDV (MEDECIN) ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleAppointmentDialog(
    state: AddRendezVousState,
    patientOptions: List<PatientOption>,
    fixedPatientId: Long?,
    onDismiss: () -> Unit,
    onUpdateField: (String, Any?) -> Unit,
    onSubmit: () -> Unit
) {
    val dateDialogState = rememberMaterialDialogState()
    val timeDialogState = rememberMaterialDialogState()

    MaterialDialog(
        dialogState = dateDialogState,
        buttons = {
            positiveButton("OK")
            negativeButton("Annuler")
        }
    ) {
        datepicker(
            initialDate = state.date,
            title = "Date du rendez-vous",
            allowedDateValidator = { it.isAfter(LocalDate.now().minusDays(1)) }
        ) { date ->
            onUpdateField("date", date)
        }
    }

    MaterialDialog(
        dialogState = timeDialogState,
        buttons = {
            positiveButton("OK")
            negativeButton("Annuler")
        }
    ) {
        timepicker(
            initialTime = state.heure,
            title = "Heure du rendez-vous"
        ) { time ->
            onUpdateField("heure", time)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Programmer un rendez-vous") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Selection du patient (si pas fixe)
                if (fixedPatientId == null) {
                    var patientExpanded by remember { mutableStateOf(false) }
                    val selectedOption = patientOptions.find { it.id == state.selectedPatientId }
                    ExposedDropdownMenuBox(
                        expanded = patientExpanded,
                        onExpandedChange = { patientExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedOption?.nom ?: "",
                            onValueChange = { },
                            label = { RequiredFieldLabel("Patient", required = true) },
                            placeholder = {
                                Text(
                                    if (patientOptions.isEmpty())
                                        "Aucun patient disponible"
                                    else
                                        "Choisir un patient"
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(patientExpanded) },
                            colors = diaSmartTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = patientExpanded,
                            onDismissRequest = { patientExpanded = false }
                        ) {
                            patientOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(option.nom)
                                            if (option.isFirestore) {
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    "en ligne",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onUpdateField("patientId", option.id)
                                        patientExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Titre
                OutlinedTextField(
                    value = state.titre,
                    onValueChange = { onUpdateField("titre", it) },
                    label = { RequiredFieldLabel("Titre", required = true) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = diaSmartTextFieldColors()
                )

                // Date + heure
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        onValueChange = { },
                        label = { RequiredFieldLabel("Date", required = true) },
                        modifier = Modifier.weight(1f),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { dateDialogState.show() }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null)
                            }
                        },
                        colors = diaSmartTextFieldColors()
                    )
                    OutlinedTextField(
                        value = state.heure.format(DateTimeFormatter.ofPattern("HH:mm")),
                        onValueChange = { },
                        label = { RequiredFieldLabel("Heure", required = true) },
                        modifier = Modifier.weight(1f),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { timeDialogState.show() }) {
                                Icon(Icons.Default.Schedule, contentDescription = null)
                            }
                        },
                        colors = diaSmartTextFieldColors()
                    )
                }

                // Type
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.type.getDisplayName(),
                        onValueChange = { },
                        label = { RequiredFieldLabel("Type") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        colors = diaSmartTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        TypeRendezVous.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.getDisplayName()) },
                                onClick = {
                                    onUpdateField("type", type)
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Duree
                OutlinedTextField(
                    value = state.duree.toString(),
                    onValueChange = { onUpdateField("duree", it.toIntOrNull() ?: 30) },
                    label = { RequiredFieldLabel("Durée (minutes)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = diaSmartTextFieldColors()
                )

                // Lieu
                OutlinedTextField(
                    value = state.lieu,
                    onValueChange = { onUpdateField("lieu", it) },
                    label = { RequiredFieldLabel("Lieu") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = diaSmartTextFieldColors()
                )

                // Notes
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { onUpdateField("notes", it) },
                    label = { RequiredFieldLabel("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    colors = diaSmartTextFieldColors()
                )

                if (state.error != null) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = Error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = state.titre.isNotBlank() &&
                          (fixedPatientId != null || state.selectedPatientId > 0L)
            ) {
                Text("Valider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RendezVousEditScreen(
    rdvId: Long?,
    patientId: Long?,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: RendezVousViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val addState by viewModel.addState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val dateDialogState = rememberMaterialDialogState()
    val timeDialogState = rememberMaterialDialogState()

    LaunchedEffect(addState.error) {
        addState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.addSuccess) {
        if (uiState.addSuccess) {
            viewModel.clearAddSuccess()
            onSaveSuccess()
        }
    }

    // Dialogs
    MaterialDialog(
        dialogState = dateDialogState,
        buttons = {
            positiveButton("OK")
            negativeButton("Annuler")
        }
    ) {
        datepicker(
            initialDate = addState.date,
            title = "Date du rendez-vous"
        ) { date ->
            viewModel.updateAddField("date", date)
        }
    }

    MaterialDialog(
        dialogState = timeDialogState,
        buttons = {
            positiveButton("OK")
            negativeButton("Annuler")
        }
    ) {
        timepicker(
            initialTime = addState.heure,
            title = "Heure du rendez-vous"
        ) { time ->
            viewModel.updateAddField("heure", time)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (rdvId == null) "Nouveau rendez-vous" else "Modifier rendez-vous") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = diaSmartTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Patient (si non prédéfini)
            if (patientId == null) {
                // Liste déroulante des patients (Room + Firestore acceptés)
                var expanded by remember { mutableStateOf(false) }
                val selectedOption = uiState.patientOptions.find { it.id == addState.selectedPatientId }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedOption?.nom ?: "",
                        onValueChange = { },
                        label = { RequiredFieldLabel("Patient", required = true) },
                        placeholder = {
                            Text(
                                if (uiState.patientOptions.isEmpty())
                                    "Aucun patient disponible"
                                else
                                    "Selectionner un patient"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        colors = diaSmartTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        uiState.patientOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(option.nom)
                                        if (option.isFirestore) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "en ligne",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.updateAddField("patientId", option.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = addState.titre,
                onValueChange = { viewModel.updateAddField("titre", it) },
                label = { RequiredFieldLabel("Titre", required = true) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = diaSmartTextFieldColors()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = addState.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    onValueChange = { },
                    label = { RequiredFieldLabel("Date", required = true) },
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { dateDialogState.show() }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Choisir date")
                        }
                    },
                    colors = diaSmartTextFieldColors()
                )

                OutlinedTextField(
                    value = addState.heure.format(DateTimeFormatter.ofPattern("HH:mm")),
                    onValueChange = { },
                    label = { RequiredFieldLabel("Heure", required = true) },
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { timeDialogState.show() }) {
                            Icon(Icons.Default.Schedule, contentDescription = "Choisir heure")
                        }
                    },
                    colors = diaSmartTextFieldColors()
                )
            }

            // Type
            var typeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it }
            ) {
                OutlinedTextField(
                    value = addState.type.getDisplayName(),
                    onValueChange = { },
                    label = { RequiredFieldLabel("Type") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    colors = diaSmartTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    TypeRendezVous.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.getDisplayName()) },
                            onClick = {
                                viewModel.updateAddField("type", type)
                                typeExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = addState.duree.toString(),
                onValueChange = { viewModel.updateAddField("duree", it.toIntOrNull() ?: 30) },
                label = { RequiredFieldLabel("Durée (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = diaSmartTextFieldColors()
            )

            OutlinedTextField(
                value = addState.lieu,
                onValueChange = { viewModel.updateAddField("lieu", it) },
                label = { RequiredFieldLabel("Lieu") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = diaSmartTextFieldColors()
            )

            OutlinedTextField(
                value = addState.notes,
                onValueChange = { viewModel.updateAddField("notes", it) },
                label = { RequiredFieldLabel("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = diaSmartTextFieldColors()
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Annuler")
                }
                Button(
                    onClick = { viewModel.addRendezVous() },
                    modifier = Modifier.weight(1f),
                    enabled = addState.titre.isNotBlank() && addState.selectedPatientId > 0L
                ) {
                    Text("Sauvegarder")
                }
            }
        }
    }
}
