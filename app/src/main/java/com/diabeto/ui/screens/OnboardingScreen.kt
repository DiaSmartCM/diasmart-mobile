package com.diabeto.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diabeto.R
import com.diabeto.ui.theme.*
import kotlinx.coroutines.launch

private val DarkBg = Color(0xFF0D0B2E)
private val DarkBg2 = Color(0xFF1A1452)
private val Cyan = Primary
private val Teal = HeaderGradientStart
private val Mint = Tertiary

data class OnboardingPage(
    val icon: String,
    val title: String,
    val subtitle: String,
    val features: List<String>,
    val accentColor: Color
)

@Composable
private fun pages() = listOf(
    OnboardingPage(
        icon = "\uD83E\uDE78",  // 🩸
        title = stringResource(R.string.onb_t1),
        subtitle = stringResource(R.string.onb_s1),
        features = listOf(
            "\uD83D\uDCC8  Suivi glycemique en temps reel",
            "\uD83D\uDC8A  Gestion des medicaments",
            "\uD83C\uDF7D\uFE0F  Analyse nutritionnelle par IA",
            "\uD83D\uDEB6  Podometre integre"
        ),
        accentColor = Cyan
    ),
    OnboardingPage(
        icon = "\uD83E\uDD16",  // 🤖
        title = stringResource(R.string.onb_t2),
        subtitle = stringResource(R.string.onb_s2),
        features = listOf(
            "\uD83D\uDCAC  Reponses personnalisees",
            "\uD83D\uDCC9  Predictions glycemiques a 6h",
            "\u26A0\uFE0F  Alertes intelligentes",
            "\uD83D\uDCCB  Rapports detailles"
        ),
        accentColor = Primary
    ),
    OnboardingPage(
        icon = "\uD83D\uDC65",  // 👥
        title = stringResource(R.string.onb_t3),
        subtitle = stringResource(R.string.onb_s3),
        features = listOf(
            "\uD83D\uDCE9  Messagerie medecin-patient",
            "\uD83D\uDD14  Rappels intelligents",
            "\uD83D\uDD12  Donnees chiffrees et privees",
            "\uD83C\uDF10  Widget sur ecran d'accueil"
        ),
        accentColor = Mint
    )
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val pageList = pages()
    val pagerState = rememberPagerState(pageCount = { pageList.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBg, DarkBg2, DarkBg)
                )
            )
    ) {
        // Cercles decoratifs flous
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-100).dp, y = (-80).dp)
                .blur(80.dp)
                .alpha(0.2f)
                .background(pageList[pagerState.currentPage].accentColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .offset(x = 150.dp, y = 500.dp)
                .blur(70.dp)
                .alpha(0.15f)
                .background(pageList[pagerState.currentPage].accentColor, CircleShape)
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, end = 24.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinished) {
                    Text(
                        stringResource(R.string.onb_skip),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                OnboardingPageContent(pageList[page])
            }

            // Indicateurs de page
            Row(
                modifier = Modifier.padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(pageList.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (isSelected) 28.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) pageList[index].accentColor
                                else Color.White.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            // Bouton action
            val isLastPage = pagerState.currentPage == pageList.size - 1

            Button(
                onClick = {
                    if (isLastPage) {
                        onFinished()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = pageList[pagerState.currentPage].accentColor
                )
            ) {
                Text(
                    text = if (isLastPage) stringResource(R.string.onb_lets_go) else stringResource(R.string.onb_next),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            // Lien "Deja un compte"
            TextButton(
                onClick = onFinished,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Text(
                    "Deja un compte ? Se connecter",
                    color = Cyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    val infiniteTransition = rememberInfiniteTransition(label = "onboarding")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icone principale avec halo
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.offset(y = (-floatAnim).dp)
        ) {
            // Halo
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .blur(30.dp)
                    .alpha(0.3f)
                    .background(page.accentColor, CircleShape)
            )
            // Cercle fond
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                page.accentColor.copy(alpha = 0.2f),
                                page.accentColor.copy(alpha = 0.05f)
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = page.icon,
                    fontSize = 56.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Titre
        Text(
            text = page.title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Sous-titre
        Text(
            text = page.subtitle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Liste des features
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            page.features.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = feature,
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
