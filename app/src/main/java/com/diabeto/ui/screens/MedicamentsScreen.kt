package com.diabeto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
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
import com.diabeto.data.entity.FrequencePrise
import com.diabeto.data.entity.MedicamentEntity
import com.diabeto.ui.components.RequiredFieldLabel
import com.diabeto.ui.components.diaSmartTextFieldColors
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.MedicamentViewModel
import com.vanpra.composematerialdialogs.MaterialDialog
import com.vanpra.composematerialdialogs.datetime.time.timepicker
import com.vanpra.composematerialdialogs.rememberMaterialDialogState
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicamentsScreen(
    patientId: Long,
    onNavigateBack: () -> Unit,
    viewModel: MedicamentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val addState by viewModel.addState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val timeDialogState = rememberMaterialDialogState()
    
    LaunchedEffect(uiState.error, addState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
        addState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    
    LaunchedEffect(uiState.addSuccess) {
        if (uiState.addSuccess) {
            snackbarHostState.showSnackbar("Médicament ajouté avec succès")
            viewModel.clearAddSuccess()
        }
    }
    
    // Dialog pour l'heure
    MaterialDialog(
        dialogState = timeDialogState,
        buttons = {
            positiveButton(stringResource(R.string.ok))
            negativeButton(stringResource(R.string.action_cancel))
        }
    ) {
        timepicker(
            initialTime = addState.heurePrise,
            title = stringResource(R.string.med_time_pick)
        ) { time ->
            viewModel.updateAddField("heurePrise", time)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.med_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = diaSmartTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.toggleAddDialog(true) },
                containerColor = Primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            items(
                items = uiState.medicaments,
                key = { it.id }
            ) { medicament ->
                MedicamentCard(
                    medicament = medicament,
                    onToggleActive = { viewModel.toggleMedicamentStatus(medicament) },
                    onToggleRappel = { viewModel.toggleRappelStatus(medicament) },
                    onDelete = { viewModel.deleteMedicament(medicament) }
                )
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
    
    // Dialog d'ajout
    if (uiState.showAddDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleAddDialog(false) },
            title = { Text(stringResource(R.string.med_new)) },
            text = {
                // v2.1.68 : verticalScroll pour ecrans denses (Huawei 6.3")
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = addState.nom,
                        onValueChange = { viewModel.updateAddField("nom", it) },
                        label = { RequiredFieldLabel("Nom", required = true) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = diaSmartTextFieldColors()
                    )

                    OutlinedTextField(
                        value = addState.dosage,
                        onValueChange = { viewModel.updateAddField("dosage", it) },
                        label = { RequiredFieldLabel("Dosage (ex: 500mg)", required = true) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = diaSmartTextFieldColors()
                    )
                    
                    // Fréquence
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = addState.frequence.getDisplayName(),
                            onValueChange = { },
                            label = { RequiredFieldLabel("Frequence") },
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
                            FrequencePrise.entries.forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text(freq.getDisplayName()) },
                                    onClick = {
                                        viewModel.updateAddField("frequence", freq)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Heure
                    OutlinedTextField(
                        value = addState.heurePrise.format(DateTimeFormatter.ofPattern("HH:mm")),
                        onValueChange = { },
                        label = { RequiredFieldLabel(stringResource(R.string.med_time_pick), required = true) },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { timeDialogState.show() }) {
                                Icon(Icons.Default.Schedule, contentDescription = stringResource(R.string.cd_pick_time))
                            }
                        },
                        colors = diaSmartTextFieldColors()
                    )
                    
                    // Rappel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = addState.rappelActive,
                            onCheckedChange = { viewModel.updateAddField("rappelActive", it) }
                        )
                        Text(stringResource(R.string.med_enable_reminders))
                    }
                    
                    OutlinedTextField(
                        value = addState.notes,
                        onValueChange = { viewModel.updateAddField("notes", it) },
                        label = { RequiredFieldLabel("Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        colors = diaSmartTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.addMedicament() },
                    enabled = addState.nom.isNotBlank() && addState.dosage.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleAddDialog(false) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun MedicamentCard(
    medicament: MedicamentEntity,
    onToggleActive: () -> Unit,
    onToggleRappel: () -> Unit,
    onDelete: () -> Unit
) {
    val isActive = medicament.estEnCours()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 2.dp else 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Surface else SurfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = medicament.nom,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) OnSurface else OnSurfaceVariant
                        )
                        if (!isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = OnSurfaceVariant.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = stringResource(R.string.med_inactive),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "${medicament.dosage} • ${medicament.frequence.getDisplayName()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = stringResource(R.string.med_time_pick),
                            modifier = Modifier.size(16.dp),
                            tint = Primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = medicament.heurePrise.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Primary
                        )
                    }
                }
                
                // Actions
                Row {
                    IconButton(onClick = onToggleActive) {
                        Icon(
                            imageVector = if (medicament.estActif) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (medicament.estActif) "Désactiver" else "Activer",
                            tint = if (medicament.estActif) Warning else Success
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_delete),
                            tint = Error
                        )
                    }
                }
            }
            
            // Rappel
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleRappel) {
                    Icon(
                        imageVector = if (medicament.rappelActive) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = stringResource(R.string.cd_reminder),
                        tint = if (medicament.rappelActive) Primary else OnSurfaceVariant
                    )
                }
                Text(
                    text = if (medicament.rappelActive) "Rappels activés" else "Rappels désactivés",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}
