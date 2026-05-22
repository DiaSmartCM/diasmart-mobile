package com.diabeto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diabeto.data.model.UserRole
import kotlinx.coroutines.launch

/**
 * Overlay tutorial affiche au PREMIER lancement du Dashboard (apres login).
 *
 * 4 ecrans pour les patients, 3 pour les medecins. Slider horizontal avec
 * boutons "Passer" et "Suivant" / "C'est parti".
 *
 * Une fois vu/skipped, la preference est sauvegardee dans DataStore et
 * l'overlay ne reapparait jamais (sauf reinstall ou reset preferences).
 *
 * Pour reset en dev : Settings → "Reset onboarding" (a ajouter si besoin).
 */

data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val tip: String
)

@Composable
fun OnboardingDashboardOverlay(
    role: UserRole,
    onFinish: () -> Unit
) {
    val steps = if (role == UserRole.MEDECIN) medecinSteps else patientSteps
    val pagerState = rememberPagerState(initialPage = 0) { steps.size }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar : Skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinish) {
                    Text("Passer", color = Color.White.copy(alpha = 0.7f))
                }
            }

            Spacer(Modifier.height(24.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val step = steps[page]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            step.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(
                        step.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        step.description,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "💡 ${step.tip}",
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Indicateurs de page (dots)
            Row(
                modifier = Modifier.padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(steps.size) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == pagerState.currentPage) 12.dp else 8.dp)
                            .background(
                                if (i == pagerState.currentPage) Color.White
                                else Color.White.copy(alpha = 0.3f),
                                RoundedCornerShape(50)
                            )
                    )
                }
            }

            // Bouton Suivant / C'est parti
            val isLast = pagerState.currentPage == steps.size - 1
            Button(
                onClick = {
                    if (isLast) {
                        onFinish()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    if (isLast) "C'est parti !" else "Suivant",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

private val patientSteps = listOf(
    OnboardingStep(
        icon = Icons.Filled.MonitorHeart,
        title = "Suis ta glycémie",
        description = "Saisis tes mesures quotidiennes. DiaSmart genère ton historique, des courbes et une estimation d'HbA1c automatiquement.",
        tip = "Vise 4 à 6 mesures par jour pour un suivi optimal."
    ),
    OnboardingStep(
        icon = Icons.Filled.Chat,
        title = "Parle à ROLLY",
        description = "Notre assistant IA spécialisé en diabétologie répond à tes questions, analyse tes repas et alerte en cas d'urgence — en français, pidgin, ewondo, duala, fulfulde, arabe.",
        tip = "Pour une urgence, tape simplement \"malaise\" ou \"vertige\" — les numéros SAMU s'affichent immédiatement."
    ),
    OnboardingStep(
        icon = Icons.Filled.Share,
        title = "Partage avec ton médecin",
        description = "Autorise un médecin à voir tes données depuis l'écran \"Mon médecin\". Il pourra consulter ton dossier et te suivre à distance.",
        tip = "Tu gardes le contrôle : tu peux retirer le partage à tout moment."
    ),
    OnboardingStep(
        icon = Icons.Filled.Groups,
        title = "Rejoins la communauté",
        description = "Echange avec d'autres patients diabétiques camerounais. Conseils, expériences, soutien — tu n'es pas seul.",
        tip = "Reste anonyme si tu préfères : aucun nom de famille requis."
    )
)

private val medecinSteps = listOf(
    OnboardingStep(
        icon = Icons.Filled.Groups,
        title = "Tes patients en un coup d'œil",
        description = "Liste \"Mes patients\" : tous ceux qui t'ont autorisé voient leurs glycémies, HbA1c, repas et médicaments synchronisés.",
        tip = "Demande à ton patient d'activer le partage depuis son onglet \"Mon médecin\"."
    ),
    OnboardingStep(
        icon = Icons.Filled.HealthAndSafety,
        title = "Génère ordonnances et comptes-rendus",
        description = "Depuis l'écran Rapports, tu peux créer un compte-rendu de consultation ou une ordonnance PDF et l'envoyer directement au patient.",
        tip = "L'envoi se fait par messagerie in-app + email + WhatsApp (au choix)."
    ),
    OnboardingStep(
        icon = Icons.Filled.Chat,
        title = "Téléconsultation intégrée",
        description = "Appel vidéo ou audio depuis la messagerie. Pas de tier service externe — tout reste dans DiaSmart.",
        tip = "Vérifie ton micro/caméra dans Settings → Permissions avant le premier appel."
    )
)
