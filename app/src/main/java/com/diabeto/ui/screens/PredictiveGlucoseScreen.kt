package com.diabeto.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.diabeto.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.ui.components.AiRichText
import com.diabeto.ui.components.RollyIcon
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.PredictiveGlucoseViewModel
import com.diabeto.ui.viewmodel.PredictivePoint
import com.diabeto.ui.viewmodel.PredictiveUiState
import com.diabeto.ui.viewmodel.RiskLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictiveGlucoseScreen(
    onNavigateBack: () -> Unit,
    viewModel: PredictiveGlucoseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.predict_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.predict_subtitle), fontSize = 12.sp, color = OnSurfaceVariant)
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.predict_analyzing))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                // Carte résumé actuel
                item { CurrentStatusCard(uiState) }

                // v2.1.79 : l'excursion attendue, chiffrée et datée.
                item { ExcursionCard(uiState) }

                // Graphique prédictif
                item { PredictiveChartCard(uiState) }

                // Alertes
                if (uiState.alerts.isNotEmpty()) {
                    item { AlertsCard(uiState.alerts) }
                }

                // Prédictions détaillées
                item { PredictionDetailsCard(uiState) }

                // Bouton analyse ROLLY
                item { RollyAnalysisCard(uiState, viewModel) }

                // Dialog ROLLY
                if (uiState.rollyAnalysis != null) {
                    item { /* Dialog handled separately */ }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // Dialog analyse ROLLY
    if (uiState.rollyAnalysis != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAnalysis,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RollyIcon(size = 28.dp, showBackground = true)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.predict_rolly), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        AiRichText(
                            texte = uiState.rollyAnalysis ?: "",
                            couleurAccent = Primary,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::dismissAnalysis) { Text(stringResource(R.string.common_close)) }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

/**
 * Excursion post-prandiale attendue.
 *
 * C'est la carte qui repond a « de combien et quand ». Elle n'apparait que
 * lorsqu'un repas des six dernieres heures agit encore ; hors repas, il n'y a
 * rien a annoncer et la carte se replie sur le conseil du moment.
 *
 * Trois choix d'affichage assumes :
 *  - une FOURCHETTE plutot qu'un chiffre sec, parce qu'une prediction
 *    glycemique n'est jamais exacte ;
 *  - l'heure du pic en clair, plus utile qu'un « dans 82 minutes » ;
 *  - la base de calcul ecrite noir sur blanc, pour qu'une estimation generique
 *    ne se fasse jamais passer pour une valeur personnalisee.
 */
@Composable
private fun ExcursionCard(uiState: PredictiveUiState) {
    val pic = uiState.picPrevu
    val conseil = uiState.conseil
    if (pic == null && conseil == null) return

    val accent = when {
        pic == null -> Primary
        pic > 250 -> Error
        pic > 180 -> Warning
        else -> Success
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(accent)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (pic != null) "Excursion attendue" else "Conseil du moment",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }

            if (pic != null) {
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$pic",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Text(
                        " mg/dL",
                        fontSize = 15.sp,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    uiState.heurePic?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("vers", fontSize = 11.sp, color = OnSurfaceVariant)
                            Text(it, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                val bas = uiState.picBas
                val haut = uiState.picHaut
                val montee = uiState.monteePrevue
                if (bas != null && haut != null && montee != null) {
                    Text(
                        "Fourchette $bas–$haut · montée d'environ $montee mg/dL",
                        fontSize = 12.5.sp,
                        color = OnSurfaceVariant
                    )
                }

                uiState.repasDeclencheur?.let {
                    Spacer(Modifier.height(2.dp))
                    Text("D'après : $it", fontSize = 12.5.sp, color = OnSurfaceVariant)
                }
            }

            conseil?.let { c ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accent.copy(alpha = 0.09f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.titre, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            c.message,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            if (uiState.baseCalibration.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    uiState.baseCalibration,
                    fontSize = 11.5.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun CurrentStatusCard(uiState: PredictiveUiState) {
    val riskColor = Color(uiState.riskLevel.color)

    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.predict_current), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${uiState.currentValue?.toInt() ?: "---"}",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                (uiState.currentValue ?: 100.0) < 70 -> GlucoseLow
                                (uiState.currentValue ?: 100.0) > 180 -> GlucoseHigh
                                else -> GlucoseNormal
                            }
                        )
                        Text(" mg/dL", fontSize = 14.sp, color = OnSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text(uiState.trendDescription, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }

                // Badge risque
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = riskColor.copy(alpha = 0.12f)
                ) {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            when (uiState.riskLevel) {
                                RiskLevel.LOW -> Icons.Outlined.CheckCircle
                                RiskLevel.NORMAL -> Icons.Outlined.Info
                                RiskLevel.MODERATE -> Icons.Outlined.Warning
                                RiskLevel.HIGH -> Icons.Default.Warning
                                RiskLevel.CRITICAL -> Icons.Default.Error
                            },
                            null,
                            tint = riskColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Risque ${uiState.riskLevel.label}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = riskColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictiveChartCard(uiState: PredictiveUiState) {
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.predict_6h_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(stringResource(R.string.predict_6h_basis), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            if (uiState.historicalPoints.isNotEmpty() || uiState.predictedPoints.isNotEmpty()) {
                PredictiveChart(
                    historicalPoints = uiState.historicalPoints,
                    predictedPoints = uiState.predictedPoints,
                    modifier = Modifier.fillMaxWidth().height(280.dp)
                )

                Spacer(Modifier.height(12.dp))

                // Légende
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    ChartLegendDot(Primary, "Historique")
                    ChartLegendDot(StatusOrange, "Prédiction")
                    ChartLegendDot(GlucoseNormal.copy(alpha = 0.3f), "Zone cible")
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Timeline, null, Modifier.size(48.dp), tint = OnSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.glucose_insufficient_data), color = OnSurfaceVariant)
                        Text(stringResource(R.string.predict_add_measures), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictiveChart(
    historicalPoints: List<PredictivePoint>,
    predictedPoints: List<PredictivePoint>,
    modifier: Modifier = Modifier
) {
    val allPoints = historicalPoints + predictedPoints
    if (allPoints.isEmpty()) return

    val historicalColor = Primary
    val predictionColor = StatusOrange
    val targetZoneColor = GlucoseNormal.copy(alpha = 0.12f)
    val gridColor = Outline.copy(alpha = 0.2f)
    val confidenceColor = predictionColor.copy(alpha = 0.15f)

    // Animation de la ligne prédictive
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(predictedPoints) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(1500, easing = EaseOutCubic))
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val leftPad = 48f
        val rightPad = 16f
        val topPad = 16f
        val bottomPad = 36f
        val chartW = w - leftPad - rightPad
        val chartH = h - topPad - bottomPad

        val xMin = allPoints.minOf { it.hoursFromNow }
        val xMax = allPoints.maxOf { it.hoursFromNow }
        val xRange = (xMax - xMin).coerceAtLeast(1f)

        val yMin = 40f
        val yMax = 350f
        val yRange = yMax - yMin

        fun toX(hours: Float) = leftPad + ((hours - xMin) / xRange) * chartW
        fun toY(value: Double) = topPad + chartH * (1 - (value.toFloat() - yMin) / yRange)

        // Zone cible (70-180)
        val targetTop = toY(180.0)
        val targetBottom = toY(70.0)
        drawRect(
            targetZoneColor,
            Offset(leftPad, targetTop),
            Size(chartW, targetBottom - targetTop)
        )

        // Grille
        listOf(70f, 100f, 140f, 180f, 250f).forEach { v ->
            val y = toY(v.toDouble())
            drawLine(gridColor, Offset(leftPad, y), Offset(leftPad + chartW, y), 1f)
        }

        // Ligne verticale "maintenant"
        val nowX = toX(0f)
        drawLine(
            Color(0xFF90A4AE),
            Offset(nowX, topPad),
            Offset(nowX, topPad + chartH),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        )

        // Labels axe Y
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#607D8B")
            textSize = 22f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        listOf(70, 140, 180, 250).forEach { v ->
            drawContext.canvas.nativeCanvas.drawText(
                "$v", leftPad - 8f, toY(v.toDouble()) + 7f, textPaint
            )
        }

        // Label "Maintenant"
        textPaint.textAlign = android.graphics.Paint.Align.CENTER
        textPaint.textSize = 20f
        textPaint.color = android.graphics.Color.parseColor("#455A64")
        drawContext.canvas.nativeCanvas.drawText(
            "Maintenant", nowX, topPad + chartH + 28f, textPaint
        )

        // Labels heures
        textPaint.color = android.graphics.Color.parseColor("#607D8B")
        listOf(-6f, -3f, 3f, 6f).forEach { hr ->
            if (hr >= xMin && hr <= xMax) {
                val label = if (hr < 0) "${hr.toInt()}h" else "+${hr.toInt()}h"
                drawContext.canvas.nativeCanvas.drawText(
                    label, toX(hr), topPad + chartH + 28f, textPaint
                )
            }
        }

        // Zone de confiance prédiction
        if (predictedPoints.size >= 2) {
            val confPath = Path()
            // Bord supérieur
            predictedPoints.forEachIndexed { i, pt ->
                val animatedIdx = (animProgress.value * predictedPoints.size).toInt()
                if (i > animatedIdx) return@forEachIndexed
                val spread = (1 - pt.confidence) * 40
                val x = toX(pt.hoursFromNow)
                val y = toY(pt.value + spread)
                if (i == 0) confPath.moveTo(x, y) else confPath.lineTo(x, y)
            }
            // Bord inférieur (inversé)
            val animatedMax = (animProgress.value * predictedPoints.size).toInt()
                .coerceAtMost(predictedPoints.size - 1)
            for (i in animatedMax downTo 0) {
                val pt = predictedPoints[i]
                val spread = (1 - pt.confidence) * 40
                confPath.lineTo(toX(pt.hoursFromNow), toY(pt.value - spread))
            }
            confPath.close()
            drawPath(confPath, confidenceColor)
        }

        // Courbe historique
        if (historicalPoints.size >= 2) {
            val hPath = Path()
            historicalPoints.first().let { hPath.moveTo(toX(it.hoursFromNow), toY(it.value)) }
            historicalPoints.drop(1).forEach { hPath.lineTo(toX(it.hoursFromNow), toY(it.value)) }
            drawPath(hPath, historicalColor, style = Stroke(4f, cap = StrokeCap.Round))

            // Points
            historicalPoints.forEach { pt ->
                val color = when {
                    pt.value < 70 -> GlucoseLow
                    pt.value > 180 -> GlucoseHigh
                    else -> GlucoseNormal
                }
                drawCircle(color, 6f, Offset(toX(pt.hoursFromNow), toY(pt.value)))
                drawCircle(Color.White, 3f, Offset(toX(pt.hoursFromNow), toY(pt.value)))
            }
        }

        // Courbe prédictive (animée)
        if (predictedPoints.size >= 2) {
            val pPath = Path()
            val animatedCount = (animProgress.value * predictedPoints.size).toInt()
                .coerceAtMost(predictedPoints.size - 1)
                .coerceAtLeast(1)

            // Connecter depuis le dernier point historique
            val startPoint = if (historicalPoints.isNotEmpty()) {
                historicalPoints.last()
            } else {
                predictedPoints.first()
            }
            pPath.moveTo(toX(startPoint.hoursFromNow), toY(startPoint.value))

            for (i in 0..animatedCount) {
                val pt = predictedPoints[i]
                pPath.lineTo(toX(pt.hoursFromNow), toY(pt.value))
            }

            drawPath(pPath, predictionColor, style = Stroke(
                3f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
            ))

            // Points prédits
            for (i in 0..animatedCount) {
                val pt = predictedPoints[i]
                val alpha = pt.confidence
                drawCircle(
                    predictionColor.copy(alpha = alpha),
                    5f,
                    Offset(toX(pt.hoursFromNow), toY(pt.value))
                )
            }
        }
    }
}

@Composable
private fun AlertsCard(alerts: List<String>) {
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(Color(0xFFFFF3E0))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Alertes", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
            }
            Spacer(Modifier.height(8.dp))
            alerts.forEach { alert ->
                Text(
                    "- $alert",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFBF360C),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PredictionDetailsCard(uiState: PredictiveUiState) {
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Détails de la prédiction", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                PredictionStatItem(
                    label = "Min prédit",
                    value = "${uiState.predictedMin?.toInt() ?: "---"}",
                    unit = "mg/dL",
                    color = if ((uiState.predictedMin ?: 100.0) < 70) GlucoseLow else GlucoseNormal
                )
                PredictionStatItem(
                    label = "Max prédit",
                    value = "${uiState.predictedMax?.toInt() ?: "---"}",
                    unit = "mg/dL",
                    color = if ((uiState.predictedMax ?: 100.0) > 180) GlucoseHigh else GlucoseNormal
                )
                PredictionStatItem(
                    label = "Tendance",
                    value = uiState.trendDescription.take(12),
                    unit = "",
                    color = Primary
                )
            }

            Spacer(Modifier.height(12.dp))

            // Tableau des prédictions par heure
            if (uiState.predictedPoints.isNotEmpty()) {
                Text("Prévision horaire", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    listOf(1f, 2f, 3f, 4f, 5f, 6f).forEach { hour ->
                        val point = uiState.predictedPoints.minByOrNull {
                            kotlin.math.abs(it.hoursFromNow - hour)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "+${hour.toInt()}h",
                                fontSize = 10.sp,
                                color = OnSurfaceVariant
                            )
                            Text(
                                "${point?.value?.toInt() ?: "---"}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    (point?.value ?: 100.0) < 70 -> GlucoseLow
                                    (point?.value ?: 100.0) > 180 -> GlucoseHigh
                                    else -> GlucoseNormal
                                }
                            )
                            // Barre de confiance
                            val conf = point?.confidence ?: 0f
                            Surface(
                                Modifier.width(24.dp).height(3.dp),
                                shape = RoundedCornerShape(2.dp),
                                color = StatusOrange.copy(alpha = conf)
                            ) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictionStatItem(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        if (unit.isNotEmpty()) {
            Text(unit, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun RollyAnalysisCard(uiState: PredictiveUiState, viewModel: PredictiveGlucoseViewModel) {
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(Primary.copy(alpha = 0.08f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RollyIcon(size = 40.dp, showBackground = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("ROLLY - Analyse prédictive IA", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "Analyse détaillée des risques des prochaines heures",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
            Button(
                onClick = { viewModel.requestRollyAnalysis() },
                shape = RoundedCornerShape(12.dp),
                enabled = !uiState.isAnalyzing
            ) {
                if (uiState.isAnalyzing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Prédire", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color,
            modifier = Modifier.size(12.dp, 4.dp)
        ) {}
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = OnSurfaceVariant)
    }
}
