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
import androidx.compose.ui.res.stringResource
import com.diabeto.R
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
    val steps = if (role == UserRole.MEDECIN) medecinSteps() else patientSteps()
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
                    Text(stringResource(R.string.onb_skip), color = Color.White.copy(alpha = 0.7f))
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
                    if (isLast) stringResource(R.string.onb_lets_go) else stringResource(R.string.onb_next),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun patientSteps() = listOf(
    OnboardingStep(
        icon = Icons.Filled.MonitorHeart,
        title = stringResource(R.string.onb_glucose_title),
        description = stringResource(R.string.onb_glucose_title),  // titre court suffit, on garde la simplicite
        tip = stringResource(R.string.onb_glucose_tip)
    ),
    OnboardingStep(
        icon = Icons.Filled.Chat,
        title = stringResource(R.string.onb_rolly_title),
        description = stringResource(R.string.onb_rolly_tip),
        tip = stringResource(R.string.onb_rolly_tip)
    ),
    OnboardingStep(
        icon = Icons.Filled.Share,
        title = stringResource(R.string.onb_doctor_title),
        description = stringResource(R.string.onb_doctor_desc),
        tip = stringResource(R.string.onb_doctor_desc)
    ),
    OnboardingStep(
        icon = Icons.Filled.Groups,
        title = stringResource(R.string.onb_community_title),
        description = stringResource(R.string.onb_community_title),
        tip = stringResource(R.string.onb_community_title)
    )
)

@Composable
private fun medecinSteps() = listOf(
    OnboardingStep(
        icon = Icons.Filled.Groups,
        title = stringResource(R.string.onb_med_view_title),
        description = stringResource(R.string.onb_med_view_desc),
        tip = stringResource(R.string.onb_med_view_desc)
    ),
    OnboardingStep(
        icon = Icons.Filled.HealthAndSafety,
        title = stringResource(R.string.card_report_title),
        description = stringResource(R.string.card_report_subtitle),
        tip = stringResource(R.string.card_report_subtitle)
    ),
    OnboardingStep(
        icon = Icons.Filled.Chat,
        title = stringResource(R.string.card_messaging_title),
        description = stringResource(R.string.card_messaging_subtitle),
        tip = stringResource(R.string.card_messaging_subtitle)
    )
)
