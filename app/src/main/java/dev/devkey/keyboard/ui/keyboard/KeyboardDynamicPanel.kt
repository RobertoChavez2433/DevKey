package dev.devkey.keyboard.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.devkey.keyboard.core.KeyboardActionBridge
import dev.devkey.keyboard.core.KeyboardModeManager
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.data.db.entity.ClipboardHistoryEntity
import dev.devkey.keyboard.data.db.entity.MacroEntity
import dev.devkey.keyboard.feature.clipboard.ClipboardRepository
import dev.devkey.keyboard.feature.command.InputMode
import dev.devkey.keyboard.feature.macro.MacroEngine
import dev.devkey.keyboard.feature.macro.MacroRepository
import dev.devkey.keyboard.feature.macro.MacroSerializer
import dev.devkey.keyboard.feature.macro.MacroStep
import dev.devkey.keyboard.feature.prediction.PredictionResult
import dev.devkey.keyboard.feature.voice.VoiceInputEngine
import dev.devkey.keyboard.ui.clipboard.ClipboardPanel
import dev.devkey.keyboard.ui.macro.MacroChipStrip
import dev.devkey.keyboard.ui.macro.MacroGridPanel
import dev.devkey.keyboard.ui.macro.MacroNameDialog
import dev.devkey.keyboard.ui.macro.MacroRecordingBar
import dev.devkey.keyboard.ui.suggestion.SuggestionBar
import dev.devkey.keyboard.ui.voice.VoiceInputPanel
import kotlinx.coroutines.launch

/**
 * Renders the dynamic middle panel area based on [keyboardMode].
 *
 * Covers: suggestion bar (Normal), macro chip strip, macro grid, macro recording bar,
 * clipboard panel, and voice input panel. No panel is rendered for Symbols mode.
 * Also renders [MacroNameDialog] when a recording stop requires naming.
 *
 * @param onRecordingStopped Called with captured [MacroStep] list when the user taps
 *   "stop"; shell uses this to set [pendingMacroSteps] and show the name dialog.
 * @param onMacroNameDialogDismiss Called on save or cancel of the name dialog.
 */
@Composable
fun KeyboardDynamicPanel(
    keyboardMode: KeyboardMode,
    modeManager: KeyboardModeManager,
    bridge: KeyboardActionBridge,
    modifierState: ModifierStateManager,
    macroRepo: MacroRepository,
    macroEngine: MacroEngine,
    clipboardRepo: ClipboardRepository,
    voiceInputEngine: VoiceInputEngine,
    voiceState: VoiceInputEngine.VoiceState,
    voiceAmplitude: Float,
    macros: List<MacroEntity>,
    clipboardEntries: List<ClipboardHistoryEntity>,
    predictions: List<PredictionResult>,
    suggestionBarCollapsed: Boolean,
    onSuggestionBarCollapseToggle: () -> Unit,
    macroNameDialogVisible: Boolean,
    pendingMacroSteps: List<MacroStep>,
    onRecordingStopped: (List<MacroStep>) -> Unit,
    onMacroNameDialogDismiss: () -> Unit,
    @Suppress("UnusedParameter") inputMode: InputMode,
    currentInputConnection: (() -> android.view.inputmethod.InputConnection?)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    fun onMacroClick(macro: MacroEntity) {
        macroEngine.replay(MacroSerializer.deserialize(macro.keySequence), bridge, modifierState)
        coroutineScope.launch { macroRepo.incrementUsage(macro.id) }
        modeManager.setMode(KeyboardMode.Normal)
    }

    when (keyboardMode) {
        KeyboardMode.Normal -> if (predictions.isNotEmpty()) {
            SuggestionBar(
                predictions = predictions,
                onSuggestionClick = { result ->
                    val ic = currentInputConnection?.invoke()
                    if (ic != null) {
                        // commitText replaces any active composing region automatically
                        ic.commitText(result.word + " ", 1)
                    } else {
                        bridge.onText(result.word + " ")
                    }
                    // Reset legacy ImeState so next keystroke starts a fresh composition
                    SessionDependencies.resetPredictionState?.invoke()
                    SessionDependencies.commitWord(result.word, coroutineScope)
                    SessionDependencies.composingWord.value = ""
                    SessionDependencies.pendingCorrection.value = null
                    SessionDependencies.triggerNextSuggestions?.invoke()
                },
                onSuggestionLongPress = { result ->
                    val wordToAdd = SessionDependencies.composingWord.value.ifBlank { result.word }
                    SessionDependencies.learningEngine?.let { le ->
                        coroutineScope.launch {
                            le.addCustomWord(wordToAdd)
                            android.widget.Toast.makeText(context, "\"$wordToAdd\" added to dictionary", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onCollapseToggle = onSuggestionBarCollapseToggle,
                isCollapsed = suggestionBarCollapsed
            )
        }
        KeyboardMode.MacroChips -> MacroChipStrip(
            macros = macros,
            onMacroClick = { onMacroClick(it) },
            onAddClick = { macroEngine.startRecording(); modeManager.setMode(KeyboardMode.MacroRecording) },
            onCollapse = { modeManager.setMode(KeyboardMode.Normal) }
        )
        KeyboardMode.MacroGrid -> MacroGridPanel(
            macros = macros,
            onMacroClick = { onMacroClick(it) },
            onRecordClick = { macroEngine.startRecording(); modeManager.setMode(KeyboardMode.MacroRecording) },
            onEditMacro = { macro -> coroutineScope.launch { macroRepo.updateMacroName(macro.id, macro.name) } },
            onDeleteMacro = { macro -> coroutineScope.launch { macroRepo.deleteMacro(macro.id) } }
        )
        KeyboardMode.MacroRecording -> MacroRecordingBar(
            capturedSteps = macroEngine.getCapturedSteps(),
            onCancel = { macroEngine.cancelRecording(); modeManager.setMode(KeyboardMode.Normal) },
            onStop = { onRecordingStopped(macroEngine.stopRecording()) }
        )
        KeyboardMode.Clipboard -> ClipboardPanel(
            entries = clipboardEntries,
            onPaste = { entry -> bridge.onText(entry.content); modeManager.setMode(KeyboardMode.Normal) },
            onPin = { id -> coroutineScope.launch { clipboardRepo.pin(id) } },
            onUnpin = { id -> coroutineScope.launch { clipboardRepo.unpin(id) } },
            onDelete = { id -> coroutineScope.launch { clipboardRepo.delete(id) } },
            onClearAll = { coroutineScope.launch { clipboardRepo.clearAll() } }
        )
        KeyboardMode.Symbols -> { /* No suggestion bar for symbols layout */ }
        KeyboardMode.Voice -> VoiceInputPanel(
            voiceState = voiceState,
            amplitude = voiceAmplitude,
            onStop = {
                coroutineScope.launch {
                    val transcription = voiceInputEngine.stopListening()
                    if (voiceInputEngine.shouldCommitTranscription(transcription)) {
                        bridge.onText(transcription)
                    }
                    modeManager.setMode(KeyboardMode.Normal)
                }
            },
            onCancel = { voiceInputEngine.cancelListening(); modeManager.setMode(KeyboardMode.Normal) }
        )
    }

    if (macroNameDialogVisible) {
        MacroNameDialog(
            onSave = { name ->
                coroutineScope.launch { macroRepo.saveMacro(name, pendingMacroSteps) }
                onMacroNameDialogDismiss()
                modeManager.setMode(KeyboardMode.Normal)
            },
            onCancel = { onMacroNameDialogDismiss(); modeManager.setMode(KeyboardMode.Normal) }
        )
    }
}
