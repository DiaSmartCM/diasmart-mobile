package com.diabeto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.entity.*
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.JournalViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onNavigateBack: () -> Unit,
    viewModel: JournalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            snackbarHostState.showSnackbar("Entrée sauvegardée")
        }
    }

    Scaffold(
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 4.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(GradientStart, GradientMid, GradientEnd)))
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
                        }
                        Column {
                            Text("Carnet de bord", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Suivi quotidien", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.isEditing) {
                FloatingActionButton(
                    onClick = { viewModel.startEditing() },
                    containerColor = Primary
                ) {
                    Icon(
                        if (uiState.currentEntry != null) Icons.Default.Edit else Icons.Default.Add,
                        contentDescription = "Ajouter entrée",
                        tint = Color.White
                    )
                }
            }
        },
        containerColor = Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // ── Today's glycemie correlation ────────────────
            uiState.todayGlycemie?.let { glyc ->
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MonitorHeart, null, tint = Primary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Glycémie moyenne (24h)", fontSize = 13.sp, color = OnSurfaceVariant)
                                Text("${glyc.toInt()} mg/dL", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = when {
                                    glyc < 70 -> GlucoseLow
                                    glyc in 70.0..180.0 -> GlucoseNormal
                                    glyc in 180.0..250.0 -> GlucoseHigh
                                    else -> GlucoseSevere
                                })
                            }
                        }
                    }
                }
            }

            // ── Edit form ───────────────────────────────────
            if (uiState.isEditing) {
                item {
                    JournalEditForm(
                        humeur = uiState.editHumeur,
                        stress = uiState.editStress,
                        sommeil = uiState.editSommeil,
                        heuresSommeil = uiState.editHeuresSommeil,
                        activitePhysique = uiState.editActivitePhysique,
                        minutesActivite = uiState.editMinutesActivite,
                        notes = uiState.editNotes,
                        isLoading = uiState.isLoading,
                        onHumeurChange = viewModel::setHumeur,
                        onStressChange = viewModel::setStress,
                        onSommeilChange = viewModel::setSommeil,
                        onHeuresSommeilChange = viewModel::setHeuresSommeil,
                        onActiviteChange = viewModel::setActivitePhysique,
                        onMinutesChange = viewModel::setMinutesActivite,
                        onNotesChange = viewModel::setNotes,
                        onSave = viewModel::saveEntry,
                        onCancel = viewModel::cancelEditing
                    )
                }
            } else {
                // ── Today's summary ─────────────────────────
                uiState.currentEntry?.let { entry ->
                    item {
                        TodaySummaryCard(entry)
                    }
                }

                // ── History ─────────────────────────────────
                if (uiState.entries.isNotEmpty()) {
                    item {
                        Text(
                            "Historique",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(uiState.entries.take(30), key = { it.id }) { entry ->
                        JournalHistoryItem(entry)
                    }
                }

                if (uiState.entries.isEmpty() && uiState.currentEntry == null && !uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.MenuBook, null, tint = OnSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Aucune entrée", fontWeight = FontWeight.Medium, color = OnSurfaceVariant)
                                Text("Commencez votre carnet de bord quotidien", fontSize = 13.sp, color = OnSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun JournalEditForm(
    humeur: Humeur,
    stress: NiveauStress,
    sommeil: QualiteSommeil,
    heuresSommeil: String,
    activitePhysique: Boolean,
    minutesActivite: String,
    notes: String,
    isLoading: Boolean,
    onHumeurChange: (Humeur) -> Unit,
    onStressChange: (NiveauStress) -> Unit,
    onSommeilChange: (QualiteSommeil) -> Unit,
    onHeuresSommeilChange: (String) -> Unit,
    onActiviteChange: (Boolean) -> Unit,
    onMinutesChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Journal du jour", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            // Humeur
            Text("Humeur", fontWeight = FontWeight.Medium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Humeur.entries, key = { it.name }) { h ->
                    FilterChip(
                        selected = humeur == h,
                        onClick = { onHumeurChange(h) },
                        label = { Text("${h.emoji} ${h.label}", fontSize = 13.sp) }
                    )
                }
            }

            // Stress
            Text("Niveau de stress", fontWeight = FontWeight.Medium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(NiveauStress.entries, key = { it.name }) { s ->
                    FilterChip(
                        selected = stress == s,
                        onClick = { onStressChange(s) },
                        label = { Text(s.label, fontSize = 13.sp) }
                    )
                }
            }

            // Sommeil
            Text("Qualité du sommeil", fontWeight = FontWeight.Medium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QualiteSommeil.entries, key = { it.name }) { q ->
                    FilterChip(
                        selected = sommeil == q,
                        onClick = { onSommeilChange(q) },
                        label = { Text("${q.emoji} ${q.label}", fontSize = 13.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = heuresSommeil,
                onValueChange = onHeuresSommeilChange,
                label = { Text("Heures de sommeil") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Activité physique
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Activité physique", fontWeight = FontWeight.Medium)
                }
                Switch(checked = activitePhysique, onCheckedChange = onActiviteChange)
            }

            if (activitePhysique) {
                OutlinedTextField(
                    value = minutesActivite,
                    onValueChange = onMinutesChange,
                    label = { Text("Minutes d'activité") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Annuler")
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Sauvegarder")
                    }
                }
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(entry: JournalEntity) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Aujourd'hui", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryChip(entry.humeur.emoji, entry.humeur.label)
                SummaryChip(entry.qualiteSommeil.emoji, "${entry.heuresSommeil}h")
                SummaryChip(
                    if (entry.activitePhysique) "🏃" else "🪑",
                    if (entry.activitePhysique) "${entry.minutesActivite} min" else "Repos"
                )
                SummaryChip(
                    when (entry.niveauStress) {
                        NiveauStress.AUCUN -> "😌"
                        NiveauStress.LEGER -> "😐"
                        NiveauStress.MODERE -> "😟"
                        NiveauStress.ELEVE -> "😰"
                        NiveauStress.EXTREME -> "🤯"
                    },
                    entry.niveauStress.label
                )
            }
            if (entry.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(entry.notes, fontSize = 13.sp, color = OnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SummaryChip(emoji: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = OnSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun JournalHistoryItem(entry: JournalEntity) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(entry.humeur.emoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    "${entry.humeur.label} | ${entry.qualiteSommeil.label} ${entry.heuresSommeil}h | Stress: ${entry.niveauStress.label}",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
            }
            entry.glycemieCorrelation?.let { glyc ->
                Text(
                    "${glyc.toInt()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = when {
                        glyc < 70 -> GlucoseLow
                        glyc in 70.0..180.0 -> GlucoseNormal
                        else -> GlucoseHigh
                    }
                )
                Text(" mg/dL", fontSize = 10.sp, color = OnSurfaceVariant)
            }
        }
    }
}
