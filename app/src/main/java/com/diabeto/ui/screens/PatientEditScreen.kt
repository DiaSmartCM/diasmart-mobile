package com.diabeto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.entity.Sexe
import com.diabeto.data.entity.TypeDiabete
import com.diabeto.ui.components.RequiredFieldLabel
import com.diabeto.ui.components.diaSmartTextFieldColors
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.PatientEditViewModel
import com.vanpra.composematerialdialogs.MaterialDialog
import com.vanpra.composematerialdialogs.datetime.date.datepicker
import com.vanpra.composematerialdialogs.rememberMaterialDialogState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientEditScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: PatientEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val dateNaissanceDialogState = rememberMaterialDialogState()
    val dateDiagnosticDialogState = rememberMaterialDialogState()
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onSaveSuccess()
        }
    }
    
    // Dialog pour la date de naissance
    MaterialDialog(
        dialogState = dateNaissanceDialogState,
        buttons = {
            positiveButton("OK")
            negativeButton("Annuler")
        }
    ) {
        datepicker(
            initialDate = uiState.dateNaissance,
            title = "Date de naissance"
        ) { date ->
            viewModel.updateField("dateNaissance", date)
        }
    }
    
    // Dialog pour la date de diagnostic
    MaterialDialog(
        dialogState = dateDiagnosticDialogState,
        buttons = {
            positiveButton("OK")
            negativeButton("Annuler")
        }
    ) {
        datepicker(
            initialDate = uiState.dateDiagnostic ?: LocalDate.now(),
            title = "Date de diagnostic"
        ) { date ->
            viewModel.updateField("dateDiagnostic", date)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Édition Patient") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nom et Prénom
            OutlinedTextField(
                value = uiState.nom,
                onValueChange = { viewModel.updateField("nom", it) },
                label = { RequiredFieldLabel("Nom", required = true) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = diaSmartTextFieldColors()
            )

            OutlinedTextField(
                value = uiState.prenom,
                onValueChange = { viewModel.updateField("prenom", it) },
                label = { RequiredFieldLabel("Prenom", required = true) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = diaSmartTextFieldColors()
            )

            // Date de naissance
            OutlinedTextField(
                value = uiState.dateNaissance.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                onValueChange = { },
                label = { RequiredFieldLabel("Date de naissance", required = true) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { dateNaissanceDialogState.show() }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Choisir date")
                    }
                },
                colors = diaSmartTextFieldColors()
            )

            // Sexe
            RequiredFieldLabel(
                text = "Sexe",
                required = true,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Sexe.entries.forEach { sexe ->
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = uiState.sexe == sexe,
                                onClick = { viewModel.updateField("sexe", sexe) },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.sexe == sexe,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(sexe.name)
                    }
                }
            }
            
            // Contact
            OutlinedTextField(
                value = uiState.telephone,
                onValueChange = { viewModel.updateField("telephone", it) },
                label = { RequiredFieldLabel("Telephone") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                colors = diaSmartTextFieldColors()
            )

            OutlinedTextField(
                value = uiState.email,
                onValueChange = { viewModel.updateField("email", it) },
                label = { RequiredFieldLabel("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                colors = diaSmartTextFieldColors()
            )

            OutlinedTextField(
                value = uiState.adresse,
                onValueChange = { viewModel.updateField("adresse", it) },
                label = { RequiredFieldLabel("Adresse") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                colors = diaSmartTextFieldColors()
            )

            // Type de diabète
            RequiredFieldLabel(
                text = "Type de diabete",
                required = true,
                color = MaterialTheme.colorScheme.onSurface
            )
            TypeDiabete.entries.forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = uiState.typeDiabete == type,
                            onClick = { viewModel.updateField("typeDiabete", type) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = uiState.typeDiabete == type,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(type.name.replace("_", " "))
                }
            }
            
            // Date de diagnostic
            OutlinedTextField(
                value = uiState.dateDiagnostic?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "",
                onValueChange = { },
                label = { RequiredFieldLabel("Date de diagnostic") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    Row {
                        if (uiState.dateDiagnostic != null) {
                            IconButton(onClick = { viewModel.updateField("dateDiagnostic", null) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer")
                            }
                        }
                        IconButton(onClick = { dateDiagnosticDialogState.show() }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Choisir date")
                        }
                    }
                },
                colors = diaSmartTextFieldColors()
            )
            
            // ── Données corporelles ──
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "Données corporelles",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.poids,
                    onValueChange = { viewModel.updateField("poids", it) },
                    label = { RequiredFieldLabel("Poids (kg)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    colors = diaSmartTextFieldColors()
                )
                OutlinedTextField(
                    value = uiState.taille,
                    onValueChange = { viewModel.updateField("taille", it) },
                    label = { RequiredFieldLabel("Taille (cm)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    colors = diaSmartTextFieldColors()
                )
            }

            // Affichage IMC calculé en temps réel
            val poidsVal = uiState.poids.toDoubleOrNull()
            val tailleVal = uiState.taille.toDoubleOrNull()
            if (poidsVal != null && tailleVal != null && tailleVal > 0) {
                val imcCalc = poidsVal / ((tailleVal / 100.0) * (tailleVal / 100.0))
                val categorie = when {
                    imcCalc < 18.5 -> "Insuffisance pondérale"
                    imcCalc < 25.0 -> "Poids normal"
                    imcCalc < 30.0 -> "Surpoids"
                    imcCalc < 35.0 -> "Obésité I"
                    imcCalc < 40.0 -> "Obésité II"
                    else -> "Obésité III"
                }
                Text(
                    text = "IMC calculé : ${"%.1f".format(imcCalc)} kg/m² — $categorie",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.tourDeTaille,
                    onValueChange = { viewModel.updateField("tourDeTaille", it) },
                    label = { RequiredFieldLabel("Tour de taille (cm)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    colors = diaSmartTextFieldColors()
                )
                OutlinedTextField(
                    value = uiState.masseGrasse,
                    onValueChange = { viewModel.updateField("masseGrasse", it) },
                    label = { RequiredFieldLabel("Masse grasse (%)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    colors = diaSmartTextFieldColors()
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Notes
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.updateField("notes", it) },
                label = { RequiredFieldLabel("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                colors = diaSmartTextFieldColors()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Boutons
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
                    onClick = { viewModel.savePatient() },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.nom.isNotBlank() && uiState.prenom.isNotBlank()
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Sauvegarder")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
