package com.diabeto.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diabeto.BuildConfig
import com.diabeto.R
import com.diabeto.ui.theme.*
import com.diabeto.data.repository.PreferencesRepository
import com.diabeto.util.AppUpdateChecker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Écran de démarrage animé DiaSmart
 *
 * Animation séquentielle (6 phases) :
 *   0.0s → État initial (fond sombre #001A3C, tout invisible)
 *   0.0–0.5s → Apparition du cercle/logo (scale 0.8→1.0 + fade-in)
 *   0.5–1.0s → Remplissage de la larme (scale-in + rebond)
 *   1.0–1.5s → Apparition du nom « DiaSmart »
 *   1.5–2.0s → Apparition du slogan « Diabétologie Intelligente »
 *   2.0–2.5s → Ligne ECG animée + état final statique
 *   2.5s → Fin → Navigation (Dashboard si connecté, Login sinon)
 *
 *  ✅ Si l'utilisateur est déjà connecté → skip Login → Dashboard
 *  ✅ Vérification de mise à jour en arrière-plan
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
private interface SplashEntryPoint {
    fun preferencesRepository(): PreferencesRepository
}

@Composable
fun SplashScreen(
    onSplashFinished: (isLoggedIn: Boolean) -> Unit,
    isUserLoggedIn: Boolean
) {
    // ─── Contrôle de la séquence ─────────────────────────────────────
    var startAnimation by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        startAnimation = true

        // Vérification de mise à jour NON-BLOQUANTE en arrière-plan
        // On ne bloque JAMAIS la navigation — les infos sont stockées
        // dans DataStore et le dialogue s'affichera sur le Dashboard
        launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext, SplashEntryPoint::class.java
                )
                val prefsRepo = entryPoint.preferencesRepository()
                val checker = AppUpdateChecker(context)
                val update = checker.checkForUpdate()
                if (update != null && update.apkUrl.isNotBlank()) {
                    prefsRepo.setPendingUpdate(
                        version = update.versionName,
                        url = update.apkUrl,
                        changelog = update.changelog,
                        force = update.forceUpdate
                    )
                }
            } catch (_: Exception) {
                // Silencieux — la MAJ est non-critique
            }
        }

        // Animation de 3 secondes, puis navigation IMMÉDIATE
        delay(3000)
        onSplashFinished(isUserLoggedIn)
    }

    // ─── Animations séquentielles ────────────────────────────────────

    // Phase 1 : Logo — scale 0.6→1.0 + alpha 0→1  (0–0.8s)
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.6f,
        animationSpec = tween(800, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(700, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )

    // Phase 2 : Halo lumineux — scale-in rebond (0.5–1.2s)
    val glowScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "glowScale"
    )

    // Phase 3 : Titre « DiaSmart » — slide-up + fade-in (1.0–1.5s)
    val titleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(600, delayMillis = 1000, easing = FastOutSlowInEasing),
        label = "titleAlpha"
    )
    val titleOffset by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 30f,
        animationSpec = tween(600, delayMillis = 1000, easing = FastOutSlowInEasing),
        label = "titleOffset"
    )

    // Phase 4 : Slogan — fade-in (1.5–2.0s)
    val sloganAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(600, delayMillis = 1600, easing = FastOutSlowInEasing),
        label = "sloganAlpha"
    )
    val sloganOffset by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 20f,
        animationSpec = tween(600, delayMillis = 1600, easing = FastOutSlowInEasing),
        label = "sloganOffset"
    )

    // Phase 5 : ECG + barre de chargement (2.0s+)
    val ecgAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(500, delayMillis = 1800, easing = LinearEasing),
        label = "ecgAlpha"
    )
    val loadingAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(400, delayMillis = 2000, easing = LinearEasing),
        label = "loadingAlpha"
    )

    // Pulsation douce du logo (boucle infinie)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // ECG trace animation
    val ecgProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ecgProgress"
    )

    // Particules flottantes
    val particle1Y by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -15f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p1"
    )
    val particle2Y by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p2"
    )

    // ─── UI ──────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0B2E),
                        Color(0xFF1A1452),
                        Color(0xFF120F3D),
                        Color(0xFF0D0B2E)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // ── Cercles décoratifs flous (ambiance médicale) ──
        Box(
            modifier = Modifier
                .size(350.dp)
                .offset(x = (-120).dp, y = (-200).dp)
                .blur(80.dp)
                .alpha(0.25f * logoAlpha)
                .background(GradientStart, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = 130.dp, y = 250.dp)
                .blur(70.dp)
                .alpha(0.2f * logoAlpha)
                .background(Primary, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = 100.dp, y = (-150).dp)
                .blur(60.dp)
                .alpha(0.15f * logoAlpha)
                .background(Secondary, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(160.dp)
                .offset(x = (-80).dp, y = 180.dp)
                .blur(50.dp)
                .alpha(0.18f * logoAlpha)
                .background(GradientEnd, CircleShape)
        )

        // ── Particules médicales flottantes ──
        Box(
            modifier = Modifier
                .size(8.dp)
                .offset(x = (-60).dp, y = (particle1Y - 100).dp)
                .alpha(0.4f * ecgAlpha)
                .background(GradientStart, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .offset(x = 80.dp, y = (particle2Y + 120).dp)
                .alpha(0.3f * ecgAlpha)
                .background(Primary, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .offset(x = 50.dp, y = (particle1Y - 50).dp)
                .alpha(0.25f * ecgAlpha)
                .background(Secondary, CircleShape)
        )

        // ── Ligne ECG en fond (full width) ──
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .offset(y = 150.dp)
                .alpha(ecgAlpha * 0.2f)
        ) {
            val w = size.width
            val h = size.height
            val cy = h / 2f

            val ecgPath = Path().apply {
                moveTo(0f, cy)
                // Repeat ECG pattern across screen
                var x = 0f
                val segmentWidth = w / 3f
                repeat(3) {
                    lineTo(x + segmentWidth * 0.3f, cy)
                    lineTo(x + segmentWidth * 0.35f, cy - h * 0.15f)
                    lineTo(x + segmentWidth * 0.4f, cy)
                    lineTo(x + segmentWidth * 0.45f, cy + h * 0.12f)
                    lineTo(x + segmentWidth * 0.5f, cy - h * 0.4f)
                    lineTo(x + segmentWidth * 0.55f, cy + h * 0.3f)
                    lineTo(x + segmentWidth * 0.6f, cy)
                    lineTo(x + segmentWidth * 0.65f, cy - h * 0.08f)
                    lineTo(x + segmentWidth * 0.7f, cy)
                    lineTo(x + segmentWidth, cy)
                    x += segmentWidth
                }
            }

            // Glow
            drawPath(
                path = ecgPath,
                color = Primary,
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // ── Croix médicale subtile en fond ──
        Canvas(
            modifier = Modifier
                .size(300.dp)
                .alpha(logoAlpha * 0.04f)
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val crossW = w * 0.15f
            val crossH = h * 0.5f

            drawRoundRect(
                color = Primary,
                topLeft = Offset(cx - crossW / 2f, cy - crossH / 2f),
                size = androidx.compose.ui.geometry.Size(crossW, crossH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(crossW * 0.3f)
            )
            drawRoundRect(
                color = Primary,
                topLeft = Offset(cx - crossH / 2f, cy - crossW / 2f),
                size = androidx.compose.ui.geometry.Size(crossH, crossW),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(crossW * 0.3f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // ── Halo lumineux + Logo ──
            Box(contentAlignment = Alignment.Center) {
                // Anneau extérieur (orbite)
                Canvas(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(glowScale)
                        .alpha(logoAlpha * 0.3f)
                ) {
                    drawCircle(
                        color = Primary,
                        radius = size.width / 2f - 4f,
                        style = Stroke(width = 1.5f)
                    )
                    // Points orbitaux
                    listOf(0f, 90f, 180f, 270f).forEach { angle ->
                        val rad = Math.toRadians(angle.toDouble() + ecgProgress * 360)
                        val r = size.width / 2f - 4f
                        val px = size.width / 2f + r * kotlin.math.cos(rad).toFloat()
                        val py = size.height / 2f + r * kotlin.math.sin(rad).toFloat()
                        drawCircle(Primary, 3f, Offset(px, py))
                    }
                }

                // Glow radial
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(glowScale * pulseScale)
                        .alpha(logoAlpha * 0.3f)
                        .blur(30.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Primary.copy(alpha = 0.6f),
                                    GradientEnd.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )

                // ── Logo principal ──
                Image(
                    painter = painterResource(id = R.drawable.ic_diasmart_logo),
                    contentDescription = "DiaSmart",
                    modifier = Modifier
                        .size(130.dp)
                        .scale(logoScale * pulseScale)
                        .alpha(logoAlpha)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Titre « DiaSmart » ──
            Text(
                text = "DiaSmart",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(titleAlpha)
                    .offset(y = titleOffset.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Slogan ──
            Text(
                text = "Diabétologie Intelligente",
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                color = PrimaryContainer,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .alpha(sloganAlpha)
                    .offset(y = sloganOffset.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Mini ligne ECG sous le slogan ──
            Canvas(
                modifier = Modifier
                    .width(200.dp)
                    .height(30.dp)
                    .alpha(ecgAlpha * 0.6f)
            ) {
                val w = size.width
                val h = size.height
                val cy = h / 2f

                val visibleEnd = w * ecgProgress

                val miniEcg = Path().apply {
                    moveTo(0f, cy)
                    lineTo(w * 0.2f, cy)
                    lineTo(w * 0.25f, cy - h * 0.2f)
                    lineTo(w * 0.3f, cy)
                    lineTo(w * 0.35f, cy + h * 0.15f)
                    lineTo(w * 0.45f, cy - h * 0.45f)
                    lineTo(w * 0.55f, cy + h * 0.35f)
                    lineTo(w * 0.6f, cy)
                    lineTo(w * 0.65f, cy - h * 0.1f)
                    lineTo(w * 0.7f, cy)
                    lineTo(w, cy)
                }

                // Glow
                drawPath(
                    miniEcg,
                    Primary.copy(alpha = 0.3f),
                    style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Main line
                drawPath(
                    miniEcg,
                    Primary,
                    style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Point lumineux qui trace
                val dotX = visibleEnd.coerceIn(0f, w)
                val dotY = cy // simplifié
                drawCircle(Primary, 4f, Offset(dotX, dotY))
                drawCircle(Primary.copy(alpha = 0.3f), 8f, Offset(dotX, dotY))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Barre de chargement ──
            Box(
                modifier = Modifier.alpha(loadingAlpha),
                contentAlignment = Alignment.Center
            ) {
                val barProgress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "barProgress"
                )

                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(barProgress)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Primary,
                                        GradientEnd,
                                        Primary
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Footer ──
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.3f * sloganAlpha),
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}
