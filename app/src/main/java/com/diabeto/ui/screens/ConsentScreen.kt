package com.diabeto.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.R
import com.diabeto.data.repository.PreferencesRepository
import com.diabeto.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v2.1.70 : ecran de consentement RGPD au premier lancement.
 *
 * Affiche les donnees collectees, les finalites, les droits de l'utilisateur.
 * Trois checkboxes obligatoires (CGU, Politique Confidentialite, Donnees sante).
 * Bouton "J'accepte" desactive tant que les 3 cases ne sont pas cochees.
 * Bouton "Refuser et quitter" qui ferme l'app (l'utilisateur ne PEUT pas
 * utiliser DiaSmart sans accepter — conforme article 9 RGPD pour donnees sante).
 *
 * Versioning : on stocke `consent_version` dans DataStore. Quand on bump
 * CURRENT_CONSENT_VERSION (cf. PreferencesRepository), les utilisateurs sont
 * re-promptes au prochain lancement.
 *
 * Liens HTML externes :
 *  - CGU : https://public-one-omega-88.vercel.app/terms.html
 *  - Politique : https://public-one-omega-88.vercel.app/privacy.html
 */
const val CURRENT_CONSENT_VERSION = 1

@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val prefs: PreferencesRepository
) : ViewModel() {
    fun accept(onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.acceptConsent(CURRENT_CONSENT_VERSION)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentScreen(
    onAccepted: () -> Unit,
    onDeclined: () -> Unit,
    viewModel: ConsentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current

    var checkedCgu by remember { mutableStateOf(false) }
    var checkedPrivacy by remember { mutableStateOf(false) }
    var checkedHealth by remember { mutableStateOf(false) }
    val canContinue = checkedCgu && checkedPrivacy && checkedHealth

    val bg = if (isDark) DarkBackground else Color(0xFFF7F8FC)
    val cardBg = if (isDark) Color(0xFF1A1A2E) else Color.White
    val textPri = if (isDark) DarkTextPrimary else TextPrimary
    val textSec = if (isDark) DarkTextSecondary else TextSecondary
    val headerGradient = listOf(
        if (isDark) Color(0xFF2A2B55) else Color(0xFF6771E4),
        if (isDark) Color(0xFF1A1A3E) else Color(0xFF8B93F0)
    )

    Scaffold(containerColor = bg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(headerGradient))
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.consent_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.consent_subtitle),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Body scrollable
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ConsentSection(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.consent_data_section),
                    items = listOf(
                        stringResource(R.string.consent_data_item_1),
                        stringResource(R.string.consent_data_item_2),
                        stringResource(R.string.consent_data_item_3),
                        stringResource(R.string.consent_data_item_4)
                    ),
                    cardBg = cardBg,
                    textPri = textPri,
                    textSec = textSec
                )

                ConsentSection(
                    icon = Icons.Default.HealthAndSafety,
                    title = stringResource(R.string.consent_purpose_section),
                    items = listOf(
                        stringResource(R.string.consent_purpose_item_1),
                        stringResource(R.string.consent_purpose_item_2),
                        stringResource(R.string.consent_purpose_item_3),
                        stringResource(R.string.consent_purpose_item_4)
                    ),
                    cardBg = cardBg,
                    textPri = textPri,
                    textSec = textSec
                )

                ConsentSection(
                    icon = Icons.Default.Shield,
                    title = stringResource(R.string.consent_rights_section),
                    items = listOf(
                        stringResource(R.string.consent_rights_item_1),
                        stringResource(R.string.consent_rights_item_2),
                        stringResource(R.string.consent_rights_item_3),
                        stringResource(R.string.consent_rights_item_4)
                    ),
                    cardBg = cardBg,
                    textPri = textPri,
                    textSec = textSec
                )

                // Section checkboxes obligatoires
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.consent_required_label),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textPri
                        )
                        Spacer(Modifier.height(4.dp))
                        ConsentCheckbox(
                            checked = checkedCgu,
                            label = stringResource(R.string.consent_checkbox_cgu),
                            actionLabel = stringResource(R.string.consent_read_cgu),
                            onCheckedChange = { checkedCgu = it },
                            onAction = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://public-one-omega-88.vercel.app/terms.html"))
                                context.startActivity(intent)
                            },
                            textPri = textPri
                        )
                        ConsentCheckbox(
                            checked = checkedPrivacy,
                            label = stringResource(R.string.consent_checkbox_privacy),
                            actionLabel = stringResource(R.string.consent_read_privacy),
                            onCheckedChange = { checkedPrivacy = it },
                            onAction = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://public-one-omega-88.vercel.app/privacy.html"))
                                context.startActivity(intent)
                            },
                            textPri = textPri
                        )
                        ConsentCheckbox(
                            checked = checkedHealth,
                            label = stringResource(R.string.consent_checkbox_health),
                            actionLabel = null,
                            onCheckedChange = { checkedHealth = it },
                            onAction = {},
                            textPri = textPri
                        )
                    }
                }

                Text(
                    stringResource(R.string.consent_age_disclaimer),
                    fontSize = 11.sp,
                    color = textSec,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
                Text(
                    stringResource(R.string.consent_contact),
                    fontSize = 11.sp,
                    color = textSec,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }

            // Footer fixed : boutons accept / decline
            Surface(
                shadowElevation = 12.dp,
                color = cardBg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.accept(onAccepted) },
                        enabled = canContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.consent_button_accept),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                    TextButton(
                        onClick = onDeclined,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.consent_button_decline),
                            color = StatusRedDark,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    items: List<String>,
    cardBg: Color,
    textPri: Color,
    textSec: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPri)
            }
            Spacer(Modifier.height(10.dp))
            items.forEach { item ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("•", fontSize = 14.sp, color = Primary, modifier = Modifier.padding(end = 8.dp, top = 1.dp))
                    Text(item, fontSize = 13.sp, color = textSec, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun ConsentCheckbox(
    checked: Boolean,
    label: String,
    actionLabel: String?,
    onCheckedChange: (Boolean) -> Unit,
    onAction: () -> Unit,
    textPri: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = Primary)
        )
        Column(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
            Text(label, fontSize = 13.sp, color = textPri, lineHeight = 17.sp)
            if (actionLabel != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.padding(start = 0.dp).heightIn(min = 30.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Text(actionLabel, fontSize = 12.sp, color = Primary)
                }
            }
        }
    }
}
