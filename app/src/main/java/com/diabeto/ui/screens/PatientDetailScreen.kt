package com.diabeto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diabeto.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.entity.GlucoseStatistics
import com.diabeto.data.entity.PatientEntity
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.PatientDetailViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * Fiche d'un patient.
 *
 * v2.1.78 : le meme ecran sert desormais aux deux roles. Cote medecin il
 * presente un patient suivi, cote patient sa propre fiche — la mise en page,
 * les statistiques et les actions rapides sont identiques, seule change
 * l'autorite sur les donnees. Le patient consulte et modifie les siennes ;
 * il ne peut pas supprimer son dossier depuis ici, cette operation relevant
 * de la suppression de compte.
 *
 * @param estMaFiche vrai lorsque le patient consulte son propre dossier.
 */
fun PatientDetailScreen(
    patientId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onNavigateToGlucose: () -> Unit,
    onNavigateToMedicaments: () -> Unit,
    onNavigateToRendezVous: () -> Unit,
    estMaFiche: Boolean = false,
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
                title = {
                    Text(stringResource(if (estMaFiche) R.string.pd_title_self else R.string.pd_title))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_modify))
                    }
                    if (!estMaFiche) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.cd_delete),
                                tint = Error
                            )
                        }
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
            title = { Text(stringResource(R.string.pd_confirm_delete_title)) },
            text = { Text(stringResource(R.string.pd_confirm_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePatient()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
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
                text = stringResource(R.string.pd_stats_title),
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
                    label = stringResource(R.string.pd_stat_average)
                )
                StatItem(
                    value = "${stats.minimum.toInt()}-${stats.maximum.toInt()}",
                    unit = "mg/dL",
                    label = stringResource(R.string.pd_stat_min_max)
                )
                StatItem(
                    value = "${stats.timeInRange.toInt()}",
                    unit = "%",
                    label = stringResource(R.string.pd_stat_in_target)
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
                        text = stringResource(R.string.pd_last_reading),
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
            label = stringResource(R.string.glucose_tab_glycemie),
            onClick = onGlucoseClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Default.Medication,
            label = stringResource(R.string.med_title),
            onClick = onMedicamentsClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Default.CalendarToday,
            label = stringResource(R.string.nav_rdv_short),
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
                text = stringResource(R.string.pd_info_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            InfoRow(
                icon = Icons.Default.Cake,
                label = stringResource(R.string.pd_info_birth),
                value = patient.dateNaissance.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            )
            InfoRow(
                icon = Icons.Default.Person,
                label = stringResource(R.string.pd_info_sex),
                value = patient.sexe.name
            )
            InfoRow(
                icon = Icons.Default.Phone,
                label = stringResource(R.string.pd_info_phone),
                value = patient.telephone.ifBlank { "Non renseigné" }
            )
            InfoRow(
                icon = Icons.Default.Email,
                label = stringResource(R.string.pd_info_email),
                value = patient.email.ifBlank { "Non renseigné" }
            )
            if (patient.adresse.isNotBlank()) {
                InfoRow(
                    icon = Icons.Default.LocationOn,
                    label = stringResource(R.string.pd_info_address),
                    value = patient.adresse
                )
            }
            patient.dateDiagnostic?.let {
                InfoRow(
                    icon = Icons.Default.Event,
                    label = stringResource(R.string.pd_info_diag_date),
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
