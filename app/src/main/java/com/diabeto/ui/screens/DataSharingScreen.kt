package com.diabeto.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.diabeto.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.DataSharingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSharingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPatientDetail: (Long) -> Unit = {},
    viewModel: DataSharingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMedecinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.data_sharing_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Retour")
                    }
                },
                colors = diaSmartTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Info carte
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = Primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.data_sharing_control), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "Vous décidez quelles données partager avec votre médecin. Vous pouvez révoquer l'accès à tout moment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Bouton ajouter un médecin
            if (uiState.isPatient) {
                item {
                    Button(
                        onClick = { showMedecinDialog = true; viewModel.loadMedecins() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.data_sharing_share_with_doctor))
                    }
                }
            }

            // Consentements actifs
            if (uiState.consents.isNotEmpty()) {
                item {
                    Text(
                        if (uiState.isPatient) "Médecins ayant accès" else "Patients partagés",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(uiState.consents) { consent ->
                    Card(
                        Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (consent.isActive) Success.copy(alpha = 0.15f) else Error.copy(alpha = 0.15f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (uiState.isPatient) Icons.Default.LocalHospital else Icons.Default.Person,
                                            null,
                                            tint = if (consent.isActive) Success else Error,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        if (uiState.isPatient) consent.medecinNom else consent.patientNom,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (consent.isActive) "Accès actif" else "Accès révoqué",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (consent.isActive) Success else Error
                                    )
                                    // Badges données partagées
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (consent.shareGlucose) DataBadge("Glycémie")
                                        if (consent.shareHbA1c) DataBadge("HbA1c")
                                        if (consent.shareMedications) DataBadge("Médicaments")
                                    }
                                }
                            }
                            if (uiState.isPatient && consent.isActive) {
                                IconButton(
                                    onClick = { viewModel.revokeConsent(consent.medecinUid) }
                                ) {
                                    Icon(Icons.Default.Close, "Révoquer", tint = Error)
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                null,
                                Modifier.size(48.dp),
                                tint = OnSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (uiState.isPatient) "Aucun partage actif" else "Aucun patient n'a partagé ses données",
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                if (uiState.isPatient) "Partagez vos données avec votre médecin pour un meilleur suivi"
                                else "Les patients peuvent partager leurs données depuis leur application",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Option export (patient)
            if (uiState.isPatient) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Autres options", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                item {
                    val context = LocalContext.current
                    Card(
                        Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Envoyer un rapport",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                "Exportez vos données sous forme de fichier à envoyer à votre médecin",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.generateExportData("csv")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.TableChart, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export CSV", fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.generateExportData("text")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Description, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Rapport texte", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // Dialog sélection médecin
    if (showMedecinDialog) {
        AlertDialog(
            onDismissRequest = { showMedecinDialog = false },
            title = { Text(stringResource(R.string.data_sharing_choose_doctor)) },
            text = {
                if (uiState.availableMedecins.isEmpty()) {
                    Text(stringResource(R.string.data_sharing_no_doctor), color = OnSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.availableMedecins.forEach { medecin ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceVariant,
                                onClick = {
                                    viewModel.grantConsent(medecin.uid)
                                    showMedecinDialog = false
                                }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocalHospital,
                                        null,
                                        tint = Primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(medecin.nomComplet, fontWeight = FontWeight.SemiBold)
                                        Text(medecin.email, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMedecinDialog = false }) { Text("Fermer") }
            }
        )
    }

    // Handle export sharing
    val context = LocalContext.current
    LaunchedEffect(uiState.exportData) {
        uiState.exportData?.let { data ->
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, data)
                putExtra(Intent.EXTRA_SUBJECT, "DiaSmart - Rapport de données")
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Envoyer le rapport"))
            viewModel.clearExportData()
        }
    }
}

@Composable
private fun DataBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Primary.copy(alpha = 0.1f)
    ) {
        Text(
            label,
            fontSize = 9.sp,
            color = Primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
