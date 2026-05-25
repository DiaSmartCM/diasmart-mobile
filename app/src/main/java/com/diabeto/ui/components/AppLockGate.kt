package com.diabeto.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.diabeto.R
import com.diabeto.security.AppLockCredential
import com.diabeto.security.AppLockMethod
import com.diabeto.util.AppLockManager

/**
 * Verrou applicatif a 3 methodes au choix : empreinte (systeme),
 * PIN 4 chiffres, mot de passe complexe.
 *
 * Comportement :
 *  - enabled=false → bypass complet, content() rendu directement
 *  - enabled=true → ecran de verrou bloquant ; au demarrage et apres > 30s
 *    en background, on prompt selon la methode choisie
 */
@Composable
fun AppLockGate(
    enabled: Boolean,
    method: AppLockMethod,
    credentialSerialized: String?,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? FragmentActivity

    var locked by remember(enabled, method) { mutableStateOf(enabled && method != AppLockMethod.NONE) }
    var lastBackgroundedAt by remember { mutableStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var biometricPromptInFlight by remember { mutableStateOf(false) }

    // Re-verrouillage apres > 30s en arriere-plan
    DisposableEffect(lifecycleOwner, enabled, method) {
        if (!enabled || method == AppLockMethod.NONE) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> lastBackgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val gap = System.currentTimeMillis() - lastBackgroundedAt
                    if (gap > 30_000L && lastBackgroundedAt > 0L) {
                        locked = true
                        errorMessage = null
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-prompt biometrique au demarrage
    LaunchedEffect(locked, method) {
        if (locked && method == AppLockMethod.BIOMETRIC && activity != null && !biometricPromptInFlight) {
            biometricPromptInFlight = true
            launchBiometric(activity, onUnlock = {
                locked = false
                errorMessage = null
                biometricPromptInFlight = false
            }, onError = {
                errorMessage = it
                biometricPromptInFlight = false
            })
        }
    }

    if (locked && enabled && method != AppLockMethod.NONE) {
        when (method) {
            AppLockMethod.BIOMETRIC -> BiometricLockScreen(
                errorMessage = errorMessage,
                onRetry = {
                    if (activity != null && !biometricPromptInFlight) {
                        biometricPromptInFlight = true
                        launchBiometric(activity, onUnlock = {
                            locked = false
                            errorMessage = null
                            biometricPromptInFlight = false
                        }, onError = {
                            errorMessage = it
                            biometricPromptInFlight = false
                        })
                    }
                },
                onClose = { (context as? Activity)?.finishAffinity() }
            )
            AppLockMethod.PIN -> PinLockScreen(
                errorMessage = errorMessage,
                onSubmit = { entered ->
                    val ok = verifyCredential(entered, credentialSerialized)
                    if (ok) {
                        locked = false
                        errorMessage = null
                    } else {
                        errorMessage = context.getString(R.string.lock_pin_incorrect)
                    }
                },
                onClose = { (context as? Activity)?.finishAffinity() }
            )
            AppLockMethod.PASSWORD -> PasswordLockScreen(
                errorMessage = errorMessage,
                onSubmit = { entered ->
                    val ok = verifyCredential(entered, credentialSerialized)
                    if (ok) {
                        locked = false
                        errorMessage = null
                    } else {
                        errorMessage = context.getString(R.string.lock_password_incorrect)
                    }
                },
                onClose = { (context as? Activity)?.finishAffinity() }
            )
            AppLockMethod.NONE -> Unit
        }
    } else {
        content()
    }
}

private fun verifyCredential(entered: String, serialized: String?): Boolean {
    if (serialized.isNullOrBlank()) return false
    val stored = AppLockCredential.HashedCredential.parse(serialized) ?: return false
    return AppLockCredential.verify(entered, stored)
}

private fun launchBiometric(
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
            onError(
                "Aucun verrouillage configure dans Android. Configurez une " +
                    "empreinte ou choisissez PIN / mot de passe DiaSmart dans " +
                    "Parametres > Securite."
            )
            onUnlock()
        }
        AppLockManager.CredentialAvailability.UNSUPPORTED -> {
            onError("Biometrie non supportee. Choisissez PIN ou mot de passe.")
            onUnlock()
        }
    }
}

// ─── Ecrans ────────────────────────────────────────────────────────────

@Composable
private fun lockBackgroundBrush() = Brush.verticalGradient(
    colors = listOf(Color(0xFF6771E4), Color(0xFF8B93F0))
)

@Composable
private fun BiometricLockScreen(
    errorMessage: String?,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    LockShell(errorMessage = errorMessage, onClose = onClose) {
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
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.lock_unlock))
        }
    }
}

@Composable
private fun PinLockScreen(
    errorMessage: String?,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    LaunchedEffect(pin) {
        if (pin.length == 4) {
            onSubmit(pin)
            pin = ""
        }
    }
    LockShell(errorMessage = errorMessage, onClose = onClose) {
        Text("Entrez votre code PIN", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(
                            if (i < pin.length) Color.White else Color.White.copy(alpha = 0.3f),
                            CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        PinKeypad(
            onDigit = { d -> if (pin.length < 4) pin += d },
            onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
        )
    }
}

@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(null, "0", "<")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { key ->
                    when (key) {
                        null -> Box(modifier = Modifier.size(64.dp))
                        "<" -> KeypadKey(onClick = onBackspace) {
                            Icon(Icons.Default.Backspace, contentDescription = "Effacer", tint = Color.White)
                        }
                        else -> KeypadKey(onClick = { onDigit(key) }) {
                            Text(key, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = Modifier.size(64.dp).clip(CircleShape),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.18f),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
private fun PasswordLockScreen(
    errorMessage: String?,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    LockShell(errorMessage = errorMessage, onClose = onClose) {
        Text("Entrez votre mot de passe", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            placeholder = { Text("Mot de passe", color = Color.White.copy(alpha = 0.5f)) },
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) stringResource(R.string.lock_hide) else stringResource(R.string.lock_show),
                        tint = Color.White.copy(alpha = 0.85f)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                onSubmit(password)
                password = ""
            },
            enabled = password.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF6771E4)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.lock_unlock))
        }
    }
}

@Composable
private fun LockShell(
    errorMessage: String?,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(lockBackgroundBrush()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("DiaSmart", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Application verrouillee", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            content()
            errorMessage?.let { msg ->
                Spacer(Modifier.height(16.dp))
                Text(
                    msg,
                    color = Color(0xFFFFE0E0),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            TextButton(onClick = onClose) {
                Text("Quitter l'application", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
