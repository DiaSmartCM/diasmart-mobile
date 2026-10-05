package com.diabeto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.diabeto.data.repository.AuthRepository
import com.diabeto.data.repository.DataSharingRepository
import com.diabeto.data.model.UserProfile
import com.diabeto.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ══════════════════════════════════════════════════════════════════
//  ViewModel
// ══════════════════════════════════════════════════════════════════

data class SharedPatientUiState(
    val isLoading: Boolean = true,
    val patientProfile: UserProfile? = null,
    val glucoseData: List<Map<String, Any?>> = emptyList(),
    val tensionData: List<Map<String, Any?>> = emptyList(),
    val repasData: List<Map<String, Any?>> = emptyList(),
    val objectifTension: com.diabeto.domain.ObjectifTension? = null,
    val messageObjectif: String? = null,
    val error: String? = null
)

@HiltViewModel
class SharedPatientDataViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dataSharingRepository: DataSharingRepository,
    private val authRepository: AuthRepository,
    private val objectifRepository: com.diabeto.data.repository.ObjectifTensionRepository
) : ViewModel() {

    private val patientUid: String = savedStateHandle.get<String>("patientUid") ?: ""

    private val _uiState = MutableStateFlow(SharedPatientUiState())
    val uiState: StateFlow<SharedPatientUiState> = _uiState.asStateFlow()

    init {
        loadPatientData()
    }

    private fun loadPatientData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val profile = authRepository.getUserProfile(patientUid)
                val glucose = dataSharingRepository.getPatientGlucoseData(patientUid)
                val repas = dataSharingRepository.getPatientRepasData(patientUid)
                val tensions = dataSharingRepository.getPatientTensionData(patientUid)
                val objectif = objectifRepository.lire(patientUid)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        patientProfile = profile,
                        glucoseData = glucose,
                        tensionData = tensions,
                        objectifTension = objectif,
                        repasData = repas
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refresh() {
        loadPatientData()
    }

    /** Objectif de tension personnel du patient ; null pour revenir a l'objectif general. */
    fun fixerObjectifTension(systolique: Int?, diastolique: Int?) {
        viewModelScope.launch {
            val r = if (systolique == null || diastolique == null) objectifRepository.retirer(patientUid)
                else objectifRepository.fixer(patientUid, systolique, diastolique)
            r.onSuccess {
                _uiState.update { it.copy(
                    objectifTension = if (systolique != null && diastolique != null)
                        com.diabeto.domain.ObjectifTension(systolique, diastolique) else null,
                    messageObjectif = if (systolique != null) "Objectif enregistré : le patient le voit dans son suivi." else "Objectif retiré."
                ) }
            }.onFailure { e -> _uiState.update { it.copy(messageObjectif = e.message ?: "Enregistrement impossible") } }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  Screen
// ══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPatientDataScreen(
    patientUid: String,
    patientNom: String,
    onNavigateBack: () -> Unit,
    onNavigateToRendezVous: () -> Unit = {},
    viewModel: SharedPatientDataViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    patientNom.take(2).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = Primary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(patientNom, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Donnees partagees", fontSize = 11.sp, color = OnSurfaceVariant)
                        }
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
        SharedPatientDataContent(
            modifier = Modifier.padding(padding),
            onNavigateToRendezVous = onNavigateToRendezVous,
            viewModel = viewModel
        )
    }
}

/**
 * Synthese des donnees partagees par le patient (profil, glycemies, repas).
 * Sert d'ecran autonome et de premier onglet du dossier numerique.
 */
@Composable
fun SharedPatientDataContent(
    modifier: Modifier = Modifier,
    onNavigateToRendezVous: () -> Unit = {},
    viewModel: SharedPatientDataViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    run {
        if (uiState.isLoading) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // ── Profil patient ──
                item {
                    uiState.patientProfile?.let { profile ->
                        PatientProfileCard(profile)
                    }
                }

                // ── Actions rapides ──
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionChipCard(
                            icon = Icons.Default.CalendarToday,
                            label = "Creer un RDV",
                            color = StatusGreen,
                            onClick = onNavigateToRendezVous,
                            modifier = Modifier.weight(1f)
                        )
                        ActionChipCard(
                            icon = Icons.Default.Refresh,
                            label = "Actualiser",
                            color = Primary,
                            onClick = { viewModel.refresh() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── Glycemie ──
                item {
                    SectionHeader("Glycemie", Icons.Default.MonitorHeart, uiState.glucoseData.size)
                }

                if (uiState.glucoseData.isEmpty()) {
                    item {
                        EmptyDataCard("Aucune donnee de glycemie partagee")
                    }
                } else {
                    items(
                        uiState.glucoseData.take(20),
                        key = { (it["id"] as? String) ?: (it["dateHeure"]?.toString() ?: it.hashCode().toString()) }
                    ) { glucose ->
                        GlucoseDataCard(glucose)
                    }
                }

                // ── Tension ──
                item {
                    SectionHeader("Tension", Icons.Default.Favorite, uiState.tensionData.size)
                }
                item {
                    ObjectifTensionCard(uiState.objectifTension, uiState.messageObjectif, viewModel::fixerObjectifTension)
                }
                if (uiState.tensionData.isEmpty()) {
                    item { EmptyDataCard("Aucune mesure de tension partagee") }
                } else {
                    item { TensionResumeCard(uiState.tensionData) }
                    items(uiState.tensionData.take(15)) { t -> TensionDataCard(t) }
                }

                // ── Repas ──
                item {
                    SectionHeader("Analyse de repas", Icons.Default.Restaurant, uiState.repasData.size)
                }

                if (uiState.repasData.isEmpty()) {
                    item {
                        EmptyDataCard("Aucune donnee de repas partagee")
                    }
                } else {
                    items(
                        uiState.repasData.take(20),
                        key = { (it["id"] as? String) ?: (it["dateHeure"]?.toString() ?: it.hashCode().toString()) }
                    ) { repas ->
                        RepasDataCard(repas)
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  Composants
// ══════════════════════════════════════════════════════════════════

@Composable
private fun PatientProfileCard(profile: UserProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Primary.copy(alpha = 0.2f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            profile.nomComplet.take(2).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                            fontSize = 20.sp
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        profile.nomComplet.ifBlank { "Patient" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(profile.email, color = OnSurfaceVariant, fontSize = 13.sp)
                    if (profile.telephone.isNotBlank()) {
                        Text(profile.telephone, color = OnSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // Morphometric data if available
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                profile.poids?.let {
                    MorphoItem("Poids", "${it.toInt()} kg")
                }
                profile.taille?.let {
                    MorphoItem("Taille", "${it.toInt()} cm")
                }
                if (profile.poids != null && profile.taille != null && profile.taille!! > 0) {
                    val bmi = profile.poids!! / ((profile.taille!! / 100.0) * (profile.taille!! / 100.0))
                    MorphoItem("IMC", "%.1f".format(bmi))
                }
                profile.tourDeTaille?.let {
                    MorphoItem("Tour taille", "${it.toInt()} cm")
                }
            }
        }
    }
}

@Composable
private fun MorphoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Primary)
        Text(label, fontSize = 11.sp, color = OnSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Primary.copy(alpha = 0.15f)
        ) {
            Text(
                "$count entrees",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = Primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ObjectifTensionCard(
    objectif: com.diabeto.domain.ObjectifTension?,
    message: String?,
    enregistrer: (Int?, Int?) -> Unit
) {
    var sys by remember(objectif) { mutableStateOf(objectif?.systolique?.toString() ?: "130") }
    var dia by remember(objectif) { mutableStateOf(objectif?.diastolique?.toString() ?: "80") }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Objectif de tension du patient", fontWeight = FontWeight.SemiBold)
            Text(objectif?.let { "Objectif personnel : ${it.texte}" } ?: "Objectif général : < 130/80 mmHg (ADA 2025 / ESC 2024)",
                fontSize = 12.sp, color = OnSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(sys, { sys = it.filter(Char::isDigit).take(3) }, label = { Text("PAS <") },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                OutlinedTextField(dia, { dia = it.filter(Char::isDigit).take(3) }, label = { Text("PAD <") },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { enregistrer(sys.toIntOrNull() ?: 0, dia.toIntOrNull() ?: 0) }) { Text("Enregistrer") }
                if (objectif != null) OutlinedButton(onClick = { enregistrer(null, null) }) { Text("Revenir au général") }
            }
            message?.let { Text(it, fontSize = 12.sp, color = OnSurfaceVariant) }
        }
    }
}

@Composable
private fun TensionResumeCard(data: List<Map<String, Any?>>) {
    val depuis = java.time.LocalDateTime.now().minusDays(30).toString()
    val recentes = data.filter { (it["dateHeure"] as? String ?: "") >= depuis }.mapNotNull { m ->
        val s = (m["systolique"] as? Number)?.toInt(); val d = (m["diastolique"] as? Number)?.toInt()
        if (s != null && d != null) s to d else null
    }
    if (recentes.isEmpty()) return
    val sys = recentes.map { it.first }.average().toInt()
    val dia = recentes.map { it.second }.average().toInt()
    val cat = com.diabeto.domain.ReglesTension.categorie(sys, dia)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cat.couleur().copy(alpha = 0.08f))) {
        Column(Modifier.padding(12.dp)) {
            Text("Moyenne 30 jours : $sys/$dia mmHg (${recentes.size} mesures)", fontWeight = FontWeight.SemiBold)
            Text("Pression pulsée moyenne : ${sys - dia} mmHg · PAM ${com.diabeto.domain.ReglesTension.pam(sys, dia)}" +
                (if (data.firstOrNull()?.get("traitementAntihypertenseur") == true) " · traitement antihypertenseur en cours" else ""),
                fontSize = 12.sp)
            Text(cat.libelle + " · aide au suivi, pas un diagnostic", fontSize = 12.sp, color = cat.couleur())
            val mesures = data.mapNotNull { versMesureTension(it) }
            com.diabeto.domain.ReglesTension.testsOrthostatiques(mesures).firstOrNull()?.let { o ->
                Text("Dernier test couché/debout : baisse ${o.baissePas}/${o.baissePad} mmHg" +
                    if (o.positif) " · hypotension orthostatique possible" else " · normal",
                    fontSize = 12.sp, color = if (o.positif) Color(0xFFF57C00) else OnSurfaceVariant)
            }
            if (com.diabeto.domain.ReglesTension.tachycardiePersistante(mesures))
                Text("Tachycardie de repos persistante : neuropathie autonome possible", fontSize = 12.sp, color = Color(0xFFF57C00))
            if (sys - dia > 60)
                Text("Pression pulsée > 60 : rigidité artérielle possible", fontSize = 12.sp, color = Color(0xFFF57C00))
        }
    }
}

private fun versMesureTension(m: Map<String, Any?>): com.diabeto.domain.MesureTension? {
    val s = (m["systolique"] as? Number)?.toInt() ?: return null
    val d = (m["diastolique"] as? Number)?.toInt() ?: return null
    val date = (m["dateHeure"] as? String)?.let { runCatching { kotlinx.datetime.LocalDateTime.parse(it) }.getOrNull() } ?: return null
    return com.diabeto.domain.MesureTension(date, s, d, (m["pouls"] as? Number)?.toInt(),
        m["position"] as? String ?: "", m["bras"] as? String ?: "", m["traitementAntihypertenseur"] as? Boolean)
}

@Composable
private fun TensionDataCard(m: Map<String, Any?>) {
    val s = (m["systolique"] as? Number)?.toInt() ?: return
    val d = (m["diastolique"] as? Number)?.toInt() ?: return
    val cat = com.diabeto.domain.ReglesTension.categorie(s, d)
    val date = (m["dateHeure"] as? String)?.let {
        runCatching { java.time.LocalDateTime.parse(it).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) }.getOrNull()
    } ?: ""
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$s/$d", fontWeight = FontWeight.Bold, color = cat.couleur(), fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("mmHg" + ((m["pouls"] as? Number)?.let { " · FC ${it.toInt()}" } ?: "") +
                    " · PP ${s - d} · PAM ${com.diabeto.domain.ReglesTension.pam(s, d)}" +
                    ((m["position"] as? String)?.let { " · " + com.diabeto.domain.ReglesTension.libellePosition(it) } ?: "") +
                    ((m["bras"] as? String)?.let { ", bras " + com.diabeto.domain.ReglesTension.libelleBras(it) } ?: ""),
                    fontSize = 13.sp)
                Text("${cat.libelle} · $date", fontSize = 12.sp, color = OnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GlucoseDataCard(data: Map<String, Any?>) {
    val value = (data["valeur"] as? Number)?.toDouble()
        ?: (data["value"] as? Number)?.toDouble()
        ?: 0.0
    val date = (data["dateHeure"] as? String)
        ?: (data["date"] as? String)
        ?: (data["timestamp"]?.toString())
        ?: ""
    val context = (data["contexte"] as? String)
        ?: (data["context"] as? String)
        ?: ""

    val glucoseColor = when {
        value < 70 -> StatusRedDark
        value > 180 -> StatusOrange
        else -> StatusGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = glucoseColor.copy(alpha = 0.15f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${value.toInt()}",
                        fontWeight = FontWeight.Bold,
                        color = glucoseColor,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${value.toInt()} mg/dL", fontWeight = FontWeight.SemiBold)
                if (context.isNotBlank()) {
                    Text(context.replace("_", " "), fontSize = 12.sp, color = OnSurfaceVariant)
                }
            }
            if (date.isNotBlank()) {
                Text(
                    date.take(16).replace("T", " "),
                    fontSize = 11.sp,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RepasDataCard(data: Map<String, Any?>) {
    val description = (data["description"] as? String)
        ?: (data["aliment"] as? String)
        ?: "Repas"
    val score = (data["score"] as? Number)?.toInt()
    val glucides = (data["glucides"] as? Number)?.toDouble()
    val ig = (data["indexGlycemique"] as? Number)?.toInt()
        ?: (data["ig"] as? Number)?.toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                description,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                score?.let {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            it >= 7 -> StatusGreen.copy(alpha = 0.15f)
                            it >= 4 -> StatusOrange.copy(alpha = 0.15f)
                            else -> StatusRedDark.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            "Score: $it/10",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                glucides?.let {
                    Text("Glucides: ${it.toInt()}g", fontSize = 12.sp, color = OnSurfaceVariant)
                }
                ig?.let {
                    Text("IG: $it", fontSize = 12.sp, color = OnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EmptyDataCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Info, null, tint = OnSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.width(8.dp))
            Text(text, color = OnSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ActionChipCard(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}
