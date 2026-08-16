package com.diabeto.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

// Palette DiaSmart (cf. ui/theme/Color.kt). Volontairement en dur : la Surface
// du dialogue est blanche quel que soit le theme, donc des jetons qui basculent
// en sombre rendraient le texte illisible sur ce fond.
private val OtpIndigo      = Color(0xFF6771E4)
private val OtpIndigoSoft  = Color(0xFFE8E5FF)
private val OtpInk         = Color(0xFF1A2B4B)
private val OtpInkSoft     = Color(0xFF4A5A78)
private val OtpInkFaint    = Color(0xFF8492A6)
private val OtpLine        = Color(0xFFE4E8F0)
private val OtpCellBg      = Color(0xFFFAFBFC)

private const val OTP_LENGTH = 6
/** Delai avant de pouvoir redemander un code (anti-spam de l'endpoint Vercel). */
private const val RESEND_DELAY_SECONDS = 30

/**
 * Bottom-sheet-style dialog pour saisir le code OTP a 6 chiffres recu par email.
 * Non-dismissable (force la verification) — on peut annuler via le bouton.
 *
 * v2.1.72 : cadran a 6 cases (au lieu d'un champ unique), avec trace anime
 * autour de chaque chiffre saisi, curseur clignotant sur la case active, et
 * compte a rebours sur "Renvoyer le code".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailOtpDialog(
    email: String,
    code: String,
    infoMessage: String?,
    isLoading: Boolean,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    // Compte a rebours du renvoi. `resendRound` s'incremente a chaque envoi :
    // relancer l'effet remet le compteur a RESEND_DELAY_SECONDS.
    var resendRound by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(RESEND_DELAY_SECONDS) }
    LaunchedEffect(resendRound) {
        secondsLeft = RESEND_DELAY_SECONDS
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
    }

    Dialog(
        onDismissRequest = { /* non-dismissable, on utilise Annuler */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icone
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(OtpIndigoSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MarkEmailRead,
                        contentDescription = null,
                        tint = OtpIndigo,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Verification email",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OtpInk,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Entrez le code a 6 chiffres envoye a\n$email",
                    fontSize = 13.sp,
                    color = OtpInkSoft,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── Cadran OTP ────────────────────────────────────────────
                // Un champ invisible capte la frappe ; `decorationBox` dessine
                // les 6 cases a la place (innerTextField n'est pas appele).
                BasicTextField(
                    value = code,
                    onValueChange = { entree ->
                        val propre = entree.filter { it.isDigit() }.take(OTP_LENGTH)
                        if (propre != code) onCodeChange(propre)
                    },
                    enabled = !isLoading,
                    singleLine = true,
                    cursorBrush = SolidColor(Color.Transparent),
                    textStyle = TextStyle(color = Color.Transparent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    decorationBox = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            repeat(OTP_LENGTH) { index ->
                                OtpCell(
                                    chiffre = code.getOrNull(index)?.toString().orEmpty(),
                                    estActive = index == code.length.coerceAtMost(OTP_LENGTH - 1),
                                    saisieTerminee = code.length == OTP_LENGTH,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                )

                // v2.1.74 : focus demande ICI, a l'interieur du Dialog et APRES
                // que le champ soit dans la composition. Place avant le Dialog,
                // l'effet partait alors que le contenu vivait encore dans une
                // fenetre non attachee -> "FocusRequester is not initialized"
                // (crash fatal, systematique sur EMUI/Android 10).
                // withFrameNanos attend la fin de la passe de layout ; le
                // runCatching garantit qu'un echec ne coute qu'un clavier non
                // ouvert, jamais un plantage.
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    runCatching { focusRequester.requestFocus() }
                }

                if (!infoMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFEFF6FF))
                            .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = infoMessage,
                            fontSize = 12.sp,
                            color = Color(0xFF1E40AF),
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onVerify,
                    enabled = !isLoading && code.length == OTP_LENGTH,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OtpIndigo
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text("Valider", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Renvoi avec compte a rebours ──────────────────────────
                val renvoiPossible = secondsLeft <= 0 && !isLoading
                TextButton(
                    onClick = {
                        onResend()
                        resendRound++
                    },
                    enabled = renvoiPossible,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (renvoiPossible) {
                            "Renvoyer le code"
                        } else {
                            "Renvoyer dans ${secondsLeft}s"
                        },
                        color = if (renvoiPossible) OtpIndigo else OtpInkFaint,
                        fontWeight = if (renvoiPossible) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }

                TextButton(
                    onClick = onCancel,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Annuler",
                        color = OtpInkFaint,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Une case du cadran OTP.
 *
 * Le trace indigo se dessine autour de la case des qu'un chiffre est saisi :
 * un `dashPathEffect` dont la longueur du trait est animee de 0 au perimetre
 * complet, ce qui reproduit l'effet de dessin progressif.
 */
@Composable
private fun OtpCell(
    chiffre: String,
    estActive: Boolean,
    saisieTerminee: Boolean,
    modifier: Modifier = Modifier
) {
    val rempli = chiffre.isNotEmpty()

    val progressionTrace by animateFloatAsState(
        targetValue = if (rempli) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "traceOtp"
    )
    val apparitionChiffre by animateFloatAsState(
        targetValue = if (rempli) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "chiffreOtp"
    )

    // Curseur clignotant, uniquement sur la case en attente de frappe.
    val montrerCurseur = estActive && !rempli && !saisieTerminee
    val transition = rememberInfiniteTransition(label = "curseurOtp")
    val alphaCurseur by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 560),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaCurseurOtp"
    )

    Box(
        modifier = modifier
            .aspectRatio(0.78f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (estActive && !rempli) OtpIndigoSoft else OtpCellBg),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val rayon = CornerRadius(12.dp.toPx())
            val epaisseur = 1.5.dp.toPx()
            val tailleCadre = Size(size.width - epaisseur, size.height - epaisseur)
            val coin = Offset(epaisseur / 2f, epaisseur / 2f)

            // Contour de base
            drawRoundRect(
                color = if (estActive) OtpIndigo else OtpLine,
                topLeft = coin,
                size = tailleCadre,
                cornerRadius = rayon,
                style = Stroke(width = epaisseur)
            )

            // Trace anime par-dessus, une fois le chiffre saisi
            if (progressionTrace > 0f) {
                val perimetre = 2f * (tailleCadre.width + tailleCadre.height)
                drawRoundRect(
                    color = OtpIndigo,
                    topLeft = coin,
                    size = tailleCadre,
                    cornerRadius = rayon,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(perimetre * progressionTrace, perimetre),
                            0f
                        )
                    )
                )
            }

            if (montrerCurseur) {
                val hauteur = size.height * 0.38f
                drawLine(
                    color = OtpIndigo.copy(alpha = alphaCurseur),
                    start = Offset(size.width / 2f, (size.height - hauteur) / 2f),
                    end = Offset(size.width / 2f, (size.height + hauteur) / 2f),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        Text(
            text = chiffre,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = OtpInk.copy(alpha = apparitionChiffre),
            textAlign = TextAlign.Center
        )
    }
}
