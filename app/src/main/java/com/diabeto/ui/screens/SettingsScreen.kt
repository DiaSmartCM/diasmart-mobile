package com.diabeto.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diabeto.R
import com.diabeto.ui.theme.LocalIsDarkTheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.repository.AppLanguage
import com.diabeto.data.repository.CloudBackupRepository
import com.diabeto.data.repository.GlucoseUnit
import com.diabeto.data.repository.MeasureType
import com.diabeto.data.repository.ThemeMode
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════
//  DayLife-inspired Settings — Clean medical wellness UI
//  Soft cards, colored icon circles, generous spacing
// ══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFamily: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val scope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showUnitDialog by remember { mutableStateOf(false) }
    var showMeasureTypeDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAppLockChooser by remember { mutableStateOf(false) }
    var pendingMethod by remember { mutableStateOf<com.diabeto.security.AppLockMethod?>(null) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    // v2.1.60 : recu RGPD genere a la suppression. Affiche dans un dialog avant
    // redirection vers Login pour que l'utilisateur puisse le sauvegarder.
    var deletionReceipt by remember { mutableStateOf<String?>(null) }

    // (Partage de fichier deplace dans l'ecran "Compte-rendu / Ordonnance".)

    // DayLife-inspired colors
    val screenBg = if (isDark) DarkBackground else Color(0xFFF7F8FC)
    val cardBg = if (isDark) Color(0xFF1A1A2E) else Color.White
    val headerGradient = listOf(
        if (isDark) Color(0xFF2A2B55) else Color(0xFF6771E4),
        if (isDark) Color(0xFF1A1A3E) else Color(0xFF8B93F0)
    )
    val sectionTextColor = if (isDark) Color(0xFF8B93F0) else Primary
    val titleColor = if (isDark) DarkTextPrimary else TextPrimary
    val subtitleColor = if (isDark) DarkTextSecondary else TextSecondary
    val dividerColor = if (isDark) DarkOutline else Color(0xFFF0EFF5)

    Scaffold(
        topBar = {
            // DayLife clean gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(headerGradient))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = Color.White
                        )
                    }
                    Text(
                        stringResource(R.string.settings_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        },
        containerColor = screenBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ── Apparence ─────────────────────────────────────
            item {
                DayLifeSectionHeader(
                    title = stringResource(R.string.settings_section_appearance),
                    color = sectionTextColor,
                    isDark = isDark
                )
            }
            item {
                DayLifeSettingsCard(cardBg = cardBg) {
                    DayLifeSettingsItem(
                        icon = Icons.Default.Palette,
                        iconBg = Color(0xFF6771E4),
                        title = stringResource(R.string.settings_theme),
                        subtitle = when (uiState.themeMode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        },
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        onClick = { showThemeDialog = true }
                    )
                    DayLifeDivider(dividerColor)
                    DayLifeSettingsItem(
                        icon = Icons.Default.Language,
                        iconBg = Color(0xFF00C9A7),
                        title = stringResource(R.string.settings_language),
                        subtitle = uiState.language.displayName,
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        onClick = { showLanguageDialog = true }
                    )
                }
            }

            // ── Mesures & Unités ──────────────────────────────
            item {
                DayLifeSectionHeader(
                    title = stringResource(R.string.settings_section_measures),
                    color = sectionTextColor,
                    isDark = isDark
                )
            }
            item {
                DayLifeSettingsCard(cardBg = cardBg) {
                    DayLifeSettingsItem(
                        icon = Icons.Default.Straighten,
                        iconBg = Color(0xFF3B82F6),
                        title = stringResource(R.string.settings_glucose_unit_title),
                        subtitle = uiState.glucoseUnit.label,
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        onClick = { showUnitDialog = true }
                    )
                    DayLifeDivider(dividerColor)
                    DayLifeSettingsItem(
                        icon = Icons.Default.MonitorHeart,
                        iconBg = Color(0xFFEF4444),
                        title = stringResource(R.string.settings_measure_type_title),
                        subtitle = uiState.measureType.displayName,
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        onClick = { showMeasureTypeDialog = true }
                    )
                    // Objectif glycemique : patient uniquement (un medecin n'a pas
                    // de glycemie personnelle a cibler sur son compte)
                    if (!uiState.isMedecin) {
                        DayLifeDivider(dividerColor)
                        DayLifeSettingsItem(
                            icon = Icons.Default.Analytics,
                            iconBg = Color(0xFF8B5CF6),
                            title = stringResource(R.string.settings_target_title),
                            subtitle = if (uiState.glucoseUnit == GlucoseUnit.MG_DL)
                                "${uiState.targetMin.toInt()} - ${uiState.targetMax.toInt()} mg/dL"
                            else
                                "${"%.1f".format(uiState.targetMin / 18.0182)} - ${"%.1f".format(uiState.targetMax / 18.0182)} mmol/L",
                            titleColor = titleColor,
                            subtitleColor = subtitleColor,
                            onClick = { showTargetDialog = true }
                        )
                    }
                }
            }

            // ── Notifications ─────────────────────────────────
            item {
                DayLifeSectionHeader(
                    title = stringResource(R.string.settings_notifications),
                    color = sectionTextColor,
                    isDark = isDark
                )
            }
            item {
                DayLifeSettingsCard(cardBg = cardBg) {
                    DayLifeToggleItem(
                        icon = Icons.Default.Notifications,
                        iconBg = Color(0xFFFF8C42),
                        title = stringResource(R.string.settings_notifications),
                        subtitle = stringResource(R.string.settings_notifications_subtitle),
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = viewModel::setNotificationsEnabled,
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        isDark = isDark
                    )
                    // Rappels medicaments + glycemie : patient uniquement.
                    // Le medecin ne suit pas sa propre prise de medicaments
                    // ni ses propres glycemies sur le compte professionnel.
                    if (!uiState.isMedecin) {
                        DayLifeDivider(dividerColor)
                        DayLifeToggleItem(
                            icon = Icons.Default.Medication,
                            iconBg = Color(0xFFEF4444),
                            title = stringResource(R.string.settings_med_reminders_title),
                            subtitle = stringResource(R.string.settings_med_reminders_subtitle),
                            checked = uiState.medicationReminders,
                            onCheckedChange = viewModel::setMedicationReminders,
                            enabled = uiState.notificationsEnabled,
                            titleColor = titleColor,
                            subtitleColor = subtitleColor,
                            isDark = isDark
                        )
                        DayLifeDivider(dividerColor)
                        DayLifeToggleItem(
                            icon = Icons.Default.MonitorHeart,
                            iconBg = Color(0xFF6771E4),
                            title = stringResource(R.string.settings_glucose_reminders_title),
                            subtitle = stringResource(R.string.settings_glucose_reminders_subtitle),
                            checked = uiState.measurementReminders,
                            onCheckedChange = viewModel::setMeasurementReminders,
                            enabled = uiState.notificationsEnabled,
                            titleColor = titleColor,
                            subtitleColor = subtitleColor,
                            isDark = isDark
                        )
                    }
                    DayLifeDivider(dividerColor)
                    DayLifeToggleItem(
                        icon = Icons.Default.CalendarMonth,
                        iconBg = Color(0xFF14B8A6),
                        title = stringResource(R.string.settings_rdv_reminders_title),
                        subtitle = stringResource(R.string.settings_rdv_reminders_subtitle),
                        checked = uiState.appointmentReminders,
                        onCheckedChange = viewModel::setAppointmentReminders,
                        enabled = uiState.notificationsEnabled,
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        isDark = isDark
                    )
                }
            }

            // ── Export & Données ──────────────────────────────
            // ── Securite ──────────────────────────────────────
            item {
                DayLifeSectionHeader(
                    title = stringResource(R.string.settings_section_security),
                    color = sectionTextColor,
                    isDark = isDark
                )
            }
            item {
                DayLifeSettingsCard(cardBg = cardBg) {
                    val methodLabel = when (uiState.appLockMethod) {
                        com.diabeto.security.AppLockMethod.BIOMETRIC -> "Empreinte digitale"
                        com.diabeto.security.AppLockMethod.PIN -> "Code PIN (4 chiffres)"
                        com.diabeto.security.AppLockMethod.PASSWORD -> "Mot de passe"
                        com.diabeto.security.AppLockMethod.NONE -> "Aucune"
                    }
                    DayLifeToggleItem(
                        icon = Icons.Default.Lock,
                        iconBg = Color(0xFF6771E4),
                        title = stringResource(R.string.settings_lock_title),
                        subtitle = if (uiState.appLockEnabled) "Active — $methodLabel"
                            else stringResource(R.string.settings_lock_disabled),
                        checked = uiState.appLockEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showAppLockChooser = true
                            } else {
                                viewModel.setAppLockEnabled(false)
                            }
                        },
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        isDark = isDark
                    )
                    if (uiState.appLockEnabled) {
                        DayLifeDivider(dividerColor)
                        DayLifeSettingsItem(
                            icon = Icons.Default.Edit,
                            iconBg = Color(0xFF8B93F0),
                            title = stringResource(R.string.settings_lock_method_change),
                            subtitle = stringResource(R.string.settings_lock_method_change_sub),
                            titleColor = titleColor,
                            subtitleColor = subtitleColor,
                            onClick = { showAppLockChooser = true }
                        )
                    }
                }
            }

            item {
                DayLifeSectionHeader(
                    title = stringResource(R.string.settings_section_export),
                    color = sectionTextColor,
                    isDark = isDark
                )
            }
            item {
                DayLifeSettingsCard(cardBg = cardBg) {
                    // Cote patient uniquement : export personnel + partage avec mon medecin
                    if (!uiState.isMedecin) {
                        DayLifeSettingsItem(
                            icon = Icons.Default.FileDownload,
                            iconBg = Color(0xFF10B981),
                            title = stringResource(R.string.settings_export_my_data),
                            subtitle = stringResource(R.string.settings_export_my_data_sub),
                            titleColor = titleColor,
                            subtitleColor = subtitleColor,
                            onClick = { showExportDialog = true }
                        )
                        DayLifeDivider(dividerColor)
                    }
                    DayLifeSettingsItem(
                        icon = Icons.Default.CloudSync,
                        iconBg = Color(0xFF3B82F6),
                        title = stringResource(R.string.settings_cloud_backup_title),
                        subtitle = if (isBackingUp) "Sauvegarde en cours..." else stringResource(R.string.settings_cloud_backup_subtitle),
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        onClick = {
                            if (!isBackingUp) {
                                isBackingUp = true
                                scope.launch {
                                    try {
                                        viewModel.performCloudBackup()
                                        Toast.makeText(context, "Sauvegarde cloud réussie !", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Erreur de sauvegarde", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isBackingUp = false
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // ── Mode famille (v2.1.48+) ─────────────────────
            if (!uiState.isMedecin) {
                item {
                    DayLifeSectionHeader(
                        title = stringResource(R.string.settings_family_section),
                        color = sectionTextColor,
                        isDark = isDark
                    )
                }
                item {
                    DayLifeSettingsCard(cardBg = cardBg) {
                        DayLifeSettingsItem(
                            icon = Icons.Default.Group,
                            iconBg = Color(0xFFEC4899),
                            title = stringResource(R.string.settings_family_aidants),
                            subtitle = stringResource(R.string.settings_family_aidants_sub),
                            titleColor = titleColor,
                            subtitleColor = subtitleColor,
                            onClick = onNavigateToFamily
                        )
                    }
                }
            } else {
                // Cote medecin : ne montre pas la section. Le medecin peut
                // toujours etre aidant d'un proche s'il a un compte perso,
                // mais ce n'est pas surface principale.
            }

            // ── (Section "Partage avec un patient" deplacee vers l'ecran
            //     "Compte-rendu / Ordonnance" : envoi via la messagerie
            //     in-app au lieu du share Android natif.) ─

            // ── (Section "IA hors-ligne" supprimee en v2.1.31 — Rolly est
            //     desormais cloud-only. Les saisies locales continuent d'etre
            //     enregistrees en base Room et synchronisees automatiquement
            //     au cloud des le retour du reseau.) ───

            // ── Compte ──────────────────────────────────────
            item {
                DayLifeSectionHeader(
                    title = stringResource(R.string.settings_section_account),
                    color = sectionTextColor,
                    isDark = isDark
                )
            }
            item {
                DayLifeSettingsCard(cardBg = cardBg) {
                    DayLifeSettingsItem(
                        icon = Icons.Default.PersonOff,
                        iconBg = Color(0xFFEF4444),
                        title = stringResource(R.string.settings_delete_account_title),
                        subtitle = if (isDeletingAccount) "Suppression en cours..." else stringResource(R.string.settings_delete_account_subtitle),
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        onClick = {
                            if (!isDeletingAccount) showDeleteAccountDialog = true
                        }
                    )
                }
            }

            // ── À propos ─────────────────────────────────────
            item {
                DayLifeSectionHeader(
                    title = stringResource(R.string.settings_section_about),
                    color = sectionTextColor,
                    isDark = isDark
                )
            }
            item {
                DayLifeSettingsCard(cardBg = cardBg) {
                    DayLifeInfoItem(
                        icon = Icons.Default.Info,
                        iconBg = Color(0xFF6771E4),
                        title = stringResource(R.string.settings_about_version),
                        subtitle = "${com.diabeto.BuildConfig.VERSION_NAME} (build ${com.diabeto.BuildConfig.VERSION_CODE})",
                        titleColor = titleColor,
                        subtitleColor = subtitleColor
                    )
                    DayLifeDivider(dividerColor)
                    DayLifeInfoItem(
                        icon = Icons.Default.LocalHospital,
                        iconBg = Color(0xFFEF4444),
                        title = stringResource(R.string.app_name),
                        subtitle = stringResource(R.string.settings_about_app_desc),
                        titleColor = titleColor,
                        subtitleColor = subtitleColor
                    )
                    DayLifeDivider(dividerColor)
                    DayLifeInfoItem(
                        icon = Icons.Default.Email,
                        iconBg = Color(0xFFFF8C42),
                        title = stringResource(R.string.settings_about_contact),
                        subtitle = "ngostheo30@gmail.com",
                        titleColor = titleColor,
                        subtitleColor = subtitleColor
                    )
                }
            }

            // ── Légal ────────────────────────────────────────
            item {
                DayLifeSectionHeader(
                    title = stringResource(R.string.settings_section_legal),
                    color = sectionTextColor,
                    isDark = isDark
                )
            }
            item {
                DayLifeSettingsCard(cardBg = cardBg) {
                    DayLifeSettingsItem(
                        icon = Icons.Default.Policy,
                        iconBg = Color(0xFF6771E4),
                        title = stringResource(R.string.settings_legal_privacy_title),
                        subtitle = stringResource(R.string.settings_legal_privacy_subtitle),
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://public-one-omega-88.vercel.app/privacy.html"))
                            context.startActivity(intent)
                        }
                    )
                    DayLifeDivider(dividerColor)
                    DayLifeSettingsItem(
                        icon = Icons.Default.Gavel,
                        iconBg = Color(0xFF8B5CF6),
                        title = stringResource(R.string.settings_legal_license_title),
                        subtitle = stringResource(R.string.settings_legal_license_subtitle),
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://public-one-omega-88.vercel.app/license.html"))
                            context.startActivity(intent)
                        }
                    )
                    DayLifeDivider(dividerColor)
                    DayLifeSettingsItem(
                        icon = Icons.Default.Description,
                        iconBg = Color(0xFF14B8A6),
                        title = stringResource(R.string.settings_legal_terms_title),
                        subtitle = stringResource(R.string.settings_legal_terms_subtitle),
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://public-one-omega-88.vercel.app/terms.html"))
                            context.startActivity(intent)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────

    // Theme Dialog
    if (showThemeDialog) {
        DayLifeSelectionDialog(
            title = stringResource(R.string.dialog_choose_theme),
            options = ThemeMode.entries.map { mode ->
                when (mode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system_auto) to (uiState.themeMode == mode)
                    ThemeMode.LIGHT -> stringResource(R.string.theme_light_full) to (uiState.themeMode == mode)
                    ThemeMode.DARK -> stringResource(R.string.theme_dark_full) to (uiState.themeMode == mode)
                }
            },
            onSelect = { index ->
                viewModel.setThemeMode(ThemeMode.entries[index])
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    // Language Dialog
    if (showLanguageDialog) {
        DayLifeSelectionDialog(
            title = stringResource(R.string.dialog_choose_language),
            options = AppLanguage.entries.map { lang ->
                lang.displayName to (uiState.language == lang)
            },
            onSelect = { index ->
                viewModel.setLanguage(AppLanguage.entries[index])
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    // Unit Dialog
    if (showUnitDialog) {
        DayLifeSelectionDialog(
            title = stringResource(R.string.dialog_choose_glucose_unit),
            options = GlucoseUnit.entries.map { unit ->
                val desc = when (unit) {
                    GlucoseUnit.MG_DL -> stringResource(R.string.unit_mgdl_full)
                    GlucoseUnit.MMOL_L -> stringResource(R.string.unit_mmoll_full)
                }
                desc to (uiState.glucoseUnit == unit)
            },
            onSelect = { index ->
                viewModel.setGlucoseUnit(GlucoseUnit.entries[index])
                showUnitDialog = false
            },
            onDismiss = { showUnitDialog = false }
        )
    }

    // Measure Type Dialog
    if (showMeasureTypeDialog) {
        DayLifeSelectionDialog(
            title = stringResource(R.string.dialog_choose_measure_type),
            options = MeasureType.entries.map { type ->
                type.displayName to (uiState.measureType == type)
            },
            onSelect = { index ->
                viewModel.setMeasureType(MeasureType.entries[index])
                showMeasureTypeDialog = false
            },
            onDismiss = { showMeasureTypeDialog = false }
        )
    }

    // Glycemic Target Dialog
    if (showTargetDialog) {
        DayLifeTargetDialog(
            currentMin = uiState.targetMin,
            currentMax = uiState.targetMax,
            onConfirm = { min, max ->
                viewModel.setGlycemicTarget(min, max)
                showTargetDialog = false
            },
            onDismiss = { showTargetDialog = false }
        )
    }

    // App Lock — chooser de la methode
    if (showAppLockChooser) {
        AppLockMethodChooser(
            current = uiState.appLockMethod,
            isDark = isDark,
            onPick = { m ->
                showAppLockChooser = false
                if (m == com.diabeto.security.AppLockMethod.BIOMETRIC) {
                    viewModel.configureAppLock(m, null)
                } else {
                    pendingMethod = m
                }
            },
            onDismiss = { showAppLockChooser = false }
        )
    }

    // App Lock — setup PIN / mot de passe
    pendingMethod?.let { method ->
        AppLockSecretSetup(
            method = method,
            isDark = isDark,
            onConfirm = { secret ->
                viewModel.configureAppLock(method, secret)
                pendingMethod = null
            },
            onDismiss = { pendingMethod = null }
        )
    }

    // Delete Account Confirmation Dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            containerColor = if (isDark) Color(0xFF1A1A2E) else Color.White,
            shape = RoundedCornerShape(24.dp),
            icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFEF4444)) },
            title = {
                Text(
                    "Supprimer mon compte ?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = if (isDark) DarkTextPrimary else TextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Cette action est DEFINITIVE et IRREVERSIBLE. Seront supprimes :",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) DarkTextPrimary else TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    val items = listOf(
                        "Profil utilisateur",
                        "Toutes vos lectures glycemiques + HbA1c",
                        "Tous vos repas, medicaments, RDV, journal",
                        "Conversations + messages (patient/medecin)",
                        "Avis donnes ou recus",
                        "Liens de partage (patients/medecins)",
                        "Notifications FCM",
                        "Backup cloud + rapports PDF",
                        "Compte Firebase Auth"
                    )
                    items.forEach { Text("• $it", fontSize = 13.sp, color = if (isDark) DarkTextSecondary else TextSecondary) }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Un recu RGPD signe vous sera fourni comme preuve legale.",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    // v2.1.82 : la sauvegarde cloud figure elle aussi dans la
                    // liste des elements supprimes. Il faut donc EXPORTER hors
                    // de DiaSmart avant, pas seulement sauvegarder — le rappel
                    // renvoie vers l'export, seul moyen de conserver quelque
                    // chose apres suppression du compte.
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Default.Download, null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "La sauvegarde cloud sera detruite avec le compte. " +
                                "Pour garder une trace de vos donnees, utilisez " +
                                "« Exporter mes donnees » AVANT de confirmer : le " +
                                "fichier reste sur votre telephone.",
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                color = if (isDark) DarkTextPrimary else TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        isDeletingAccount = true
                        viewModel.deleteMyAccount { ok, receipt, err ->
                            isDeletingAccount = false
                            if (ok) {
                                // v2.1.60 : affiche le recu RGPD avant logout pour que
                                // l'utilisateur le copie / le partage en preuve legale.
                                deletionReceipt = receipt
                            } else {
                                Toast.makeText(context, "Erreur : ${err ?: "inconnue"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = Primary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // v2.1.60 : Dialog recu RGPD apres suppression compte.
    // Affiche le recu Base64 signe HMAC + bouton "Partager" (export texte) +
    // bouton "J'ai sauvegarde" qui declenche le logout / redirect.
    deletionReceipt?.let { receipt ->
        AlertDialog(
            onDismissRequest = { /* non dismissable — l'utilisateur DOIT acter */ },
            containerColor = if (isDark) Color(0xFF1A1A2E) else Color.White,
            shape = RoundedCornerShape(24.dp),
            icon = { Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF10B981)) },
            title = {
                Text(
                    "Recu RGPD",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = if (isDark) DarkTextPrimary else TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        "Votre compte a ete supprime. Conservez ce recu signe comme preuve legale (article 17 RGPD / loi camerounaise sur les donnees personnelles).",
                        fontSize = 13.sp,
                        color = if (isDark) DarkTextSecondary else TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color(0xFF0F0F1F) else Color(0xFFF5F5F7)
                    ) {
                        Text(
                            receipt,
                            modifier = Modifier.padding(10.dp),
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = if (isDark) DarkTextPrimary else TextPrimary,
                            maxLines = 6,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        // Partage : intent SEND avec le recu en clair
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Recu RGPD DiaSmart")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Recu de suppression de compte DiaSmart\n" +
                                "Date : ${java.time.LocalDateTime.now()}\n\n" +
                                "Recu signe (HMAC-SHA256, Base64) :\n$receipt\n\n" +
                                "Pour verifier l'authenticite, contactez support@diasmart.app"
                            )
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Partager le recu"))
                    }) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_receipt_share), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        deletionReceipt = null
                        Toast.makeText(context, "Compte supprime", Toast.LENGTH_LONG).show()
                        // Redemarrage : relancer l'activite pour retomber sur Login
                        val pm = context.packageManager
                        val intent = pm.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(stringResource(R.string.settings_receipt_saved)) }
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        DayLifeExportDialog(
            onExportCsv = {
                showExportDialog = false
                viewModel.exportData(context, "csv")
            },
            onExportPdf = {
                showExportDialog = false
                viewModel.exportData(context, "pdf")
            },
            onExportEmail = {
                showExportDialog = false
                viewModel.exportData(context, "email")
            },
            onDismiss = { showExportDialog = false }
        )
    }
}

// ══════════════════════════════════════════════════════════════════
//  DayLife Design System Components
// ══════════════════════════════════════════════════════════════════

@Composable
private fun DayLifeSectionHeader(
    title: String,
    color: Color,
    isDark: Boolean
) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
private fun DayLifeSettingsCard(
    cardBg: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (cardBg == Color.White) Color(0xFFF0EFF5) else DarkOutline
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            content = content
        )
    }
}

@Composable
private fun DayLifeSettingsItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    titleColor: Color,
    subtitleColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // DayLife colored icon circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = iconBg,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 13.sp,
                color = subtitleColor,
                lineHeight = 16.sp
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "Ouvrir $title",
            tint = subtitleColor.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DayLifeToggleItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    titleColor: Color,
    subtitleColor: Color,
    isDark: Boolean
) {
    val alpha = if (enabled) 1f else 0.4f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // DayLife colored icon circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg.copy(alpha = 0.12f * alpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = iconBg.copy(alpha = alpha),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = titleColor.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 13.sp,
                color = subtitleColor.copy(alpha = alpha),
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Primary,
                uncheckedThumbColor = if (isDark) Color(0xFF4A4A60) else Color(0xFFD4D2E0),
                uncheckedTrackColor = if (isDark) DarkOutline else Color(0xFFF0EFF5)
            )
        )
    }
}

@Composable
private fun DayLifeInfoItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    titleColor: Color,
    subtitleColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconBg,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 13.sp,
                color = subtitleColor,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun DayLifeDivider(color: Color) {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 70.dp),
        thickness = 0.5.dp,
        color = color
    )
}

// ── DayLife-styled Dialogs ──────────────────────────────────────

@Composable
private fun DayLifeSelectionDialog(
    title: String,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val dialogBg = if (isDark) Color(0xFF1A1A2E) else Color.White

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (isDark) DarkTextPrimary else TextPrimary
            )
        },
        text = {
            // v2.1.68 : verticalScroll + hauteur max pour ecrans denses
            // (Huawei Nova 3i / EMUI 6.3" zoomes : les 8 langues etaient
            //  coupees, "Dii" et "Fermer" inaccessibles).
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEachIndexed { index, (label, selected) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) Primary.copy(alpha = 0.1f)
                                else Color.Transparent
                            )
                            .clickable { onSelect(index) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelect(index) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Primary,
                                unselectedColor = if (isDark) Color(0xFF6E6B7B) else TextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            label,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Primary
                                   else if (isDark) DarkTextPrimary else TextPrimary
                        )
                    }
                    if (index < options.lastIndex) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.common_close),
                    color = Primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun DayLifeExportDialog(
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onExportEmail: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val dialogBg = if (isDark) Color(0xFF1A1A2E) else Color.White

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Exporter mes données",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (isDark) DarkTextPrimary else TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportOptionCard(
                    icon = Icons.Default.TableChart,
                    iconBg = Color(0xFF10B981),
                    title = "Export CSV",
                    subtitle = "Tableur compatible Excel, Google Sheets",
                    isDark = isDark,
                    onClick = onExportCsv
                )
                ExportOptionCard(
                    icon = Icons.Default.PictureAsPdf,
                    iconBg = Color(0xFFEF4444),
                    title = "Export PDF",
                    subtitle = "Rapport médical formaté avec graphiques",
                    isDark = isDark,
                    onClick = onExportPdf
                )
                ExportOptionCard(
                    icon = Icons.Default.Email,
                    iconBg = Color(0xFF3B82F6),
                    title = "Envoyer par email",
                    subtitle = "Partager directement avec votre médecin",
                    isDark = isDark,
                    onClick = onExportEmail
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Fermer",
                    color = Primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun ExportOptionCard(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color(0xFF252540) else Color(0xFFF7F8FC))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = iconBg,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (isDark) DarkTextPrimary else TextPrimary
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = if (isDark) DarkTextSecondary else TextSecondary
            )
        }
    }
}

@Composable
private fun DayLifeTargetDialog(
    currentMin: Double,
    currentMax: Double,
    onConfirm: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val dialogBg = if (isDark) Color(0xFF1A1A2E) else Color.White
    var minText by remember { mutableStateOf(currentMin.toInt().toString()) }
    var maxText by remember { mutableStateOf(currentMax.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Objectif glycémique",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (isDark) DarkTextPrimary else TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Définissez votre plage cible (mg/dL)",
                    fontSize = 14.sp,
                    color = if (isDark) DarkTextSecondary else TextSecondary
                )
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.settings_target_min)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.settings_target_max)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val min = minText.toDoubleOrNull() ?: 70.0
                    val max = maxText.toDoubleOrNull() ?: 180.0
                    onConfirm(min.coerceIn(40.0, 200.0), max.coerceIn(100.0, 400.0))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.common_register))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Primary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

// ─── Verrouillage app : chooser de methode ─────────────────────────────────
@Composable
private fun AppLockMethodChooser(
    current: com.diabeto.security.AppLockMethod,
    isDark: Boolean,
    onPick: (com.diabeto.security.AppLockMethod) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1A1A2E) else Color.White,
        shape = RoundedCornerShape(20.dp),
        title = { Text(stringResource(R.string.settings_lock_method_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Choisissez comment vous souhaitez deverrouiller DiaSmart.",
                    fontSize = 13.sp,
                    color = if (isDark) DarkTextSecondary else TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                MethodChoice(
                    icon = Icons.Default.Fingerprint,
                    label = "Empreinte digitale",
                    description = "Capteur biometrique du telephone (rapide)",
                    selected = current == com.diabeto.security.AppLockMethod.BIOMETRIC,
                    onClick = { onPick(com.diabeto.security.AppLockMethod.BIOMETRIC) }
                )
                MethodChoice(
                    icon = Icons.Default.Pin,
                    label = "Code PIN",
                    description = "4 chiffres",
                    selected = current == com.diabeto.security.AppLockMethod.PIN,
                    onClick = { onPick(com.diabeto.security.AppLockMethod.PIN) }
                )
                MethodChoice(
                    icon = Icons.Default.Password,
                    label = "Mot de passe",
                    description = "Lettres, chiffres, caracteres speciaux (8+ caracteres)",
                    selected = current == com.diabeto.security.AppLockMethod.PASSWORD,
                    onClick = { onPick(com.diabeto.security.AppLockMethod.PASSWORD) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Primary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun MethodChoice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Primary else Color.Gray.copy(alpha = 0.25f)
        ),
        color = if (selected) Primary.copy(alpha = 0.08f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(description, fontSize = 12.sp, color = TextSecondary)
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Primary)
            }
        }
    }
}

// ─── Verrouillage app : setup PIN / mot de passe ──────────────────────────
@Composable
private fun AppLockSecretSetup(
    method: com.diabeto.security.AppLockMethod,
    isDark: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val isPin = method == com.diabeto.security.AppLockMethod.PIN
    val title = if (isPin) "Configurer un code PIN" else "Configurer un mot de passe"
    val description = if (isPin) {
        "Saisissez 4 chiffres puis confirmez-les."
    } else {
        "Au moins 8 caracteres, dont au moins une lettre, un chiffre et un caractere special."
    }
    val isStrong: Boolean = if (isPin) {
        first.length == 4 && first.all { it.isDigit() }
    } else {
        first.length >= 8 &&
            first.any { it.isLetter() } &&
            first.any { it.isDigit() } &&
            first.any { !it.isLetterOrDigit() }
    }
    val matches = first == second && first.isNotEmpty()
    val canConfirm = isStrong && matches

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1A1A2E) else Color.White,
        shape = RoundedCornerShape(20.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(description, fontSize = 13.sp, color = if (isDark) DarkTextSecondary else TextSecondary)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = first,
                    onValueChange = { v ->
                        first = if (isPin) v.filter { it.isDigit() }.take(4) else v
                    },
                    label = { Text(if (isPin) "Code PIN" else "Mot de passe") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPin) KeyboardType.NumberPassword else KeyboardType.Password
                    ),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = second,
                    onValueChange = { v ->
                        second = if (isPin) v.filter { it.isDigit() }.take(4) else v
                    },
                    label = { Text(stringResource(R.string.settings_confirm)) },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPin) KeyboardType.NumberPassword else KeyboardType.Password
                    ),
                    isError = second.isNotEmpty() && !matches,
                    supportingText = {
                        when {
                            !isStrong && first.isNotEmpty() -> Text(
                                if (isPin) "Le PIN doit contenir 4 chiffres."
                                else "Mot de passe trop faible : 8+ caracteres avec lettre, chiffre et caractere special.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                            second.isNotEmpty() && !matches -> Text(
                                "Les deux saisies ne correspondent pas.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                            canConfirm -> Text(
                                "Tout est bon, vous pouvez confirmer.",
                                color = Color(0xFF10B981),
                                fontSize = 11.sp
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canConfirm) onConfirm(first) },
                enabled = canConfirm
            ) { Text(stringResource(R.string.settings_confirm), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
