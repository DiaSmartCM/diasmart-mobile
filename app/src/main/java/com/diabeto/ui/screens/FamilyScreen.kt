package com.diabeto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.model.FamilyLink
import com.diabeto.data.model.FamilyLinkStatus
import com.diabeto.ui.theme.OnSurfaceVariant
import com.diabeto.ui.theme.Primary
import com.diabeto.ui.theme.StatusGreen
import com.diabeto.ui.viewmodel.FamilyViewModel

/**
 * v2.1.48 : Mode famille V1.
 *
 * 3 sections :
 * 1. "Mes aidants" : aidants que J'ai invites (vue OWNER)
 *    - Carte + bouton "Inviter un aidant" (limite 1 gratuit)
 *    - Pour chaque aidant : nom + relation + statut + bouton revoquer
 * 2. "Patients que j'aide" : owners qui M'ont invite (vue AIDANT)
 *    - Pour chaque : nom + statut PENDING (boutons Accepter/Refuser) ou
 *      ACCEPTED (bouton "Se desabonner")
 * 3. Snackbar pour message/erreur transitoire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(
    onNavigateBack: () -> Unit,
    viewModel: FamilyViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(ui.error) {
        ui.error?.let { snackbar.showSnackbar("⚠ $it"); viewModel.clearMessages() }
    }

    var showInviteDialog by remember { mutableStateOf(false) }
    var pendingRevoke by remember { mutableStateOf<FamilyLink?>(null) }
    var pendingUnlink by remember { mutableStateOf<FamilyLink?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mode famille") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showInviteDialog = true },
                icon = { Icon(Icons.Default.PersonAdd, null) },
                text = { Text("Inviter un proche") }
            )
        }
    ) { padding ->
        // v2.1.58 : breadcrumb Crashlytics
        LaunchedEffect(Unit) { com.diabeto.monitoring.CrashlyticsLogger.setScreen("FamilyScreen") }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Bandeau explicatif ──
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Inviter un proche",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tes aidants verront tes glycemies en lecture seule et recevront une alerte en cas d'urgence. Ils ne pourront RIEN modifier. Tu peux retirer leur acces a tout moment.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Version gratuite : 1 aidant. Premium (a venir) : illimite.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // ── Section : Mes aidants (vue OWNER) ──
            item { SectionHeader("Mes aidants", count = ui.myAidants.size) }
            if (ui.myAidants.isEmpty() && !ui.isLoading) {
                item {
                    EmptyHint("Aucun aidant invite. Tape sur \"Inviter un proche\" pour commencer.")
                }
            }
            items(ui.myAidants, key = { "aidant-${it.aidantUid}" }) { link ->
                AidantCard(link = link, onRevoke = { pendingRevoke = link })
            }

            item { Spacer(Modifier.height(16.dp)) }

            // ── Section : Patients que j'aide (vue AIDANT) ──
            item { SectionHeader("Patients que j'aide", count = ui.myOwners.size) }
            if (ui.myOwners.isEmpty() && !ui.isLoading) {
                item {
                    EmptyHint("Aucun proche ne t'a invite comme aidant. Demande-lui de t'inviter depuis son app.")
                }
            }
            items(ui.myOwners, key = { "owner-${it.ownerUid}" }) { link ->
                OwnerCard(
                    link = link,
                    onAccept = { viewModel.acceptInvitation(link.ownerUid) },
                    onReject = { viewModel.rejectInvitation(link.ownerUid) },
                    onUnlink = { pendingUnlink = link },
                    onReactivate = { viewModel.reactivate(link.ownerUid) }
                )
            }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    // ── Dialog : inviter un aidant ──
    if (showInviteDialog) {
        InviteAidantDialog(
            isInviting = ui.isInviting,
            onCancel = { showInviteDialog = false },
            onConfirm = { email, relation ->
                viewModel.inviteAidantByEmail(email, relation)
                showInviteDialog = false
            }
        )
    }

    // ── Dialog : revoquer un aidant ──
    pendingRevoke?.let { link ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            icon = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Retirer l'acces ?") },
            text = {
                Text(
                    "${link.aidantNom} ne verra plus tes donnees. Tu pourras le re-inviter plus tard si tu changes d'avis."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.revokeAidant(link.aidantUid)
                        pendingRevoke = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Retirer") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevoke = null }) { Text("Annuler") }
            }
        )
    }

    // ── Dialog : aidant se desabonne d'un owner ──
    pendingUnlink?.let { link ->
        AlertDialog(
            onDismissRequest = { pendingUnlink = null },
            icon = { Icon(Icons.Default.LinkOff, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Se desabonner ?") },
            text = {
                Text("Tu ne suivras plus les donnees de ${link.ownerNom}. Il pourra te re-inviter plus tard.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.unlinkOwner(link.ownerUid)
                        pendingUnlink = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Se desabonner") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnlink = null }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = OnSurfaceVariant.copy(alpha = 0.15f)) {
            Text(
                count.toString(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                fontSize = 11.sp,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyHint(message: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = OnSurfaceVariant.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            color = OnSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AidantCard(link: FamilyLink, onRevoke: () -> Unit) {
    val statusColor = when {
        link.isActive -> StatusGreen
        link.status == FamilyLinkStatus.PENDING -> Primary
        else -> OnSurfaceVariant
    }
    val statusLabel = when {
        link.isActive -> "Actif"
        link.status == FamilyLinkStatus.PENDING -> "En attente d'acceptation"
        else -> "Revoque"
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        link.aidantNom.take(2).uppercase().ifBlank { "?" },
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(link.aidantNom.ifBlank { link.aidantEmail }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (link.relation.isNotBlank()) {
                    Text(link.relation, fontSize = 11.sp, color = OnSurfaceVariant)
                }
                Text(statusLabel, fontSize = 11.sp, color = statusColor)
            }
            if (link.isActive || link.status == FamilyLinkStatus.PENDING) {
                IconButton(onClick = onRevoke) {
                    Icon(
                        Icons.Default.PersonRemove, contentDescription = "Retirer",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun OwnerCard(
    link: FamilyLink,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onUnlink: () -> Unit,
    onReactivate: () -> Unit
) {
    val isPending = link.status == FamilyLinkStatus.PENDING
    val isActive = link.isActive
    val isRevoked = !isActive && link.status == FamilyLinkStatus.REJECTED
    val statusColor = when {
        isActive -> StatusGreen
        isPending -> Primary
        else -> OnSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            link.ownerNom.take(2).uppercase().ifBlank { "?" },
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(link.ownerNom.ifBlank { "Patient" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (link.relation.isNotBlank()) {
                        Text(link.relation, fontSize = 11.sp, color = OnSurfaceVariant)
                    }
                    Text(
                        when {
                            isActive -> "Tu suis ses donnees"
                            isPending -> "T'invite a etre aidant"
                            else -> "Acces revoque"
                        },
                        fontSize = 11.sp,
                        color = statusColor
                    )
                }
            }
            if (isPending) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAccept, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Accepter")
                    }
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) { Text("Refuser") }
                }
            } else if (isActive) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onUnlink,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Se desabonner", fontSize = 12.sp) }
            } else if (isRevoked) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onReactivate) { Text("Reactiver", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun InviteAidantDialog(
    isInviting: Boolean,
    onCancel: () -> Unit,
    onConfirm: (email: String, relation: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Inviter un aidant") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "L'aidant doit deja avoir un compte DiaSmart pour etre invite. Saisis son email :",
                    fontSize = 13.sp,
                    color = OnSurfaceVariant
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email de l'aidant") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("Relation (ex: fils, conjoint, parent)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(email, relation) },
                enabled = !isInviting && email.contains("@") && relation.isNotBlank()
            ) {
                if (isInviting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Envoyer")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Annuler") }
        }
    )
}
