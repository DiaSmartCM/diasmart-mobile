package com.diabeto.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import com.diabeto.R
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.ProfileSyncViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    profileViewModel: ProfileSyncViewModel = hiltViewModel()
) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Profile state
    var name by remember { mutableStateOf(user?.displayName ?: "") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("patient") }
    // Ne pas utiliser user?.photoUrl qui peut pointer vers un Storage inexistant
    var photoUrl by remember { mutableStateOf("") }
    var taille by remember { mutableStateOf("") }
    var poids by remember { mutableStateOf("") }
    var tourTaille by remember { mutableStateOf("") }
    var masseGrasse by remember { mutableStateOf("") }
    // Champs medecin
    var specialite by remember { mutableStateOf("") }
    var numeroOrdre by remember { mutableStateOf("") }
    var structureSante by remember { mutableStateOf("") }
    var anneesExperience by remember { mutableStateOf("") }
    var modeConsultation by remember { mutableStateOf("") }
    var disponibilite by remember { mutableStateOf("") }
    var joursGarde by remember { mutableStateOf("") }
    var languesParlees by remember { mutableStateOf("") }
    // Localisation (medecin)
    var ville by remember { mutableStateOf("") }
    var adresse by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var isCapturingLocation by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showEditDialog by remember { mutableStateOf(false) }
    // v2.1.82 : la deconnexion efface desormais les dossiers locaux du compte.
    // Elle ne peut plus se declencher d'un seul appui sans avertissement.
    var showLogoutDialog by remember { mutableStateOf(false) }
    var sauvegardeEnCours by remember { mutableStateOf(false) }
    var messageSauvegarde by remember { mutableStateOf<String?>(null) }
    var editField by remember { mutableStateOf("") }
    var editValue by remember { mutableStateOf("") }
    var editTitle by remember { mutableStateOf("") }

    // Load profile from Firestore
    LaunchedEffect(user?.uid) {
        if (user == null) return@LaunchedEffect
        try {
            val doc = db.collection("users").document(user.uid).get().await()
            if (doc.exists()) {
                name = doc.getString("name") ?: doc.getString("nom") ?: user.displayName ?: ""
                phone = doc.getString("phone") ?: doc.getString("telephone") ?: ""
                role = (doc.getString("role") ?: "patient").lowercase()
                taille = doc.getDouble("taille")?.let { if (it > 0) it.toInt().toString() else "" } ?: ""
                poids = doc.getDouble("poids")?.let { if (it > 0) String.format("%.1f", it) else "" } ?: ""
                tourTaille = doc.getDouble("tourTaille")?.let { if (it > 0) it.toInt().toString() else "" } ?: ""
                masseGrasse = doc.getDouble("masseGrasse")?.let { if (it > 0) String.format("%.1f", it) else "" } ?: ""
                // Champs medecin
                specialite = doc.getString("specialite") ?: ""
                numeroOrdre = doc.getString("numeroOrdre") ?: ""
                structureSante = doc.getString("structureSante") ?: ""
                anneesExperience = doc.getLong("anneesExperience")?.toString() ?: ""
                modeConsultation = doc.getString("modeConsultation") ?: ""
                disponibilite = doc.getString("disponibilite") ?: ""
                joursGarde = doc.getString("joursGarde") ?: ""
                languesParlees = doc.getString("languesParlees") ?: ""
                ville = doc.getString("ville") ?: ""
                adresse = doc.getString("adresse") ?: ""
                latitude = doc.getDouble("latitude")
                longitude = doc.getDouble("longitude")
                // Priorité : Firestore base64, sinon Firebase Auth URL (Google/etc.)
                val firestorePhoto = doc.getString("photoURL")
                if (!firestorePhoto.isNullOrBlank()) {
                    photoUrl = firestorePhoto
                } else {
                    // Fallback: utiliser la photo de Firebase Auth (Google profile, etc.)
                    val authPhoto = user.photoUrl?.toString()
                    if (!authPhoto.isNullOrBlank() && !authPhoto.startsWith("gs://")) {
                        photoUrl = authPhoto
                    }
                }
            }
        } catch (_: Exception) {}

        // Si données morpho vides dans Firestore, pré-remplir depuis Room (Patient)
        if (taille.isBlank() || poids.isBlank()) {
            val patient = profileViewModel.getFirstPatient()
            if (patient != null) {
                if (taille.isBlank()) taille = patient.taille?.toInt()?.toString() ?: ""
                if (poids.isBlank()) poids = patient.poids?.let { String.format("%.1f", it) } ?: ""
                if (tourTaille.isBlank()) tourTaille = patient.tourDeTaille?.toInt()?.toString() ?: ""
                if (masseGrasse.isBlank()) masseGrasse = patient.masseGrasse?.let { String.format("%.1f", it) } ?: ""
            }
        }

        isLoading = false
    }

    // Photo picker — resize to 256px and store as base64 in Firestore (no Storage needed)
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isUploadingPhoto = true
            try {
                // Read and resize bitmap
                val inputStream = context.contentResolver.openInputStream(uri)
                val original = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (original == null) {
                    snackbarHostState.showSnackbar("Impossible de lire l'image")
                    return@launch
                }

                val maxSize = 256
                val w: Int; val h: Int
                if (original.width > original.height) {
                    w = maxSize; h = (original.height * maxSize.toFloat() / original.width).toInt()
                } else {
                    h = maxSize; w = (original.width * maxSize.toFloat() / original.height).toInt()
                }
                val resized = Bitmap.createScaledBitmap(original, w, h, true)

                // Convert to base64 data URL
                val baos = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                val dataUrl = "data:image/jpeg;base64,$base64"

                // Save directly in Firestore user document
                user?.let {
                    db.collection("users").document(it.uid)
                        .set(mapOf("photoURL" to dataUrl), SetOptions.merge()).await()
                }
                photoUrl = dataUrl
                snackbarHostState.showSnackbar("Photo mise à jour")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Erreur: ${e.message}")
            } finally {
                isUploadingPhoto = false
            }
        }
    }

    // ── GPS capture (medecin) ─────────────────────────────────────────
    fun launchLocationCapture() {
        isCapturingLocation = true
        profileViewModel.captureMyDoctorLocation { success, message ->
            isCapturingLocation = false
            scope.launch {
                if (success) {
                    // Recharger les valeurs depuis Firestore pour refleter la capture
                    user?.uid?.let { uid ->
                        try {
                            val doc = db.collection("users").document(uid).get().await()
                            ville = doc.getString("ville") ?: ville
                            adresse = doc.getString("adresse") ?: adresse
                            latitude = doc.getDouble("latitude") ?: latitude
                            longitude = doc.getDouble("longitude") ?: longitude
                        } catch (_: Exception) {}
                    }
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            launchLocationCapture()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Permission de localisation refusee")
            }
        }
    }

    fun requestLocationCapture() {
        if (profileViewModel.hasLocationPermission()) {
            launchLocationCapture()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Save field
    fun saveField(field: String, value: String) {
        scope.launch {
            try {
                val uid = user?.uid ?: return@launch
                when (field) {
                    "name" -> {
                        user.updateProfile(userProfileChangeRequest { displayName = value }).await()
                        // v2.1.69 : ecrit dans les 2 champs (name + nom) pour
                        // matcher ProfileScreen.read + UserProfile.fromMap qui
                        // accepte les deux.
                        db.collection("users").document(uid).set(
                            mapOf("name" to value, "nom" to value),
                            SetOptions.merge()
                        ).await()
                        name = value
                    }
                    "phone" -> {
                        // v2.1.69 BUG FIX : on ecrivait dans "phone", mais
                        // UserProfile.fromMap (utilise par ReportsScreen
                        // WhatsApp + tous les listings recipients) lit
                        // "telephone". Resultat : WhatsApp affichait
                        // "numero du destinataire absent" alors que le
                        // numero etait bien saisi. Fix : ecrire dans les 2.
                        db.collection("users").document(uid).set(
                            mapOf("phone" to value, "telephone" to value),
                            SetOptions.merge()
                        ).await()
                        phone = value
                    }
                    "taille" -> {
                        val v = value.toDoubleOrNull() ?: return@launch
                        db.collection("users").document(uid).set(mapOf("taille" to v), SetOptions.merge()).await()
                        taille = value
                    }
                    "poids" -> {
                        val v = value.toDoubleOrNull() ?: return@launch
                        db.collection("users").document(uid).set(mapOf("poids" to v), SetOptions.merge()).await()
                        poids = value
                    }
                    "tourTaille" -> {
                        val v = value.toDoubleOrNull() ?: return@launch
                        db.collection("users").document(uid).set(mapOf("tourTaille" to v), SetOptions.merge()).await()
                        tourTaille = value
                    }
                    "masseGrasse" -> {
                        val v = value.toDoubleOrNull() ?: return@launch
                        db.collection("users").document(uid).set(mapOf("masseGrasse" to v), SetOptions.merge()).await()
                        masseGrasse = value
                    }
                    "specialite" -> {
                        db.collection("users").document(uid).set(mapOf("specialite" to value), SetOptions.merge()).await()
                        specialite = value
                    }
                    "numeroOrdre" -> {
                        db.collection("users").document(uid).set(mapOf("numeroOrdre" to value), SetOptions.merge()).await()
                        numeroOrdre = value
                    }
                    "structureSante" -> {
                        db.collection("users").document(uid).set(mapOf("structureSante" to value), SetOptions.merge()).await()
                        structureSante = value
                    }
                    "anneesExperience" -> {
                        val v = value.toIntOrNull() ?: return@launch
                        db.collection("users").document(uid).set(mapOf("anneesExperience" to v), SetOptions.merge()).await()
                        anneesExperience = value
                    }
                    "modeConsultation" -> {
                        db.collection("users").document(uid).set(mapOf("modeConsultation" to value), SetOptions.merge()).await()
                        modeConsultation = value
                    }
                    "disponibilite" -> {
                        db.collection("users").document(uid).set(mapOf("disponibilite" to value), SetOptions.merge()).await()
                        disponibilite = value
                    }
                    "joursGarde" -> {
                        db.collection("users").document(uid).set(mapOf("joursGarde" to value), SetOptions.merge()).await()
                        joursGarde = value
                    }
                    "languesParlees" -> {
                        db.collection("users").document(uid).set(mapOf("languesParlees" to value), SetOptions.merge()).await()
                        languesParlees = value
                    }
                    "ville" -> {
                        db.collection("users").document(uid).set(mapOf("ville" to value), SetOptions.merge()).await()
                        ville = value
                    }
                    "adresse" -> {
                        db.collection("users").document(uid).set(mapOf("adresse" to value), SetOptions.merge()).await()
                        adresse = value
                    }
                }
                // Synchroniser les données morphométriques vers Room DB (Patient)
                if (field in listOf("taille", "poids", "tourTaille", "masseGrasse")) {
                    profileViewModel.syncMorphoToPatient(field, value.toDoubleOrNull())
                }
                snackbarHostState.showSnackbar("Sauvegardé")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Erreur: ${e.message}")
            }
        }
    }

    // IMC calculation
    val imc = remember(taille, poids) {
        val t = taille.toDoubleOrNull() ?: return@remember null
        val p = poids.toDoubleOrNull() ?: return@remember null
        if (t > 0) String.format("%.1f", p / ((t / 100.0) * (t / 100.0))) else null
    }

    val isDark = LocalIsDarkTheme.current
    val screenBg = if (isDark) DarkBackground else Color(0xFFF7F8FC)
    val cardBg = if (isDark) Color(0xFF1A1A2E) else Color.White
    val headerGradient = listOf(
        if (isDark) Color(0xFF2A2B55) else Color(0xFF6771E4),
        if (isDark) Color(0xFF1A1A3E) else Color(0xFF8B93F0)
    )
    val titleColor = if (isDark) DarkTextPrimary else TextPrimary
    val subtitleColor = if (isDark) DarkTextSecondary else TextSecondary

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(headerGradient))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
                    }
                    Text(stringResource(R.string.profile_screen_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = screenBg
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Photo de profil ──
            // Décoder le base64 si c'est un data URL
            val profileBitmap = remember(photoUrl) {
                if (photoUrl.startsWith("data:image")) {
                    try {
                        val base64Part = photoUrl.substringAfter("base64,")
                        val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (_: Exception) { null }
                } else null
            }

            Box(contentAlignment = Alignment.BottomEnd) {
                if (profileBitmap != null) {
                    // Photo stockée en base64 dans Firestore
                    androidx.compose.foundation.Image(
                        bitmap = profileBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_profile_photo),
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(3.dp, Primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else if (photoUrl.isNotBlank() && !photoUrl.startsWith("data:")) {
                    // Photo URL normale (Google profile, etc.)
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = stringResource(R.string.cd_profile_photo),
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(3.dp, Primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainer)
                            .border(3.dp, Primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name.firstOrNull()?.uppercase() ?: "?",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnPrimaryContainer
                        )
                    }
                }
                if (isUploadingPhoto) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Primary,
                        strokeWidth = 2.dp
                    )
                }
                IconButton(
                    onClick = { if (!isUploadingPhoto) photoLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Primary)
                ) {
                    Icon(Icons.Default.CameraAlt, "Changer photo", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            val isMedecin = role.equals("medecin", ignoreCase = true)
            val displayName = if (isMedecin && name.isNotBlank()) "Dr. $name" else name.ifBlank { "Utilisateur" }
            Text(displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(email.ifBlank { phone }, fontSize = 13.sp, color = OnSurfaceVariant)
            Text(
                if (isMedecin) specialite.ifBlank { "Médecin" } else "Patient",
                fontSize = 12.sp,
                color = Primary,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(20.dp))

            // ── Informations du compte ──
            ProfileSection("Informations du compte", Icons.Default.Person) {
                ProfileField("Nom complet", name.ifBlank { "--" }) {
                    editField = "name"; editTitle = "Modifier le nom"; editValue = name; showEditDialog = true
                }
                ProfileField("Email", email.ifBlank { "--" })
                ProfileField("Téléphone", phone.ifBlank { "Non renseigné" }) {
                    editField = "phone"; editTitle = "Modifier le téléphone"; editValue = phone; showEditDialog = true
                }
                ProfileField(
                    "Compte Google",
                    if (user?.providerData?.any { it.providerId == "google.com" } == true) "Lié" else "Non lié"
                )
            }

            Spacer(Modifier.height(12.dp))

            if (isMedecin) {
                // ── Informations professionnelles ──
                ProfileSection("Informations professionnelles", Icons.Default.LocalHospital) {
                    ProfileField("Spécialité", specialite.ifBlank { "Non renseignée" }) {
                        editField = "specialite"; editTitle = "Spécialité"; editValue = specialite; showEditDialog = true
                    }
                    ProfileField("Numéro d'Ordre (ONMC)", numeroOrdre.ifBlank { "Non renseigné" }) {
                        editField = "numeroOrdre"; editTitle = "Numéro d'inscription à l'Ordre"; editValue = numeroOrdre; showEditDialog = true
                    }
                    ProfileField("Structure de santé", structureSante.ifBlank { "Non renseignée" }) {
                        editField = "structureSante"; editTitle = "Hôpital / Clinique"; editValue = structureSante; showEditDialog = true
                    }
                    ProfileField("Années d'expérience", anneesExperience.ifBlank { "--" }.let { if (it != "--") "$it ans" else it }) {
                        editField = "anneesExperience"; editTitle = "Années d'expérience"; editValue = anneesExperience; showEditDialog = true
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Localisation du cabinet ──
                ProfileSection("Localisation", Icons.Default.LocationOn) {
                    ProfileField("Ville", ville.ifBlank { "Non renseignée" }) {
                        editField = "ville"; editTitle = "Ville"; editValue = ville; showEditDialog = true
                    }
                    ProfileField("Adresse", adresse.ifBlank { "Non renseignée" }) {
                        editField = "adresse"; editTitle = "Adresse du cabinet"; editValue = adresse; showEditDialog = true
                    }
                    val coordsLabel = if (latitude != null && longitude != null) {
                        "%.4f, %.4f".format(latitude, longitude)
                    } else "Non capturées"
                    ProfileField("Coordonnées GPS", coordsLabel)

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { requestLocationCapture() },
                        enabled = !isCapturingLocation,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (isCapturingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.profile_capturing))
                        } else {
                            Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.profile_use_gps))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Votre position aide vos patients à vous trouver à proximité.",
                        fontSize = 11.sp,
                        color = OnSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Paramètres de consultation ──
                ProfileSection("Paramètres de consultation", Icons.Default.MedicalServices) {
                    ProfileField(
                        "Mode de consultation",
                        when (modeConsultation) {
                            "TELECONSULTATION" -> "Téléconsultation"
                            "CABINET" -> "En cabinet"
                            "LES_DEUX" -> "Les deux"
                            else -> "Non défini"
                        }
                    ) {
                        editField = "modeConsultation"; editTitle = "Mode (TELECONSULTATION / CABINET / LES_DEUX)"; editValue = modeConsultation; showEditDialog = true
                    }
                    ProfileField(
                        "Disponibilité",
                        when (disponibilite) {
                            "EN_LIGNE" -> "En ligne"
                            "INDISPONIBLE" -> "Indisponible"
                            "SUR_RDV" -> "Sur rendez-vous"
                            else -> "Non défini"
                        }
                    ) {
                        editField = "disponibilite"; editTitle = "Statut (EN_LIGNE / INDISPONIBLE / SUR_RDV)"; editValue = disponibilite; showEditDialog = true
                    }
                    ProfileField("Jours et heures de garde", joursGarde.ifBlank { "Non renseigné" }) {
                        editField = "joursGarde"; editTitle = "Jours/heures (ex: Lun-Ven 8h-18h)"; editValue = joursGarde; showEditDialog = true
                    }
                    ProfileField("Langues parlées", languesParlees.ifBlank { "Non renseigné" }) {
                        editField = "languesParlees"; editTitle = "Langues (ex: Français, Anglais)"; editValue = languesParlees; showEditDialog = true
                    }
                }
            } else {
                // ── Données morphométriques (PATIENT uniquement) ──
                ProfileSection("Données morphométriques", Icons.Default.FitnessCenter) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MorphoCard("📏", "Taille", taille.ifBlank { "--" }, "cm", Modifier.weight(1f)) {
                            editField = "taille"; editTitle = "Taille (cm)"; editValue = taille; showEditDialog = true
                        }
                        MorphoCard("⚖️", "Poids", poids.ifBlank { "--" }, "kg", Modifier.weight(1f)) {
                            editField = "poids"; editTitle = "Poids (kg)"; editValue = poids; showEditDialog = true
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MorphoCard("⭕", "Tour taille", tourTaille.ifBlank { "--" }, "cm", Modifier.weight(1f)) {
                            editField = "tourTaille"; editTitle = "Tour de taille (cm)"; editValue = tourTaille; showEditDialog = true
                        }
                        MorphoCard("📉", "Masse grasse", masseGrasse.ifBlank { "--" }, "%", Modifier.weight(1f)) {
                            editField = "masseGrasse"; editTitle = "Masse grasse (%)"; editValue = masseGrasse; showEditDialog = true
                        }
                    }
                    if (imc != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "IMC : $imc",
                            fontSize = 14.sp,
                            color = OnSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Déconnexion ──
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Error),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
            ) {
                Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.profile_logout))
            }

            Spacer(Modifier.height(24.dp))
            Text("DiaSmart v${com.diabeto.BuildConfig.VERSION_NAME}", fontSize = 11.sp, color = subtitleColor)
        }
    }

    // ── Edit Dialog ──
    // ═══════════════════════════════════════════════════════════════
    //  Deconnexion — avertissement de sauvegarde
    //
    //  Le bouton de sauvegarde vit DANS le dialogue. Un avertissement qui
    //  renverrait vers un autre ecran ne serait pas suivi : l'utilisateur
    //  qui veut partir ne fait pas un detour.
    // ═══════════════════════════════════════════════════════════════
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { if (!sauvegardeEnCours) showLogoutDialog = false },
            shape = RoundedCornerShape(24.dp),
            icon = { Icon(Icons.Default.CloudUpload, null, tint = Primary) },
            title = { Text("Sauvegarder avant de partir ?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "En vous deconnectant, les dossiers enregistres sur ce " +
                        "telephone seront effaces. C'est ce qui empeche le compte " +
                        "suivant d'y acceder.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Les donnees deja sauvegardees dans le cloud seront " +
                        "restaurees a votre prochaine connexion. Celles qui ne " +
                        "l'ont jamais ete seront perdues.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                    messageSauvegarde?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Primary)
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            sauvegardeEnCours = true
                            messageSauvegarde = null
                            profileViewModel.sauvegarderAvantDeconnexion { ok, msg ->
                                sauvegardeEnCours = false
                                messageSauvegarde = if (ok) "Sauvegarde terminee — $msg" else "Echec : $msg"
                            }
                        },
                        enabled = !sauvegardeEnCours,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (sauvegardeEnCours) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Sauvegarde en cours...")
                        } else {
                            Icon(Icons.Default.CloudUpload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sauvegarder maintenant")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        profileViewModel.purgerPuisDeconnecter { onNavigateBack() }
                    },
                    enabled = !sauvegardeEnCours,
                    colors = ButtonDefaults.textButtonColors(contentColor = Error)
                ) { Text("Se deconnecter") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    enabled = !sauvegardeEnCours
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(editTitle) },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (editField) {
                            in listOf("taille", "poids", "tourTaille", "masseGrasse") -> KeyboardType.Decimal
                            "anneesExperience" -> KeyboardType.Number
                            else -> KeyboardType.Text
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    saveField(editField, editValue)
                    showEditDialog = false
                }) { Text(stringResource(R.string.common_register)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun ProfileSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val cardBg = if (isDark) Color(0xFF1A1A2E) else Color.White
    val sectionColor = if (isDark) Color(0xFF8B93F0) else Primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDark) Color(0xFF2A2A40) else Color(0xFFF0EFF5)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(sectionColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = sectionColor, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    title.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = sectionColor,
                    letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ProfileField(label: String, value: String, onEdit: (() -> Unit)? = null) {
    val isDark = LocalIsDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 12.sp,
                color = if (isDark) DarkTextSecondary else OnSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) DarkTextPrimary else TextPrimary
            )
        }
        if (onEdit != null) {
            TextButton(
                onClick = onEdit,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.common_modify), fontSize = 13.sp, color = Primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    HorizontalDivider(
        color = if (isDark) Color(0xFF2A2A40) else Color(0xFFF0EFF5),
        thickness = 0.5.dp
    )
}

@Composable
private fun MorphoCard(
    emoji: String,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF252540) else Color(0xFFF7F8FC)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDark) Color(0xFF2A2A40) else Color(0xFFEDEBF5)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) DarkTextPrimary else TextPrimary
            )
            Text(
                "$label ($unit)",
                fontSize = 11.sp,
                color = if (isDark) DarkTextSecondary else OnSurfaceVariant
            )
        }
    }
}
