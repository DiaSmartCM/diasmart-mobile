package com.diabeto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.entity.GlucoseStatistics
import com.diabeto.data.entity.PatientEntity
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.PatientDetailViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(
    patientId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onNavigateToGlucose: () -> Unit,
    onNavigateToMedicaments: () -> Unit,
    onNavigateToRendezVous: () -> Unit,
    viewModel: PatientDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    
    LaunchedEffect(uiState.deleteSuccess) {
        if (uiState.deleteSuccess) {
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détail du patient") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifier")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Supprimer",
                            tint = Error
                        )
                    }
                },
                colors = diaSmartTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        uiState.patient?.let { patient ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // En-tête avec avatar
                PatientHeader(patient = patient)
                
                // Statistiques glycémie
                GlucoseStatsCard(
                    stats = uiState.glucoseStats,
                    latestGlucose = uiState.latestGlucose
                )
                
                // Actions rapides
                QuickActionsRow(
                    onGlucoseClick = onNavigateToGlucose,
                    onMedicamentsClick = onNavigateToMedicaments,
                    onRendezVousClick = onNavigateToRendezVous
                )
                
                // Informations détaillées
                PatientInfoCard(patient = patient)
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        } ?: run {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
    
    // Dialog de confirmation de suppression
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirmer la suppression") },
            text = { Text("Voulez-vous vraiment supprimer ce patient ? Cette action est irréversible.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePatient()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun PatientHeader(patient: PatientEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Surface(
                shape = CircleShape,
                color = Primary.copy(alpha = 0.2f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = patient.initiales,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = patient.nomComplet,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { },
                    label = { Text(patient.typeDiabete.name.replace("_", " ")) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Primary.copy(alpha = 0.1f),
                        labelColor = Primary
                    )
                )
                AssistChip(
                    onClick = { },
                    label = { Text("${patient.age} ans") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = SurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun GlucoseStatsCard(
    stats: GlucoseStatistics,
    latestGlucose: Double?
) {
    val statusColor = when {
        stats.timeInRange >= 70 -> GlucoseNormal
        stats.timeInRange >= 50 -> Warning
        else -> GlucoseLow
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Statistiques Glycémie (30 jours)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = "${stats.moyenne.toInt()}",
                    unit = "mg/dL",
                    label = "Moyenne"
                )
                StatItem(
                    value = "${stats.minimum.toInt()}-${stats.maximum.toInt()}",
                    unit = "mg/dL",
                    label = "Min-Max"
                )
                StatItem(
                    value = "${stats.timeInRange.toInt()}",
                    unit = "%",
                    label = "Dans cible"
                )
            }
            
            latestGlucose?.let {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dernière lecture: ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${it.toInt()} mg/dL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            it < 70 -> GlucoseLow
                            it > 180 -> GlucoseHigh
                            else -> GlucoseNormal
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, unit: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant
        )
    }
}

@Composable
private fun QuickActionsRow(
    onGlucoseClick: () -> Unit,
    onMedicamentsClick: () -> Unit,
    onRendezVousClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionButton(
            icon = Icons.Default.MonitorHeart,
            label = "Glycémie",
            onClick = onGlucoseClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Default.Medication,
            label = "Médicaments",
            onClick = onMedicamentsClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Default.CalendarToday,
            label = "RDV",
            onClick = onRendezVousClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(80.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PatientInfoCard(patient: PatientEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Informations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            InfoRow(
                icon = Icons.Default.Cake,
                label = "Date de naissance",
                value = patient.dateNaissance.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            )
            InfoRow(
                icon = Icons.Default.Person,
                label = "Sexe",
                value = patient.sexe.name
            )
            InfoRow(
                icon = Icons.Default.Phone,
                label = "Téléphone",
                value = patient.telephone.ifBlank { "Non renseigné" }
            )
            InfoRow(
                icon = Icons.Default.Email,
                label = "Email",
                value = patient.email.ifBlank { "Non renseigné" }
            )
            if (patient.adresse.isNotBlank()) {
                InfoRow(
                    icon = Icons.Default.LocationOn,
                    label = "Adresse",
                    value = patient.adresse
                )
            }
            patient.dateDiagnostic?.let {
                InfoRow(
                    icon = Icons.Default.Event,
                    label = "Date diagnostic",
                    value = it.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                )
            }

            // Données corporelles
            if (patient.poids != null || patient.taille != null || patient.tourDeTaille != null || patient.masseGrasse != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Données corporelles",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                patient.poids?.let { p ->
                    InfoRow(icon = Icons.Default.FitnessCenter, label = "Poids", value = "${p} kg")
                }
                patient.taille?.let { t ->
                    InfoRow(icon = Icons.Default.Height, label = "Taille", value = "${t} cm")
                }
                patient.imc?.let { imc ->
                    InfoRow(icon = Icons.Default.Monitor, label = "IMC", value = "${"%.1f".format(imc)} kg/m² (${patient.categorieImc})")
                }
                patient.tourDeTaille?.let { tdt ->
                    InfoRow(icon = Icons.Default.RadioButtonChecked, label = "Tour de taille", value = "${tdt} cm (${patient.risqueTourDeTaille})")
                }
                patient.masseGrasse?.let { mg ->
                    InfoRow(icon = Icons.Default.Percent, label = "Masse grasse", value = "${mg}%")
                }
            }

            if (patient.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Notes:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = patient.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = OnSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
