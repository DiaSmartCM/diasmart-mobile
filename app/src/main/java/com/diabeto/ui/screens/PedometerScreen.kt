package com.diabeto.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.R
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.PedometerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedometerScreen(
    onNavigateBack: () -> Unit,
    viewModel: PedometerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    // Runtime permission for ACTIVITY_RECOGNITION (Android 10+)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
        if (granted) {
            viewModel.startTracking()
        }
    }

    // Auto-check on Android < 10 (no runtime permission needed)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissionGranted = true
        }
    }

    fun requestPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            permissionGranted = true
            viewModel.startTracking()
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
                            Text(stringResource(R.string.pedometer_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(stringResource(R.string.pedometer_subtitle), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ── Permission denied warning ────────────────────
            if (permissionDenied) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = Warning)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.pedometer_permission_denied),
                                    fontWeight = FontWeight.Bold,
                                    color = Warning
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "La permission d'activité physique est nécessaire pour compter vos pas. " +
                                "Veuillez l'autoriser dans les paramètres de l'application.",
                                fontSize = 13.sp,
                                color = OnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { requestPermissionAndStart() }) {
                                Text(stringResource(R.string.pedometer_retry), color = Primary)
                            }
                        }
                    }
                }
            }

            // ── Sensor not available ────────────────────────
            if (!uiState.sensorAvailable) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SensorOccupied, null, tint = Error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Le capteur de pas n'est pas disponible sur cet appareil.", color = Error)
                        }
                    }
                }
            }

            // ── Step counter circle ─────────────────────────
            item {
                StepCounterCircle(
                    steps = uiState.steps,
                    goal = uiState.dailyGoal,
                    isTracking = uiState.isTracking
                )
            }

            // ── Start/Stop button ───────────────────────────
            item {
                Button(
                    onClick = {
                        if (uiState.isTracking) {
                            viewModel.stopTracking()
                        } else {
                            if (permissionGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                viewModel.startTracking()
                            } else {
                                requestPermissionAndStart()
                            }
                        }
                    },
                    enabled = uiState.sensorAvailable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isTracking) Error else Primary
                    )
                ) {
                    Icon(
                        if (uiState.isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isTracking) stringResource(R.string.pedometer_stop) else stringResource(R.string.pedometer_start),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (uiState.isTracking) stringResource(R.string.pedometer_stop) else stringResource(R.string.pedometer_start),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Stats cards ─────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatMiniCard(
                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        value = String.format("%.2f km", uiState.distanceKm),
                        label = "Distance",
                        color = Primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatMiniCard(
                        icon = Icons.Default.LocalFireDepartment,
                        value = String.format("%.0f cal", uiState.caloriesBurned),
                        label = "Calories",
                        color = Warning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Glycémie correlation ────────────────────────
            uiState.glycemieImpact?.let { impact ->
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MonitorHeart, null, tint = Primary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.pedometer_glycemic_impact), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.avgGlycemie?.let { avg ->
                                Text(
                                    "Glycémie moyenne : ${avg.toInt()} mg/dL",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = when {
                                        avg < 70 -> GlucoseLow
                                        avg in 70.0..180.0 -> GlucoseNormal
                                        else -> GlucoseHigh
                                    }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(impact, fontSize = 13.sp, color = OnSurfaceVariant)
                        }
                    }
                }
            }

            // ── Historique journalier (v2.1.72) ─────────────
            // Stocke en local : consultable hors ligne, 90 jours glissants.
            if (uiState.history.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CalendarMonth, null,
                                    tint = Primary, modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.pedometer_history_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.pedometer_history_subtitle),
                                fontSize = 12.sp,
                                color = OnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            uiState.history.take(30).forEach { (date, steps) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        formatHistoryDate(date),
                                        fontSize = 13.sp,
                                        color = OnSurfaceVariant
                                    )
                                    Text(
                                        stringResource(R.string.pedometer_history_steps, steps),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (steps >= uiState.dailyGoal) GlucoseNormal else Primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Info card ───────────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Info.copy(alpha = 0.08f))
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Info, null, tint = Info, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "La marche aide à réguler la glycémie. L'exercice stimule la captation du glucose par les muscles (GLUT4) indépendamment de l'insuline.",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

/**
 * v2.1.72 : "2026-08-16" -> "16/08/2026". Purement local, sans dependance
 * reseau ni locale (les cles d'historique sont toujours en AAAA-MM-JJ).
 */
private fun formatHistoryDate(iso: String): String {
    val p = iso.split('-')
    return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso
}

@Composable
private fun StepCounterCircle(
    steps: Int,
    goal: Int,
    isTracking: Boolean
) {
    val progress = (steps.toFloat() / goal).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600),
        label = "progress"
    )

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val topLeft = Offset(
                (size.width - radius * 2) / 2,
                (size.height - radius * 2) / 2
            )

            // Background circle
            drawArc(
                color = Primary.copy(alpha = 0.1f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(GradientStart, Primary, GradientEnd)
                ),
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isTracking) {
                Surface(
                    shape = CircleShape,
                    color = Success.copy(alpha = pulseAlpha),
                    modifier = Modifier.size(10.dp)
                ) {}
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                "$steps",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
            Text(stringResource(R.string.pedometer_steps), fontSize = 14.sp, color = OnSurfaceVariant)
            Text(
                "${(progress * 100).toInt()}% de l'objectif",
                fontSize = 12.sp,
                color = if (progress >= 1f) Success else OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatMiniCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(label, fontSize = 12.sp, color = OnSurfaceVariant)
        }
    }
}
