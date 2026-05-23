package com.diabeto.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.model.ConsentStatus
import com.diabeto.data.model.DataSharingConsent
import com.diabeto.data.model.GeoUtils
import com.diabeto.data.model.UserProfile
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.DataSharingViewModel
import com.diabeto.ui.viewmodel.DataSharingUiState
import com.diabeto.ui.viewmodel.DoctorListItem
import com.diabeto.ui.viewmodel.DoctorSortMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSharingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPatientDetail: (Long) -> Unit = {},
    initialTab: Int = 0,
    viewModel: DataSharingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Permission GPS pour tri par proximité
    val locPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) viewModel.captureMyLocation()
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(Unit) {
        // Charge la liste des médecins dès l'ouverture pour l'onglet Médecin
        viewModel.loadMedecins()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isPatient) "Médecin" else "Mes patients") },
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
        if (uiState.isPatient) {
            PatientDoctorView(
                modifier = Modifier.padding(padding),
                uiState = uiState,
                viewModel = viewModel,
                locPermLauncher = locPermLauncher,
                initialTab = initialTab
            )
        } else {
            DoctorPatientsView(
                modifier = Modifier.padding(padding),
                uiState = uiState,
                onUnlinkPatient = viewModel::unlinkPatient,
                onReactivateLink = viewModel::reactivateLink
            )
        }
    }

    // Dialog de notation d'un médecin (patient → médecin traitant)
    uiState.ratingTarget?.let { doctor ->
        RateDoctorDialog(
            doctor = doctor,
            existingRating = uiState.myExistingReview?.rating,
            existingComment = uiState.myExistingReview?.comment.orEmpty(),
            isSubmitting = uiState.isSubmittingReview,
            onSubmit = { rating, comment -> viewModel.submitReview(rating, comment) },
            onDismiss = viewModel::closeRateDoctor
        )
    }

    // Dialog consultation avis
    uiState.reviewsTarget?.let { doctor ->
        DoctorReviewsDialog(
            doctor = doctor,
            reviews = uiState.reviewsList,
            isLoading = uiState.isLoadingReviews,
            onDismiss = viewModel::closeDoctorReviews
        )
    }
}

// ══════════════════════════════════════════════════════════════════
//  VUE PATIENT — onglets Médecin | Mon Médecin
// ══════════════════════════════════════════════════════════════════

@Composable
private fun PatientDoctorView(
    modifier: Modifier = Modifier,
    uiState: DataSharingUiState,
    viewModel: DataSharingViewModel,
    locPermLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    initialTab: Int = 0
) {
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }

    Column(modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = Primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Médecin", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                icon = { Icon(Icons.Default.LocalHospital, null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Mon Médecin", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                icon = { Icon(Icons.Default.Favorite, null, modifier = Modifier.size(18.dp)) }
            )
        }

        when (selectedTab) {
            0 -> MedecinTabContent(uiState, viewModel, locPermLauncher)
            1 -> MonMedecinTabContent(uiState, viewModel)
        }
    }
}

// ─── Onglet 1 : Répertoire de tous les médecins ─────────────────────────────

@Composable
private fun MedecinTabContent(
    uiState: DataSharingUiState,
    viewModel: DataSharingViewModel,
    locPermLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    Column(Modifier.fillMaxSize()) {
        // Barre de tri
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortChip(
                label = "Meilleure note",
                selected = uiState.sortMode == DoctorSortMode.SCORE,
                onClick = { viewModel.setSortMode(DoctorSortMode.SCORE) },
                modifier = Modifier.weight(1f)
            )
            SortChip(
                label = if (uiState.patientLat != null) "Proximité" else "📍 GPS",
                selected = uiState.sortMode == DoctorSortMode.DISTANCE,
                onClick = {
                    if (uiState.patientLat != null) {
                        viewModel.setSortMode(DoctorSortMode.DISTANCE)
                    } else {
                        locPermLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (uiState.availableMedecins.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.SearchOff, null,
                    Modifier.size(56.dp), tint = OnSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Aucun médecin disponible",
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.availableMedecins, key = { it.profile.uid }) { item ->
                    BrowseDoctorCard(
                        item = item,
                        onShowReviews = { viewModel.openDoctorReviews(item.profile) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun BrowseDoctorCard(
    item: DoctorListItem,
    onShowReviews: () -> Unit
) {
    val d = item.profile
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar
            Surface(
                shape = CircleShape,
                color = Primary.copy(alpha = 0.12f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LocalHospital, null, tint = Primary, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.nomComplet.ifBlank { d.email }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (d.specialite.isNotBlank()) {
                    Text(d.specialite, fontSize = 13.sp, color = OnSurfaceVariant)
                }
                // Étoiles sous le nom
                Spacer(Modifier.height(5.dp))
                StarRatingDisplay(d.averageRating, size = 15)
                Spacer(Modifier.height(2.dp))
                if (d.reviewCount > 0) {
                    Text(
                        "%.1f / 5  ·  %d avis".format(d.averageRating, d.reviewCount),
                        fontSize = 12.sp,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text("Pas encore d'avis", fontSize = 12.sp, color = OnSurfaceVariant)
                }
                // Distance + ville
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item.distanceKm?.let {
                        Icon(Icons.Default.Place, null, Modifier.size(13.dp), tint = OnSurfaceVariant)
                        Text(GeoUtils.formatDistance(it), fontSize = 12.sp, color = OnSurfaceVariant)
                    }
                    if (d.ville.isNotBlank()) {
                        if (item.distanceKm != null) Text("·", fontSize = 12.sp, color = OnSurfaceVariant)
                        Text(d.ville, fontSize = 12.sp, color = OnSurfaceVariant)
                    }
                }
                if (d.reviewCount > 0) {
                    TextButton(
                        onClick = onShowReviews,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.RateReview, null, tint = Primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Voir les avis", fontSize = 12.sp, color = Primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─── Onglet 2 : Mon Médecin traitant ─────────────────────────────────────────

@Composable
private fun MonMedecinTabContent(
    uiState: DataSharingUiState,
    viewModel: DataSharingViewModel
) {
    val treatingDoctors = uiState.consents.filter {
        it.isActive && it.status == ConsentStatus.ACCEPTED
    }
    val pendingRequests = uiState.consents.filter {
        it.status == ConsentStatus.PENDING
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Demandes en attente
        if (pendingRequests.isNotEmpty()) {
            item {
                Text(
                    "Demandes d'accès en attente",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Warning
                )
            }
            items(pendingRequests, key = { it.documentId }) { consent ->
                PatientPendingRequestCard(
                    consent = consent,
                    onAccept = { viewModel.acceptRequest(consent.medecinUid) },
                    onReject = { viewModel.rejectRequest(consent.medecinUid) }
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
        }

        if (treatingDoctors.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.FavoriteBorder, null,
                        Modifier.size(64.dp), tint = OnSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Aucun médecin traitant", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Votre médecin peut vous envoyer une demande d'accès depuis son application DiaSmart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            item {
                Text(
                    "Mes médecins traitants",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            items(treatingDoctors, key = { it.medecinUid }) { consent ->
                TreatingDoctorCard(
                    consent = consent,
                    onRateDoctor = {
                        viewModel.openRateDoctor(UserProfile(uid = consent.medecinUid, nom = consent.medecinNom))
                    },
                    onRevoke = {
                        viewModel.revokeConsent(consent.medecinUid)
                    }
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TreatingDoctorCard(
    consent: DataSharingConsent,
    onRateDoctor: () -> Unit,
    onRevoke: () -> Unit
) {
    // v2.1.60 : dialogue de confirmation pour revocation cote patient
    // (avant, seul le medecin pouvait revoquer — desequilibre RGPD).
    var showRevokeDialog by remember { mutableStateOf(false) }

    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            icon = { Icon(Icons.Default.LinkOff, null, tint = StatusRedDark) },
            title = { Text("Révoquer l'accès ?") },
            text = {
                Text(
                    "Dr. ${consent.medecinNom} n'aura plus accès à vos données médicales. " +
                    "Vos données restent dans votre compte. Vous pourrez le réinviter plus tard."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRevoke()
                    showRevokeDialog = false
                }) {
                    Text("Révoquer", color = StatusRedDark, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) { Text("Annuler") }
            }
        )
    }

    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.06f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Primary.copy(alpha = 0.15f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LocalHospital, null, tint = Primary, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(consent.medecinNom, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = StatusGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        "Médecin traitant",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        color = StatusGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                // Badges données partagées
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (consent.shareGlucose) DataBadge("Glycémie")
                    if (consent.shareHbA1c) DataBadge("HbA1c")
                    if (consent.shareMedications) DataBadge("Médicaments")
                }
            }
            // Bouton noter
            IconButton(onClick = onRateDoctor) {
                Icon(Icons.Default.StarRate, "Noter", tint = Color(0xFFF59E0B))
            }
            // v2.1.60 : bouton revoquer (parite RGPD avec le medecin)
            IconButton(onClick = { showRevokeDialog = true }) {
                Icon(Icons.Default.LinkOff, "Révoquer l'accès", tint = StatusRedDark)
            }
        }
    }
}

@Composable
private fun PatientPendingRequestCard(
    consent: DataSharingConsent,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Warning.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MedicalServices, null, tint = Warning, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Dr. ${consent.medecinNom}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Souhaite accéder à vos données", fontSize = 12.sp, color = OnSurfaceVariant)
            }
            IconButton(onClick = onAccept, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.CheckCircle, "Accepter", tint = StatusGreen)
            }
            IconButton(onClick = onReject, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Cancel, "Refuser", tint = StatusRedDark)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  VUE MÉDECIN — ses patients partagés (lecture seule, visuel)
// ══════════════════════════════════════════════════════════════════

@Composable
private fun DoctorPatientsView(
    modifier: Modifier = Modifier,
    uiState: DataSharingUiState,
    onUnlinkPatient: (String) -> Unit = {},
    onReactivateLink: (String) -> Unit = {}
) {
    val activePatients = uiState.consents.filter { it.isActive }
    val revokedPatients = uiState.consents.filter { !it.isActive }

    var pendingUnlink by remember { mutableStateOf<DataSharingConsent?>(null) }
    var pendingReactivate by remember { mutableStateOf<DataSharingConsent?>(null) }

    // ── Dialogs de confirmation ──
    pendingUnlink?.let { consent ->
        AlertDialog(
            onDismissRequest = { pendingUnlink = null },
            icon = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Se desabonner ?") },
            text = {
                Text(
                    "Vous ne verrez plus les donnees de ${consent.patientNom}. " +
                        "Le patient devra reactiver le partage ou vous pourrez reactiver le lien depuis la section \"Acces revoques\". " +
                        "Aucune donnee n'est supprimee."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUnlinkPatient(consent.patientUid)
                        pendingUnlink = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Se desabonner") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnlink = null }) { Text("Annuler") }
            }
        )
    }
    pendingReactivate?.let { consent ->
        AlertDialog(
            onDismissRequest = { pendingReactivate = null },
            icon = { Icon(Icons.Default.LinkOff, null, tint = StatusGreen) },
            title = { Text("Reactiver l'acces ?") },
            text = {
                Text(
                    "Reactiver le lien avec ${consent.patientNom} ? " +
                        "Vous pourrez de nouveau consulter ses donnees. " +
                        "Le patient peut a tout moment revoquer ce lien."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onReactivateLink(consent.patientUid)
                    pendingReactivate = null
                }) { Text("Reactiver") }
            },
            dismissButton = {
                TextButton(onClick = { pendingReactivate = null }) { Text("Annuler") }
            }
        )
    }

    if (activePatients.isEmpty() && revokedPatients.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.PeopleOutline, null, Modifier.size(64.dp), tint = OnSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(16.dp))
            Text("Aucun patient partagé", fontWeight = FontWeight.SemiBold)
            Text(
                "Les patients peuvent partager leurs données depuis l'onglet Médecin de leur application.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (activePatients.isNotEmpty()) {
                item {
                    Text(
                        "Patients actifs (${activePatients.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                items(activePatients, key = { "active-${it.patientUid}" }) { consent ->
                    Card(
                        Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = Primary.copy(alpha = 0.15f), modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        consent.patientNom.take(2).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = Primary,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(consent.patientNom, fontWeight = FontWeight.SemiBold)
                                Text("Acces actif", fontSize = 12.sp, color = StatusGreen)
                            }
                            // v2.1.45 : bouton "se desabonner"
                            IconButton(onClick = { pendingUnlink = consent }) {
                                Icon(
                                    Icons.Default.PersonRemove,
                                    contentDescription = "Se desabonner",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            if (revokedPatients.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    Text(
                        "Acces revoques (${revokedPatients.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = OnSurfaceVariant
                    )
                }
                items(revokedPatients, key = { "revoked-${it.patientUid}" }) { consent ->
                    Card(
                        Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(0.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = OnSurfaceVariant.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        consent.patientNom.take(2).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurfaceVariant,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(consent.patientNom, fontSize = 14.sp, color = OnSurfaceVariant)
                                Text("Acces revoque", fontSize = 11.sp, color = OnSurfaceVariant.copy(alpha = 0.7f))
                            }
                            TextButton(onClick = { pendingReactivate = consent }) {
                                Text("Reactiver", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  Composants partagés
// ══════════════════════════════════════════════════════════════════

@Composable
private fun DataBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Primary.copy(alpha = 0.1f)
    ) {
        Text(
            label,
            fontSize = 9.sp,
            color = Primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Primary else SurfaceVariant,
        border = if (!selected)
            androidx.compose.foundation.BorderStroke(1.dp, OnSurfaceVariant.copy(alpha = 0.15f))
        else null
    ) {
        Text(
            label,
            color = if (selected) Color.White else OnSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 10.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
internal fun StarRatingDisplay(rating: Double, size: Int = 16) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val filled = rating >= i - 0.25
            val half = !filled && rating >= i - 0.75
            Icon(
                when {
                    filled -> Icons.Default.Star
                    half -> Icons.Default.StarHalf
                    else -> Icons.Default.StarBorder
                },
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(size.dp)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  Rate doctor dialog
// ══════════════════════════════════════════════════════════════════

@Composable
private fun RateDoctorDialog(
    doctor: UserProfile,
    existingRating: Int?,
    existingComment: String,
    isSubmitting: Boolean,
    onSubmit: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRating by remember(existingRating) { mutableIntStateOf(existingRating ?: 5) }
    var comment by remember(existingComment) { mutableStateOf(existingComment) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Column {
                Text(
                    if (existingRating != null) "Modifier mon avis" else "Noter ce médecin",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Dr. ${doctor.nomComplet}".ifBlank { doctor.email },
                    fontSize = 13.sp,
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (i in 1..5) {
                        IconButton(
                            onClick = { selectedRating = i },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                if (i <= selectedRating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$i étoile${if (i > 1) "s" else ""}",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
                Text(
                    when (selectedRating) {
                        1 -> "Très insatisfait"
                        2 -> "Insatisfait"
                        3 -> "Correct"
                        4 -> "Satisfait"
                        5 -> "Excellent"
                        else -> ""
                    },
                    fontSize = 14.sp,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= 500) comment = it },
                    label = { Text("Commentaire (optionnel)") },
                    placeholder = { Text("Votre expérience, accueil, suivi...") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${comment.length} / 500",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedRating, comment.trim()) },
                enabled = !isSubmitting,
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(if (existingRating != null) "Mettre à jour" else "Publier")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Annuler") }
        }
    )
}

// ══════════════════════════════════════════════════════════════════
//  Doctor reviews dialog
// ══════════════════════════════════════════════════════════════════

@Composable
private fun DoctorReviewsDialog(
    doctor: UserProfile,
    reviews: List<com.diabeto.data.model.DoctorReview>,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "Avis sur Dr. ${doctor.nomComplet}".ifBlank { "Avis patients" },
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    StarRatingDisplay(doctor.averageRating, size = 16)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "%.1f / 5 · %d avis".format(doctor.averageRating, doctor.reviewCount),
                        fontSize = 13.sp,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        text = {
            Column(Modifier.heightIn(min = 120.dp, max = 480.dp)) {
                when {
                    isLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    reviews.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Aucun avis publié pour le moment.", color = OnSurfaceVariant, textAlign = TextAlign.Center)
                    }
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(reviews, key = { it.id }) { r -> ReviewItemCard(r) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

@Composable
private fun ReviewItemCard(review: com.diabeto.data.model.DoctorReview) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    review.patientNom.ifBlank { "Patient" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                StarRatingDisplay(review.rating.toDouble(), size = 13)
            }
            if (review.comment.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(review.comment, fontSize = 13.sp, color = OnSurfaceVariant)
            }
            val daysAgo = ((System.currentTimeMillis() - review.createdAt.toDate().time) / (1000L * 60 * 60 * 24)).toInt()
            val dateLabel = when {
                daysAgo <= 0 -> "Aujourd'hui"
                daysAgo == 1 -> "Hier"
                daysAgo < 30 -> "Il y a $daysAgo j"
                daysAgo < 365 -> "Il y a ${daysAgo / 30} mois"
                else -> "Il y a ${daysAgo / 365} an(s)"
            }
            Spacer(Modifier.height(4.dp))
            Text(dateLabel, fontSize = 11.sp, color = OnSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}
