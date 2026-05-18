package com.diabeto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.model.DoctorReview
import com.diabeto.data.repository.DoctorReviewRepository
import com.diabeto.ui.theme.OnSurfaceVariant
import com.diabeto.ui.theme.SurfaceVariant
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * "Mes avis" — vue MEDECIN, lecture seule.
 *
 * Affiche la note moyenne globale du medecin connecte + la liste de tous les
 * avis recus. Aucun bouton d'edition / suppression : un medecin ne peut pas
 * modifier l'avis qu'un patient lui a laisse.
 */

data class MesAvisUiState(
    val isLoading: Boolean = true,
    val averageRating: Double = 0.0,
    val reviewCount: Int = 0,
    val reviews: List<DoctorReview> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class MesAvisViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val reviewRepo: DoctorReviewRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MesAvisUiState())
    val uiState: StateFlow<MesAvisUiState> = _uiState.asStateFlow()

    init { reload() }

    fun reload() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Non connecte") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // 1) compteurs agreges sur users/{uid}
                val userDoc = firestore.collection("users").document(uid).get().await()
                val ratingSum = (userDoc.get("ratingSum") as? Number)?.toDouble() ?: 0.0
                val reviewCount = (userDoc.get("reviewCount") as? Number)?.toInt() ?: 0
                val average = if (reviewCount > 0) ratingSum / reviewCount else 0.0
                // 2) liste detaillee
                val list = reviewRepo.getReviewsForDoctor(uid, limit = 100)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        averageRating = average,
                        reviewCount = reviewCount,
                        reviews = list,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Erreur de chargement")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MesAvisScreen(
    onNavigateBack: () -> Unit,
    viewModel: MesAvisViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes avis") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ── En-tete : moyenne globale + nombre d'avis ──
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Note moyenne",
                        fontSize = 13.sp,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    if (ui.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    } else {
                        Text(
                            "%.1f / 5".format(ui.averageRating),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        StarRatingDisplay(rating = ui.averageRating, size = 22)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${ui.reviewCount} avis reçu${if (ui.reviewCount > 1) "s" else ""}",
                            fontSize = 13.sp,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Avertissement lecture seule ──
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Lecture seule — les avis sont laisses par vos patients et ne peuvent pas etre modifies.",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                "Avis reçus",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            // ── Liste des avis ──
            when {
                ui.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                ui.error != null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Erreur : ${ui.error}",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                ui.reviews.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Aucun avis publié pour le moment.",
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ui.reviews, key = { it.id }) { r -> MesAvisItemCard(r) }
                }
            }
        }
    }
}

@Composable
private fun MesAvisItemCard(review: DoctorReview) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
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
