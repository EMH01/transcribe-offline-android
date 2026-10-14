package com.emh01.transcribe.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emh01.transcribe.ChatMessage
import com.emh01.transcribe.ChatConversation
import com.emh01.transcribe.ChatRole
import com.emh01.transcribe.ChatVoiceState
import com.emh01.transcribe.R
import com.emh01.transcribe.TranscribeStage
import com.emh01.transcribe.TranscribeUiState
import com.emh01.transcribe.TranscribeViewModel
import com.emh01.transcribe.WorkspaceMode
import kotlinx.coroutines.delay

private val LightColors = lightColorScheme(
    primary = Color(0xFF356A63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9ECE3),
    onPrimaryContainer = Color(0xFF00201C),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFF7FAF8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ED1C8),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF1C4F48),
    onPrimaryContainer = Color(0xFFB9ECE3),
    background = Color(0xFF0F1513),
    surface = Color(0xFF0F1513),
)

@Composable
fun TranscribeTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}

@Composable
fun TranscribeApp(viewModel: TranscribeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showGlossary by remember { mutableStateOf(false) }
    var glossaryDraft by remember(state.glossary) { mutableStateOf(state.glossary) }
    var pendingChatMic by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (pendingChatMic) viewModel.startChatRecording()
            else viewModel.startRecording()
        } else {
            if (pendingChatMic) viewModel.showChatMicrophonePermissionError()
            else viewModel.showMicrophonePermissionError()
        }
        pendingChatMic = false
    }

    fun requestMic(forChat: Boolean) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            if (forChat) viewModel.startChatRecording()
            else viewModel.startRecording()
        } else {
            pendingChatMic = forChat
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    TranscribeScreen(
        state = state,
        onRecordRequested = { requestMic(false) },
        onStop = viewModel::stopAndTranscribe,
        onTextChanged = viewModel::updateText,
        onReset = viewModel::reset,
        onImprove = viewModel::improveWriting,
        onRestoreOriginal = viewModel::restoreOriginalText,
        onModeChange = viewModel::switchMode,
        onChatInputChanged = viewModel::updateChatInput,
        onChatMic = {
            if (state.chatVoiceState == ChatVoiceState.Recording) {
                viewModel.stopChatRecording()
            } else if (state.chatVoiceState == ChatVoiceState.Idle) {
                requestMic(true)
            }
        },
        onSendChat = viewModel::sendChatMessage,
        onNewChat = viewModel::startNewConversation,
        onShowChatHistory = viewModel::showChatHistory,
        onHideChatHistory = viewModel::hideChatHistory,
        onOpenConversation = viewModel::openConversation,
        onDeleteConversation = viewModel::deleteConversation,
        onGlossaryRequested = {
            glossaryDraft = state.glossary
            showGlossary = true
        },
    )

    if (showGlossary) {
        AlertDialog(
            onDismissRequest = { showGlossary = false },
            title = { Text("Vocabulario especial") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Añade nombres propios y términos que el reconocimiento de voz y el asistente local deban conservar con precisión. Se guarda solo en este teléfono.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = glossaryDraft,
                        onValueChange = { glossaryDraft = it.take(500) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombres y términos") },
                        placeholder = { Text("Ej.: nombre propio, término técnico, sigla") },
                        minLines = 3,
                        maxLines = 6,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveGlossary(glossaryDraft)
                        showGlossary = false
                    },
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGlossary = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun TranscribeScreen(
    state: TranscribeUiState,
    onRecordRequested: () -> Unit,
    onStop: () -> Unit,
    onTextChanged: (String) -> Unit,
    onReset: () -> Unit,
    onImprove: () -> Unit,
    onRestoreOriginal: () -> Unit,
    onModeChange: (WorkspaceMode) -> Unit,
    onChatInputChanged: (String) -> Unit,
    onChatMic: () -> Unit,
    onSendChat: () -> Unit,
    onNewChat: () -> Unit,
    onShowChatHistory: () -> Unit,
    onHideChatHistory: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onGlossaryRequested: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, top = 2.dp, end = 24.dp, bottom = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.loquia_logo_mark),
                        contentDescription = "Logo de Loquia",
                        modifier = Modifier.size(42.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Loquia",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Asistente de voz privado, local y sin conexión",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Keep the glossary action in its own row so it cannot squeeze
                // the app name or split words into individual letters.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onGlossaryRequested) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Vocabulario",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                            Text(
                                text = "especial",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                ModeTabs(
                    selected = state.mode,
                    onModeChange = onModeChange,
                    enabled = state.stage != TranscribeStage.Recording &&
                        state.stage != TranscribeStage.Processing &&
                        state.chatVoiceState == ChatVoiceState.Idle,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            if (state.mode == WorkspaceMode.Chat) {
                AssistantContent(
                    state = state,
                    onInputChanged = onChatInputChanged,
                    onMic = onChatMic,
                    onSend = onSendChat,
                    onNewChat = onNewChat,
                    onShowHistory = onShowChatHistory,
                    onHideHistory = onHideChatHistory,
                    onOpenConversation = onOpenConversation,
                    onDeleteConversation = onDeleteConversation,
                )
            } else {
                AnimatedContent(
                    targetState = state.stage,
                    modifier = Modifier.fillMaxSize(),
                    label = "transcribe-stage",
                ) { stage ->
                    when (stage) {
                        TranscribeStage.Idle -> IdleContent(onRecordRequested)
                        TranscribeStage.Recording -> RecordingContent(state, onStop)
                        TranscribeStage.Processing -> ProcessingContent()
                        TranscribeStage.Result -> {
                            val onCopy = {
                                val clipboard = context.getSystemService(
                                    android.content.Context.CLIPBOARD_SERVICE,
                                ) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        "Transcripción",
                                        state.text,
                                    ),
                                )
                            }
                            val onShare = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, state.text)
                                }
                                context.startActivity(
                                    Intent.createChooser(intent, "Compartir texto"),
                                )
                            }

                            ResultContent(
                                state = state,
                                onTextChanged = onTextChanged,
                                onReset = onReset,
                                onImprove = onImprove,
                                onRestoreOriginal = onRestoreOriginal,
                                onCopy = onCopy,
                                onShare = onShare,
                            )
                        }
                        TranscribeStage.Error -> ErrorContent(
                            message = state.errorMessage.orEmpty(),
                            onReset = onReset,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTabs(
    selected: WorkspaceMode,
    onModeChange: (WorkspaceMode) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selected == WorkspaceMode.Transcribe) {
            FilledTonalButton(
                onClick = { onModeChange(WorkspaceMode.Transcribe) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            ) {
                Text("Transcribir")
            }
            OutlinedButton(
                onClick = { onModeChange(WorkspaceMode.Chat) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            ) {
                Text("Chat")
            }
        } else {
            OutlinedButton(
                onClick = { onModeChange(WorkspaceMode.Transcribe) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            ) {
                Text("Transcribir")
            }
            FilledTonalButton(
                onClick = { onModeChange(WorkspaceMode.Chat) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            ) {
                Text("Chat")
            }
        }
    }
}

@Composable
private fun AssistantContent(
    state: TranscribeUiState,
    onInputChanged: (String) -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onNewChat: () -> Unit,
    onShowHistory: () -> Unit,
    onHideHistory: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
) {
    if (state.isChatHistoryVisible) {
        ChatHistoryContent(
            conversations = state.chatConversations,
            onBack = onHideHistory,
            onNewChat = onNewChat,
            onOpenConversation = onOpenConversation,
            onDeleteConversation = onDeleteConversation,
        )
        return
    }

    val listState = rememberLazyListState()

    LaunchedEffect(state.chatMessages.size, state.isGeneratingChat) {
        val extra = if (state.isGeneratingChat) 1 else 0
        val target = state.chatMessages.size + extra - 1
        if (target >= 0) {
            listState.scrollToItem(target)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Asistente",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Parakeet + LFM2.5 1.2B · local",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onShowHistory,
                enabled = !state.isGeneratingChat && state.chatVoiceState == ChatVoiceState.Idle,
            ) { Text("Chats") }
            TextButton(
                onClick = onNewChat,
                enabled = !state.isGeneratingChat && state.chatVoiceState == ChatVoiceState.Idle,
            ) { Text("Nuevo") }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (state.chatMessages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "¿Qué quieres hacer?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Escribe una petición o usa el micrófono. La voz aparecerá primero en el cuadro para que puedas revisarla antes de enviar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            itemsIndexed(state.chatMessages) { _, message ->
                ChatBubble(message)
            }

            if (state.isGeneratingChat) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Pensando…")
                            }
                        }
                    }
                }
            }
        }

        state.chatError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.chatVoiceState == ChatVoiceState.Recording) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Grabando… toca ■ para detener",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.weight(1f))
                MiniWaveBars(state.chatVoiceAmplitude)
            }
        } else if (state.chatVoiceState == ChatVoiceState.Processing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Pasando tu voz a texto…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (state.chatVoiceProcessingMs > 0L) {
            Text(
                text = "Último dictado transcrito en %.1f s".format(
                    state.chatVoiceProcessingMs / 1000f,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.chatInput,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        when (state.chatVoiceState) {
                            ChatVoiceState.Idle -> "Escribe un mensaje…"
                            ChatVoiceState.Recording -> "Escuchando…"
                            ChatVoiceState.Processing -> "Transcribiendo…"
                        },
                    )
                },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                enabled = state.chatVoiceState != ChatVoiceState.Processing,
            )

            FilledTonalButton(
                onClick = onMic,
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                enabled = state.chatVoiceState != ChatVoiceState.Processing &&
                    !state.isGeneratingChat,
            ) {
                if (state.chatVoiceState == ChatVoiceState.Recording) {
                    Text(text = "■", fontSize = 20.sp)
                } else {
                    ActionIcon(
                        drawable = R.drawable.ic_action_microphone,
                        description = "Dictar mensaje",
                    )
                }
            }

            Button(
                onClick = onSend,
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                enabled = state.chatInput.isNotBlank() &&
                    !state.isGeneratingChat &&
                    state.chatVoiceState == ChatVoiceState.Idle,
            ) {
                ActionIcon(
                    drawable = R.drawable.ic_action_send,
                    description = "Enviar mensaje",
                )
            }
        }
    }
}

@Composable
private fun ChatHistoryContent(
    conversations: List<ChatConversation>,
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
) {
    var pendingDeletion by remember { mutableStateOf<ChatConversation?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ Volver") }
            Text(
                text = "Chats",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onNewChat) { Text("+ Nuevo") }
        }
        Text(
            text = "Guardados solo en este teléfono",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (conversations.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Aún no tienes conversaciones guardadas.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(conversations) { _, conversation ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenConversation(conversation.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conversation.title,
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${conversation.messages.size} mensajes · local",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { pendingDeletion = conversation }) {
                                Text("Eliminar")
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeletion?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Eliminar conversación") },
            text = { Text("Se borrará definitivamente de este teléfono y no podrá recuperarse.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConversation(conversation.id)
                    pendingDeletion = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatRole.User

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (!isUser && message.elapsedMs > 0L) {
                    Text(
                        text = "LFM2.5 1.2B · %.1f s".format(message.elapsedMs / 1000f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent(onRecordRequested: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Habla y conviértelo en texto",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "El audio se procesa directamente en este teléfono.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onRecordRequested,
            modifier = Modifier.size(116.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
        ) {
            ActionIcon(
                drawable = R.drawable.ic_action_microphone,
                description = "Iniciar transcripción",
                size = 88.dp,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Toca para hablar",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ActionIcon(
    @androidx.annotation.DrawableRes drawable: Int,
    description: String,
    size: androidx.compose.ui.unit.Dp = 32.dp,
) {
    Image(
        painter = painterResource(id = drawable),
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun RecordingContent(state: TranscribeUiState, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Grabando",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        RecordingTimer()
        Spacer(Modifier.height(28.dp))
        WaveBars(amplitude = state.amplitude)
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onStop,
            modifier = Modifier.size(116.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(text = "■", fontSize = 38.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Detener y transcribir",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RecordingTimer() {
    var startedAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var elapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        startedAt = SystemClock.elapsedRealtime()
        while (true) {
            elapsedMs = SystemClock.elapsedRealtime() - startedAt
            delay(250)
        }
    }

    val totalSeconds = elapsedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    Text(
        text = "%02d:%02d".format(minutes, seconds),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun WaveBars(amplitude: Float) {
    val factors = listOf(0.45f, 0.8f, 1f, 0.72f, 0.52f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(72.dp),
    ) {
        factors.forEach { factor ->
            val target = (14 + 52 * (amplitude * factor).coerceIn(0f, 1f)).dp
            val height by animateDpAsState(targetValue = target, label = "wave-bar")
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(height)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun MiniWaveBars(amplitude: Float) {
    val factors = listOf(0.55f, 1f, 0.72f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(24.dp),
    ) {
        factors.forEach { factor ->
            val target = (6 + 16 * (amplitude * factor).coerceIn(0f, 1f)).dp
            val height by animateDpAsState(targetValue = target, label = "chat-wave-bar")
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.error),
            )
        }
    }
}

@Composable
private fun ProcessingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(58.dp))
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Transcribiendo…",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Parakeet está procesando el audio en el teléfono.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultContent(
    state: TranscribeUiState,
    onTextChanged: (String) -> Unit,
    onReset: () -> Unit,
    onImprove: () -> Unit,
    onRestoreOriginal: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Transcripción",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = state.text,
            onValueChange = onTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(20.dp),
            placeholder = { Text("No se reconoció texto.") },
        )

        AnimatedVisibility(visible = state.processingMs > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Audio %.1f s · Procesado en %.1f s · RTF %.1f×".format(
                        state.originalAudioMs / 1000f,
                        state.processingMs / 1000f,
                        state.realtimeFactor,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.processedAudioMs in 1 until state.originalAudioMs) {
                    Text(
                        text = "Voz útil: %.1f s después de recortar silencios".format(
                            state.processedAudioMs / 1000f,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        FilledTonalButton(
            onClick = onImprove,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.text.isNotBlank() &&
                state.llmAvailable &&
                !state.isImprovingText,
        ) {
            if (state.isImprovingText) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text("Mejorando redacción…")
            } else {
                Text("✨ Mejorar redacción")
            }
        }

        if (!state.llmAvailable) {
            Text(
                text = "La mejora con IA local requiere un dispositivo Android de 64 bits.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.improvementError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.improvementMs > 0L) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "LFM2.5 1.2B · %.1f s".format(state.improvementMs / 1000f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (state.originalText != null) {
                    TextButton(onClick = onRestoreOriginal) {
                        Text("Restaurar original")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = onCopy,
                modifier = Modifier.weight(1f),
                enabled = state.text.isNotBlank(),
            ) {
                Text("Copiar")
            }
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f),
                enabled = state.text.isNotBlank(),
            ) {
                Text("Compartir")
            }
        }

        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Nueva grabación")
        }
    }
}

@Composable
private fun ErrorContent(message: String, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No se pudo completar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                SelectionContainer {
                    Text(
                        text = message.ifBlank { "Ha ocurrido un error." },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onReset) {
            Text("Volver")
        }
    }
}
