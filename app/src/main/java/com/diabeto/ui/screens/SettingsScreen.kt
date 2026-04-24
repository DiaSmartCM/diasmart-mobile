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
import com.diabeto.data.repository.LocalAIStatus
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
    var showTargetDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }

    // Launcher pour selectionner un document (PDF, image) a partager avec un patient
    val shareDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Document medical DiaSmart")
                putExtra(Intent.EXTRA_TEXT, "Bonjour,\n\nVeuillez trouver ci-joint un document (diagnostic, resultats, ordonnance) transmis par votre medecin via DiaSmart.\n\nCordialement")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Envoyer au patient"))
        }
    }

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
                            contentDescription = "Retour",
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
                    title = "Apparence",
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
                            ThemeMode.SYSTEM -> "Système"
                            ThemeMode.LIGHT -> "Clair"
                            ThemeMode.DARK -> "Sombre"
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
                    title = "Mesures & Unités",
                    color = sectionTextColor,
                    isDark = isDark
                )
            }
            item {
                DayLifeSettingsCard(cardBg = cardBg) {
                    DayLifeSettingsItem(
                        icon = Icons.Default.Straighten,
                        iconBg = Color(0xFF3B82F6),
                        title = "Unité de glycémie",
                        subtitle = uiState.glucoseUnit.label,
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        onClick = { showUnitDialog = true }
                    )
                    DayLifeDivider(dividerColor)
                    DayLifeSettingsItem(
                        icon = Icons.Default.MonitorHeart,
                        iconBg = Color(0xFFEF4444),
                        title = "Type de mesure",
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
                            title = "Objectif glycémique",
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
                        subtitle = "Activer les notifications push",
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
                            title = "Rappels médicaments",
                            subtitle = "Rappel avant chaque prise",
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
                            title = "Rappels glycémie",
                            subtitle = "Rappel de mesure quotidien",
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
                        title = "Rappels rendez-vous",
                        subtitle = "Rappel 1h avant le RDV",
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
            item {
                DayLifeSectionHeader(
                    title = "Export & Données",
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
                            title = "Exporter mes données",
                            subtitle = "CSV, PDF — Glycémie, repas, médicaments",
                            titleColor = titleColor,
                            subtitleColor = subtitleColor,
                            onClick = { showExportDialog = true }
                        )
                        DayLifeDivider(dividerColor)
                    }
                    DayLifeSettingsItem(
                        icon = Icons.Default.CloudSync,
                        iconBg = Color(0xFF3B82F6),
                        title = "Sauvegarde cloud",
                        subtitle = if (isBackingUp) "Sauvegarde en cours..." else "Sauvegarder maintenant",
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

            // ── Partage medecin -> patient (medecin uniquement) ─
            if (uiState.isMedecin) {
                item {
                    DayLifeSectionHeader(
                        title = "Partage avec un patient",
                        color = sectionTextColor,
                        isDark = isDark
                    )
                }
                item {
                    DayLifeSettingsCard(cardBg = cardBg) {
                        DayLifeSettingsItem(
                            icon = Icons.Default.PictureAsPdf,
                            iconBg = Color(0xFFEF4444),
                            title = "Envoyer un PDF au patient",
                            subtitle = "Diagnostic, examens, ordonnance",
                            titleColor = titleColor,
                            subtitleColor = subtitleColor,
                            onClick = {
                                shareDocumentLauncher.launch(arrayOf("application/pdf"))
                            }
                        )
                        DayLifeDivider(dividerColor)
                        DayLifeSettingsItem(
                            icon = Icons.Default.Image,
                            iconBg = Color(0xFF8B5CF6),
                            title = "Envoyer une image au patient",
                            subtitle = "Resultats d'examens, radiographie",
                            titleColor = titleColor,
                            subtitleColor = subtitleColor,
                            onClick = {
                                shareDocumentLauncher.launch(arrayOf("image/*"))
                            }
                        )
                        DayLifeDivider(dividerColor)
                        DayLifeSettingsItem(
                            icon = Icons.Default.AttachFile,
                            iconBg = Color(0xFF3B82F6),
                            title = "Envoyer un autre document",
                            subtitle = "Tout type de fichier",
                            titleColor = titleColor,
                            subtitleColor = subtitleColor,
                            onClick = {
                                shareDocumentLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }
            }

            // ── IA hors-ligne (patient uniquement) ───────────
            if (!uiState.isMedecin) {
            item {
                DayLifeSectionHeader(
                    title = "IA hors-ligne",
                    color = sectionTextColor,
                    isDark = isDark
                )
            }
            item {
                DayLifeSettingsCard(cardBg = cardBg) {
                    // Status display
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
                                .background(
                                    when (uiState.localAIStatus) {
                                        LocalAIStatus.READY -> Color(0xFF22C55E)
                                        LocalAIStatus.ERROR -> Color(0xFFEF4444)
                                        else -> Color(0xFF6771E4)
                                    }.copy(alpha = 0.12f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                when (uiState.localAIStatus) {
                                    LocalAIStatus.READY -> Icons.Default.CheckCircle
                                    LocalAIStatus.ERROR -> Icons.Default.ErrorOutline
                                    LocalAIStatus.DOWNLOADED -> Icons.Default.DownloadDone
                                    LocalAIStatus.NOT_DOWNLOADED -> Icons.Default.CloudDownload
                                },
                                contentDescription = "Statut IA",
                                tint = when (uiState.localAIStatus) {
                                    LocalAIStatus.READY -> Color(0xFF22C55E)
                                    LocalAIStatus.ERROR -> Color(0xFFEF4444)
                                    else -> Color(0xFF6771E4)
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Modèle Gemma 3 (1B)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = titleColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                when (uiState.localAIStatus) {
                                    LocalAIStatus.NOT_DOWNLOADED -> "Non installé • ~${uiState.modelSizeMB} Mo"
                                    LocalAIStatus.DOWNLOADED -> "Installé • Prêt à charger"
                                    LocalAIStatus.READY -> "Actif • Mode hors-ligne disponible"
                                    LocalAIStatus.ERROR -> "Erreur d'initialisation"
                                },
                                fontSize = 13.sp,
                                color = when (uiState.localAIStatus) {
                                    LocalAIStatus.READY -> Color(0xFF22C55E)
                                    LocalAIStatus.ERROR -> Color(0xFFEF4444)
                                    else -> subtitleColor
                                },
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Download progress bar
                    if (uiState.isDownloadingModel) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { uiState.modelDownloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF6771E4),
                                trackColor = if (isDark) DarkOutline else Color(0xFFF0EFF5),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Téléchargement... ${(uiState.modelDownloadProgress * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = subtitleColor
                            )
                        }
                    }

                    // Error message
                    uiState.downloadError?.let { error ->
                        Text(
                            "Erreur : $error",
                            fontSize = 12.sp,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    DayLifeDivider(dividerColor)

                    // Action buttons based on status
                    when (uiState.localAIStatus) {
                        LocalAIStatus.NOT_DOWNLOADED -> {
                            DayLifeSettingsItem(
                                icon = Icons.Default.Download,
                                iconBg = Color(0xFF6771E4),
                                title = "Télécharger le modèle",
                                subtitle = "~${uiState.modelSizeMB} Mo • Wi-Fi recommandé",
                                titleColor = titleColor,
                                subtitleColor = subtitleColor,
                                onClick = { viewModel.downloadLocalModel() }
                            )
                        }
                        LocalAIStatus.DOWNLOADED -> {
                            DayLifeSettingsItem(
                                icon = Icons.Default.PlayArrow,
                                iconBg = Color(0xFF22C55E),
                                title = "Activer le modèle",
                                subtitle = "Charger en mémoire pour utilisation",
                                titleColor = titleColor,
                                subtitleColor = subtitleColor,
                                onClick = { viewModel.initLocalModel() }
                            )
                            DayLifeDivider(dividerColor)
                            DayLifeSettingsItem(
                                icon = Icons.Default.Delete,
                                iconBg = Color(0xFFEF4444),
                                title = "Supprimer le modèle",
                                subtitle = "Libérer ~${uiState.modelSizeMB} Mo d'espace",
                                titleColor = titleColor,
                                subtitleColor = subtitleColor,
                                onClick = { viewModel.deleteLocalModel() }
                            )
                        }
                        LocalAIStatus.READY -> {
                            DayLifeSettingsItem(
                                icon = Icons.Default.Delete,
                                iconBg = Color(0xFFEF4444),
                                title = "Supprimer le modèle",
                                subtitle = "Libérer ~${uiState.modelSizeMB} Mo d'espace",
                                titleColor = titleColor,
                                subtitleColor = subtitleColor,
                                onClick = { viewModel.deleteLocalModel() }
                            )
                        }
                        LocalAIStatus.ERROR -> {
                            DayLifeSettingsItem(
                                icon = Icons.Default.Refresh,
                                iconBg = Color(0xFFFF8C42),
                                title = "Réessayer l'initialisation",
                                subtitle = "Recharger le modèle local",
                                titleColor = titleColor,
                                subtitleColor = subtitleColor,
                                onClick = { viewModel.initLocalModel() }
                            )
                            DayLifeDivider(dividerColor)
                            DayLifeSettingsItem(
                                icon = Icons.Default.Delete,
                                iconBg = Color(0xFFEF4444),
                                title = "Supprimer et retélécharger",
                                subtitle = "Libérer ~${uiState.modelSizeMB} Mo d'espace",
                                titleColor = titleColor,
                                subtitleColor = subtitleColor,
                                onClick = { viewModel.deleteLocalModel() }
                            )
                        }
                    }
                }
            }
            } // fin if (!uiState.isMedecin) - IA hors-ligne gatee pour patient uniquement

            // ── Compte ──────────────────────────────────────
            item {
                DayLifeSectionHeader(
                    title = "Compte",
                    color = sectionTextColor,
                    isDark = isDark
                )
            }
            item {
                DayLifeSettingsCard(cardBg = cardBg) {
                    DayLifeSettingsItem(
                        icon = Icons.Default.PersonOff,
                        iconBg = Color(0xFFEF4444),
                        title = "Supprimer mon compte",
                        subtitle = if (isDeletingAccount) "Suppression en cours..." else "Efface profil et partages Firestore",
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
                    title = "À propos",
                    color = sectionTextColor,
                    isDark = isDark
                )
            }
            item {
                DayLifeSettingsCard(cardBg = cardBg) {
                    DayLifeInfoItem(
                        icon = Icons.Default.Info,
                        iconBg = Color(0xFF6771E4),
                        title = "Version",
                        subtitle = "2.1.15",
                        titleColor = titleColor,
                        subtitleColor = subtitleColor
                    )
                    DayLifeDivider(dividerColor)
                    DayLifeInfoItem(
                        icon = Icons.Default.LocalHospital,
                        iconBg = Color(0xFFEF4444),
                        title = "DiaSmart",
                        subtitle = "Diabétologie Intelligente",
                        titleColor = titleColor,
                        subtitleColor = subtitleColor
                    )
                    DayLifeDivider(dividerColor)
                    DayLifeInfoItem(
                        icon = Icons.Default.Email,
                        iconBg = Color(0xFFFF8C42),
                        title = "Contact",
                        subtitle = "ngostheo30@gmail.com",
                        titleColor = titleColor,
                        subtitleColor = subtitleColor
                    )
                }
            }

            // ── Légal ────────────────────────────────────────
            item {
                DayLifeSectionHeader(
                    title = "Légal",
                    color = sectionTextColor,
                    isDark = isDark
                )
            }
            item {
                DayLifeSettingsCard(cardBg = cardBg) {
                    DayLifeSettingsItem(
                        icon = Icons.Default.Policy,
                        iconBg = Color(0xFF6771E4),
                        title = "Politique de confidentialité",
                        subtitle = "RGPD - Protection des données",
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
                        title = "Licence",
                        subtitle = "Licence propriétaire - NGOS THEODORE",
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
                        title = "Conditions d'utilisation",
                        subtitle = "Termes et conditions",
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
            title = "Choisir le thème",
            options = ThemeMode.entries.map { mode ->
                when (mode) {
                    ThemeMode.SYSTEM -> "Système (automatique)" to (uiState.themeMode == mode)
                    ThemeMode.LIGHT -> "Mode clair" to (uiState.themeMode == mode)
                    ThemeMode.DARK -> "Mode sombre" to (uiState.themeMode == mode)
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
            title = "Choisir la langue",
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
            title = "Unité de glycémie",
            options = GlucoseUnit.entries.map { unit ->
                val desc = when (unit) {
                    GlucoseUnit.MG_DL -> "mg/dL (milligrammes par décilitre)"
                    GlucoseUnit.MMOL_L -> "mmol/L (millimoles par litre)"
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
            title = "Type de mesure",
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
                Text(
                    "Cette action est definitive. Votre profil, vos consentements de partage et votre compte seront supprimes. Les medecins ne pourront plus vous trouver sur la plateforme.",
                    fontSize = 14.sp,
                    color = if (isDark) DarkTextSecondary else TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        isDeletingAccount = true
                        viewModel.deleteMyAccount { ok, err ->
                            isDeletingAccount = false
                            if (ok) {
                                Toast.makeText(context, "Compte supprime", Toast.LENGTH_LONG).show()
                                // Redemarrage : relancer l'activite pour retomber sur Login
                                val pm = context.packageManager
                                val intent = pm.getLaunchIntentForPackage(context.packageName)
                                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "Erreur : ${err ?: "inconnue"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Annuler", color = Primary, fontWeight = FontWeight.SemiBold)
                }
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
            Column {
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
                    "Fermer",
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
                    label = { Text("Minimum (mg/dL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it.filter { c -> c.isDigit() } },
                    label = { Text("Maximum (mg/dL)") },
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
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Primary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
