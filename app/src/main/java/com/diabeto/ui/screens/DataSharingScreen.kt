package com.diabeto.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.diabeto.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.model.GeoUtils
import com.diabeto.data.model.UserProfile
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.DataSharingViewModel
import com.diabeto.ui.viewmodel.DoctorListItem
import com.diabeto.ui.viewmodel.DoctorSortMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSharingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPatientDetail: (Long) -> Unit = {},
    viewModel: DataSharingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMedecinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.data_sharing_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Retour")
                    }
                },
                colors = diaSmartTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Info carte
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = Primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.data_sharing_control), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "Vous décidez quelles données partager avec votre médecin. Vous pouvez révoquer l'accès à tout moment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Bouton ajouter un médecin
            if (uiState.isPatient) {
                item {
                    Button(
                        onClick = { showMedecinDialog = true; viewModel.loadMedecins() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.data_sharing_share_with_doctor))
                    }
                }
            }

            // Consentements actifs
            if (uiState.consents.isNotEmpty()) {
                item {
                    Text(
                        if (uiState.isPatient) "Médecins ayant accès" else "Patients partagés",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(uiState.consents) { consent ->
                    Card(
                        Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (consent.isActive) Success.copy(alpha = 0.15f) else Error.copy(alpha = 0.15f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (uiState.isPatient) Icons.Default.LocalHospital else Icons.Default.Person,
                                            null,
                                            tint = if (consent.isActive) Success else Error,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        if (uiState.isPatient) consent.medecinNom else consent.patientNom,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (consent.isActive) "Accès actif" else "Accès révoqué",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (consent.isActive) Success else Error
                                    )
                                    // Badges données partagées
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (consent.shareGlucose) DataBadge("Glycémie")
                                        if (consent.shareHbA1c) DataBadge("HbA1c")
                                        if (consent.shareMedications) DataBadge("Médicaments")
                                    }
                                }
                            }
                            if (uiState.isPatient && consent.isActive) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Bouton "Noter ce medecin" — ouvre le dialog de notation
                                    IconButton(
                                        onClick = {
                                            viewModel.openRateDoctor(
                                                UserProfile(
                                                    uid = consent.medecinUid,
                                                    nom = consent.medecinNom
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.StarRate,
                                            contentDescription = "Noter ce médecin",
                                            tint = Color(0xFFF59E0B)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.revokeConsent(consent.medecinUid) }
                                    ) {
                                        Icon(Icons.Default.Close, "Révoquer", tint = Error)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                null,
                                Modifier.size(48.dp),
                                tint = OnSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (uiState.isPatient) "Aucun partage actif" else "Aucun patient n'a partagé ses données",
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                if (uiState.isPatient) "Partagez vos données avec votre médecin pour un meilleur suivi"
                                else "Les patients peuvent partager leurs données depuis leur application",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Option export (patient)
            if (uiState.isPatient) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Autres options", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                item {
                    val context = LocalContext.current
                    Card(
                        Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Envoyer un rapport",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                "Exportez vos données sous forme de fichier à envoyer à votre médecin",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.generateExportData("csv")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.TableChart, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export CSV", fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.generateExportData("text")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Description, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Rapport texte", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // Dialog sélection médecin — version enrichie (notes + distance + tri)
    if (showMedecinDialog) {
        DoctorPickerDialog(
            items = uiState.availableMedecins,
            sortMode = uiState.sortMode,
            hasPatientLocation = uiState.patientLat != null,
            onSelectSort = viewModel::setSortMode,
            onCaptureLocation = viewModel::captureMyLocation,
            onSelect = { medecin ->
                viewModel.grantConsent(medecin.uid)
                showMedecinDialog = false
            },
            onShowReviews = { viewModel.openDoctorReviews(it) },
            onDismiss = { showMedecinDialog = false }
        )
    }

    // Dialog de notation d'un medecin
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

    // Dialog de consultation des avis
    uiState.reviewsTarget?.let { doctor ->
        DoctorReviewsDialog(
            doctor = doctor,
            reviews = uiState.reviewsList,
            isLoading = uiState.isLoadingReviews,
            onDismiss = viewModel::closeDoctorReviews
        )
    }

    // Handle export sharing
    val context = LocalContext.current
    LaunchedEffect(uiState.exportData) {
        uiState.exportData?.let { data ->
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, data)
                putExtra(Intent.EXTRA_SUBJECT, "DiaSmart - Rapport de données")
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Envoyer le rapport"))
            viewModel.clearExportData()
        }
    }
}

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

// ══════════════════════════════════════════════════════════════════
//  Doctor picker dialog — ratings + distance + sort
// ══════════════════════════════════════════════════════════════════

@Composable
private fun DoctorPickerDialog(
    items: List<DoctorListItem>,
    sortMode: DoctorSortMode,
    hasPatientLocation: Boolean,
    onSelectSort: (DoctorSortMode) -> Unit,
    onCaptureLocation: () -> Unit,
    onSelect: (UserProfile) -> Unit,
    onShowReviews: (UserProfile) -> Unit,
    onDismiss: () -> Unit
) {
    // Demande la permission GPS si besoin, puis declenche la capture
    val locPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) onCaptureLocation()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.data_sharing_choose_doctor))
                Text(
                    "Les mieux notés et les plus proches en premier",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }
        },
        text = {
            if (items.isEmpty()) {
                Text(stringResource(R.string.data_sharing_no_doctor), color = OnSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Barre de tri
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SortChip(
                            label = "Meilleure note",
                            selected = sortMode == DoctorSortMode.SCORE,
                            onClick = { onSelectSort(DoctorSortMode.SCORE) },
                            modifier = Modifier.weight(1f)
                        )
                        SortChip(
                            label = if (hasPatientLocation) "Proximité" else "📍 Activer GPS",
                            selected = sortMode == DoctorSortMode.DISTANCE,
                            onClick = {
                                if (hasPatientLocation) {
                                    onSelectSort(DoctorSortMode.DISTANCE)
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

                    Spacer(Modifier.height(4.dp))

                    // Liste des medecins (scrollable en cas de debordement)
                    Column(
                        Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(items) { item ->
                                DoctorCard(
                                    item = item,
                                    onClick = { onSelect(item.profile) },
                                    onShowReviews = { onShowReviews(item.profile) }
                                )
                            }
                        }
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
        border = if (selected) null
        else androidx.compose.foundation.BorderStroke(1.dp, OnSurfaceVariant.copy(alpha = 0.15f))
    ) {
        Text(
            label,
            color = if (selected) Color.White else OnSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp).fillMaxWidth()
        )
    }
}

@Composable
private fun DoctorCard(
    item: DoctorListItem,
    onClick: () -> Unit,
    onShowReviews: () -> Unit
) {
    val d = item.profile
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceVariant,
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocalHospital,
                null,
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.nomComplet.ifBlank { d.email }, fontWeight = FontWeight.SemiBold)
                if (d.specialite.isNotBlank()) {
                    Text(
                        d.specialite,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
                // Ligne note + avis + distance
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (d.reviewCount > 0) {
                        StarRatingDisplay(d.averageRating, size = 14)
                        Text(
                            "%.1f (%d)".format(d.averageRating, d.reviewCount),
                            fontSize = 12.sp,
                            color = OnSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            "Aucun avis",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant
                        )
                    }
                    item.distanceKm?.let {
                        Text("•", fontSize = 12.sp, color = OnSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Place,
                                null,
                                modifier = Modifier.size(12.dp),
                                tint = OnSurfaceVariant
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                GeoUtils.formatDistance(it),
                                fontSize = 12.sp,
                                color = OnSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                if (d.ville.isNotBlank()) {
                    Text(
                        d.ville,
                        fontSize = 11.sp,
                        color = OnSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (d.reviewCount > 0) {
                    TextButton(
                        onClick = onShowReviews,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.RateReview,
                            null,
                            tint = Primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Voir les avis",
                            fontSize = 12.sp,
                            color = Primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/** Affiche 5 etoiles remplies/vides selon le rating (0.0 a 5.0). */
@Composable
private fun StarRatingDisplay(rating: Double, size: Int = 16) {
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
                // Selecteur d'etoiles interactif
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
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
                    ),
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(if (existingRating != null) "Mettre à jour" else "Publier")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Annuler")
            }
        }
    )
}

// ══════════════════════════════════════════════════════════════════
//  Doctor reviews dialog — lecture seule, liste des avis patients
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
                    isLoading -> {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    reviews.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Aucun avis publié pour le moment.",
                                color = OnSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(reviews) { r -> ReviewItemCard(r) }
                        }
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
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
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
                Text(
                    review.comment,
                    fontSize = 13.sp,
                    color = OnSurfaceVariant
                )
            }
            // Date "il y a X jours" simplifiee
            val daysAgo = ((System.currentTimeMillis() - review.createdAt.toDate().time) /
                    (1000L * 60 * 60 * 24)).toInt()
            val dateLabel = when {
                daysAgo <= 0 -> "Aujourd'hui"
                daysAgo == 1 -> "Hier"
                daysAgo < 30 -> "Il y a $daysAgo j"
                daysAgo < 365 -> "Il y a ${daysAgo / 30} mois"
                else -> "Il y a ${daysAgo / 365} an(s)"
            }
            Spacer(Modifier.height(4.dp))
            Text(
                dateLabel,
                fontSize = 11.sp,
                color = OnSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
