package com.diabeto.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.diabeto.util.AppLockManager

/**
 * Verrou applicatif. Si `enabled = true` :
 *  - affiche un ecran de verrouillage qui bloque tout acces a l'app
 *  - lance automatiquement le BiometricPrompt au demarrage et a chaque
 *    retour en avant-plan (apres > 30 s en background pour ne pas
 *    re-prompter a la moindre interruption)
 *  - debloque l'app uniquement apres authentification systeme reussie
 *
 * Quand `enabled = false`, le composant est transparent : `content` est
 * affiche directement.
 */
@Composable
fun AppLockGate(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? FragmentActivity

    // Etat : true = verrouille, false = deverrouille pour cette session
    var locked by remember(enabled) { mutableStateOf(enabled) }
    var lastBackgroundedAt by remember { mutableStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Re-verrouille apres > 30 s en arriere-plan
    DisposableEffect(lifecycleOwner, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> lastBackgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val gap = System.currentTimeMillis() - lastBackgroundedAt
                    if (gap > 30_000L && lastBackgroundedAt > 0L) {
                        locked = true
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-prompt des que l'ecran apparait en mode verrouille
    LaunchedEffect(locked, enabled) {
        if (enabled && locked && activity != null) {
            triggerPrompt(activity, onUnlock = {
                locked = false
                errorMessage = null
            }, onError = { errorMessage = it })
        }
    }

    if (enabled && locked) {
        LockedScreen(
            errorMessage = errorMessage,
            onRetry = {
                if (activity != null) {
                    triggerPrompt(activity, onUnlock = {
                        locked = false
                        errorMessage = null
                    }, onError = { errorMessage = it })
                }
            },
            onClose = {
                // L'utilisateur quitte l'app plutot que de s'authentifier
                (context as? Activity)?.finishAffinity()
            }
        )
    } else {
        content()
    }
}

private fun triggerPrompt(
    activity: FragmentActivity,
    onUnlock: () -> Unit,
    onError: (String) -> Unit
) {
    when (AppLockManager.checkAvailability(activity)) {
        AppLockManager.CredentialAvailability.AVAILABLE -> {
            AppLockManager.authenticate(
                activity = activity,
                onSuccess = onUnlock,
                onError = onError
            )
        }
        AppLockManager.CredentialAvailability.NONE_ENROLLED -> {
            // Aucun PIN / biometrie sur l'appareil → on ne bloque pas
            // l'utilisateur, on l'invite simplement a en configurer un.
            onError(
                "Aucun verrouillage configure sur votre appareil. " +
                    "Ajoutez un PIN, un schema ou une empreinte dans les " +
                    "Parametres Android pour activer le verrouillage de DiaSmart."
            )
            onUnlock()
        }
        AppLockManager.CredentialAvailability.UNSUPPORTED -> {
            onError("Verrouillage non supporte par cet appareil.")
            onUnlock()
        }
    }
}

@Composable
private fun LockedScreen(
    errorMessage: String?,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF6771E4), Color(0xFF8B93F0))
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "DiaSmart",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Application verrouillee",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF6771E4)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Deverrouiller")
            }
            errorMessage?.let { msg ->
                Spacer(Modifier.height(20.dp))
                Text(
                    msg,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Spacer(Modifier.height(40.dp))
            androidx.compose.material3.TextButton(onClick = onClose) {
                Text("Quitter l'application", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
