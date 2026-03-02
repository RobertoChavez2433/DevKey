package dev.devkey.keyboard.ui.keyboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.preference.PreferenceManager
import dev.devkey.keyboard.LatinKeyboardBaseView
import dev.devkey.keyboard.core.KeyboardActionBridge
import dev.devkey.keyboard.core.ModifierKeyState
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.feature.clipboard.ClipboardRepository
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.feature.command.CommandModeRepository
import dev.devkey.keyboard.feature.command.InputMode
import dev.devkey.keyboard.feature.macro.MacroEngine
import dev.devkey.keyboard.feature.macro.MacroRepository
import dev.devkey.keyboard.feature.macro.MacroSerializer
import dev.devkey.keyboard.feature.macro.MacroStep
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.debug.KeyMapGenerator
import dev.devkey.keyboard.feature.voice.VoiceInputEngine
import dev.devkey.keyboard.ui.clipboard.ClipboardPanel
import dev.devkey.keyboard.ui.macro.MacroChipStrip
import dev.devkey.keyboard.ui.macro.MacroGridPanel
import dev.devkey.keyboard.ui.macro.MacroNameDialog
import dev.devkey.keyboard.ui.macro.MacroRecordingBar
import dev.devkey.keyboard.ui.toolbar.ToolbarRow
import dev.devkey.keyboard.ui.voice.VoiceInputPanel
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
    actionListener: LatinKeyboardBaseView.OnKeyboardActionListener,
    currentInputConnection: (() -> android.view.inputmethod.InputConnection?)? = null
) {
    val context = LocalContext.current
    val modifierState = remember { ModifierStateManager() }
    val bridge = remember { KeyboardActionBridge(actionListener) }
    val coroutineScope = rememberCoroutineScope()

    // Database and repositories
    val database = remember { DevKeyDatabase.getInstance(context) }
    val macroRepo = remember { MacroRepository(database.macroDao()) }
    val clipboardRepo = remember { ClipboardRepository(database.clipboardHistoryDao()) }
    val macroEngine = remember { MacroEngine() }

    // Command mode dependencies
    val commandModeRepo = remember { CommandModeRepository(database.commandAppDao()) }
    val commandModeDetector = remember { CommandModeDetector(commandModeRepo) }
    val voiceInputEngine = remember { VoiceInputEngine(context) }

    // Release voice engine resources when composable leaves composition
    DisposableEffect(voiceInputEngine) {
        onDispose { voiceInputEngine.release() }
    }

    // Layout mode preference (replaces old compact mode boolean)
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val layoutModeStr = rememberPreference(prefs, SettingsRepository.KEY_LAYOUT_MODE, "full") { p, k, d -> p.getString(k, d) ?: d }
    val layoutMode = remember(layoutModeStr.value) {
        when (layoutModeStr.value) {
            "compact" -> LayoutMode.COMPACT
            "compact_dev" -> LayoutMode.COMPACT_DEV
            else -> LayoutMode.FULL
        }
    }

    // Debug: dump key coordinate map on startup
    val currentView = LocalView.current
    LaunchedEffect(Unit) {
        if (KeyMapGenerator.isDebugBuild(context)) {
            kotlinx.coroutines.delay(500L)  // wait for layout
            KeyMapGenerator.dumpToLogcat(context, currentView, layoutMode)
        }
    }

    // Dynamic keyboard height from user preference
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val heightKey = if (isLandscape) SettingsRepository.KEY_HEIGHT_LANDSCAPE else SettingsRepository.KEY_HEIGHT_PORTRAIT
    val heightDefault = if (isLandscape) SettingsRepository.DEFAULT_HEIGHT_LANDSCAPE else SettingsRepository.DEFAULT_HEIGHT_PORTRAIT

    val keyboardHeightPercent = rememberPreference(prefs, heightKey, heightDefault) { p, k, d -> p.getInt(k, d) }

    // Hint mode preference: "0" = Hidden, "1" = Visible (dim), "2" = Visible (bright)
    val hintModeValue = rememberPreference(prefs, SettingsRepository.KEY_HINT_MODE, "0") { p, k, d -> p.getString(k, d) ?: d }
    val showHints = hintModeValue.value != "0"
    val hintBright = hintModeValue.value == "2"

    // State
    var keyboardMode by remember { mutableStateOf<KeyboardMode>(KeyboardMode.Normal) }
    val ctrlState by modifierState.ctrlState.collectAsState()
    val macros by macroRepo.getAllMacros().collectAsState(initial = emptyList())
    val clipboardEntries by clipboardRepo.getAll().collectAsState(initial = emptyList())

    // Session 4: Observe command mode and voice state
    val inputMode by commandModeDetector.inputMode.collectAsState()
    val voiceState by voiceInputEngine.state.collectAsState()
    val voiceAmplitude by voiceInputEngine.amplitude.collectAsState()


    // Macro recording state
    var macroNameDialogVisible by remember { mutableStateOf(false) }
    var pendingMacroSteps by remember { mutableStateOf<List<MacroStep>>(emptyList()) }

    // Toggle helper
    fun toggleMode(target: KeyboardMode) {
        keyboardMode = if (keyboardMode == target) KeyboardMode.Normal else target
    }

    // Macro click handler (shared between chip strip and grid)
    fun onMacroClick(macro: dev.devkey.keyboard.data.db.entity.MacroEntity) {
        val steps = MacroSerializer.deserialize(macro.keySequence)
        macroEngine.replay(steps, bridge, modifierState)
        coroutineScope.launch { macroRepo.incrementUsage(macro.id) }
        keyboardMode = KeyboardMode.Normal
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Toolbar (hidden during recording)
        if (keyboardMode !is KeyboardMode.MacroRecording) {
            ToolbarRow(
                onClipboard = { toggleMode(KeyboardMode.Clipboard) },
                onVoice = {
                    if (keyboardMode == KeyboardMode.Voice) {
                        voiceInputEngine.cancelListening()
                        keyboardMode = KeyboardMode.Normal
                    } else {
                        keyboardMode = KeyboardMode.Voice
                        coroutineScope.launch { voiceInputEngine.startListening() }
                    }
                },
                onSymbols = { toggleMode(KeyboardMode.Symbols) },
                onMacros = { toggleMode(KeyboardMode.MacroChips) },
                onMacrosLongPress = { toggleMode(KeyboardMode.MacroGrid) },
                onOverflow = { /* Session 5 */ },
                activeMode = keyboardMode,
                isCommandMode = inputMode == InputMode.COMMAND,
                onCommandModeToggle = { commandModeDetector.toggleManualOverride() }
            )
        }

        // 2. Dynamic middle area
        when (keyboardMode) {
            KeyboardMode.Normal -> {
                // SuggestionBar removed — predictions handled externally
            }
            KeyboardMode.MacroChips -> {
                MacroChipStrip(
                    macros = macros,
                    onMacroClick = { macro -> onMacroClick(macro) },
                    onAddClick = {
                        macroEngine.startRecording()
                        keyboardMode = KeyboardMode.MacroRecording
                    },
                    onCollapse = { keyboardMode = KeyboardMode.Normal }
                )
            }
            KeyboardMode.MacroGrid -> {
                MacroGridPanel(
                    macros = macros,
                    onMacroClick = { macro -> onMacroClick(macro) },
                    onRecordClick = {
                        macroEngine.startRecording()
                        keyboardMode = KeyboardMode.MacroRecording
                    },
                    onEditMacro = { macro ->
                        coroutineScope.launch { macroRepo.updateMacroName(macro.id, macro.name) }
                    },
                    onDeleteMacro = { macro ->
                        coroutineScope.launch { macroRepo.deleteMacro(macro.id) }
                    }
                )
            }
            KeyboardMode.MacroRecording -> {
                MacroRecordingBar(
                    capturedSteps = macroEngine.getCapturedSteps(),
                    onCancel = {
                        macroEngine.cancelRecording()
                        keyboardMode = KeyboardMode.Normal
                    },
                    onStop = {
                        pendingMacroSteps = macroEngine.stopRecording()
                        macroNameDialogVisible = true
                    }
                )
            }
            KeyboardMode.Clipboard -> {
                ClipboardPanel(
                    entries = clipboardEntries,
                    onPaste = { entry ->
                        bridge.onText(entry.content)
                        keyboardMode = KeyboardMode.Normal
                    },
                    onPin = { id -> coroutineScope.launch { clipboardRepo.pin(id) } },
                    onUnpin = { id -> coroutineScope.launch { clipboardRepo.unpin(id) } },
                    onDelete = { id -> coroutineScope.launch { clipboardRepo.delete(id) } },
                    onClearAll = { coroutineScope.launch { clipboardRepo.clearAll() } }
                )
            }
            KeyboardMode.Symbols -> {
                // No suggestion bar for symbols layout
            }
            KeyboardMode.Voice -> {
                VoiceInputPanel(
                    voiceState = voiceState,
                    amplitude = voiceAmplitude,
                    onStop = {
                        coroutineScope.launch {
                            val transcription = voiceInputEngine.stopListening()
                            if (transcription.isNotEmpty()) {
                                bridge.onText(transcription)
                            }
                            keyboardMode = KeyboardMode.Normal
                        }
                    },
                    onCancel = {
                        voiceInputEngine.cancelListening()
                        keyboardMode = KeyboardMode.Normal
                    }
                )
            }
        }

        // 3. Ctrl Mode banner (independent of KeyboardMode)
        AnimatedVisibility(
            visible = ctrlState == ModifierKeyState.HELD,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it })
        ) {
            CtrlModeBanner()
        }

        // 4. Keyboard (hidden during voice input)
        if (keyboardMode !is KeyboardMode.Voice) {
            // Determine which layout mode to use for KeyboardView
            val activeLayoutMode = when (keyboardMode) {
                is KeyboardMode.Symbols -> LayoutMode.FULL  // Symbols uses its own layout data
                else -> layoutMode
            }

            // Extract layout to local val so Compose reliably tracks keyboardMode reads
            val activeLayout = when (keyboardMode) {
                is KeyboardMode.Symbols -> SymbolsLayout.layout
                else -> QwertyLayout.getLayout(layoutMode)
            }
            KeyboardView(
                layout = activeLayout,
                layoutMode = activeLayoutMode,
                modifierState = modifierState,
                ctrlHeld = ctrlState == ModifierKeyState.HELD,
                heightPercent = keyboardHeightPercent.value / 100f,
                showHints = showHints,
                hintBright = hintBright,
                onKeyAction = { code ->
                    // Capture for macro recording
                    if (macroEngine.isRecording) {
                        val modifiers = buildList {
                            if (modifierState.isCtrlActive()) add("ctrl")
                            if (modifierState.isAltActive()) add("alt")
                            if (modifierState.isShiftActive()) add("shift")
                        }
                        val key = if (code in 32..126) code.toChar().toString() else ""
                        if (key.isNotEmpty()) {
                            macroEngine.captureKey(key, code, modifiers)
                        }
                    }
                    // Handle special keycodes
                    when (code) {
                        SymbolsLayout.KEYCODE_ALPHA -> {
                            // ABC key from symbols layout
                            keyboardMode = KeyboardMode.Normal
                        }
                        KeyCodes.SYMBOLS -> {
                            // 123 key — toggle symbols mode
                            toggleMode(KeyboardMode.Symbols)
                        }
                        KeyCodes.EMOJI -> {
                            // Emoji key — show system emoji picker
                            // TODO: implement InputMethodService.switchToNextInputMethod()
                            // For now, no-op (emoji picker requires IME service access)
                        }
                        KeyCodes.SMART_BACK_ESC -> {
                            // Smart backspace/esc — resolve using InputConnection
                            val ic = currentInputConnection?.invoke()
                            val resolvedCode = bridge.resolveSmartBackEsc(ic)
                            bridge.onKey(resolvedCode, modifierState)
                        }
                        else -> {
                            bridge.onKey(code, modifierState)
                        }
                    }
                },
                onKeyPress = { code -> bridge.onKeyPress(code) },
                onKeyRelease = { code -> bridge.onKeyRelease(code) }
            )
        }
    }

    // Macro naming dialog
    if (macroNameDialogVisible) {
        MacroNameDialog(
            onSave = { name ->
                coroutineScope.launch {
                    macroRepo.saveMacro(name, pendingMacroSteps)
                }
                macroNameDialogVisible = false
                keyboardMode = KeyboardMode.Normal
            },
            onCancel = {
                macroNameDialogVisible = false
                keyboardMode = KeyboardMode.Normal
            }
        )
    }
}

/**
 * Helper composable that reads a SharedPreferences value and returns a [State] that
 * automatically updates whenever the preference changes.
 *
 * @param prefs The SharedPreferences instance to read from.
 * @param key The preference key to observe.
 * @param defaultValue The default value to use when the key is absent.
 * @param reader A function that reads the typed value from prefs.
 */
@Composable
private fun <T> rememberPreference(
    prefs: SharedPreferences,
    key: String,
    defaultValue: T,
    reader: (SharedPreferences, String, T) -> T
): State<T> {
    val state = remember { mutableStateOf(reader(prefs, key, defaultValue)) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                state.value = reader(prefs, key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
