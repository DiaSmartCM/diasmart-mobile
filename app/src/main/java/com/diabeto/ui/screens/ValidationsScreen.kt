package com.diabeto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.R
import com.diabeto.data.model.RollyValidation
import com.diabeto.data.model.ValidationStatus
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.ValidationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValidationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ValidationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.validations_title), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.validations_subtitle),
                                fontSize = 11.sp,
                                color = Primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                colors = diaSmartTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Info banner
            Surface(
                color = Primary.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🤖", fontSize = 24.sp)
                    Text(
                        "Les réponses de ROLLY nécessitent votre validation médicale. Validez ou corrigez avant confirmation au patient.",
                        fontSize = 13.sp,
                        color = OnSurface.copy(alpha = 0.7f),
                        lineHeight = 19.sp
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.validations.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = Success.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.validations_none_pending),
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.validations, key = { it.id }) { validation ->
                        ValidationCard(
                            validation = validation,
                            isMedecin = uiState.isMedecin,
                            onValidate = { comment -> viewModel.validate(validation.id, true, comment) },
                            onReject = { comment -> viewModel.validate(validation.id, false, comment) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValidationCard(
    validation: RollyValidation,
    isMedecin: Boolean,
    onValidate: (String) -> Unit,
    onReject: (String) -> Unit
) {
    var comment by remember { mutableStateOf("") }
    val statusColor = when (validation.status) {
        ValidationStatus.PENDING -> StatusOrange
        ValidationStatus.VALIDATED -> StatusGreen
        ValidationStatus.REJECTED -> StatusRed
    }
    val statusText = when (validation.status) {
        ValidationStatus.PENDING -> "En attente"
        ValidationStatus.VALIDATED -> "Validé"
        ValidationStatus.REJECTED -> "Rejeté"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isMedecin) validation.patientNom else "Dr. ${validation.medecinNom}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Question
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Background
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(stringResource(R.string.val_question), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Primary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        validation.question,
                        fontSize = 13.sp,
                        color = OnSurface.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ROLLY response (texte complet, scrollable si tres long)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Primary.copy(alpha = 0.06f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(10.dp)
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.val_rolly), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Primary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        validation.rollyResponse,
                        fontSize = 13.sp,
                        color = OnSurface.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )
                }
            }

            // Doctor comment (if exists)
            if (validation.medecinComment.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Success.copy(alpha = 0.06f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(stringResource(R.string.val_doctor_comment), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Success)
                        Spacer(Modifier.height(4.dp))
                        Text(validation.medecinComment, fontSize = 13.sp, color = OnSurface.copy(alpha = 0.7f))
                    }
                }
            }

            // Actions (doctor only, pending only)
            if (isMedecin && validation.status == ValidationStatus.PENDING) {
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text(stringResource(R.string.val_comment_placeholder), fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onValidate(comment) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Success),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.val_validate), fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { onReject(comment) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Error),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.val_reject), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
