package dev.devkey.keyboard.ui.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import dev.devkey.keyboard.core.KeyboardActionListener
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.debug.KeyMapGenerator
import dev.devkey.keyboard.feature.command.InputMode
import dev.devkey.keyboard.feature.macro.MacroStep
import dev.devkey.keyboard.ui.toolbar.ToolbarChevronBar
import dev.devkey.keyboard.ui.toolbar.ToolbarRow
import kotlinx.coroutines.launch

/**
 * Root composable for the DevKey keyboard.
 *
 * Assembles the toolbar, dynamic middle area, and keyboard view
 * into a single keyboard UI, wired to the legacy IME action listener.
 * Supports multiple modes: Normal, MacroChips, MacroGrid, MacroRecording,
 * Clipboard, Symbols, and Voice.
 *
 * @param actionListener The IME action listener to bridge key events to.
 * @param currentInputConnection Provider for the current InputConnection (for smart backspace/esc).
 */
@Composable
fun DevKeyKeyboard(
    actionListener: KeyboardActionListener,
    currentInputConnection: (() -> android.view.inputmethod.InputConnection?)? = null
) {
    val context = LocalContext.current
    val currentView = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val prefs = rememberKeyboardPreferences()
    val deps = rememberKeyboardDependencies(actionListener, prefs.layoutMode)
    val predictions = rememberPredictions()

    val keyboardMode by deps.modeManager.mode.collectAsState()
    val ctrlState by deps.modifierState.ctrlState.collectAsState()
    val macros by deps.macroRepo.getAllMacros().collectAsState(initial = emptyList())
    val clipboardEntries by deps.clipboardRepo.getAll().collectAsState(initial = emptyList())
    val inputMode by deps.commandModeDetector.inputMode.collectAsState()
    val voiceState by deps.voiceInputEngine.state.collectAsState()
    val voiceAmplitude by deps.voiceInputEngine.amplitude.collectAsState()

    var suggestionBarCollapsed by remember { mutableStateOf(false) }
    var macroNameDialogVisible by remember { mutableStateOf(false) }
    var pendingMacroSteps by remember { mutableStateOf<List<MacroStep>>(emptyList()) }

    LaunchedEffect(prefs.layoutMode) {
        if (KeyMapGenerator.isDebugBuild(context)) {
            KeyMapGenerator.dumpToLogcatWhenReady(context, currentView, prefs.layoutMode)
            // WHY: Phase 2 Python harness replaces `time.sleep(0.8)` after SET_LAYOUT_MODE
            //      broadcast with a wait_for on this event.
            DevKeyLogger.ime(
                "layout_mode_recomposed",
                mapOf("mode" to prefs.layoutMode.name.lowercase())
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 0. Toolbar chevron — slim clickable bar that toggles devkey_show_toolbar.
        //    Hidden during macro recording so the recording bar stays dominant.
        //    WHY: Phase 3 parity task #30 — give users a way to reveal the toolbar
        //    without entering Settings.
        if (keyboardMode !is KeyboardMode.MacroRecording) {
            ToolbarChevronBar(
                expanded = prefs.showToolbar.value,
                onToggle = {
                    prefs.prefs.edit()
                        .putBoolean(SettingsRepository.KEY_SHOW_TOOLBAR, !prefs.showToolbar.value)
                        .apply()
                }
            )
        }

        // 1. Toolbar (hidden during recording, and hidden by default for SwiftKey parity)
        if (prefs.showToolbar.value && keyboardMode !is KeyboardMode.MacroRecording) {
            ToolbarRow(
                onClipboard = { deps.modeManager.toggleMode(KeyboardMode.Clipboard) },
                onVoice = {
                    if (keyboardMode == KeyboardMode.Voice) {
                        deps.voiceInputEngine.cancelListening()
                        deps.modeManager.setMode(KeyboardMode.Normal)
                    } else {
                        deps.modeManager.setMode(KeyboardMode.Voice)
                        coroutineScope.launch { deps.voiceInputEngine.startListening() }
                    }
                },
                onSymbols = { deps.modeManager.toggleMode(KeyboardMode.Symbols) },
                onMacros = { deps.modeManager.toggleMode(KeyboardMode.MacroChips) },
                onMacrosLongPress = { deps.modeManager.toggleMode(KeyboardMode.MacroGrid) },
                onOverflow = { /* Session 5 */ },
                activeMode = keyboardMode,
                isCommandMode = inputMode == InputMode.COMMAND,
                onCommandModeToggle = { deps.commandModeDetector.toggleManualOverride() }
            )
        }

        // 2. Dynamic middle area
        KeyboardDynamicPanel(
            keyboardMode = keyboardMode,
            modeManager = deps.modeManager,
            bridge = deps.bridge,
            modifierState = deps.modifierState,
            macroRepo = deps.macroRepo,
            macroEngine = deps.macroEngine,
            clipboardRepo = deps.clipboardRepo,
            voiceInputEngine = deps.voiceInputEngine,
            voiceState = voiceState,
            voiceAmplitude = voiceAmplitude,
            macros = macros,
            clipboardEntries = clipboardEntries,
            predictions = predictions,
            suggestionBarCollapsed = suggestionBarCollapsed,
            onSuggestionBarCollapseToggle = { suggestionBarCollapsed = !suggestionBarCollapsed },
            macroNameDialogVisible = macroNameDialogVisible,
            pendingMacroSteps = pendingMacroSteps,
            onRecordingStopped = { steps ->
                pendingMacroSteps = steps
                macroNameDialogVisible = true
            },
            onMacroNameDialogDismiss = {
                macroNameDialogVisible = false
            },
            inputMode = inputMode,
            currentInputConnection = currentInputConnection
        )

        // 3. Ctrl banner + 4. Keyboard grid
        KeyboardRenderLayer(
            keyboardMode = keyboardMode,
            modeManager = deps.modeManager,
            modifierState = deps.modifierState,
            ctrlState = ctrlState,
            macroEngine = deps.macroEngine,
            layoutMode = prefs.layoutMode,
            keyboardHeightPercent = prefs.keyboardHeightPercent.value / 100f,
            showHints = prefs.showHints,
            hintBright = prefs.hintBright,
            showNumberRow = prefs.showNumberRow.value,
            bridge = deps.bridge,
            currentInputConnection = currentInputConnection,
            onVoiceKey = {
                deps.modeManager.setMode(KeyboardMode.Voice)
                coroutineScope.launch { deps.voiceInputEngine.startListening() }
            }
        )
    }
}

/**
 * Returns true for key codes that the Compose UI handles locally and should NOT
 * be forwarded to the IME bridge (which would trigger LatinIME's legacy mode switching).
 */
internal fun isComposeLocalCode(code: Int): Boolean = when (code) {
    KeyCodes.SYMBOLS, KeyCodes.EMOJI, SymbolsLayout.KEYCODE_ALPHA -> true
    else -> false
}
