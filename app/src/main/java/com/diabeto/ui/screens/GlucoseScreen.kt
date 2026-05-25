package com.diabeto.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.diabeto.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.entity.ContexteGlucose
import com.diabeto.data.entity.HbA1cEntity
import com.diabeto.data.entity.LectureGlucoseEntity
import com.diabeto.ui.components.RequiredFieldLabel
import com.diabeto.ui.components.RollyIcon
import com.diabeto.ui.components.RollyIconInline
import com.diabeto.ui.components.diaSmartTextFieldColors
import com.diabeto.ui.theme.*
import com.diabeto.data.repository.GlucoseRepository.DailyGlucoseAverage
import com.diabeto.ui.viewmodel.GlucoseTab
import com.diabeto.ui.viewmodel.GlucoseUiState
import com.diabeto.ui.viewmodel.GlucoseViewModel
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseTrackingScreen(
    patientId: Long,
    onNavigateBack: () -> Unit,
    viewModel: GlucoseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showContexteMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(uiState.addSuccess) {
        if (uiState.addSuccess) { snackbarHostState.showSnackbar("Ajouté avec succès"); viewModel.clearAddSuccess() }
    }

    // Export share intent
    LaunchedEffect(uiState.exportCsvData) {
        uiState.exportCsvData?.let { data ->
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, data)
                putExtra(Intent.EXTRA_SUBJECT, "DiaSmart - Rapport glycémique")
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Partager le rapport"))
            viewModel.clearExportData()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.glucose_tracking)) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    // Bouton export/partage
                    IconButton(onClick = { viewModel.generateExportData() }) {
                        Icon(Icons.Default.Share, "Exporter")
                    }
                    if (uiState.activeTab == GlucoseTab.GLYCEMIE) {
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(if (uiState.showGraph) Icons.Default.List else Icons.Default.ShowChart, "Vue")
                        }
                    }
                    // Bouton ROLLY analyse
                    IconButton(onClick = { viewModel.triggerRollyAnalysis() }) {
                        RollyIconInline(size = 24.dp, tint = Primary)
                    }
                },
                colors = diaSmartTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        // v2.1.58 : breadcrumb Crashlytics
        LaunchedEffect(Unit) { com.diabeto.monitoring.CrashlyticsLogger.setScreen("GlucoseScreen") }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // v2.1.46 : tooltip contextuel a la 1ere ouverture
            val onboardingVm: com.diabeto.ui.viewmodel.OnboardingViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val glucoseSeen by onboardingVm.glucoseSeen.collectAsStateWithLifecycle()
            com.diabeto.ui.components.ContextualTooltip(
                visible = !glucoseSeen,
                title = stringResource(R.string.glucose_screen_title),
                message = "Tap sur le bouton + en bas a droite pour saisir une nouvelle lecture. Onglet HbA1c pour ton historique trimestriel. Tu peux exporter en CSV depuis Reglages.",
                onDismiss = onboardingVm::dismissGlucose
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Onglets : Glycémie | HbA1c | Suivi
            TabRow(
                selectedTabIndex = when (uiState.activeTab) {
                    GlucoseTab.GLYCEMIE -> 0
                    GlucoseTab.HBA1C -> 1
                    GlucoseTab.SUIVI -> 2
                },
                containerColor = Background,
                contentColor = Primary
            ) {
                Tab(
                    selected = uiState.activeTab == GlucoseTab.GLYCEMIE,
                    onClick = { viewModel.setActiveTab(GlucoseTab.GLYCEMIE) },
                    selectedContentColor = Primary,
                    unselectedContentColor = TextPrimary,
                    text = { Text(stringResource(R.string.glucose_tab_glycemie), fontSize = 12.sp) },
                    icon = { Icon(Icons.Outlined.Bloodtype, null, Modifier.size(18.dp)) }
                )
                Tab(
                    selected = uiState.activeTab == GlucoseTab.HBA1C,
                    onClick = { viewModel.setActiveTab(GlucoseTab.HBA1C) },
                    selectedContentColor = Primary,
                    unselectedContentColor = TextPrimary,
                    text = { Text(stringResource(R.string.glucose_tab_hba1c), fontSize = 12.sp) },
                    icon = { Icon(Icons.Outlined.Science, null, Modifier.size(18.dp)) }
                )
                Tab(
                    selected = uiState.activeTab == GlucoseTab.SUIVI,
                    onClick = { viewModel.setActiveTab(GlucoseTab.SUIVI) },
                    selectedContentColor = Primary,
                    unselectedContentColor = TextPrimary,
                    text = { Text(stringResource(R.string.glucose_tab_suivi), fontSize = 12.sp) },
                    icon = { Icon(Icons.Outlined.Timeline, null, Modifier.size(18.dp)) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            when (uiState.activeTab) {
                GlucoseTab.GLYCEMIE -> GlycemieContent(uiState, viewModel) { showContexteMenu = it }
                GlucoseTab.HBA1C -> HbA1cContent(uiState, viewModel)
                GlucoseTab.SUIVI -> SuiviContent(uiState, viewModel)
            }
        }
        }  // v2.1.46 : closes outer Column wrapping the tooltip
    }

    DropdownMenu(expanded = showContexteMenu, onDismissRequest = { showContexteMenu = false }) {
        ContexteGlucose.entries.forEach { ctx ->
            DropdownMenuItem(text = { Text(ctx.getDisplayName()) }, onClick = { viewModel.onContexteChange(ctx); showContexteMenu = false })
        }
    }

    // Dialog ajout HbA1c
    if (uiState.showAddHbA1cDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideAddHbA1cDialog() },
            title = { Text(stringResource(R.string.glucose_add_hba1c)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = uiState.newHbA1cValeur, onValueChange = viewModel::onHbA1cValeurChange,
                        label = { RequiredFieldLabel(stringResource(R.string.glucose_hba1c_label), required = true) }, placeholder = { Text(stringResource(R.string.glucose_hba1c_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = diaSmartTextFieldColors())
                    OutlinedTextField(value = uiState.newHbA1cLabo, onValueChange = viewModel::onHbA1cLaboChange,
                        label = { RequiredFieldLabel("Laboratoire (optionnel)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = diaSmartTextFieldColors())
                    Text(stringResource(R.string.glucose_hba1c_info),
                        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.addHbA1c() }) { Text(stringResource(R.string.action_add)) } },
            dismissButton = { TextButton(onClick = { viewModel.hideAddHbA1cDialog() }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    // Dialog ROLLY auto-analyse
    if (uiState.showRollyAnalysis && uiState.rollyAnalysis != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRollyAnalysis,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RollyIcon(size = 28.dp, showBackground = true)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.glucose_rolly_analysis), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    LazyColumn {
                        item {
                            Text(uiState.rollyAnalysis ?: "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = viewModel::dismissRollyAnalysis) { Text(stringResource(R.string.common_close)) } }
        )
    }
}

// ── ONGLET GLYCÉMIE ─────────────────────────────────────────────────
@Composable
private fun GlycemieContent(uiState: GlucoseUiState, viewModel: GlucoseViewModel, onShowContexteMenu: (Boolean) -> Unit) {
    Column {
        AddReadingCard(uiState.newValeur, viewModel::onValeurChange, uiState.selectedContexte, { onShowContexteMenu(true) }, { viewModel.addLecture() }, uiState.newValeur.isNotBlank())
        Spacer(Modifier.height(8.dp))

        uiState.hba1cEstimee?.let { est ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = viewModel.getHbA1cColor(est).copy(alpha = 0.1f))) {
                Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column { Text(stringResource(R.string.glucose_hba1c_est), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant); Text("${est}%", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = viewModel.getHbA1cColor(est)) }
                    Column(horizontalAlignment = Alignment.End) { Text(stringResource(R.string.glucose_avg_short), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant); Text("${uiState.statistics.moyenne.toInt()} mg/dL", fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = { viewModel.setActiveTab(GlucoseTab.HBA1C) }) { Text(stringResource(R.string.glucose_details), fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (uiState.showGraph && uiState.lectures.size >= 2) {
            GlucoseChartCard(uiState.lectures.reversed(), Modifier.weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(uiState.lectures, key = { it.id }) { LectureCard(it, { viewModel.getGlucoseColor(it) }, { viewModel.getGlucoseStatus(it) }) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ── ONGLET HbA1c ────────────────────────────────────────────────────
@Composable
private fun HbA1cContent(uiState: GlucoseUiState, viewModel: GlucoseViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(Surface)) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.glucose_hba1c_long), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                    Text("Reflète la glycémie moyenne des 2-3 derniers mois", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Dernier résultat", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            val latest = uiState.hba1cHistorique.firstOrNull { !it.estEstimation }
                            if (latest != null) { Text("${latest.valeur}%", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = viewModel.getHbA1cColor(latest.valeur)); Text(latest.getInterpretation().getDisplayName(), style = MaterialTheme.typography.bodySmall, color = viewModel.getHbA1cColor(latest.valeur)) }
                            else { Text("--", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariant); Text("Aucune mesure", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant) }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Estimation", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            val est = uiState.hba1cEstimee
                            if (est != null) { Text("${est}%", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = viewModel.getHbA1cColor(est)); Text("(glyc. moy: ${uiState.statistics.moyenne.toInt()})", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant) }
                            else { Text("--", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariant); Text("Données insuffisantes", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HbA1cTargetScale(uiState.latestHbA1c?.valeur ?: uiState.hba1cEstimee)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.showAddHbA1cDialog() }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Résultat labo", fontSize = 13.sp) }
                        if (uiState.hba1cEstimee != null) { OutlinedButton(onClick = { viewModel.sauvegarderEstimation() }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Calculate, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Sauver estim.", fontSize = 13.sp) } }
                    }
                }
            }
        }
        if (uiState.hba1cHistorique.isNotEmpty()) {
            item { Text("Historique HbA1c", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
            items(uiState.hba1cHistorique, key = { it.id }) { HbA1cCard(it) { viewModel.getHbA1cColor(it) } }
        } else {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Science, null, Modifier.size(48.dp), tint = OnSurfaceVariant); Spacer(Modifier.height(12.dp)); Text("Aucun résultat HbA1c", fontWeight = FontWeight.SemiBold); Text("Ajoutez vos résultats de laboratoire", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, textAlign = TextAlign.Center) } } }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── ONGLET SUIVI (DOUBLE COURBE) ────────────────────────────────────
@Composable
private fun SuiviContent(uiState: GlucoseUiState, viewModel: GlucoseViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Carte résumé rapide
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(Surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Progression globale", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                    Text("Glycémie moyenne & HbA1c", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        // Stat glycémie
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Glycémie moy.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            Text(
                                "${uiState.statistics.moyenne.toInt()} mg/dL",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = viewModel.getGlucoseColor(uiState.statistics.moyenne)
                            )
                            Text("${uiState.statistics.totalLectures} lectures", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                        // Stat TIR
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TIR (70-180)", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            Text(
                                "${uiState.statistics.timeInRange.toInt()}%",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    uiState.statistics.timeInRange >= 70 -> GlucoseNormal
                                    uiState.statistics.timeInRange >= 50 -> Warning
                                    else -> GlucoseLow
                                }
                            )
                            Text("cible > 70%", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                        // Stat HbA1c
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("HbA1c", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            val hba1cVal = uiState.latestHbA1c?.valeur ?: uiState.hba1cEstimee
                            if (hba1cVal != null) {
                                Text(
                                    "${hba1cVal}%",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = viewModel.getHbA1cColor(hba1cVal)
                                )
                                Text(
                                    if (uiState.latestHbA1c != null && !uiState.latestHbA1c.estEstimation) "labo" else "estimée",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant
                                )
                            } else {
                                Text("--", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurfaceVariant)
                                Text("pas de données", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Diagramme double courbe
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(Surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Diagramme de suivi", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Text("Glycémie journalière + HbA1c", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    Spacer(Modifier.height(12.dp))

                    if (uiState.dailyAverages.size >= 2) {
                        DualCurveChart(
                            dailyAverages = uiState.dailyAverages,
                            hba1cHistory = uiState.hba1cHistorique,
                            modifier = Modifier.fillMaxWidth().height(280.dp)
                        )
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Timeline, null, Modifier.size(48.dp), tint = OnSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("Minimum 2 jours de données", color = OnSurfaceVariant)
                                Text("pour afficher le diagramme", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Légende
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        ChartLegendItem(Primary, "Glycémie moy. (mg/dL)")
                        ChartLegendItem(Secondary, "HbA1c (%)")
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        ChartLegendItem(GlucoseNormal.copy(alpha = 0.3f), "Zone cible (70-180)")
                        ChartLegendItem(GlucoseLow, "Hypo < 70")
                        ChartLegendItem(GlucoseHigh, "Hyper > 180")
                    }
                }
            }
        }

        // Boutons d'action rapide
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.setActiveTab(GlucoseTab.GLYCEMIE) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Glycémie", fontSize = 13.sp)
                }
                Button(
                    onClick = { viewModel.showAddHbA1cDialog() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Secondary)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("HbA1c", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.generateExportData() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Exporter", fontSize = 13.sp)
                }
            }
        }

        // Analyse ROLLY
        item {
            Card(
                Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(Primary.copy(alpha = 0.08f))
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RollyIcon(size = 40.dp, showBackground = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ROLLY - Prédiction IA", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Analyse vos données pour prédire votre glycémie", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Button(
                        onClick = { viewModel.triggerRollyAnalysis() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Analyser", fontSize = 12.sp)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── DIAGRAMME DOUBLE COURBE ─────────────────────────────────────────
@Composable
private fun DualCurveChart(
    dailyAverages: List<DailyGlucoseAverage>,
    hba1cHistory: List<HbA1cEntity>,
    modifier: Modifier = Modifier
) {
    val glucoseColor = Primary
    val hba1cColor = Secondary
    val targetZoneColor = GlucoseNormal.copy(alpha = 0.15f)
    val gridColor = Outline.copy(alpha = 0.3f)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val leftPad = 44f
        val rightPad = 44f
        val topPad = 20f
        val bottomPad = 36f
        val chartW = w - leftPad - rightPad
        val chartH = h - topPad - bottomPad

        // Plages Y
        val gMin = 50f
        val gMax = 350f
        val hMin = 4f
        val hMax = 14f

        // Zone cible (70-180 mg/dL)
        val targetTop = topPad + chartH * (1 - (180f - gMin) / (gMax - gMin))
        val targetBottom = topPad + chartH * (1 - (70f - gMin) / (gMax - gMin))
        drawRect(
            color = targetZoneColor,
            topLeft = Offset(leftPad, targetTop),
            size = Size(chartW, targetBottom - targetTop)
        )

        // Lignes de grille horizontales glycémie
        listOf(70f, 100f, 140f, 180f, 250f).forEach { v ->
            val y = topPad + chartH * (1 - (v - gMin) / (gMax - gMin))
            drawLine(gridColor, Offset(leftPad, y), Offset(leftPad + chartW, y), strokeWidth = 1f)
        }

        // Axes
        drawLine(Outline, Offset(leftPad, topPad), Offset(leftPad, topPad + chartH), 2f)
        drawLine(Outline, Offset(w - rightPad, topPad), Offset(w - rightPad, topPad + chartH), 2f)
        drawLine(Outline, Offset(leftPad, topPad + chartH), Offset(w - rightPad, topPad + chartH), 2f)

        // Axe X : dates
        val allDates = dailyAverages.map { it.date }
        val minDate = allDates.min()
        val maxDate = allDates.max()
        val dateRange = ChronoUnit.DAYS.between(minDate, maxDate).toFloat().coerceAtLeast(1f)

        // Étiquettes axes avec drawContext.canvas.nativeCanvas
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#6E6B7B")
            textSize = 22f
            isAntiAlias = true
        }

        // Labels axe gauche (glycémie)
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        listOf(70, 140, 250).forEach { v ->
            val y = topPad + chartH * (1 - (v.toFloat() - gMin) / (gMax - gMin))
            drawContext.canvas.nativeCanvas.drawText("$v", leftPad - 6f, y + 7f, textPaint)
        }

        // Labels axe droit (HbA1c)
        textPaint.textAlign = android.graphics.Paint.Align.LEFT
        textPaint.color = android.graphics.Color.parseColor("#FF6B8A")
        listOf(5, 7, 9, 12).forEach { v ->
            val y = topPad + chartH * (1 - (v.toFloat() - hMin) / (hMax - hMin))
            drawContext.canvas.nativeCanvas.drawText("$v%", w - rightPad + 6f, y + 7f, textPaint)
        }

        // Labels axe X (dates)
        textPaint.textAlign = android.graphics.Paint.Align.CENTER
        textPaint.color = android.graphics.Color.parseColor("#6E6B7B")
        textPaint.textSize = 20f
        val dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM")
        val step = (dailyAverages.size / 5).coerceAtLeast(1)
        dailyAverages.forEachIndexed { i, da ->
            if (i % step == 0 || i == dailyAverages.size - 1) {
                val xRatio = ChronoUnit.DAYS.between(minDate, da.date).toFloat() / dateRange
                val x = leftPad + xRatio * chartW
                drawContext.canvas.nativeCanvas.drawText(
                    da.date.format(dateFmt),
                    x,
                    topPad + chartH + 28f,
                    textPaint
                )
            }
        }

        // ── Courbe glycémie (bleu) ──
        if (dailyAverages.size >= 2) {
            val glucosePoints = dailyAverages.map { da ->
                val xRatio = ChronoUnit.DAYS.between(minDate, da.date).toFloat() / dateRange
                val yRatio = (da.average.toFloat() - gMin) / (gMax - gMin)
                Offset(
                    leftPad + xRatio * chartW,
                    topPad + chartH * (1 - yRatio)
                )
            }

            val gPath = Path()
            glucosePoints.first().let { gPath.moveTo(it.x, it.y) }
            glucosePoints.drop(1).forEach { gPath.lineTo(it.x, it.y) }
            drawPath(gPath, glucoseColor, style = Stroke(4f, cap = StrokeCap.Round))

            glucosePoints.forEachIndexed { i, pt ->
                val value = dailyAverages[i].average
                val pointColor = when {
                    value < 70 -> GlucoseLow
                    value > 180 -> GlucoseHigh
                    else -> GlucoseNormal
                }
                drawCircle(pointColor, 6f, pt)
                drawCircle(Color.White, 3f, pt)
            }
        }

        // ── Courbe HbA1c (rouge) ──
        val relevantHba1c = hba1cHistory
            .filter { it.dateMesure >= minDate.minusDays(7) && it.dateMesure <= maxDate.plusDays(7) }
            .sortedBy { it.dateMesure }

        if (relevantHba1c.isNotEmpty()) {
            val hba1cPoints = relevantHba1c.map { ha ->
                val days = ChronoUnit.DAYS.between(minDate, ha.dateMesure).toFloat()
                val xRatio = (days / dateRange).coerceIn(0f, 1f)
                val yRatio = (ha.valeur.toFloat() - hMin) / (hMax - hMin)
                Offset(
                    leftPad + xRatio * chartW,
                    topPad + chartH * (1 - yRatio)
                )
            }

            if (hba1cPoints.size >= 2) {
                val hPath = Path()
                hba1cPoints.first().let { hPath.moveTo(it.x, it.y) }
                hba1cPoints.drop(1).forEach { hPath.lineTo(it.x, it.y) }
                drawPath(hPath, hba1cColor, style = Stroke(3f, cap = StrokeCap.Round))
            }

            hba1cPoints.forEach { pt ->
                drawCircle(hba1cColor, 7f, pt)
                drawCircle(Color.White, 4f, pt)
            }
        }
    }
}

// ── LÉGENDE DU DIAGRAMME ────────────────────────────────────────────
@Composable
private fun ChartLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = color,
            modifier = Modifier.size(14.dp, 4.dp)
        ) {}
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = OnSurfaceVariant)
    }
}

// ── COMPOSANTS EXISTANTS ────────────────────────────────────────────

@Composable
private fun HbA1cTargetScale(currentValue: Double?) {
    Column {
        Text("Échelle HbA1c", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(2.dp)) {
            listOf(Triple("< 5.7%", HbA1cNormal, "Normal"), Triple("5.7-6.4%", HbA1cPreDiabete, "Prédiabète"), Triple("6.5-7.0%", HbA1cCible, "Cible"), Triple("7.0-8.0%", HbA1cAuDessus, "Au-dessus"), Triple("> 8.0%", HbA1cRisque, "Risque")).forEach { (range, color, label) ->
                val isActive = currentValue?.let { v -> when (range) { "< 5.7%" -> v < 5.7; "5.7-6.4%" -> v in 5.7..6.4; "6.5-7.0%" -> v in 6.5..7.0; "7.0-8.0%" -> v in 7.0..8.0; else -> v > 8.0 } } ?: false
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(modifier = Modifier.fillMaxWidth().height(8.dp), shape = RoundedCornerShape(4.dp), color = color.copy(alpha = if (isActive) 1f else 0.3f)) {}
                    Text(label, fontSize = 9.sp, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun HbA1cCard(hba1c: HbA1cEntity, getColor: (Double) -> Color) {
    val color = getColor(hba1c.valeur)
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${hba1c.valeur}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
                    Spacer(Modifier.width(8.dp))
                    if (hba1c.estEstimation) { Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFFF3E0)) { Text("estimée", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color(0xFFE65100)) } }
                }
                Text(hba1c.getInterpretation().getDisplayName(), style = MaterialTheme.typography.bodySmall, color = color)
                Text("≈ ${hba1c.getGlycemieMoyenneEstimee().toInt()} mg/dL moy.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(hba1c.dateMesure.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), style = MaterialTheme.typography.bodyMedium)
                if (hba1c.laboratoire.isNotBlank()) Text(hba1c.laboratoire, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
        }
    }
}

@Composable private fun AddReadingCard(valeur: String, onValeurChange: (String) -> Unit, contexte: ContexteGlucose, onContexteClick: () -> Unit, onAddClick: () -> Unit, isValid: Boolean) { Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(16.dp)) { Text("Nouvelle lecture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) { OutlinedTextField(valeur, onValeurChange, label = { Text("mg/dL *") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); OutlinedTextField(contexte.getDisplayName(), { }, label = { Text("Contexte") }, modifier = Modifier.weight(1.5f), readOnly = true, trailingIcon = { IconButton(onClick = onContexteClick) { Icon(Icons.Default.ArrowDropDown, null) } }) }; Spacer(Modifier.height(12.dp)); Button(onClick = onAddClick, Modifier.fillMaxWidth(), enabled = isValid) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Ajouter") } } } }

@Composable private fun GlucoseChartCard(lectures: List<LectureGlucoseEntity>, modifier: Modifier = Modifier) { Card(modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(16.dp)) { Text("Évolution (15 derniers jours)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(16.dp)); GlucoseChart(lectures, Modifier.fillMaxWidth().height(250.dp)); Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) { LegendItem(GlucoseLow, "< 70"); LegendItem(GlucoseNormal, "70-180"); LegendItem(GlucoseHigh, "> 180") } } } }

@Composable private fun GlucoseChart(lectures: List<LectureGlucoseEntity>, modifier: Modifier = Modifier) { Canvas(modifier) { val w = size.width; val h = size.height; val p = 32f; val cw = w - 2 * p; val ch = h - 2 * p; drawRect(GlucoseNormal.copy(0.15f), Offset(p, p + ch * (1 - (180f - 50f) / 200f)), Size(cw, ch * 110f / 200f)); drawLine(Outline, Offset(p, p), Offset(p, h - p), 2f); drawLine(Outline, Offset(p, h - p), Offset(w - p, h - p), 2f); if (lectures.size >= 2) { val sx = cw / (lectures.size - 1).coerceAtLeast(1); val pts = lectures.mapIndexed { i, l -> Offset(p + i * sx, p + ch * (1 - (l.valeur.toFloat() - 50f) / 200f)) }; val path = Path(); pts.first().let { path.moveTo(it.x, it.y) }; pts.drop(1).forEach { path.lineTo(it.x, it.y) }; drawPath(path, Primary, style = Stroke(3f, cap = StrokeCap.Round)); pts.forEachIndexed { i, pt -> drawCircle(when { lectures[i].valeur < 70 -> GlucoseLow; lectures[i].valeur > 180 -> GlucoseHigh; else -> GlucoseNormal }, 6f, pt) } } } }

@Composable private fun LegendItem(color: Color, label: String) { Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = MaterialTheme.shapes.small, color = color, modifier = Modifier.size(12.dp)) {}; Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.bodySmall) } }

@Composable private fun LectureCard(lecture: LectureGlucoseEntity, getColor: (Double) -> Color, getStatus: (Double) -> String) { val c = getColor(lecture.valeur); Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) { Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Column { Row(verticalAlignment = Alignment.Bottom) { Text("${lecture.valeur.toInt()}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c); Spacer(Modifier.width(4.dp)); Text("mg/dL", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant) }; Text(getStatus(lecture.valeur), style = MaterialTheme.typography.bodySmall, color = c) }; Column(horizontalAlignment = Alignment.End) { Text(lecture.contexte.getDisplayName(), style = MaterialTheme.typography.bodyMedium); Text(lecture.dateHeure.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant) } } } }
