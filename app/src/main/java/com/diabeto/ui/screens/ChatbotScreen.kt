package com.diabeto.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.res.stringResource
import com.diabeto.R
import com.diabeto.ui.theme.LocalIsDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.model.ChatbotMessage
import com.diabeto.data.model.UserProfile
import com.diabeto.data.repository.ChatSession
import com.diabeto.ui.components.EmergencyCallButton
import com.diabeto.ui.components.RollyIcon
import com.diabeto.ui.components.RollyIconInline
import com.diabeto.ui.components.VoiceInputButton
import com.diabeto.ui.theme.*
import com.diabeto.ui.viewmodel.ChatbotViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(
    patientId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: ChatbotViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

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

    val isDarkTheme = LocalIsDarkTheme.current
    // Theme-aware colors: light mode = clean white, dark mode = keep current dark style
    val chatBg = if (isDarkTheme) DarkBackground else Background
    val chatTopBar = if (isDarkTheme) OnBackground else RollyCardColor
    val chatChipBg = if (isDarkTheme) DarkSurface else SurfaceVariant
    val chatInputBg = if (isDarkTheme) DarkSurface else Surface
    val chatInputBorder = if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Outline
    val chatTopBarText = Color.White  // Always white on indigo/dark bars
    val chatSuggestionBg = if (isDarkTheme) Color(0xFF1E1E30) else PrimaryContainer.copy(alpha = 0.5f)
    val chatSuggestionBorder = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Outline
    val chatSuggestionText = if (isDarkTheme) Color.White.copy(alpha = 0.8f) else TextPrimary
    val chatHistoryText = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else TextSecondary

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 2.dp,
                color = chatTopBar
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.chatbot_back), tint = chatTopBarText)
                    }
                    // Hamburger menu for sessions
                    IconButton(onClick = { viewModel.toggleSessionDrawer() }) {
                        Icon(Icons.Default.Menu, stringResource(R.string.chatbot_discussions), tint = chatTopBarText.copy(alpha = 0.8f))
                    }
                    RollyIcon(size = 34.dp, showBackground = true)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.chatbot_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = chatTopBarText
                        )
                        Text(
                            uiState.currentSessionTitle,
                            fontSize = 11.sp,
                            color = chatTopBarText.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                    // New session button
                    IconButton(onClick = { viewModel.createNewSession() }) {
                        Icon(Icons.Default.Add, stringResource(R.string.chatbot_new_discussion), tint = if (isDarkTheme) Primary else Color.White)
                    }
                    // Badge quota
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (uiState.quotaStatus.remaining <= 2)
                            StatusRed.copy(alpha = 0.2f)
                        else Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            "${uiState.quotaStatus.remaining}/${uiState.quotaStatus.limit}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.quotaStatus.remaining <= 2)
                                StatusRed else chatTopBarText
                        )
                    }
                    // Bouton d'appel d'urgence (SOS 119 Cameroun)
                    EmergencyCallButton(
                        tint = StatusRed
                    )
                    // Menu options
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Options", tint = chatTopBarText.copy(alpha = 0.7f))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chatbot_clear_history)) },
                                onClick = {
                                    viewModel.effacerHistorique()
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = chatBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Chips d'analyse rapide
            if (patientId != null && patientId > 0) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(chatChipBg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        RollyAnalyseChip(
                            label = "📊 " + stringResource(R.string.chatbot_analyse_glycemie),
                            onClick = viewModel::analyserGlycemie,
                            enabled = !uiState.isLoading
                        )
                    }
                    item {
                        RollyAnalyseChip(
                            label = "⚠️ " + stringResource(R.string.chatbot_prevision_risques),
                            onClick = viewModel::previsionRisque,
                            enabled = !uiState.isLoading
                        )
                    }
                    item {
                        RollyAnalyseChip(
                            label = "🥗 " + stringResource(R.string.chatbot_conseils_nutrition),
                            onClick = viewModel::conseilsNutritionnels,
                            enabled = !uiState.isLoading
                        )
                    }
                }
            }

            // Dialog résultat analyse rapide
            if (uiState.showAnalyse && uiState.analyseRapide != null) {
                AlertDialog(
                    onDismissRequest = viewModel::dismissAnalyse,
                    containerColor = if (isDarkTheme) Color(0xFF1A1A2E) else Color.White,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RollyIcon(size = 28.dp, showBackground = true)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Analyse ROLLY",
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) DarkTextPrimary else TextPrimary
                            )
                        }
                    },
                    text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 450.dp)) {
                            item {
                                RichMarkdownText(
                                    text = uiState.analyseRapide ?: "",
                                    textColor = if (isDarkTheme) DarkTextPrimary else TextPrimary
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = viewModel::dismissAnalyse,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) { Text("Fermer", color = Color.White) }
                    }
                )
            }

            // Dialog validation médecin
            if (uiState.showValidationDialog && uiState.availableDoctors.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = viewModel::dismissValidation,
                    containerColor = if (isDarkTheme) Color(0xFF1A1A2E) else Color.White,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RollyIcon(size = 28.dp, showBackground = true)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Validation médicale",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkTheme) DarkTextPrimary else TextPrimary
                                )
                                Text(
                                    "Collaboration IA-Médecin",
                                    fontSize = 11.sp,
                                    color = Primary
                                )
                            }
                        }
                    },
                    text = {
                        Column {
                            Text(
                                "ROLLY recommande de faire valider cette réponse par votre médecin pour une meilleure prise en charge.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                lineHeight = 19.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Choisissez un médecin :",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Primary
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                                items(uiState.availableDoctors) { medecin ->
                                    Surface(
                                        onClick = { viewModel.submitForValidation(medecin) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = SurfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp, Outline
                                        ),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Primary.copy(alpha = 0.15f),
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        medecin.nomComplet.take(1).uppercase(),
                                                        fontWeight = FontWeight.Bold,
                                                        color = Primary
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    "Dr. ${medecin.nomComplet}",
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    medecin.email,
                                                    fontSize = 12.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissValidation) {
                            Text("Passer", color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                )
            }

            // Chargement historique
            if (uiState.isLoadingHistory) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Primary
                        )
                        Text(
                            "Chargement de l'historique...",
                            fontSize = 13.sp,
                            color = chatHistoryText
                        )
                    }
                }
            }

            // Badge compteur historique
            if (uiState.messageCount > 0 && !uiState.isLoadingHistory) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = Primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "📜 ${uiState.messageCount} messages enregistrés — ROLLY se souvient de vos échanges",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        color = Primary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Suggestions pour première conversation
                if (uiState.messages.size == 1) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Suggestions",
                                style = MaterialTheme.typography.labelLarge,
                                color = chatHistoryText,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )
                            viewModel.suggestionsRapides.forEach { suggestion ->
                                Surface(
                                    onClick = {
                                        viewModel.onInputChange(suggestion)
                                        viewModel.envoyerMessage()
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = chatSuggestionBg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, chatSuggestionBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        suggestion,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        fontSize = 13.sp,
                                        color = chatSuggestionText
                                    )
                                }
                            }
                        }
                    }
                }

                items(uiState.messages) { message ->
                    RollyMessageBubble(message = message)
                }
            }

            // Avertissement quota
            if (uiState.quotaStatus.isExceeded) {
                Surface(
                    color = StatusRed.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "⚠️ Limite de ${uiState.quotaStatus.limit} requêtes/jour atteinte. Revenez demain !",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        color = StatusRed,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (uiState.quotaStatus.remaining in 1..3) {
                Surface(
                    color = StatusOrange.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Il vous reste ${uiState.quotaStatus.remaining} requête(s) aujourd'hui",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        color = StatusOrange,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Zone de saisie
            Surface(
                color = if (isDarkTheme) OnBackground else Surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = viewModel::onInputChange,
                        placeholder = {
                            Text(
                                "Demandez à ROLLY...",
                                fontSize = 14.sp,
                                color = if (isDarkTheme) Color.White.copy(alpha = 0.35f) else TextTertiary
                            )
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { viewModel.envoyerMessage() }),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = chatInputBorder,
                            focusedContainerColor = chatInputBg,
                            unfocusedContainerColor = chatInputBg,
                            focusedTextColor = if (isDarkTheme) Color.White else TextPrimary,
                            unfocusedTextColor = if (isDarkTheme) Color.White else TextPrimary,
                            cursorColor = Primary
                        )
                    )
                    // Bouton saisie vocale (Speech API Android, aucune permission requise)
                    VoiceInputButton(
                        onTextRecognized = { recognizedText ->
                            // Concatène au texte déjà présent ou remplace s'il est vide
                            val existing = uiState.inputText.trim()
                            val newText = if (existing.isEmpty()) recognizedText
                                          else "$existing $recognizedText"
                            viewModel.onInputChange(newText)
                        },
                        tint = if (isDarkTheme) Primary else Primary,
                        prompt = "Parle à ROLLY..."
                    )
                    FloatingActionButton(
                        onClick = viewModel::envoyerMessage,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        containerColor = when {
                            uiState.quotaStatus.isExceeded -> StatusRed.copy(alpha = 0.4f)
                            uiState.inputText.isNotBlank() -> Primary
                            else -> if (isDarkTheme) Color.White.copy(alpha = 0.15f) else SurfaceVariant
                        },
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Send,
                                "Envoyer",
                                tint = if (uiState.inputText.isNotBlank()) Color.White
                                       else if (isDarkTheme) Color.White.copy(alpha = 0.5f) else TextTertiary
                            )
                        }
                    }
                }
            }
        }
    }

    // Session drawer - overlay AU-DESSUS du Scaffold (z-order)
    if (uiState.showSessionDrawer) {
        SessionDrawer(
            sessions = uiState.sessions,
            currentSessionId = uiState.currentSessionId,
            onSelectSession = { viewModel.switchToSession(it) },
            onNewSession = { viewModel.createNewSession() },
            onDeleteSession = { viewModel.deleteSession(it) },
            onClose = { viewModel.closeSessionDrawer() }
        )
    }
    } // fin Box
}

// ══════════════════════════════════════════════════════════════════
//  Message Bubble - Style Gemini
// ══════════════════════════════════════════════════════════════════

@Composable
private fun RollyMessageBubble(message: ChatbotMessage) {
    val isUser = message.estUtilisateur
    val isDark = LocalIsDarkTheme.current

    if (isUser) {
        // Message utilisateur - bulle droite
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp, 6.dp, 20.dp, 20.dp),
                color = if (isDark) Primary.copy(alpha = 0.15f) else RollyCardColor,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(
                    text = message.contenu,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = if (isDark) PrimaryContainer else Color.White,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = if (isDark) Primary.copy(alpha = 0.2f) else PrimaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = Primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    } else {
        // Message IA ROLLY
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            // Avatar ROLLY
            Box(modifier = Modifier.padding(top = 4.dp)) {
                RollyIcon(size = 30.dp, showBackground = true)
            }
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (message.enChargement) {
                    LoadingDots()
                } else {
                    RichMarkdownText(
                        text = message.contenu,
                        textColor = if (isDark) Color.White.copy(alpha = 0.9f) else TextPrimary
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  Rendu Markdown Riche - Style Gemini
// ══════════════════════════════════════════════════════════════════

@Composable
fun RichMarkdownText(
    text: String,
    textColor: Color = Color.Unspecified
) {
    val lines = text.split("\n")
    val isDark = LocalIsDarkTheme.current
    val resolvedTextColor = if (textColor == Color.Unspecified) {
        if (isDark) Color.White.copy(alpha = 0.9f) else TextPrimary
    } else textColor
    val headerColor = Primary
    val boldColor = if (isDark) Color.White else TextPrimary
    val bulletColor = if (isDark) PrimaryContainer else Primary
    val tableHeaderBg = if (isDark) Color(0xFF1E3A5F) else DarkTextPrimary
    val tableBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color(0xFFD0CDE0)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()

            when {
                // Ligne vide
                line.isBlank() -> {
                    Spacer(Modifier.height(6.dp))
                }

                // Tableau markdown (détecte |)
                line.trimStart().startsWith("|") -> {
                    val tableLines = mutableListOf<String>()
                    var j = i
                    while (j < lines.size && lines[j].trimStart().startsWith("|")) {
                        val tl = lines[j].trim()
                        // Ignorer la ligne séparateur (|---|---|)
                        if (!tl.matches(Regex("^\\|[-:|\\s]+\\|$"))) {
                            tableLines.add(tl)
                        }
                        j++
                    }
                    if (tableLines.isNotEmpty()) {
                        MarkdownTable(tableLines, tableHeaderBg, tableBorderColor, resolvedTextColor)
                    }
                    i = j
                    continue
                }

                // Header ## ou ### ou #
                line.trimStart().startsWith("###") -> {
                    val content = line.trimStart().removePrefix("###").trim()
                    Text(
                        text = content,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = headerColor.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                line.trimStart().startsWith("##") -> {
                    val content = line.trimStart().removePrefix("##").trim()
                    Text(
                        text = content,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = headerColor,
                        modifier = Modifier.padding(top = 10.dp, bottom = 3.dp)
                    )
                }
                line.trimStart().startsWith("# ") -> {
                    val content = line.trimStart().removePrefix("#").trim()
                    Text(
                        text = content,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = boldColor,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }

                // Numérotation type "1. Titre" → header
                line.trimStart().matches(Regex("^\\d+\\.\\s+.+")) -> {
                    val content = line.trimStart()
                    StyledNumberedItem(content, resolvedTextColor, headerColor)
                }

                // Bullet point (*, -, •)
                line.trimStart().matches(Regex("^[•\\-\\*]\\s+.+")) -> {
                    val content = line.trimStart()
                        .removePrefix("•").removePrefix("-").removePrefix("*")
                        .trim()
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("•", color = bulletColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        StyledInlineText(content, resolvedTextColor, boldColor)
                    }
                }

                // Sub-bullet (indentation)
                line.matches(Regex("^\\s{2,}[•\\-\\*]\\s+.+")) -> {
                    val content = line.trim()
                        .removePrefix("•").removePrefix("-").removePrefix("*")
                        .trim()
                    Row(
                        modifier = Modifier.padding(start = 24.dp, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("◦", color = bulletColor.copy(alpha = 0.6f), fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        StyledInlineText(content, resolvedTextColor.copy(alpha = 0.8f), boldColor)
                    }
                }

                // Alerte / Warning (⚠️, ❗, 🔴)
                line.trimStart().let { it.startsWith("⚠") || it.startsWith("❗") || it.startsWith("🔴") || it.uppercase().startsWith("URGENCE") || it.uppercase().startsWith("ALERTE") } -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StatusRed.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Text(
                            text = line.trimStart(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Color(0xFFFF8A80),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 19.sp
                        )
                    }
                }

                // Texte normal avec gras inline
                else -> {
                    StyledInlineText(line, resolvedTextColor, boldColor)
                }
            }
            i++
        }
    }
}

/**
 * Affiche du texte avec rendu **gras** inline
 */
@Composable
private fun StyledInlineText(
    text: String,
    normalColor: Color,
    boldColor: Color
) {
    val annotated = buildAnnotatedString {
        var remaining = text
        while (remaining.isNotEmpty()) {
            val boldStart = remaining.indexOf("**")
            if (boldStart == -1) {
                withStyle(SpanStyle(color = normalColor, fontSize = 14.sp)) {
                    append(remaining)
                }
                break
            }
            // Texte avant le gras
            if (boldStart > 0) {
                withStyle(SpanStyle(color = normalColor, fontSize = 14.sp)) {
                    append(remaining.substring(0, boldStart))
                }
            }
            remaining = remaining.substring(boldStart + 2)
            val boldEnd = remaining.indexOf("**")
            if (boldEnd == -1) {
                withStyle(SpanStyle(color = normalColor, fontSize = 14.sp)) {
                    append("**$remaining")
                }
                break
            }
            // Texte en gras
            withStyle(SpanStyle(color = boldColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)) {
                append(remaining.substring(0, boldEnd))
            }
            remaining = remaining.substring(boldEnd + 2)
        }
    }
    Text(
        text = annotated,
        lineHeight = 22.sp,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

/**
 * Affiche un item numéroté "1. Titre : description" avec le titre en gras/couleur
 */
@Composable
private fun StyledNumberedItem(
    content: String,
    textColor: Color,
    headerColor: Color
) {
    val isDark = LocalIsDarkTheme.current
    val boldColor = if (isDark) Color.White else TextPrimary

    // Extraire le numéro et le reste
    val match = Regex("^(\\d+)\\.\\s+(.+)").find(content.trim())
    if (match != null) {
        val number = match.groupValues[1]
        val rest = match.groupValues[2]

        // Vérifier si c'est un titre (première lettre majuscule, possiblement avec ":")
        val colonIndex = rest.indexOf(":")
        val isTitle = rest.first().isUpperCase() && (colonIndex > 0 || rest.length < 80)

        if (isTitle && colonIndex > 0) {
            val title = rest.substring(0, colonIndex).trim()
            val desc = rest.substring(colonIndex + 1).trim()

            Column(modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = headerColor, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)) {
                            append("$number. $title")
                        }
                    }
                )
                if (desc.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    StyledInlineText(desc, textColor, boldColor)
                }
            }
        } else {
            // Numérotation simple sans sous-titre
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) {
                val annotated = buildAnnotatedString {
                    withStyle(SpanStyle(color = headerColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)) {
                        append("$number. ")
                    }
                    // Le reste peut contenir du **gras**
                    var remaining = rest
                    while (remaining.isNotEmpty()) {
                        val bs = remaining.indexOf("**")
                        if (bs == -1) {
                            withStyle(SpanStyle(color = textColor, fontSize = 14.sp)) {
                                append(remaining)
                            }
                            break
                        }
                        if (bs > 0) {
                            withStyle(SpanStyle(color = textColor, fontSize = 14.sp)) {
                                append(remaining.substring(0, bs))
                            }
                        }
                        remaining = remaining.substring(bs + 2)
                        val be = remaining.indexOf("**")
                        if (be == -1) {
                            withStyle(SpanStyle(color = textColor, fontSize = 14.sp)) {
                                append("**$remaining")
                            }
                            break
                        }
                        withStyle(SpanStyle(color = boldColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)) {
                            append(remaining.substring(0, be))
                        }
                        remaining = remaining.substring(be + 2)
                    }
                }
                Text(text = annotated, lineHeight = 22.sp)
            }
        }
    } else {
        StyledInlineText(content, textColor, boldColor)
    }
}

/**
 * Tableau markdown formaté
 */
@Composable
private fun MarkdownTable(
    rows: List<String>,
    headerBg: Color,
    borderColor: Color,
    textColor: Color
) {
    val isDark = LocalIsDarkTheme.current
    val parsedRows = rows.map { row ->
        row.trim().removePrefix("|").removeSuffix("|")
            .split("|").map { it.trim() }
    }

    if (parsedRows.isEmpty()) return

    val tableBg = if (isDark) DarkSurface else Color(0xFFF5F3FF)
    val altRowBg = if (isDark) Color.White.copy(alpha = 0.03f) else Color(0xFFECEAFF).copy(alpha = 0.5f)
    val headerTextColor = if (isDark) DarkTextPrimary else Color.White
    val cellTextColor = if (isDark) textColor.copy(alpha = 0.85f) else TextPrimary.copy(alpha = 0.85f)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tableBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column {
            parsedRows.forEachIndexed { rowIdx, cells ->
                val isHeader = rowIdx == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isHeader) Modifier.background(headerBg)
                            else if (rowIdx % 2 == 0) Modifier.background(altRowBg)
                            else Modifier
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    cells.forEach { cell ->
                        Text(
                            text = cell.replace("**", ""),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            fontSize = if (isHeader) 11.sp else 12.sp,
                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                            color = if (isHeader) headerTextColor else cellTextColor,
                            lineHeight = 16.sp
                        )
                    }
                }
                if (rowIdx < parsedRows.size - 1) {
                    HorizontalDivider(color = borderColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  Animation de chargement (3 dots pulsants)
// ══════════════════════════════════════════════════════════════════

@Composable
private fun LoadingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1 = infiniteTransition.animateFloat(
        0.3f, 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "d1"
    )
    val dot2 = infiniteTransition.animateFloat(
        0.3f, 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200), RepeatMode.Reverse),
        label = "d2"
    )
    val dot3 = infiniteTransition.animateFloat(
        0.3f, 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400), RepeatMode.Reverse),
        label = "d3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        listOf(dot1, dot2, dot3).forEach { anim ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = anim.value))
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  Chips d'analyse
// ══════════════════════════════════════════════════════════════════

@Composable
private fun RollyAnalyseChip(label: String, onClick: () -> Unit, enabled: Boolean) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = Primary.copy(alpha = if (enabled) 0.12f else 0.05f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Primary.copy(alpha = if (enabled) 0.3f else 0.1f)
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) PrimaryContainer else Color.White.copy(alpha = 0.3f)
        )
    }
}

// ══════════════════════════════════════════════════════════════════
//  Session Drawer - Liste des conversations (style ChatGPT)
// ══════════════════════════════════════════════════════════════════

@Composable
private fun SessionDrawer(
    sessions: List<ChatSession>,
    currentSessionId: String?,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onClose: () -> Unit
) {
    // Full screen overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .statusBarsPadding()
    ) {
        // Clickable backdrop to close
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClose)
        )

        // Drawer panel
        Surface(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart),
            color = Color(0xFF111128),
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "💬 " + stringResource(R.string.chatbot_discussions),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Fermer", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                    }
                }

                // New chat button
                Surface(
                    onClick = onNewSession,
                    shape = RoundedCornerShape(12.dp),
                    color = Primary.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.chatbot_new_discussion), color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Session list
                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Aucune discussion.\nCommencez a discuter avec ROLLY !",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Group sessions by date
                    val now = System.currentTimeMillis()
                    val todayStart = now - (now % 86400000)
                    val yesterdayStart = todayStart - 86400000
                    val weekStart = todayStart - 7 * 86400000

                    val grouped = sessions.groupBy { s ->
                        when {
                            s.lastMessageAt >= todayStart -> "Aujourd'hui"
                            s.lastMessageAt >= yesterdayStart -> "Hier"
                            s.lastMessageAt >= weekStart -> "7 derniers jours"
                            else -> {
                                val cal = java.util.Calendar.getInstance()
                                cal.timeInMillis = s.lastMessageAt
                                val month = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.FRANCE).format(cal.time)
                                month.replaceFirstChar { it.uppercase() }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        grouped.forEach { (label, sessionList) ->
                            item {
                                Text(
                                    label,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                            items(sessionList) { session ->
                                SessionItem(
                                    session = session,
                                    isActive = session.id == currentSessionId,
                                    onClick = { onSelectSession(session.id) },
                                    onDelete = { onDeleteSession(session.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: ChatSession,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) Primary.copy(alpha = 0.1f) else Color.Transparent,
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.2f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💬", fontSize = 14.sp, modifier = Modifier.padding(end = 10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (session.lastPreview.isNotBlank()) {
                    Text(
                        session.lastPreview,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    "Supprimer",
                    tint = StatusRed.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
