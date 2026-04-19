package com.diabeto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.model.Conversation
import com.diabeto.data.model.Message
import com.diabeto.data.model.UserProfile
import com.diabeto.data.model.UserRole
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.ConversationDetailViewModel
import com.diabeto.ui.viewmodel.ConversationsViewModel
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

// ─────────────────────────────────────────────────────────────────────────────
// Liste des conversations
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagerieScreen(
    onNavigateBack: () -> Unit,
    onNavigateToConversation: (String) -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Messagerie", fontWeight = FontWeight.Bold)
                        if (uiState.currentProfile != null) {
                            Text(
                                text = uiState.currentProfile!!.nomComplet +
                                        if (uiState.currentProfile!!.role == UserRole.MEDECIN) " · Médecin" else " · Patient",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = diaSmartTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Seuls les patients peuvent initier une conversation avec un médecin
            if (uiState.currentProfile?.role == UserRole.PATIENT) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.toggleNouvelleConversation(true) },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Nouvelle conversation") },
                    text = { Text("Contacter un médecin") },
                    containerColor = Primary
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.conversations.isEmpty()) {
                // État vide
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = "Aucune conversation",
                        modifier = Modifier.size(64.dp),
                        tint = OnSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        "Aucune conversation",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurfaceVariant
                    )
                    Text(
                        if (uiState.currentProfile?.role == UserRole.PATIENT)
                            "Contactez votre médecin via le bouton +"
                        else
                            "Vos patients vous contacteront ici",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.conversations, key = { it.id }) { conversation ->
                        val isPatient = uiState.currentProfile?.role == UserRole.PATIENT
                        val nonLus = if (isPatient) conversation.nonLusPatient else conversation.nonLusMedecin
                        ConversationCard(
                            conversation = conversation,
                            interlocuteur = if (isPatient) conversation.medecinNom else conversation.patientNom,
                            nonLus = nonLus,
                            onClick = { onNavigateToConversation(conversation.id) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    // Dialog choix médecin
    if (uiState.showNouvelleConversation) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleNouvelleConversation(false) },
            title = { Text("Choisir un médecin", fontWeight = FontWeight.Bold) },
            text = {
                if (uiState.medecins.isEmpty()) {
                    Text("Aucun médecin disponible pour le moment.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(uiState.medecins) { medecin ->
                            MedecinItem(
                                medecin = medecin,
                                onClick = {
                                    viewModel.creerConversationAvec(medecin) { convId ->
                                        viewModel.toggleNouvelleConversation(false)
                                        onNavigateToConversation(convId)
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.toggleNouvelleConversation(false) }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationCard(
    conversation: Conversation,
    interlocuteur: String,
    nonLus: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar initiales
            Surface(
                shape = CircleShape,
                color = Primary.copy(alpha = 0.15f),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = interlocuteur.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        fontSize = 16.sp
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = interlocuteur,
                        fontWeight = if (nonLus > 0) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = formatTimestamp(conversation.dernierMessageAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (nonLus > 0) Primary else OnSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.dernierMessage.ifBlank { "Démarrer la conversation..." },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (nonLus > 0) OnSurface else OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        fontWeight = if (nonLus > 0) FontWeight.Medium else FontWeight.Normal
                    )
                    if (nonLus > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = Primary
                        ) {
                            Text(
                                text = if (nonLus > 99) "99+" else nonLus.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MedecinItem(medecin: UserProfile, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.LocalHospital, contentDescription = "Médecin", tint = Primary)
            Column {
                Text(medecin.nomComplet, fontWeight = FontWeight.Medium)
                Text(medecin.email, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Détail d'une conversation
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailScreen(
    conversationId: String,
    interlocuteurNom: String,
    onNavigateBack: () -> Unit,
    onNavigateToVideoCall: (String, String, Boolean) -> Unit = { _, _, _ -> },
    callManager: com.diabeto.voip.CallManager? = null,
    viewModel: ConversationDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = interlocuteurNom.take(2).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = Primary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(interlocuteurNom, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "En ligne",
                                fontSize = 11.sp,
                                color = Success
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    // Bouton appel video VoIP
                    IconButton(onClick = {
                        val calleeUid = viewModel.interlocuteurUid
                        if (calleeUid.isNotEmpty() && callManager != null) {
                            val callerNom = uiState.currentUserId ?: ""
                            callManager.startCall(calleeUid, callerNom, interlocuteurNom, true)
                            onNavigateToVideoCall("voip", interlocuteurNom, false)
                        } else {
                            // Fallback Jitsi
                            val roomName = "diasmart-${conversationId.take(12)}"
                            onNavigateToVideoCall(roomName, interlocuteurNom, false)
                        }
                    }) {
                        Icon(Icons.Default.Videocam, "Appel video", tint = Primary)
                    }
                    // Bouton appel audio VoIP
                    IconButton(onClick = {
                        val calleeUid = viewModel.interlocuteurUid
                        if (calleeUid.isNotEmpty() && callManager != null) {
                            val callerNom = uiState.currentUserId ?: ""
                            callManager.startCall(calleeUid, callerNom, interlocuteurNom, false)
                            onNavigateToVideoCall("voip", interlocuteurNom, true)
                        } else {
                            val roomName = "diasmart-${conversationId.take(12)}"
                            onNavigateToVideoCall(roomName, interlocuteurNom, true)
                        }
                    }) {
                        Icon(Icons.Default.Call, "Appel audio", tint = StatusGreen)
                    }
                },
                colors = diaSmartTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Background)
        ) {
            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    ChatMessageBubble(
                        message = message,
                        isCurrentUser = message.envoyeurId == uiState.currentUserId
                    )
                }
            }

            // Zone de saisie
            Surface(shadowElevation = 8.dp, color = Surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = viewModel::onInputChange,
                        placeholder = { Text("Votre message...", fontSize = 14.sp) },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { viewModel.envoyerMessage() }),
                        shape = RoundedCornerShape(24.dp)
                    )
                    FloatingActionButton(
                        onClick = viewModel::envoyerMessage,
                        modifier = Modifier.size(48.dp),
                        containerColor = if (uiState.inputText.isNotBlank()) Primary
                        else OnSurfaceVariant.copy(alpha = 0.3f),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Send, contentDescription = "Envoyer", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: Message, isCurrentUser: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
        if (!isCurrentUser) {
            Text(
                text = message.envoyeurNom,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        Surface(
            shape = if (isCurrentUser)
                RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
            else
                RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            color = if (isCurrentUser) Primary else Color.White,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.contenu,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (isCurrentUser) Color.White else OnSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = formatTimestamp(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
        )
    }
}

private fun formatTimestamp(timestamp: com.google.firebase.Timestamp): String {
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { time = timestamp.toDate() }
    return when {
        now.get(Calendar.DATE) == cal.get(Calendar.DATE) ->
            SimpleDateFormat("HH:mm", Locale.FRANCE).format(timestamp.toDate())
        now.get(Calendar.WEEK_OF_YEAR) == cal.get(Calendar.WEEK_OF_YEAR) ->
            SimpleDateFormat("EEE HH:mm", Locale.FRANCE).format(timestamp.toDate())
        else ->
            SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(timestamp.toDate())
    }
}
