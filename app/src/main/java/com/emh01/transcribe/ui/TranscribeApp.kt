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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startRecording()
        else viewModel.showMicrophonePermissionError()
    }

    val onRecordRequested = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    TranscribeScreen(
        state = state,
        onRecordRequested = onRecordRequested,
        onStop = viewModel::stopAndTranscribe,
        onTextChanged = viewModel::updateText,
        onInstructionChanged = viewModel::updateInstruction,
        onReset = viewModel::reset,
        onImprove = viewModel::improveWriting,
        onGenerateDraft = viewModel::generateDraft,
        onRestoreOriginal = viewModel::restoreOriginalText,
        onModeChange = viewModel::switchMode,
        onGlossaryRequested = {
            glossaryDraft = state.glossary
            showGlossary = true
        },
    )

    if (showGlossary) {
        AlertDialog(
            onDismissRequest = { showGlossary = false },
            title = { Text("Vocabulario local") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Añade nombres propios y palabras que Whisper y la IA local deban conservar con precisión. Se guarda solo en este teléfono.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = glossaryDraft,
                        onValueChange = { glossaryDraft = it.take(500) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombres y términos") },
                        placeholder = { Text("Ej.: Esther María, OpenAI, NumPy") },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscribeScreen(
    state: TranscribeUiState,
    onRecordRequested: () -> Unit,
    onStop: () -> Unit,
    onTextChanged: (String) -> Unit,
    onInstructionChanged: (String) -> Unit,
    onReset: () -> Unit,
    onImprove: () -> Unit,
    onGenerateDraft: () -> Unit,
    onRestoreOriginal: () -> Unit,
    onModeChange: (WorkspaceMode) -> Unit,
    onGlossaryRequested: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Transcribe",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Privado · sin conexión",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onGlossaryRequested) {
                        Text("Vocabulario")
                    }
                }

                Spacer(Modifier.height(8.dp))
                ModeTabs(
                    selected = state.mode,
                    onModeChange = onModeChange,
                    enabled = state.stage != TranscribeStage.Recording &&
                        state.stage != TranscribeStage.Processing,
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
            AnimatedContent(
                targetState = state.stage,
                modifier = Modifier.fillMaxSize(),
                label = "transcribe-stage",
            ) { stage ->
                when (stage) {
                    TranscribeStage.Idle -> IdleContent(
                        mode = state.mode,
                        onRecordRequested = onRecordRequested,
                    )
                    TranscribeStage.Recording -> RecordingContent(state, onStop)
                    TranscribeStage.Processing -> ProcessingContent(state.mode)
                    TranscribeStage.Result -> {
                        val onCopy = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("Texto", state.text),
                            )
                        }
                        val onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, state.text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir texto"))
                        }

                        if (state.mode == WorkspaceMode.Transcribe) {
                            ResultContent(
                                state = state,
                                onTextChanged = onTextChanged,
                                onReset = onReset,
                                onImprove = onImprove,
                                onRestoreOriginal = onRestoreOriginal,
                                onCopy = onCopy,
                                onShare = onShare,
                            )
                        } else {
                            DraftContent(
                                state = state,
                                onInstructionChanged = onInstructionChanged,
                                onTextChanged = onTextChanged,
                                onGenerate = onGenerateDraft,
                                onReset = onReset,
                                onCopy = onCopy,
                                onShare = onShare,
                            )
                        }
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
                onClick = { onModeChange(WorkspaceMode.Write) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            ) {
                Text("Redactar")
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
                onClick = { onModeChange(WorkspaceMode.Write) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            ) {
                Text("Redactar")
            }
        }
    }
}

@Composable
private fun IdleContent(
    mode: WorkspaceMode,
    onRecordRequested: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (mode == WorkspaceMode.Transcribe) {
                "Habla y conviértelo en texto"
            } else {
                "Dime qué quieres redactar"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (mode == WorkspaceMode.Transcribe) {
                "El audio se procesa directamente en este teléfono."
            } else {
                "Tu orden se transcribe y la IA local redacta el texto sin conexión."
            },
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
            Text(text = "🎙", fontSize = 42.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = if (mode == WorkspaceMode.Transcribe) {
                "Toca para hablar"
            } else {
                "Toca para dictar la orden"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RecordingContent(state: TranscribeUiState, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (state.mode == WorkspaceMode.Transcribe) {
                "Grabando"
            } else {
                "Escuchando tu orden"
            },
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
            text = if (state.mode == WorkspaceMode.Transcribe) {
                "Detener y transcribir"
            } else {
                "Detener y revisar orden"
            },
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
private fun ProcessingContent(mode: WorkspaceMode) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(58.dp))
        Spacer(Modifier.height(28.dp))
        Text(
            text = if (mode == WorkspaceMode.Transcribe) {
                "Transcribiendo…"
            } else {
                "Preparando la orden…"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (mode == WorkspaceMode.Transcribe) {
                "Whisper está procesando el audio en el teléfono."
            } else {
                "Whisper está convirtiendo tu instrucción en texto para que puedas revisarla."
            },
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
                    text = "Gemma 3 1B · %.1f s".format(state.improvementMs / 1000f),
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
private fun DraftContent(
    state: TranscribeUiState,
    onInstructionChanged: (String) -> Unit,
    onTextChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onReset: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Redactar",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = "Revisa la orden antes de ejecutarla",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.instruction,
            onValueChange = onInstructionChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Instrucción") },
            placeholder = {
                Text("Ej.: Redáctame por puntos las ventajas de trabajar sin conexión…")
            },
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(18.dp),
        )

        AnimatedVisibility(visible = state.processingMs > 0) {
            Text(
                text = "Orden transcrita en %.1f s".format(state.processingMs / 1000f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.instruction.isNotBlank() &&
                state.llmAvailable &&
                !state.isGeneratingDraft,
        ) {
            if (state.isGeneratingDraft) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text("Redactando…")
            } else {
                Text("✨ Redactar")
            }
        }

        if (!state.llmAvailable) {
            Text(
                text = "La redacción con IA local requiere un dispositivo Android de 64 bits.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.draftError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.text.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Resultado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (state.draftMs > 0) {
                    Text(
                        text = "Gemma 3 1B · %.1f s".format(state.draftMs / 1000f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(20.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Copiar")
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Compartir")
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Nueva orden")
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
