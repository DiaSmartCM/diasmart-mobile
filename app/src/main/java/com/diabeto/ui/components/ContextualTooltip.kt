package com.diabeto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.diabeto.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bulle contextuelle a la 1ere ouverture d'un ecran.
 *
 * Pattern v2.1.46 : pour chaque ecran majeur (Glucose, ROLLY, Messagerie,
 * Reports...) on affiche une bulle en haut avec :
 * - Icone ampoule (decouverte)
 * - Titre + 1 phrase d'aide
 * - Bouton X pour fermer
 *
 * Une fois fermee, le flag DataStore correspondant est set a true →
 * la bulle ne reapparait plus jamais.
 *
 * Usage :
 * ```
 * val seen by preferencesRepository.onboardingGlucoseSeen.collectAsStateWithLifecycle(initial = true)
 * ContextualTooltip(
 *     visible = !seen,
 *     title = "Ajoutez votre glycemie",
 *     message = "Tap sur le bouton + pour saisir une nouvelle lecture...",
 *     onDismiss = { coroutineScope.launch { preferencesRepository.markOnboardingGlucoseSeen() } }
 * )
 * ```
 */
@Composable
fun ContextualTooltip(
    visible: Boolean,
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    message,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    lineHeight = 17.sp
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.tooltip_close),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
