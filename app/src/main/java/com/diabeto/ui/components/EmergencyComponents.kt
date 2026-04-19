package com.diabeto.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composants d'urgence pour DiaSmart :
 * - EmergencyCallButton : bouton d'appel SOS (compose 119 SAMU Cameroun)
 * - EmergencySOSFab     : bouton SOS flottant large
 * - VoiceInputButton    : bouton micro pour saisie vocale (via Speech API Android)
 */

// ─────────────────────────────────────────────────────────────────────────────
// BOUTON D'APPEL SOS (icone rouge dans une TopBar)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bouton compact d'appel SOS. Lance un Intent ACTION_DIAL (le dialer s'ouvre
 * avec le numero pre-rempli ; le patient confirme l'appel d'un tap).
 *
 * Respecte les guidelines Android : on NE compose PAS directement l'appel
 * sans confirmation de l'utilisateur (evite appels accidentels + pas besoin
 * de permission CALL_PHONE).
 */
@Composable
fun EmergencyCallButton(
    modifier: Modifier = Modifier,
    number: String = "119", // SAMU Cameroun par defaut
    tint: Color = Color(0xFFFF3B30) // Rouge urgence
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showDialog = true },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "Appel d'urgence $number",
            tint = tint
        )
    }

    if (showDialog) {
        EmergencyNumberDialog(
            onDismiss = { showDialog = false },
            onCallNumber = { num ->
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$num")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                showDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SOS FAB (bouton flottant rouge large, tres visible)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bouton SOS flottant. Tres visible, pour placement en overlay sur dashboard
 * ou chat quand une urgence est detectee.
 */
@Composable
fun EmergencySOSFab(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    FloatingActionButton(
        onClick = {
            onClick?.invoke()
            showDialog = true
        },
        modifier = modifier.size(64.dp),
        shape = CircleShape,
        containerColor = Color(0xFFFF3B30),
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Warning, "SOS", modifier = Modifier.size(22.dp))
            Text(
                "SOS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showDialog) {
        EmergencyNumberDialog(
            onDismiss = { showDialog = false },
            onCallNumber = { num ->
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$num")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                showDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIALOG : choix du numero d'urgence
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmergencyNumberDialog(
    onDismiss: () -> Unit,
    onCallNumber: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Urgence Cameroun",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column {
                Text(
                    "Choisis le service a appeler :",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(16.dp))

                EmergencyNumberRow(
                    label = "SAMU (medical)",
                    number = "119",
                    onClick = { onCallNumber("119") }
                )
                Spacer(Modifier.height(8.dp))
                EmergencyNumberRow(
                    label = "Police Secours",
                    number = "117",
                    onClick = { onCallNumber("117") }
                )
                Spacer(Modifier.height(8.dp))
                EmergencyNumberRow(
                    label = "Pompiers",
                    number = "118",
                    onClick = { onCallNumber("118") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
private fun EmergencyNumberRow(
    label: String,
    number: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFF3B30).copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    number,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF3B30)
                )
            }
            FilledIconButton(
                onClick = onClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFFFF3B30)
                )
            ) {
                Icon(Icons.Default.Call, "Appeler $label", tint = Color.White)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BOUTON MICROPHONE (saisie vocale via Speech API Android)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bouton microphone qui ouvre l'interface Google Speech Input et retourne
 * le texte transcrit via callback.
 *
 * Utilise RecognizerIntent.ACTION_RECOGNIZE_SPEECH -> aucune permission
 * RECORD_AUDIO requise (c'est Google Speech Services qui gere l'audio).
 *
 * Langue par defaut : fr-FR. Supporte aussi les accents camerounais.
 *
 * Note : sur appareils sans Google Services, le callback reste sans effet.
 * L'utilisateur verra le clavier standard en alternative.
 */
@Composable
fun VoiceInputButton(
    onTextRecognized: (String) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF6771E4),
    prompt: String = "Parle a ROLLY..."
) {
    val context = LocalContext.current

    // v2.1.9 : detecte si l'appareil est hors-ligne pour activer le mode offline
    // de Google Speech Recognition (necessite un pack vocal fr-FR telecharge
    // dans Parametres Android > Systeme > Langues > Voix > Reconnaissance hors-ligne).
    fun isOfflineNow(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true
        return !(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = results?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                onTextRecognized(spoken)
            } else if (isOfflineNow()) {
                // v2.1.9 : aucun texte reconnu en mode offline -> probablement
                // pack vocal non installe. Guide l'utilisateur.
                Toast.makeText(
                    context,
                    "Reconnaissance vocale hors-ligne indisponible. " +
                    "Telechargez le pack vocal francais dans Parametres Android " +
                    "> Systeme > Langues > Voix.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    IconButton(
        onClick = {
            val offlineNow = isOfflineNow()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Active la detection de fin de parole (auto-arret silence)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)

                // v2.1.9 : en mode hors-ligne, demande explicitement la reconnaissance
                // offline. Necessite pack vocal telecharge sur l'appareil.
                if (offlineNow) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            try {
                launcher.launch(intent)
            } catch (e: Exception) {
                // Device sans Google Speech Services
                Toast.makeText(
                    context,
                    "Reconnaissance vocale indisponible sur cet appareil.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Saisie vocale",
            tint = tint
        )
    }
}
