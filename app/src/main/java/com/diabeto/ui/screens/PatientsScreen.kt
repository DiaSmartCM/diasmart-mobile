package com.diabeto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.model.ConsentStatus
import com.diabeto.data.model.DataSharingConsent
import com.diabeto.data.model.UserProfile
import com.diabeto.data.model.UserRole
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.PatientsViewModel

enum class MedecinPatientsMode { MY_PATIENTS, PLATFORM_SEARCH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientsListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPatientDetail: (Long) -> Unit,
    onNavigateToAddPatient: () -> Unit,
    onNavigateToSharedPatientData: (String, String) -> Unit = { _, _ -> },
    medecinMode: MedecinPatientsMode = MedecinPatientsMode.MY_PATIENTS,
    viewModel: PatientsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            !uiState.isMedecin -> "Mes Patients"
                            medecinMode == MedecinPatientsMode.PLATFORM_SEARCH -> "Ajouter un patient"
                            else -> "Mes patients"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
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
                .padding(horizontal = 16.dp)
        ) {
            // Barre de recherche
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                label = { Text("Rechercher un patient...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Effacer")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true
            )

            if (uiState.isMedecin) {
                // ═══ VUE MEDECIN ═══

                Spacer(Modifier.height(8.dp))

                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                } else when (medecinMode) {
                    MedecinPatientsMode.PLATFORM_SEARCH -> {
                        // Recherche de patients sur la plateforme
                        val filtered = uiState.allPlatformPatients.filter {
                            uiState.searchQuery.isBlank() ||
                            it.nomComplet.contains(uiState.searchQuery, ignoreCase = true) ||
                            it.email.contains(uiState.searchQuery, ignoreCase = true)
                        }

                        if (filtered.isEmpty()) {
                            EmptyState("Aucun patient sur la plateforme", Icons.Default.PeopleOutline)
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filtered, key = { it.uid }) { patient ->
                                    val consent = uiState.myRequests.find { it.patientUid == patient.uid }
                                    PlatformPatientCard(
                                        patient = patient,
                                        consent = consent,
                                        onRequestAccess = { viewModel.requestAccess(patient.uid) },
                                        isRequesting = uiState.requestingUid == patient.uid
                                    )
                                }
                                item { Spacer(Modifier.height(16.dp)) }
                            }
                        }
                    }
                    MedecinPatientsMode.MY_PATIENTS -> {
                        // Patients abonnes (consent accepte)
                        val filtered = uiState.myPatients.filter {
                            uiState.searchQuery.isBlank() ||
                            it.patientNom.contains(uiState.searchQuery, ignoreCase = true)
                        }
                        if (filtered.isEmpty()) {
                            EmptyState(
                                if (uiState.searchQuery.isBlank())
                                    "Aucun patient abonne. Ajoutez un patient depuis l'accueil."
                                else
                                    "Aucun patient ne correspond",
                                Icons.Default.HourglassEmpty
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filtered, key = { it.patientUid }) { consent ->
                                    MyPatientCard(
                                        consent = consent,
                                        onViewData = {
                                            onNavigateToSharedPatientData(
                                                consent.patientUid,
                                                consent.patientNom
                                            )
                                        }
                                    )
                                }
                                item { Spacer(Modifier.height(16.dp)) }
                            }
                        }
                    }
                }
            } else {
                // ═══ VUE PATIENT (ancienne vue Room DB) ═══
                // Demandes en attente
                if (uiState.pendingRequests.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsActive, null, tint = Warning)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Demandes d'acces en attente",
                                    fontWeight = FontWeight.Bold,
                                    color = Warning
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            uiState.pendingRequests.forEach { request ->
                                PendingRequestCard(
                                    consent = request,
                                    onAccept = { viewModel.acceptRequest(request.medecinUid) },
                                    onReject = { viewModel.rejectRequest(request.medecinUid) }
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }

                // Liste locale (Room DB) - ancienne logique
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        items = uiState.localPatients,
                        key = { it.id }
                    ) { patient ->
                        Card(
                            onClick = { onNavigateToPatientDetail(patient.id) },
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = CircleShape, color = Primary.copy(alpha = 0.2f), modifier = Modifier.size(56.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(patient.initiales, style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(patient.nomComplet, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${patient.typeDiabete.name.replace("_", " ")} • ${patient.age} ans", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceVariant)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Composants
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun PlatformPatientCard(
    patient: UserProfile,
    consent: DataSharingConsent?,
    onRequestAccess: () -> Unit,
    isRequesting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Surface(shape = CircleShape, color = Primary.copy(alpha = 0.15f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        patient.nomComplet.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    patient.nomComplet.ifBlank { patient.email },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(patient.email, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }

            Spacer(Modifier.width(8.dp))

            // Bouton selon le statut
            when (consent?.status) {
                ConsentStatus.ACCEPTED -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StatusGreen.copy(alpha = 0.15f)
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = StatusGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Accepte", fontSize = 12.sp, color = StatusGreen, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                ConsentStatus.PENDING -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Warning.copy(alpha = 0.15f)
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HourglassTop, null, tint = Warning, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("En attente", fontSize = 12.sp, color = Warning, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                ConsentStatus.REJECTED -> {
                    OutlinedButton(
                        onClick = onRequestAccess,
                        enabled = !isRequesting,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Redemander", fontSize = 12.sp)
                    }
                }
                null -> {
                    Button(
                        onClick = onRequestAccess,
                        enabled = !isRequesting,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (isRequesting) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PersonAdd, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Demander acces", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyPatientCard(
    consent: DataSharingConsent,
    onViewData: () -> Unit
) {
    Card(
        onClick = onViewData,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = Primary.copy(alpha = 0.2f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(consent.patientNom.take(2).uppercase(), fontWeight = FontWeight.Bold, color = Primary, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(consent.patientNom, fontWeight = FontWeight.SemiBold)
                Text("Acces accorde", style = MaterialTheme.typography.bodySmall, color = StatusGreen)
            }
            Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceVariant)
        }
    }
}

@Composable
private fun PendingRequestCard(
    consent: DataSharingConsent,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MedicalServices, null, tint = Primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Dr. ${consent.medecinNom}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Souhaite acceder a vos donnees", fontSize = 12.sp, color = OnSurfaceVariant)
            }
            IconButton(onClick = onAccept, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.CheckCircle, "Accepter", tint = StatusGreen)
            }
            IconButton(onClick = onReject, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Cancel, "Refuser", tint = StatusRedDark)
            }
        }
    }
}

@Composable
private fun EmptyState(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, modifier = Modifier.size(64.dp), tint = OnSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        Text(text, color = OnSurfaceVariant, fontSize = 15.sp)
    }
}
