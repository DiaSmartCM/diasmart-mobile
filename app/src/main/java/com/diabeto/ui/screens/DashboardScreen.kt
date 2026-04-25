package com.diabeto.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import com.diabeto.ui.theme.LocalIsDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.diabeto.R
import com.diabeto.util.AppUpdateChecker
import com.diabeto.data.entity.RendezVousAvecPatient
import com.diabeto.data.model.UserRole
import com.diabeto.ui.components.RollyIcon
import com.diabeto.ui.components.RollyIconInline
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.DashboardViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToPatients: () -> Unit,
    onNavigateToPatientDetail: (Long) -> Unit,
    onNavigateToRendezVous: () -> Unit,
    onNavigateToAddPatient: () -> Unit,
    onNavigateToChatbot: () -> Unit = {},
    onNavigateToMessagerie: () -> Unit = {},
    onNavigateToRepasAnalyse: () -> Unit = {},
    onNavigateToDataSharing: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToJournal: () -> Unit = {},
    onNavigateToPedometer: () -> Unit = {},
    onNavigateToPredictive: () -> Unit = {},
    onNavigateToValidations: () -> Unit = {},
    onNavigateToCommunity: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedNavIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    // ── Mise a jour automatique ──
    val pendingUpdateData by viewModel.pendingUpdate.collectAsStateWithLifecycle()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateVersion by remember { mutableStateOf("") }
    var updateUrl by remember { mutableStateOf("") }
    var updateChangelog by remember { mutableStateOf("") }
    var updateForce by remember { mutableStateOf(false) }
    var needsInstallPermission by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    fun checkInstallPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            !context.packageManager.canRequestPackageInstalls()
        } else false
    }

    LaunchedEffect(pendingUpdateData) {
        pendingUpdateData?.let { update ->
            updateVersion = update.version
            updateUrl = update.url
            updateChangelog = update.changelog
            updateForce = update.force
            needsInstallPermission = checkInstallPermission()
            showUpdateDialog = true
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && showUpdateDialog) {
                val hadPermissionIssue = needsInstallPermission
                needsInstallPermission = checkInstallPermission()
                if (hadPermissionIssue && !needsInstallPermission && updateUrl.isNotBlank()) {
                    val checker = AppUpdateChecker(context)
                    checker.downloadAndInstall(updateUrl, updateVersion)
                    isDownloading = true
                    showUpdateDialog = false
                    viewModel.clearPendingUpdate()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Dialog de mise a jour
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!updateForce) {
                    showUpdateDialog = false
                    viewModel.clearPendingUpdate()
                }
            },
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(PrimaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, null, tint = Primary, modifier = Modifier.size(28.dp))
                }
            },
            title = { Text(stringResource(R.string.dashboard_update_available), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = PrimaryContainer) {
                        Text(
                            "Version $updateVersion",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontWeight = FontWeight.Bold, color = Primary
                        )
                    }
                    if (updateChangelog.isNotBlank()) {
                        Text(stringResource(R.string.dashboard_new_features), fontWeight = FontWeight.Medium)
                        Text(updateChangelog, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    if (needsInstallPermission) {
                        HorizontalDivider(color = OutlineVariant)
                        Text(
                            stringResource(R.string.dashboard_install_permission),
                            style = MaterialTheme.typography.bodySmall, color = Warning, fontWeight = FontWeight.Medium
                        )
                    }
                    if (updateForce) {
                        Text(stringResource(R.string.dashboard_update_mandatory), style = MaterialTheme.typography.bodySmall, color = Error, fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (needsInstallPermission) {
                        Button(
                            onClick = { AppUpdateChecker(context).openInstallPermissionSettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = Warning),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.dashboard_authorize))
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(updateUrl)))
                        }) {
                            Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.dashboard_via_browser), fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                AppUpdateChecker(context).downloadAndInstall(updateUrl, updateVersion)
                                isDownloading = true; showUpdateDialog = false
                                viewModel.clearPendingUpdate()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.dashboard_install))
                        }
                    }
                }
            },
            dismissButton = {
                if (!updateForce) {
                    TextButton(onClick = {
                        showUpdateDialog = false
                        viewModel.clearPendingUpdate()
                    }) { Text(stringResource(R.string.dashboard_later)) }
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    val isDark = LocalIsDarkTheme.current
    val screenBg = if (isDark) DarkBackground else Background
    val cardSurface = if (isDark) DarkSurface else Surface
    val textPri = if (isDark) DarkTextPrimary else TextPrimary
    val textSec = if (isDark) DarkTextSecondary else TextSecondary
    val textTer = if (isDark) DarkTextTertiary else TextTertiary
    val navBg = if (isDark) DarkNavBar else NavBarBackground
    val outlineCol = if (isDark) DarkOutline else OutlineVariant
    val primaryContainerCol = if (isDark) DarkPrimaryContainer else PrimaryContainer

    Scaffold(
        bottomBar = {
            DiaSmartBottomBar(
                selectedIndex = selectedNavIndex,
                onNavigateToPatients = { selectedNavIndex = 1; onNavigateToPatients() },
                onNavigateToDataSharing = { selectedNavIndex = 1; onNavigateToDataSharing() },
                onNavigateToRendezVous = { selectedNavIndex = 2; onNavigateToRendezVous() },
                onNavigateToChatbot = { selectedNavIndex = 3; onNavigateToChatbot() },
                onNavigateToMessagerie = { selectedNavIndex = 4; onNavigateToMessagerie() },
                onDashboard = { selectedNavIndex = 0 },
                isMedecin = uiState.userRole == UserRole.MEDECIN,
                navBarBg = navBg
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = screenBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ═══════════════════════════════════════════════════════
            //  HEADER avec gradient arrondi en bas
            // ═══════════════════════════════════════════════════════
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(HeaderGradientStart, HeaderGradientEnd),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            ),
                            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                        )
                        .statusBarsPadding()
                        .padding(top = 12.dp, bottom = 28.dp, start = 24.dp, end = 24.dp)
                ) {
                    Column {
                        // Top row: greeting + icons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_diasmart_logo),
                                    contentDescription = "DiaSmart",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .shadow(8.dp, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        "Bonjour !",
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        "DiaSmart",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Row {
                                IconButton(onClick = onNavigateToProfile) {
                                    Icon(Icons.Outlined.AccountCircle, "Profil", tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                                IconButton(onClick = onNavigateToSettings) {
                                    Icon(Icons.Outlined.Settings, "Paramètres", tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ── Carte glycémie principale (dans le header) ──
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Glycémie moyenne", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = if (uiState.avgGlucose > 0) uiState.glucoseUnit.format(uiState.avgGlucose) else "--",
                                            fontSize = 38.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            uiState.glucoseUnit.shortLabel,
                                            fontSize = 14.sp,
                                            color = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                }
                                // Indicateur circulaire
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.MonitorHeart,
                                        contentDescription = "Suivi glycémique",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ── Bannière hors-ligne ──
            if (!uiState.isOnline) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Warning.copy(alpha = 0.1f)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(36.dp).background(Warning.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.WifiOff, null, tint = Warning, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Mode hors-ligne", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFE65100))
                                Text("Données locales accessibles", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }

            // ═══════════════════════════════════════════════════════
            //  STATS RAPIDES (mini-cartes)
            // ═══════════════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isPatient = uiState.userRole == UserRole.PATIENT
                    MiniStatCard(
                        value = if (isPatient) uiState.linkedDoctors.toString() else uiState.totalPatients.toString(),
                        label = if (isPatient) "Médecins" else "Patients",
                        icon = if (isPatient) Icons.Outlined.MedicalServices else Icons.Outlined.People,
                        iconBg = if (isDark) CardGlucoseDark else CardGlucose,
                        iconTint = if (isDark) Color(0xFF9D91FF) else Primary,
                        modifier = Modifier.weight(1f),
                        cardSurface = cardSurface,
                        textPri = textPri,
                        textSec = textSec
                    )
                    MiniStatCard(
                        value = uiState.todayRendezVous.toString(),
                        label = "RDV",
                        icon = Icons.Outlined.CalendarMonth,
                        iconBg = if (isDark) CardAppointmentDark else CardAppointment,
                        iconTint = if (isDark) Color(0xFF66E3CE) else Tertiary,
                        modifier = Modifier.weight(1f),
                        cardSurface = cardSurface,
                        textPri = textPri,
                        textSec = textSec
                    )
                    MiniStatCard(
                        value = uiState.upcomingMedicaments.toString(),
                        label = "Rappels",
                        icon = Icons.Outlined.Medication,
                        iconBg = if (isDark) CardMedicationDark else CardMedication,
                        iconTint = if (isDark) Color(0xFFFFB3C6) else Secondary,
                        modifier = Modifier.weight(1f),
                        cardSurface = cardSurface,
                        textPri = textPri,
                        textSec = textSec
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ═══════════════════════════════════════════════════════
            //  ACTIONS RAPIDES — Grille de fonctionnalités
            // ═══════════════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(18.dp)
                            .background(Primary, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "ACTIONS RAPIDES",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPri,
                        letterSpacing = 1.sp
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }

            // Ligne 1
            if (uiState.userRole == UserRole.PATIENT) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureCard(
                            title = "ROLLY",
                            subtitle = "Assistant IA",
                            icon = null,
                            isRolly = true,
                            cardColor = RollyCardColor,
                            isOnline = uiState.isOnline,
                            onClick = { if (uiState.isOnline) onNavigateToChatbot() },
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = "Messagerie",
                            subtitle = "Patient-Médecin",
                            icon = Icons.Outlined.Forum,
                            cardColor = CardGlucose,
                            iconTint = Primary,
                            isOnline = uiState.isOnline,
                            onClick = { if (uiState.isOnline) onNavigateToMessagerie() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }

                // Ligne 2
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureCard(
                            title = "Analyse Repas",
                            subtitle = "Glucides & IG",
                            icon = Icons.Outlined.Restaurant,
                            cardColor = CardNutrition,
                            iconTint = Color(0xFFFF8E72),
                            isOnline = uiState.isOnline,
                            onClick = { if (uiState.isOnline) onNavigateToRepasAnalyse() },
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = "Mon médecin",
                            subtitle = "Trouver et partager",
                            icon = Icons.Outlined.MedicalServices,
                            cardColor = CardInsulin,
                            iconTint = Warning,
                            onClick = onNavigateToDataSharing,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }

                // Ligne 3
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureCard(
                            title = "Carnet de bord",
                            subtitle = "Humeur & sommeil",
                            icon = Icons.Outlined.MenuBook,
                            cardColor = Color(0xFFF0E6FF),
                            iconTint = Color(0xFF8E24AA),
                            onClick = onNavigateToJournal,
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = "Podomètre",
                            subtitle = "Compteur de pas",
                            icon = Icons.Outlined.DirectionsWalk,
                            cardColor = CardAppointment,
                            iconTint = Tertiary,
                            onClick = onNavigateToPedometer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }

                // Ligne 4
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureCard(
                            title = "Courbes",
                            subtitle = "Prédiction glycémie",
                            icon = Icons.Outlined.TrendingUp,
                            cardColor = CardMedication,
                            iconTint = Secondary,
                            onClick = onNavigateToPredictive,
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = "Communauté",
                            subtitle = "Échanges patients",
                            icon = Icons.Outlined.Groups,
                            cardColor = CardActivity,
                            iconTint = Color(0xFF0288D1),
                            onClick = onNavigateToCommunity,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Vue Médecin ──
            if (uiState.userRole == UserRole.MEDECIN) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureCard(
                            title = "Données patients",
                            subtitle = "Glycémiques",
                            icon = Icons.Outlined.Assessment,
                            cardColor = CardAppointment,
                            iconTint = Tertiary,
                            onClick = onNavigateToPatients,
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = "Messagerie",
                            subtitle = "Patient-Médecin",
                            icon = Icons.Outlined.Forum,
                            cardColor = CardGlucose,
                            iconTint = Primary,
                            isOnline = uiState.isOnline,
                            onClick = { if (uiState.isOnline) onNavigateToMessagerie() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureCard(
                            title = "Ajouter patient",
                            subtitle = "Nouveau dossier",
                            icon = Icons.Outlined.PersonAdd,
                            cardColor = CardInsulin,
                            iconTint = Warning,
                            onClick = onNavigateToAddPatient,
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = "Validations",
                            subtitle = "ROLLY IA",
                            icon = Icons.Outlined.VerifiedUser,
                            cardColor = Color(0xFFF0E6FF),
                            iconTint = Color(0xFF8E24AA),
                            onClick = onNavigateToValidations,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // ═══════════════════════════════════════════════════════
            //  PROCHAINS RENDEZ-VOUS
            // ═══════════════════════════════════════════════════════
            item {
                SectionHeader(
                    title = "Prochains rendez-vous",
                    action = "Voir tout",
                    onAction = onNavigateToRendezVous
                )
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, outlineCol)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (uiState.upcomingRendezVous.isEmpty()) {
                            EmptyStateMessage("Aucun rendez-vous à venir", Icons.Outlined.CalendarMonth, textSec = textSec, textTer = textTer, surfaceVar = if (isDark) DarkOutline else SurfaceVariant)
                        } else {
                            uiState.upcomingRendezVous.take(3).forEachIndexed { index, rdv ->
                                ModernRendezVousItem(
                                    rdv = rdv,
                                    onClick = { onNavigateToPatientDetail(rdv.patient.id) },
                                    textPri = textPri,
                                    textSec = textSec,
                                    textTer = textTer,
                                    primaryContainerCol = primaryContainerCol
                                )
                                if (index < minOf(2, uiState.upcomingRendezVous.size - 1)) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = outlineCol
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ═══════════════════════════════════════════════════════
            //  PATIENTS RECENTS
            // ═══════════════════════════════════════════════════════
            item {
                SectionHeader(
                    title = "Patients récents",
                    action = "Voir tout",
                    onAction = onNavigateToPatients
                )
            }
            item {
                if (uiState.recentPatients.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardSurface),
                        elevation = CardDefaults.cardElevation(0.dp),
                        border = BorderStroke(1.dp, outlineCol)
                    ) {
                        EmptyStateMessage("Aucun patient enregistré", Icons.Outlined.People, textSec = textSec, textTer = textTer, surfaceVar = if (isDark) DarkOutline else SurfaceVariant)
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.recentPatients.size) { index ->
                            val patient = uiState.recentPatients[index]
                            PatientChip(
                                name = patient.nomComplet,
                                subtitle = "${patient.age} ans",
                                onClick = { onNavigateToPatientDetail(patient.id) }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  COMPOSANTS DU DASHBOARD — Design DayLife
// ══════════════════════════════════════════════════════════════════

@Composable
private fun MiniStatCard(
    value: String,
    label: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    cardSurface: Color = Surface,
    textPri: Color = TextPrimary,
    textSec: Color = TextSecondary
) {
    val isDark = LocalIsDarkTheme.current
    val borderCol = if (isDark) DarkOutline else OutlineVariant
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = textPri
            )
            Text(
                label,
                fontSize = 11.sp,
                color = textSec,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    isRolly: Boolean = false,
    cardColor: Color = Surface,
    iconTint: Color = Primary,
    isOnline: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSystemDark = LocalIsDarkTheme.current
    val isDarkCard = cardColor == OnBackground || cardColor == RollyCardColor
    // In dark mode, map pastel card colors to vivid dark equivalents
    val darkModeCardColor = when (cardColor) {
        CardGlucose -> CardGlucoseDark
        CardMedication -> CardMedicationDark
        CardAppointment -> CardAppointmentDark
        CardInsulin -> CardInsulinDark
        CardActivity -> CardActivityDark
        CardNutrition -> CardNutritionDark
        Color(0xFFF0E6FF) -> Color(0xFF4A2D7A) // Carnet de bord purple
        else -> cardColor
    }
    val resolvedCardColor = if (isSystemDark && !isDarkCard) darkModeCardColor else cardColor
    val textColor = if (isDarkCard || isSystemDark) Color.White else TextPrimary
    val subColor = if (isDarkCard || isSystemDark) Color.White.copy(alpha = 0.7f) else TextSecondary
    val actualCardColor = if (!isOnline && (isRolly || icon == Icons.Outlined.Forum || icon == Icons.Outlined.Restaurant))
        SurfaceVariant else resolvedCardColor

    val featureBorderCol = if (isSystemDark) DarkOutline else OutlineVariant
    Card(
        modifier = modifier
            .height(110.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = actualCardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (!isDarkCard) BorderStroke(1.dp, featureBorderCol) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRolly) {
                RollyIcon(
                    size = 36.dp,
                    showBackground = false,
                    tint = if (isOnline) Color.White else Color(0xFF666666)
                )
            } else if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isDarkCard || isSystemDark) Color.White.copy(alpha = 0.15f) else iconTint.copy(alpha = 0.12f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = if (isDarkCard || isSystemDark) Color.White else iconTint, modifier = Modifier.size(20.dp))
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (!isOnline && (isRolly || icon == Icons.Outlined.Forum || icon == Icons.Outlined.Restaurant)) "Hors-ligne" else subtitle,
                    fontSize = 11.sp,
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    val isDark = LocalIsDarkTheme.current
    val titleCol = if (isDark) DarkTextPrimary else TextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(Primary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title.uppercase(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = titleCol,
                letterSpacing = 1.sp
            )
        }
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    action,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.ArrowForward, null, tint = Primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ModernRendezVousItem(
    rdv: RendezVousAvecPatient,
    onClick: () -> Unit,
    textPri: Color = TextPrimary,
    textSec: Color = TextSecondary,
    textTer: Color = TextTertiary,
    primaryContainerCol: Color = PrimaryContainer
) {
    val isToday = rdv.rendezVous.estAujourdhui()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (isToday) Warning.copy(alpha = 0.12f) else primaryContainerCol,
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Schedule,
                null,
                tint = if (isToday) Warning else Primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(rdv.patient.nomComplet, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textPri)
            Text(rdv.rendezVous.titre, fontSize = 12.sp, color = textSec)
            Text(
                rdv.rendezVous.dateHeure.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                fontSize = 11.sp, color = textTer
            )
        }
        if (rdv.rendezVous.estConfirme) {
            Box(
                modifier = Modifier.size(28.dp).background(Success.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, tint = Success, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PatientChip(
    name: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val initials = name.split(" ").filter { it.isNotBlank() }
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2).joinToString("")
    val chipSurface = if (isDark) DarkSurface else Surface
    val chipTextPri = if (isDark) DarkTextPrimary else TextPrimary
    val chipTextSec = if (isDark) DarkTextSecondary else TextSecondary
    val chipPrimaryContainer = if (isDark) DarkPrimaryContainer else PrimaryContainer

    val chipBorderCol = if (isDark) DarkOutline else OutlineVariant
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = chipSurface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, chipBorderCol)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(chipPrimaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, color = if (isDark) Color(0xFF9D91FF) else Primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = chipTextPri,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(subtitle, fontSize = 11.sp, color = chipTextSec)
        }
    }
}

@Composable
private fun EmptyStateMessage(
    message: String,
    icon: ImageVector = Icons.Outlined.Info,
    textSec: Color = TextSecondary,
    textTer: Color = TextTertiary,
    surfaceVar: Color = SurfaceVariant
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(surfaceVar, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = textTer, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = textSec,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Bottom Navigation Bar — Design DayLife avec indicateur pill
 */
@Composable
private fun DiaSmartBottomBar(
    selectedIndex: Int,
    onDashboard: () -> Unit,
    onNavigateToPatients: () -> Unit,
    onNavigateToDataSharing: () -> Unit,
    onNavigateToRendezVous: () -> Unit,
    onNavigateToChatbot: () -> Unit,
    onNavigateToMessagerie: () -> Unit,
    isMedecin: Boolean = false,
    navBarBg: Color = NavBarBackground
) {
    val isDark = LocalIsDarkTheme.current
    val navBorderCol = if (isDark) DarkOutline else OutlineVariant
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
        color = navBarBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = BorderStroke(1.dp, navBorderCol)
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier.height(72.dp)
        ) {
            data class NavItem(val label: String, val outlined: ImageVector, val filled: ImageVector, val isRolly: Boolean = false)

            val items = if (isMedecin) {
                listOf(
                    NavItem("Accueil", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
                    NavItem("Patients", Icons.Outlined.People, Icons.Filled.People),
                    NavItem("RDV", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
                    NavItem("Messages", Icons.Outlined.Forum, Icons.Filled.Forum)
                )
            } else {
                listOf(
                    NavItem("Accueil", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
                    NavItem("Médecin", Icons.Outlined.MedicalServices, Icons.Filled.MedicalServices),
                    NavItem("RDV", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
                    NavItem("ROLLY", Icons.Outlined.Dashboard, Icons.Filled.Dashboard, isRolly = true),
                    NavItem("Messages", Icons.Outlined.Forum, Icons.Filled.Forum)
                )
            }
            val actions = if (isMedecin) {
                listOf(onDashboard, onNavigateToPatients, onNavigateToRendezVous, onNavigateToMessagerie)
            } else {
                // Cote patient : l'onglet 2 ouvre l'ecran "Mon medecin" (DataSharing).
                listOf(onDashboard, onNavigateToDataSharing, onNavigateToRendezVous, onNavigateToChatbot, onNavigateToMessagerie)
            }

            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = selectedIndex == index,
                    onClick = actions[index],
                    icon = {
                        if (item.isRolly) {
                            RollyIconInline(
                                size = 24.dp,
                                tint = if (selectedIndex == index) NavBarSelected else NavBarUnselected
                            )
                        } else {
                            Icon(
                                imageVector = if (selectedIndex == index) item.filled else item.outlined,
                                contentDescription = item.label,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            item.label,
                            fontSize = 10.sp,
                            fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NavBarSelected,
                        selectedTextColor = NavBarSelected,
                        unselectedIconColor = NavBarUnselected,
                        unselectedTextColor = NavBarUnselected,
                        indicatorColor = PrimaryContainer
                    )
                )
            }
        }
    }
}
