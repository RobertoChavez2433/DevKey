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
import dev.devkey.keyboard.core.KeyboardActionBridge
import dev.devkey.keyboard.core.KeyboardActionListener
import dev.devkey.keyboard.core.KeyboardModeManager
import dev.devkey.keyboard.core.ModifierKeyState
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.feature.clipboard.ClipboardRepository
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.feature.command.CommandModeRepository
import dev.devkey.keyboard.feature.command.InputMode
import dev.devkey.keyboard.feature.prediction.PredictionResult
import dev.devkey.keyboard.feature.macro.MacroEngine
import dev.devkey.keyboard.feature.macro.MacroRepository
import dev.devkey.keyboard.feature.macro.MacroSerializer
import dev.devkey.keyboard.feature.macro.MacroStep
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.debug.KeyMapGenerator
import dev.devkey.keyboard.feature.voice.VoiceInputEngine
import dev.devkey.keyboard.ui.clipboard.ClipboardPanel
import dev.devkey.keyboard.ui.macro.MacroChipStrip
import dev.devkey.keyboard.ui.macro.MacroGridPanel
import dev.devkey.keyboard.ui.macro.MacroNameDialog
import dev.devkey.keyboard.ui.macro.MacroRecordingBar
import dev.devkey.keyboard.ui.suggestion.SuggestionBar
import dev.devkey.keyboard.ui.toolbar.ToolbarChevronBar
import dev.devkey.keyboard.ui.toolbar.ToolbarRow
import dev.devkey.keyboard.ui.voice.VoiceInputPanel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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

    // Debug: dump key coordinate map when layout mode changes
    val currentView = LocalView.current

    // WHY: The DUMP_KEY_MAP receiver in LatinIME passes null for the View,
    //      so it uses estimated coordinates which are wrong when nav bar is
    //      present. This Compose-layer receiver uses the actual View for
    //      precise on-screen coordinates.
    if (KeyMapGenerator.isDebugBuild(context)) {
        DisposableEffect(currentView, layoutMode) {
            val dumpReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    KeyMapGenerator.dumpToLogcat(c, currentView, layoutMode)
                }
            }
            context.registerReceiver(
                dumpReceiver,
                android.content.IntentFilter("dev.devkey.keyboard.DUMP_KEY_MAP"),
                android.content.Context.RECEIVER_EXPORTED
            )
            onDispose {
                try { context.unregisterReceiver(dumpReceiver) } catch (_: IllegalArgumentException) {}
            }
        }
    }

    LaunchedEffect(layoutMode) {
        if (KeyMapGenerator.isDebugBuild(context)) {
            KeyMapGenerator.dumpToLogcatWhenReady(context, currentView, layoutMode)
        }
        // WHY: Phase 2 Python harness replaces `time.sleep(0.8)` after SET_LAYOUT_MODE broadcast
        //      with a wait_for on this event — see tools/e2e/lib/keyboard.py set_layout_mode.
        // NOTE: LaunchedEffect runs on a keyed-restart basis; emitting here means the observer
        //       will see one event per layoutMode change after Compose has re-keyed the block.
        DevKeyLogger.ime(
            "layout_mode_recomposed",
            mapOf("mode" to layoutMode.name.lowercase())
        )
    }

    // Dynamic keyboard height from user preference
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val heightKey = if (isLandscape) SettingsRepository.KEY_HEIGHT_LANDSCAPE else SettingsRepository.KEY_HEIGHT_PORTRAIT
    val heightDefault = if (isLandscape) SettingsRepository.DEFAULT_HEIGHT_LANDSCAPE else SettingsRepository.DEFAULT_HEIGHT_PORTRAIT

    val keyboardHeightPercent = rememberPreference(prefs, heightKey, heightDefault) { p, k, d -> p.getInt(k, d) }

    // Hint mode preference: "0" = Hidden, "1" = Visible (dim), "2" = Visible (bright)
    // WHY: Default "1" for SwiftKey parity — the reference keyboard shows a muted
    //      long-press hint glyph on every key. Previously defaulted to "0" which
    //      hid all hints and also mismatched `default_hint_mode` = 1 in
    //      res/values/strings.xml. Users can flip to "0" in Settings > Input if
    //      they want a cleaner look.
    val hintModeValue = rememberPreference(prefs, SettingsRepository.KEY_HINT_MODE, "1") { p, k, d -> p.getString(k, d) ?: d }
    val showHints = hintModeValue.value != "0"
    val hintBright = hintModeValue.value == "2"

    // State — using StateFlow via KeyboardModeManager for reliable recomposition
    val modeManager = remember { KeyboardModeManager() }
    val keyboardMode by modeManager.mode.collectAsState()

    // WHY: Phase 3 test harness — debug-only broadcast lets the e2e runner
    //      reset the keyboard to Normal between tests so a prior test that
    //      left the keyboard in Symbols/Voice/Macro state can't poison
    //      coordinate lookups for the next test.
    if (KeyMapGenerator.isDebugBuild(context)) {
        DisposableEffect(modeManager, modifierState) {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    // Cancel voice if active so VoiceInputEngine returns to IDLE.
                    if (modeManager.mode.value == KeyboardMode.Voice) {
                        voiceInputEngine.cancelListening()
                    }
                    modeManager.setMode(KeyboardMode.Normal)
                    modifierState.resetAll()
                    DevKeyLogger.ime("keyboard_mode_reset", mapOf("to" to "Normal"))
                }
            }
            context.registerReceiver(
                receiver,
                android.content.IntentFilter("dev.devkey.keyboard.RESET_KEYBOARD_MODE"),
                android.content.Context.RECEIVER_EXPORTED
            )
            // WHY: Phase 3 E2E — SET_KEYBOARD_MODE lets the harness open any panel
            //      (Clipboard, Voice, MacroGrid, etc.) via broadcast so tests don't
            //      need toolbar tap coordinates.
            val setModeReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    val modeName = i.getStringExtra("mode") ?: return
                    val mode = when (modeName.lowercase()) {
                        "normal" -> KeyboardMode.Normal
                        "clipboard" -> KeyboardMode.Clipboard
                        "voice" -> KeyboardMode.Voice
                        "symbols" -> KeyboardMode.Symbols
                        "macro_chips" -> KeyboardMode.MacroChips
                        "macro_grid" -> KeyboardMode.MacroGrid
                        else -> {
                            DevKeyLogger.error("set_keyboard_mode_rejected", mapOf("mode" to modeName))
                            return
                        }
                    }
                    modeManager.setMode(mode)
                    // WHY: Voice mode needs startListening() alongside the mode
                    //      change — the toolbar onVoice callback does both.
                    if (mode == KeyboardMode.Voice) {
                        coroutineScope.launch { voiceInputEngine.startListening() }
                    }
                    DevKeyLogger.ime("keyboard_mode_set", mapOf("mode" to modeName.lowercase()))
                }
            }
            context.registerReceiver(
                setModeReceiver,
                android.content.IntentFilter("dev.devkey.keyboard.SET_KEYBOARD_MODE"),
                android.content.Context.RECEIVER_EXPORTED
            )

            // WHY: Phase 3 E2E — command mode test needs to toggle without a
            //      terminal app installed. This broadcast triggers the same
            //      toggleManualOverride() path the overflow button uses.
            val toggleCmdReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    commandModeDetector.toggleManualOverride()
                }
            }
            context.registerReceiver(
                toggleCmdReceiver,
                android.content.IntentFilter("dev.devkey.keyboard.TOGGLE_COMMAND_MODE"),
                android.content.Context.RECEIVER_EXPORTED
            )

            onDispose {
                try { context.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
                try { context.unregisterReceiver(setModeReceiver) } catch (_: IllegalArgumentException) {}
                try { context.unregisterReceiver(toggleCmdReceiver) } catch (_: IllegalArgumentException) {}
            }
        }
    }

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

    // Toggle helper — delegates to KeyboardModeManager
    fun toggleMode(target: KeyboardMode) {
        modeManager.toggleMode(target)
    }

    // Macro click handler (shared between chip strip and grid)
    fun onMacroClick(macro: dev.devkey.keyboard.data.db.entity.MacroEntity) {
        val steps = MacroSerializer.deserialize(macro.keySequence)
        macroEngine.replay(steps, bridge, modifierState)
        coroutineScope.launch { macroRepo.incrementUsage(macro.id) }
        modeManager.setMode(KeyboardMode.Normal)
    }

    // SwiftKey parity — default hides the toolbar row; users can enable via
    // Settings > Input > `devkey_show_toolbar`. See SettingsRepository.KEY_SHOW_TOOLBAR.
    val showToolbar = rememberPreference(prefs, SettingsRepository.KEY_SHOW_TOOLBAR, false) {
        p, k, d -> p.getBoolean(k, d)
    }
    // SwiftKey parity — number row on top of COMPACT / COMPACT_DEV, togglable via
    // Settings > Input > `devkey_show_number_row`. Default true matches the reference.
    val showNumberRow = rememberPreference(prefs, SettingsRepository.KEY_SHOW_NUMBER_ROW, true) {
        p, k, d -> p.getBoolean(k, d)
    }

    // Prediction pipeline: collect composingWord, debounce, predict
    var predictions by remember { mutableStateOf<List<PredictionResult>>(emptyList()) }
    var suggestionBarCollapsed by remember { mutableStateOf(false) }

    @OptIn(FlowPreview::class)
    val composingWord by SessionDependencies.composingWord
        .debounce(100L)
        .distinctUntilChanged()
        .collectAsState(initial = "")

    LaunchedEffect(composingWord) {
        val pe = SessionDependencies.predictionEngine
        if (pe != null && composingWord.isNotEmpty()) {
            predictions = pe.predict(composingWord)
        } else {
            predictions = emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 0. Toolbar chevron — a slim clickable bar that toggles `devkey_show_toolbar`.
        //    Hidden during macro recording so the recording bar stays visually dominant.
        //    WHY: Phase 3 parity task #30 — give users a way to reveal the toolbar
        //    without entering Settings.
        if (keyboardMode !is KeyboardMode.MacroRecording) {
            ToolbarChevronBar(
                expanded = showToolbar.value,
                onToggle = {
                    prefs.edit()
                        .putBoolean(SettingsRepository.KEY_SHOW_TOOLBAR, !showToolbar.value)
                        .apply()
                }
            )
        }

        // 1. Toolbar (hidden during recording, and hidden by default for SwiftKey parity)
        if (showToolbar.value && keyboardMode !is KeyboardMode.MacroRecording) {
            ToolbarRow(
                onClipboard = { toggleMode(KeyboardMode.Clipboard) },
                onVoice = {
                    if (keyboardMode == KeyboardMode.Voice) {
                        voiceInputEngine.cancelListening()
                        modeManager.setMode(KeyboardMode.Normal)
                    } else {
                        modeManager.setMode(KeyboardMode.Voice)
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
                if (predictions.isNotEmpty()) {
                    SuggestionBar(
                        predictions = predictions,
                        onSuggestionClick = { result ->
                            // Replace the composing text with the suggestion
                            val ic = currentInputConnection?.invoke()
                            if (ic != null) {
                                ic.finishComposingText()
                                val composingLen = SessionDependencies.composingWord.value.length
                                if (composingLen > 0) {
                                    ic.deleteSurroundingText(composingLen, 0)
                                }
                                ic.commitText(result.word + " ", 1)
                            } else {
                                bridge.onText(result.word + " ")
                            }
                            // Feed the learning engine
                            SessionDependencies.learningEngine?.let { le ->
                                coroutineScope.launch {
                                    le.onWordCommitted(
                                        result.word,
                                        isCommand = inputMode == InputMode.COMMAND,
                                        contextApp = SessionDependencies.currentPackageName
                                    )
                                }
                            }
                            SessionDependencies.composingWord.value = ""
                            SessionDependencies.pendingCorrection.value = null
                            predictions = emptyList()
                        },
                        onSuggestionLongPress = { result ->
                            // Add the typed word (the composing word) to custom dictionary
                            val typedWord = SessionDependencies.composingWord.value
                            val wordToAdd = typedWord.ifBlank { result.word }
                            SessionDependencies.learningEngine?.let { le ->
                                coroutineScope.launch {
                                    le.addCustomWord(wordToAdd)
                                    android.widget.Toast.makeText(
                                        context,
                                        "\"$wordToAdd\" added to dictionary",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onCollapseToggle = { suggestionBarCollapsed = !suggestionBarCollapsed },
                        isCollapsed = suggestionBarCollapsed
                    )
                }
            }
            KeyboardMode.MacroChips -> {
                MacroChipStrip(
                    macros = macros,
                    onMacroClick = { macro -> onMacroClick(macro) },
                    onAddClick = {
                        macroEngine.startRecording()
                        modeManager.setMode(KeyboardMode.MacroRecording)
                    },
                    onCollapse = { modeManager.setMode(KeyboardMode.Normal) }
                )
            }
            KeyboardMode.MacroGrid -> {
                MacroGridPanel(
                    macros = macros,
                    onMacroClick = { macro -> onMacroClick(macro) },
                    onRecordClick = {
                        macroEngine.startRecording()
                        modeManager.setMode(KeyboardMode.MacroRecording)
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
                        modeManager.setMode(KeyboardMode.Normal)
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
                        modeManager.setMode(KeyboardMode.Normal)
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
                            modeManager.setMode(KeyboardMode.Normal)
                        }
                    },
                    onCancel = {
                        voiceInputEngine.cancelListening()
                        modeManager.setMode(KeyboardMode.Normal)
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

            val activeLayout = when (keyboardMode) {
                is KeyboardMode.Symbols -> SymbolsLayout.layout
                else -> QwertyLayout.getLayout(layoutMode, includeNumberRow = showNumberRow.value)
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
                            modeManager.setMode(KeyboardMode.Normal)
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
                onKeyPress = { code ->
                    // Don't forward codes handled locally by Compose UI to avoid
                    // LatinIME legacy mode-switching interference
                    if (!isComposeLocalCode(code)) bridge.onKeyPress(code)
                },
                onKeyRelease = { code ->
                    if (!isComposeLocalCode(code)) bridge.onKeyRelease(code)
                }
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
                modeManager.setMode(KeyboardMode.Normal)
            },
            onCancel = {
                macroNameDialogVisible = false
                modeManager.setMode(KeyboardMode.Normal)
            }
        )
    }
}

/**
 * Returns true for key codes that the Compose UI handles locally and should NOT
 * be forwarded to the IME bridge (which would trigger LatinIME's legacy mode switching).
 */
private fun isComposeLocalCode(code: Int): Boolean = when (code) {
    KeyCodes.SYMBOLS, KeyCodes.EMOJI, SymbolsLayout.KEYCODE_ALPHA -> true
    else -> false
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
