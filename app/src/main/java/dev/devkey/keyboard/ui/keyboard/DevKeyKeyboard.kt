package dev.devkey.keyboard.ui.keyboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.devkey.keyboard.LatinKeyboardBaseView
import dev.devkey.keyboard.core.KeyboardActionBridge
import dev.devkey.keyboard.core.ModifierKeyState
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.feature.clipboard.ClipboardRepository
import dev.devkey.keyboard.feature.macro.MacroEngine
import dev.devkey.keyboard.feature.macro.MacroRepository
import dev.devkey.keyboard.feature.macro.MacroSerializer
import dev.devkey.keyboard.feature.macro.MacroStep
import dev.devkey.keyboard.ui.clipboard.ClipboardPanel
import dev.devkey.keyboard.ui.macro.MacroChipStrip
import dev.devkey.keyboard.ui.macro.MacroGridPanel
import dev.devkey.keyboard.ui.macro.MacroNameDialog
import dev.devkey.keyboard.ui.macro.MacroRecordingBar
import dev.devkey.keyboard.ui.suggestion.SuggestionBar
import dev.devkey.keyboard.ui.toolbar.ToolbarRow
import kotlinx.coroutines.launch

/**
 * Root composable for the DevKey keyboard.
 *
 * Assembles the toolbar, suggestion bar / dynamic middle area, and keyboard view
 * into a single keyboard UI, wired to the legacy IME action listener.
 * Supports multiple modes: Normal, MacroChips, MacroGrid, MacroRecording,
 * Clipboard, and Symbols.
 *
 * @param actionListener The IME action listener to bridge key events to.
 */
@Composable
fun DevKeyKeyboard(actionListener: LatinKeyboardBaseView.OnKeyboardActionListener) {
    val context = LocalContext.current
    val modifierState = remember { ModifierStateManager() }
    val bridge = remember { KeyboardActionBridge(actionListener) }
    val coroutineScope = rememberCoroutineScope()

    // Database and repositories
    val database = remember { DevKeyDatabase.getInstance(context) }
    val macroRepo = remember { MacroRepository(database.macroDao()) }
    val clipboardRepo = remember { ClipboardRepository(database.clipboardHistoryDao()) }
    val macroEngine = remember { MacroEngine() }

    // State
    var keyboardMode by remember { mutableStateOf<KeyboardMode>(KeyboardMode.Normal) }
    val ctrlState by modifierState.ctrlState.collectAsState()
    val macros by macroRepo.getAllMacros().collectAsState(initial = emptyList())
    val clipboardEntries by clipboardRepo.getAll().collectAsState(initial = emptyList())

    var isSuggestionCollapsed by remember { mutableStateOf(false) }
    val defaultSuggestions = remember { listOf("the", "I", "and") }

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
                onVoice = { /* Session 4 */ },
                onSymbols = { toggleMode(KeyboardMode.Symbols) },
                onMacros = { toggleMode(KeyboardMode.MacroChips) },
                onMacrosLongPress = { toggleMode(KeyboardMode.MacroGrid) },
                onOverflow = { /* Session 5 */ },
                activeMode = keyboardMode
            )
        }

        // 2. Dynamic middle area
        when (keyboardMode) {
            KeyboardMode.Normal -> {
                SuggestionBar(
                    suggestions = defaultSuggestions,
                    onSuggestionClick = { suggestion -> bridge.onText(suggestion + " ") },
                    onCollapseToggle = { isSuggestionCollapsed = !isSuggestionCollapsed },
                    isCollapsed = isSuggestionCollapsed
                )
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
                SuggestionBar(
                    suggestions = defaultSuggestions,
                    onSuggestionClick = { suggestion -> bridge.onText(suggestion + " ") },
                    onCollapseToggle = { isSuggestionCollapsed = !isSuggestionCollapsed },
                    isCollapsed = isSuggestionCollapsed
                )
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
                SuggestionBar(
                    suggestions = defaultSuggestions,
                    onSuggestionClick = { suggestion -> bridge.onText(suggestion + " ") },
                    onCollapseToggle = { isSuggestionCollapsed = !isSuggestionCollapsed },
                    isCollapsed = isSuggestionCollapsed
                )
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
        }

        // 3. Ctrl Mode banner (independent of KeyboardMode)
        AnimatedVisibility(
            visible = ctrlState == ModifierKeyState.HELD,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it })
        ) {
            CtrlModeBanner()
        }

        // 4. Keyboard
        KeyboardView(
            layout = if (keyboardMode is KeyboardMode.Symbols) SymbolsLayout.layout else QwertyLayout.layout,
            modifierState = modifierState,
            ctrlHeld = ctrlState == ModifierKeyState.HELD,
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
                // Handle ABC key from symbols layout
                if (code == SymbolsLayout.KEYCODE_ALPHA) {
                    keyboardMode = KeyboardMode.Normal
                } else {
                    bridge.onKey(code, modifierState)
                }
            },
            onKeyPress = { code -> bridge.onKeyPress(code) },
            onKeyRelease = { code -> bridge.onKeyRelease(code) }
        )
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
