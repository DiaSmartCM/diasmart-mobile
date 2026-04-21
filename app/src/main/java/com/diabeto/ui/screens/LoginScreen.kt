package com.diabeto.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.R
import com.diabeto.data.model.UserRole
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.AuthViewModel
import com.diabeto.ui.viewmodel.LoginMode
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private const val WEB_CLIENT_ID = "630181163819-78j1ha7vo07ldokvqo30cntt3tlanjv2.apps.googleusercontent.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPassword by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { viewModel.signInWithGoogle(it) }
        } catch (_: ApiException) {}
    }

    // Animation gradient douce
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val animOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientAnim"
    )

    LaunchedEffect(uiState.isLoggedIn) { if (uiState.isLoggedIn) onLoginSuccess() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(uiState.resetEmailSent) {
        if (uiState.resetEmailSent) snackbarHostState.showSnackbar(context.getString(R.string.auth_reset_email_sent))
    }

    if (uiState.needsEmailOtp) {
        EmailOtpDialog(
            email = uiState.pendingOtpEmail,
            code = uiState.emailOtpCode,
            infoMessage = uiState.otpInfoMessage,
            isLoading = uiState.isLoading,
            onCodeChange = viewModel::onEmailOtpChange,
            onVerify = viewModel::verifyEmailOtp,
            onResend = viewModel::resendEmailOtp,
            onCancel = viewModel::cancelEmailOtp
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ═══════════════════════════════════════════════════
            //  FOND GRADIENT ANIME — Palette DayLife (indigo/violet)
            // ═══════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF7C6AFF),
                                Color(0xFF5B4CFF),
                                Color(0xFF4834D4),
                                Color(0xFF5B4CFF),
                                Color(0xFF7C6AFF)
                            ),
                            start = Offset(animOffset, 0f),
                            end = Offset(animOffset + 600f, 1200f)
                        )
                    )
            )

            // Cercles décoratifs
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .offset(x = (-70).dp, y = (-50).dp)
                    .blur(70.dp)
                    .background(Color(0xFF9D91FF).copy(alpha = 0.4f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 70.dp, y = 70.dp)
                    .blur(60.dp)
                    .background(Color(0xFFFF6B8A).copy(alpha = 0.25f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 50.dp)
                    .blur(40.dp)
                    .background(Color(0xFF00C9A7).copy(alpha = 0.2f), CircleShape)
            )

            // ═══════════════════════════════════════════════════
            //  CONTENU PRINCIPAL
            // ═══════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(72.dp))

                // Logo
                Image(
                    painter = painterResource(id = R.drawable.ic_diasmart_logo),
                    contentDescription = "DiaSmart Logo",
                    modifier = Modifier
                        .size(88.dp)
                        .shadow(20.dp, CircleShape)
                        .clip(CircleShape)
                        .border(3.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = if (uiState.showRegister) stringResource(R.string.auth_welcome) else stringResource(R.string.auth_hello),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (uiState.showRegister) stringResource(R.string.auth_create_account_subtitle) else stringResource(R.string.auth_login_subtitle),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ═══════════════════════════════════════════════════
                //  CARTE FORMULAIRE — Coins arrondis, ombre douce
                // ═══════════════════════════════════════════════════
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = if (uiState.showRegister) stringResource(R.string.auth_inscription) else stringResource(R.string.auth_connexion),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        // ── Toggle Email / Téléphone ──
                        if (!uiState.showRegister) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceVariant)
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                LoginModeTab(stringResource(R.string.auth_email), Icons.Outlined.Email, uiState.loginMode == LoginMode.EMAIL, { viewModel.setLoginMode(LoginMode.EMAIL) }, Modifier.weight(1f))
                                LoginModeTab(stringResource(R.string.auth_phone), Icons.Outlined.Phone, uiState.loginMode == LoginMode.PHONE, { viewModel.setLoginMode(LoginMode.PHONE) }, Modifier.weight(1f))
                            }
                        }

                        // ── Champs inscription ──
                        AnimatedVisibility(
                            visible = uiState.showRegister,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DiaSmartTextField(uiState.prenom, viewModel::onPrenomChange, "Prénom", Icons.Outlined.Person, Modifier.weight(1f), imeAction = ImeAction.Next)
                                    DiaSmartTextField(uiState.nom, viewModel::onNomChange, "Nom", Icons.Outlined.Badge, Modifier.weight(1f), imeAction = ImeAction.Next)
                                }
                                Text(stringResource(R.string.auth_i_am), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    UserRole.entries.forEach { role ->
                                        val isSelected = uiState.selectedRole == role
                                        Surface(
                                            modifier = Modifier.weight(1f).selectable(selected = isSelected, onClick = { viewModel.onRoleChange(role) }, role = Role.RadioButton),
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) PrimaryContainer else SurfaceVariant,
                                            border = if (isSelected) BorderStroke(2.dp, Primary) else BorderStroke(1.dp, Outline)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    if (role == UserRole.PATIENT) Icons.Outlined.PersonOutline else Icons.Outlined.LocalHospital,
                                                    null, tint = if (isSelected) Primary else TextTertiary, modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    if (role == UserRole.PATIENT) stringResource(R.string.auth_role_patient) else stringResource(R.string.auth_role_medecin),
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Primary else TextSecondary, fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // ── MODE EMAIL ──
                        AnimatedVisibility(
                            visible = uiState.loginMode == LoginMode.EMAIL || uiState.showRegister,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                DiaSmartTextField(uiState.email, viewModel::onEmailChange, stringResource(R.string.auth_email), Icons.Outlined.Email, keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                                DiaSmartTextField(uiState.password, viewModel::onPasswordChange, stringResource(R.string.auth_password), Icons.Outlined.Lock, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, isPassword = true, showPassword = showPassword, onTogglePassword = { showPassword = !showPassword })
                                if (!uiState.showRegister) {
                                    TextButton(onClick = viewModel::resetPassword, modifier = Modifier.align(Alignment.End)) {
                                        Text(stringResource(R.string.auth_forgot_password), fontSize = 12.sp, color = Primary, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        // ── MODE TELEPHONE ──
                        AnimatedVisibility(
                            visible = uiState.loginMode == LoginMode.PHONE && !uiState.showRegister,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                DiaSmartTextField(uiState.phoneNumber, viewModel::onPhoneNumberChange, "Numéro de téléphone", Icons.Outlined.Phone, keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done, placeholder = "0555 12 34 56")
                                AnimatedVisibility(visible = uiState.isCodeSent, enter = fadeIn() + expandVertically()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(stringResource(R.string.auth_sms_sent), fontSize = 13.sp, color = Success, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                        DiaSmartTextField(uiState.smsCode, viewModel::onSmsCodeChange, "Code de vérification", Icons.Outlined.Sms, keyboardType = KeyboardType.Number, imeAction = ImeAction.Done, placeholder = "123456")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // ── BOUTON PRINCIPAL GRADIENT ──
                        Button(
                            onClick = {
                                when {
                                    uiState.showRegister -> viewModel.signUp()
                                    uiState.loginMode == LoginMode.PHONE -> {
                                        if (uiState.isCodeSent) viewModel.verifyPhoneCode()
                                        else (context as? Activity)?.let { viewModel.sendPhoneVerification(it) }
                                    }
                                    else -> viewModel.signIn()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !uiState.isLoading
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            if (uiState.isLoading)
                                                listOf(DarkTextSecondary, DarkTextSecondary)
                                            else
                                                listOf(Color(0xFF7C6AFF), Color(0xFF5B4CFF), Color(0xFF4834D4))
                                        ),
                                        RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            when {
                                                uiState.showRegister -> Icons.Default.PersonAdd
                                                uiState.loginMode == LoginMode.PHONE && uiState.isCodeSent -> Icons.Default.VerifiedUser
                                                uiState.loginMode == LoginMode.PHONE -> Icons.Default.Sms
                                                else -> Icons.Default.Login
                                            }, null, tint = Color.White, modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            when {
                                                uiState.showRegister -> stringResource(R.string.auth_create_my_account)
                                                uiState.loginMode == LoginMode.PHONE && uiState.isCodeSent -> stringResource(R.string.auth_verify_code)
                                                uiState.loginMode == LoginMode.PHONE -> stringResource(R.string.auth_send_sms)
                                                else -> stringResource(R.string.auth_sign_in)
                                            }, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // ── Séparateur ──
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Outline)
                            Text("  ${stringResource(R.string.auth_or)}  ", color = TextTertiary, fontSize = 12.sp)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Outline)
                        }

                        // ── Boutons social ──
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            OutlinedButton(
                                onClick = {
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestIdToken(WEB_CLIENT_ID).requestEmail().build()
                                    val client = GoogleSignIn.getClient(context, gso)
                                    client.signOut()
                                    googleSignInLauncher.launch(client.signInIntent)
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Outline),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                            ) {
                                Text("G", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.auth_google), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                            }
                            Spacer(Modifier.width(12.dp))
                            OutlinedButton(
                                onClick = {
                                    if (uiState.showRegister) viewModel.toggleRegister(false)
                                    viewModel.setLoginMode(if (uiState.loginMode == LoginMode.PHONE) LoginMode.EMAIL else LoginMode.PHONE)
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Outline),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                            ) {
                                Icon(Icons.Default.Phone, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.auth_phone), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // ── Toggle inscription / connexion ──
                        OutlinedButton(
                            onClick = { viewModel.toggleRegister(!uiState.showRegister) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = 1.5.dp,
                                brush = Brush.horizontalGradient(listOf(Color(0xFF7C6AFF), Color(0xFF5B4CFF)))
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
                        ) {
                            Icon(
                                if (uiState.showRegister) Icons.Default.Login else Icons.Default.PersonAdd,
                                null, modifier = Modifier.size(18.dp), tint = Primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (uiState.showRegister) stringResource(R.string.auth_already_account) else stringResource(R.string.auth_no_account),
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.auth_tagline), fontSize = 11.sp, color = TextTertiary, textAlign = TextAlign.Center)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  COMPOSANTS REUTILISABLES
// ══════════════════════════════════════════════════════════════════

@Composable
private fun LoginModeTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color.White else Color.Transparent,
        shadowElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = if (isSelected) Primary else TextTertiary)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Primary else TextTertiary)
        }
    }
}

@Composable
private fun DiaSmartTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    placeholder: String? = null,
    required: Boolean = true
) {
    // Label avec asterisque rouge pour les champs obligatoires
    val labelWithAsterisk: @Composable () -> Unit = {
        if (required) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(" *", fontSize = 14.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
            }
        } else {
            Text(label, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = labelWithAsterisk,
        placeholder = if (placeholder != null) { { Text(placeholder, fontSize = 14.sp, color = TextSecondary) } } else null,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = androidx.compose.ui.text.TextStyle(
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            disabledTextColor = TextSecondary,
            focusedBorderColor = Primary,
            unfocusedBorderColor = TextSecondary,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedLabelColor = Primary,
            unfocusedLabelColor = TextPrimary,
            focusedPlaceholderColor = TextSecondary,
            unfocusedPlaceholderColor = TextSecondary,
            cursorColor = Primary,
            focusedLeadingIconColor = Primary,
            unfocusedLeadingIconColor = TextPrimary
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { onTogglePassword?.invoke() }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, tint = TextPrimary, modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null
    )
}
