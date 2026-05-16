package com.diabeto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            positiveButton("OK")
            negativeButton("Annuler")
        }
    ) {
        timepicker(
            initialTime = addState.heurePrise,
            title = "Heure de prise"
        ) { time ->
            viewModel.updateAddField("heurePrise", time)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Médicaments") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
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
                Icon(Icons.Default.Add, contentDescription = "Ajouter")
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
            title = { Text("Nouveau médicament") },
            text = {
                Column(
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
                        label = { RequiredFieldLabel("Heure de prise", required = true) },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { timeDialogState.show() }) {
                                Icon(Icons.Default.Schedule, contentDescription = "Choisir heure")
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
                        Text("Activer les rappels")
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
                    Text("Ajouter")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleAddDialog(false) }) {
                    Text("Annuler")
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
                                    text = "Inactif",
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
                            contentDescription = "Heure de prise",
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
                            contentDescription = "Supprimer",
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
                        contentDescription = "Rappel",
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
