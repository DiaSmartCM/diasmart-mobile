package com.diabeto.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    onNavigateToMonMedecin: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToJournal: (Long) -> Unit = {},
    onNavigateToPedometer: (Long) -> Unit = {},
    onNavigateToPredictive: (Long) -> Unit = {},
    onNavigateToValidations: () -> Unit = {},
    onNavigateToCommunity: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToMesAvis: () -> Unit = {},
    onNavigateToGlucose: (Long) -> Unit = {},
    // v2.1.78 : fiche sante personnelle du patient (meme ecran que la fiche
    // patient cote medecin, sans l'action de suppression).
    onNavigateToMaFiche: (Long) -> Unit = {},
    // v2.1.75 : ecran Medicaments (rappels de traitement), ouvert depuis
    // l'onglet "Rappels" de la barre du bas.
    onNavigateToMedicaments: (Long) -> Unit = {},
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

    // v2.1.42 : overlay d'onboarding affiche au-dessus du Scaffold a la 1ere
    // ouverture (apres login). Une fois skipped/fini, ne reapparait plus.
    if (uiState.roleLoaded && uiState.showOnboarding) {
        com.diabeto.ui.components.OnboardingDashboardOverlay(
            role = uiState.userRole,
            onFinish = { viewModel.markOnboardingSeen() }
        )
        return
    }

    Scaffold(
        bottomBar = {
            DiaSmartBottomBar(
                selectedIndex = selectedNavIndex,
                onNavigateToPatients = { selectedNavIndex = 1; onNavigateToPatients() },
                onNavigateToDataSharing = { selectedNavIndex = 1; onNavigateToDataSharing() },
                onNavigateToRendezVous = { selectedNavIndex = 2; onNavigateToRendezVous() },
                // v2.1.75 : onglet 3 = Rappels de traitement (ecran Medicaments),
                // qui n'etait plus atteignable cote patient.
                onNavigateToRappels = {
                    selectedNavIndex = 3
                    uiState.selfPatientId?.let(onNavigateToMedicaments)
                },
                onNavigateToMessagerie = { selectedNavIndex = 4; onNavigateToMessagerie() },
                onDashboard = { selectedNavIndex = 0 },
                isMedecin = uiState.userRole == UserRole.MEDECIN,
                navBarBg = navBg
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = screenBg
    ) { padding ->
        // v2.1.58 : breadcrumb Crashlytics
        LaunchedEffect(Unit) { com.diabeto.monitoring.CrashlyticsLogger.setScreen("DashboardScreen") }
        // v2.1.41 : eviter le flash du dashboard PATIENT chez un MEDECIN.
        // Tant que le role n'est pas charge depuis Firestore, on affiche un loader.
        if (!uiState.roleLoaded) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
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
                                        stringResource(R.string.dash_greeting),
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
                                    Icon(Icons.Outlined.AccountCircle, stringResource(R.string.cd_profile), tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                                IconButton(onClick = onNavigateToSettings) {
                                    Icon(Icons.Outlined.Settings, stringResource(R.string.cd_settings), tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ── Carte glycémie principale (dans le header) ──
                        // v2.1.71 : cliquable cote PATIENT -> ouvre l'ecran de
                        // saisie glycemie/HbA1c (seul point d'entree pour le
                        // patient ; il n'a pas de carte dediee ni d'onglet).
                        val canOpenGlucose = uiState.userRole == UserRole.PATIENT && uiState.selfPatientId != null
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (canOpenGlucose)
                                        Modifier.clickable { onNavigateToGlucose(uiState.selfPatientId!!) }
                                    else Modifier
                                ),
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
                                    Text(stringResource(R.string.dash_glucose_avg), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
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
                                    // v2.1.71 : indice tappable cote patient
                                    if (canOpenGlucose) {
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Add, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                stringResource(R.string.dash_glucose_tap_add),
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.85f)
                                            )
                                        }
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
                                Text(stringResource(R.string.dash_offline_mode), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFE65100))
                                Text(stringResource(R.string.dash_offline_subtitle), fontSize = 11.sp, color = TextSecondary)
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
                        label = if (isPatient) stringResource(R.string.dash_stat_doctors) else stringResource(R.string.dash_stat_patients),
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
                        label = stringResource(R.string.dash_stat_rdv),
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
                        label = stringResource(R.string.dash_stat_reminders),
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
                        stringResource(R.string.dash_section_quick_actions),
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
                            title = stringResource(R.string.card_rolly_title),
                            subtitle = stringResource(R.string.card_rolly_subtitle),
                            icon = null,
                            isRolly = true,
                            cardColor = RollyCardColor,
                            isOnline = uiState.isOnline,
                            onClick = { if (uiState.isOnline) onNavigateToChatbot() },
                            modifier = Modifier.weight(1f)
                        )
                        // v2.1.75 : Analyse Repas remonte en ligne 1 (usage
                        // quotidien), Messagerie descend en ligne 3.
                        FeatureCard(
                            title = stringResource(R.string.card_meal_title),
                            subtitle = stringResource(R.string.card_meal_subtitle),
                            icon = Icons.Outlined.Restaurant,
                            cardColor = CardNutrition,
                            iconTint = Color(0xFFFF8E72),
                            isOnline = uiState.isOnline,
                            onClick = { if (uiState.isOnline) onNavigateToRepasAnalyse() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }

                // Ligne 2 — le duo de suivi glycemique.
                // v2.1.75 : couleurs permutees a la demande — la saisie
                // Glycemie/HbA1c prend le rose/rouge (donnee vitale, doit
                // sauter aux yeux), les Courbes heritent du lavande indigo.
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureCard(
                            title = stringResource(R.string.card_glucose_title),
                            subtitle = stringResource(R.string.card_glucose_subtitle),
                            icon = Icons.Outlined.Bloodtype,
                            cardColor = CardMedication,
                            iconTint = Secondary,
                            onClick = { uiState.selfPatientId?.let(onNavigateToGlucose) },
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = stringResource(R.string.card_curves_title),
                            subtitle = stringResource(R.string.card_curves_subtitle),
                            icon = Icons.Outlined.TrendingUp,
                            cardColor = CardGlucose,
                            iconTint = Primary,
                            onClick = { uiState.selfPatientId?.let(onNavigateToPredictive) },
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
                        // v2.1.75 : Messagerie descend ici. Teinte menthe pour
                        // ne pas dupliquer le lavande des Courbes juste au-dessus.
                        FeatureCard(
                            title = stringResource(R.string.card_messaging_title),
                            subtitle = stringResource(R.string.card_messaging_subtitle),
                            icon = Icons.Outlined.Forum,
                            cardColor = CardAppointment,
                            iconTint = Tertiary,
                            isOnline = uiState.isOnline,
                            onClick = { if (uiState.isOnline) onNavigateToMessagerie() },
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = stringResource(R.string.card_community_title),
                            subtitle = stringResource(R.string.card_community_subtitle),
                            icon = Icons.Outlined.Groups,
                            cardColor = CardActivity,
                            iconTint = Color(0xFF0288D1),
                            onClick = onNavigateToCommunity,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }

                // Ligne 4 — suivi quotidien
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureCard(
                            title = stringResource(R.string.card_journal_title),
                            subtitle = stringResource(R.string.card_journal_subtitle),
                            icon = Icons.Outlined.MenuBook,
                            cardColor = Color(0xFFF0E6FF),
                            iconTint = Color(0xFF8E24AA),
                            onClick = { uiState.selfPatientId?.let(onNavigateToJournal) },
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = stringResource(R.string.card_pedometer_title),
                            subtitle = stringResource(R.string.card_pedometer_subtitle),
                            icon = Icons.Outlined.DirectionsWalk,
                            cardColor = CardInsulin,
                            iconTint = Warning,
                            onClick = { uiState.selfPatientId?.let(onNavigateToPedometer) },
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
                            title = stringResource(R.string.card_patient_data_title),
                            subtitle = stringResource(R.string.card_patient_data_subtitle),
                            icon = Icons.Outlined.Assessment,
                            cardColor = CardAppointment,
                            iconTint = Tertiary,
                            onClick = onNavigateToPatients,
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = stringResource(R.string.card_messaging_title),
                            subtitle = stringResource(R.string.card_messaging_subtitle),
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
                            title = stringResource(R.string.card_add_patient_title),
                            subtitle = stringResource(R.string.card_add_patient_subtitle),
                            icon = Icons.Outlined.PersonAdd,
                            cardColor = CardInsulin,
                            iconTint = Warning,
                            onClick = onNavigateToAddPatient,
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = stringResource(R.string.card_validations_title),
                            subtitle = stringResource(R.string.card_validations_subtitle),
                            icon = Icons.Outlined.VerifiedUser,
                            cardColor = Color(0xFFF0E6FF),
                            iconTint = Color(0xFF8E24AA),
                            onClick = onNavigateToValidations,
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
                            title = stringResource(R.string.card_report_title),
                            subtitle = stringResource(R.string.card_report_subtitle),
                            icon = Icons.Outlined.Description,
                            cardColor = CardGlucose,
                            iconTint = Primary,
                            onClick = onNavigateToReports,
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = stringResource(R.string.card_my_reviews_title),
                            subtitle = stringResource(R.string.card_my_reviews_subtitle),
                            icon = Icons.Outlined.Star,
                            cardColor = CardAppointment,
                            iconTint = Color(0xFFF59E0B),
                            onClick = onNavigateToMesAvis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Ma fiche sante ──
            // v2.1.78 : le dossier medical du patient existait deja (le meme
            // ecran que le medecin utilise pour ses patients suivis), mais plus
            // aucun chemin n'y menait cote patient depuis la reorganisation du
            // tableau de bord. Carte pleine largeur : c'est le dossier lui-meme,
            // pas une action parmi d'autres, et cela ne deplace aucune carte.
            if (uiState.userRole == UserRole.PATIENT) {
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        FeatureCard(
                            title = stringResource(R.string.card_myfile_title),
                            subtitle = stringResource(R.string.card_myfile_subtitle),
                            icon = Icons.Outlined.Badge,
                            cardColor = CardNutrition,
                            iconTint = Color(0xFFAD6A1C),
                            onClick = { uiState.selfPatientId?.let(onNavigateToMaFiche) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ── Bouton "Envoyer rapport" cote patient ──
            if (uiState.userRole == UserRole.PATIENT) {
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureCard(
                            title = stringResource(R.string.card_send_report_title),
                            subtitle = stringResource(R.string.card_send_report_subtitle),
                            icon = Icons.Outlined.Description,
                            cardColor = CardAppointment,
                            iconTint = Tertiary,
                            onClick = onNavigateToReports,
                            modifier = Modifier.weight(1f)
                        )
                        // v2.1.75 : "Mon medecin" descend en fin de grille
                        // (action ponctuelle) ; la saisie Glycemie/HbA1c, elle,
                        // remonte en ligne 2 avec les Courbes.
                        FeatureCard(
                            title = stringResource(R.string.card_my_doctor_title),
                            subtitle = stringResource(R.string.card_my_doctor_subtitle),
                            icon = Icons.Outlined.MedicalServices,
                            cardColor = CardGlucose,
                            iconTint = Primary,
                            onClick = onNavigateToMonMedecin,
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
                    title = stringResource(R.string.dash_section_next_rdv),
                    action = stringResource(R.string.dash_action_see_all),
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
                            EmptyStateMessage(stringResource(R.string.dash_no_rdv), Icons.Outlined.CalendarMonth, textSec = textSec, textTer = textTer, surfaceVar = if (isDark) DarkOutline else SurfaceVariant)
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
            //  PATIENTS RECENTS — medecin uniquement (v2.1.71)
            //  Le patient n'a pas de patients : section masquee cote patient.
            // ═══════════════════════════════════════════════════════
            if (uiState.userRole == UserRole.MEDECIN) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.dash_recent_patients),
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
                            items(uiState.recentPatients, key = { it.id }) { patient ->
                                PatientChip(
                                    name = patient.nomComplet,
                                    subtitle = "${patient.age} ans",
                                    onClick = { onNavigateToPatientDetail(patient.id) }
                                )
                            }
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
            // v2.1.75 : hauteur MINIMALE et non fixe. A 110.dp figes, un titre
            // long (arabe, pidgin) ou une grande taille de police systeme
            // (accessibilite) rognait le texte. La carte garde son gabarit
            // habituel et ne s'etire que si le contenu l'exige.
            .heightIn(min = 110.dp)
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
    // v2.1.75 : l'onglet ROLLY devient "Rappels" (ROLLY a deja sa carte).
    onNavigateToRappels: () -> Unit,
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
        // v2.1.75 : plus de hauteur figee. Material3 ajoute lui-meme la marge
        // de securite de la barre systeme A L'INTERIEUR de la hauteur donnee :
        // avec 72.dp imposes, un telephone a navigation gestuelle (barre de
        // 24-48 dp) ne laissait qu'une trentaine de dp aux icones et libelles,
        // qui se retrouvaient ecrases. On laisse la barre se dimensionner et
        // absorber les insets, ce qui l'adapte a chaque appareil.
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            data class NavItem(val label: String, val outlined: ImageVector, val filled: ImageVector, val isRolly: Boolean = false)

            // v2.1.63 : labels via stringResource pour i18n
            val labelHome = stringResource(R.string.nav_home)
            val labelPatients = stringResource(R.string.nav_patients)
            val labelRdv = stringResource(R.string.nav_rdv_short)
            val labelMessages = stringResource(R.string.nav_messages)
            val labelDoctor = stringResource(R.string.nav_doctor)
            val labelRappels = stringResource(R.string.nav_rappels)
            val items = if (isMedecin) {
                listOf(
                    NavItem(labelHome, Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
                    NavItem(labelPatients, Icons.Outlined.People, Icons.Filled.People),
                    NavItem(labelRdv, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
                    NavItem(labelMessages, Icons.Outlined.Forum, Icons.Filled.Forum)
                )
            } else {
                listOf(
                    NavItem(labelHome, Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
                    NavItem(labelDoctor, Icons.Outlined.MedicalServices, Icons.Filled.MedicalServices),
                    NavItem(labelRdv, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
                    // v2.1.75 : ROLLY a deja sa carte en tete des actions
                    // rapides ; l'onglet devient "Rappels" et ouvre l'ecran
                    // Medicaments (saisie des traitements + heures de prise),
                    // qui n'avait plus aucun point d'entree cote patient.
                    NavItem(labelRappels, Icons.Outlined.Alarm, Icons.Filled.Alarm),
                    NavItem(labelMessages, Icons.Outlined.Forum, Icons.Filled.Forum)
                )
            }
            val actions = if (isMedecin) {
                listOf(onDashboard, onNavigateToPatients, onNavigateToRendezVous, onNavigateToMessagerie)
            } else {
                // Cote patient : l'onglet 2 ouvre l'ecran "Mon medecin" (DataSharing),
                // l'onglet 4 les rappels de traitement (v2.1.75, remplace ROLLY).
                listOf(
                    onDashboard,
                    onNavigateToDataSharing,
                    onNavigateToRendezVous,
                    onNavigateToRappels,
                    onNavigateToMessagerie
                )
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
