package dev.devkey.keyboard.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import dev.devkey.keyboard.core.KeyboardActionBridge
import dev.devkey.keyboard.core.KeyboardActionListener
import dev.devkey.keyboard.core.KeyboardModeManager
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.debug.KeyMapGenerator
import dev.devkey.keyboard.feature.clipboard.ClipboardRepository
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.feature.command.CommandModeRepository
import dev.devkey.keyboard.feature.macro.MacroEngine
import dev.devkey.keyboard.feature.macro.MacroRepository
import dev.devkey.keyboard.feature.macro.MacroSerializer
import dev.devkey.keyboard.feature.prediction.PredictionResult
import dev.devkey.keyboard.feature.voice.VoiceInputEngine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** All wired-up runtime dependencies for the keyboard composable. */
class KeyboardDependencies(
    val bridge: KeyboardActionBridge,
    val modifierState: ModifierStateManager,
    val modeManager: KeyboardModeManager,
    val macroRepo: MacroRepository,
    val clipboardRepo: ClipboardRepository,
    val macroEngine: MacroEngine,
    val commandModeDetector: CommandModeDetector,
    val voiceInputEngine: VoiceInputEngine
)

/**
 * Creates and remembers all repositories, engines, and bridge objects for the keyboard.
 * Registers debug-only broadcast receivers (DUMP_KEY_MAP, RESET_KEYBOARD_MODE,
 * SET_KEYBOARD_MODE, TOGGLE_COMMAND_MODE) when running in a debug build.
 */
@Composable
fun rememberKeyboardDependencies(
    actionListener: KeyboardActionListener,
    layoutMode: LayoutMode
): KeyboardDependencies {
    val context = LocalContext.current
    val currentView = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val modifierState = remember { ModifierStateManager() }
    val bridge = remember { KeyboardActionBridge(actionListener) }
    val database = remember { DevKeyDatabase.getInstance(context) }
    val macroRepo = remember { MacroRepository(database.macroDao()) }
    val clipboardRepo = remember { ClipboardRepository(database.clipboardHistoryDao()) }
    val macroEngine = remember { MacroEngine() }
    val debugMacroName = remember { mutableStateOf<String?>(null) }
    val commandModeDetector = remember { CommandModeDetector(CommandModeRepository(database.commandAppDao())) }
    val voiceInputEngine = remember { VoiceInputEngine(context) }
    val modeManager = remember { KeyboardModeManager() }

    DisposableEffect(voiceInputEngine) {
        SessionDependencies.voiceInputEngine = voiceInputEngine
        onDispose { SessionDependencies.voiceInputEngine = null; voiceInputEngine.release() }
    }

    if (KeyMapGenerator.isDebugBuild(context)) {
        // WHY: Compose-layer DUMP_KEY_MAP uses the real View for precise on-screen coords,
        //      unlike the LatinIME receiver which uses estimated coords.
        DisposableEffect(currentView, layoutMode) {
            val r = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) =
                    KeyMapGenerator.dumpToLogcat(c, currentView, layoutMode)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context, r,
                android.content.IntentFilter("dev.devkey.keyboard.DUMP_KEY_MAP"),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
            onDispose { try { context.unregisterReceiver(r) } catch (_: IllegalArgumentException) {} }
        }

        DisposableEffect(modeManager, modifierState) {
            // RESET_KEYBOARD_MODE — e2e harness resets state between tests
            val resetR = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    if (modeManager.mode.value == KeyboardMode.Voice) voiceInputEngine.cancelListening()
                    modeManager.setMode(KeyboardMode.Normal)
                    modifierState.resetAll()
                    DevKeyLogger.ime("keyboard_mode_reset", mapOf("to" to "Normal"))
                }
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context, resetR,
                android.content.IntentFilter("dev.devkey.keyboard.RESET_KEYBOARD_MODE"),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )

            // SET_KEYBOARD_MODE — e2e harness opens any panel without needing tap coords
            val setR = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    val modeName = i.getStringExtra("mode") ?: return
                    val mode = when (modeName.lowercase()) {
                        "normal" -> KeyboardMode.Normal
                        "clipboard" -> KeyboardMode.Clipboard
                        "voice" -> KeyboardMode.Voice
                        "symbols" -> KeyboardMode.Symbols
                        "macro_chips" -> KeyboardMode.MacroChips
                        "macro_grid" -> KeyboardMode.MacroGrid
                        else -> { DevKeyLogger.error("set_keyboard_mode_rejected", mapOf("mode" to modeName)); return }
                    }
                    modeManager.setMode(mode)
                    // WHY: Voice mode needs startListening() alongside the mode change.
                    if (mode == KeyboardMode.Voice) coroutineScope.launch { voiceInputEngine.startListening() }
                    DevKeyLogger.ime("keyboard_mode_set", mapOf("mode" to modeName.lowercase()))
                }
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context, setR,
                android.content.IntentFilter("dev.devkey.keyboard.SET_KEYBOARD_MODE"),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )

            // TOGGLE_COMMAND_MODE — e2e harness triggers command mode without a terminal app
            val cmdR = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    coroutineScope.launch {
                        when (i.action) {
                            "dev.devkey.keyboard.TOGGLE_COMMAND_MODE" -> {
                                commandModeDetector.toggleManualOverride()
                                DevKeyLogger.ime(
                                    "command_mode_toggle_processed",
                                    mapOf(
                                        "trigger" to "user_toggle",
                                        "mode" to commandModeDetector.inputMode.value.name.lowercase()
                                    )
                                )
                            }
                            "dev.devkey.keyboard.SIMULATE_FOCUS_CHANGE" -> {
                                val packageName = i.getStringExtra("package")
                                commandModeDetector.detect(packageName)
                                DevKeyLogger.ime(
                                    "command_mode_focus_processed",
                                    mapOf(
                                        "trigger" to "focus_change",
                                        "mode" to commandModeDetector.inputMode.value.name.lowercase(),
                                        "terminal_detected" to (
                                            packageName != null &&
                                                packageName in CommandModeDetector.TERMINAL_PACKAGES
                                            )
                                    )
                                )
                            }
                        }
                    }
                }
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                cmdR,
                android.content.IntentFilter().apply {
                    addAction("dev.devkey.keyboard.TOGGLE_COMMAND_MODE")
                    addAction("dev.devkey.keyboard.SIMULATE_FOCUS_CHANGE")
                },
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )

            val clipboardR = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    coroutineScope.launch {
                        when (i.action) {
                            "dev.devkey.keyboard.CLIPBOARD_ADD" -> {
                                val index = i.getIntExtra("entry_index", 0)
                                val lengthHint = i.getIntExtra("length_hint", 24).coerceIn(1, 5000)
                                val specialChars = i.getBooleanExtra("special_chars", false)
                                val content = if (specialChars) {
                                    "clip-$index @#\$% [] {} <>"
                                } else {
                                    "clip-$index-" + "x".repeat(lengthHint)
                                }
                                clipboardRepo.addEntry(content)
                                logClipboardState("clipboard_add", index, lengthHint, true)
                            }
                            "dev.devkey.keyboard.CLIPBOARD_PASTE" -> {
                                val index = i.getIntExtra("entry_index", 0)
                                val entry = clipboardRepo.getAllSnapshot().getOrNull(index)
                                if (entry != null) {
                                    bridge.onText(entry.content)
                                    modeManager.setMode(KeyboardMode.Normal)
                                }
                                logClipboardState(
                                    "clipboard_paste",
                                    index,
                                    entry?.content?.length ?: 0,
                                    entry != null
                                )
                            }
                            "dev.devkey.keyboard.CLIPBOARD_PIN" -> {
                                val index = i.getIntExtra("entry_index", 0)
                                val pinned = clipboardRepo.pinByIndex(index)
                                logClipboardState("clipboard_pin", index, null, pinned)
                            }
                            "dev.devkey.keyboard.CLIPBOARD_CLEAR_ALL" -> {
                                clipboardRepo.clearEverything()
                                logClipboardState("clipboard_clear_all", null, null, true)
                            }
                            "dev.devkey.keyboard.CLIPBOARD_CLEAR_UNPINNED" -> {
                                clipboardRepo.clearAll()
                                logClipboardState("clipboard_clear_unpinned", null, null, true)
                            }
                        }
                    }
                }

                private suspend fun logClipboardState(
                    event: String,
                    entryIndex: Int?,
                    contentLength: Int?,
                    success: Boolean
                ) {
                    DevKeyLogger.ui(
                        event,
                        buildMap {
                            put("entry_count", clipboardRepo.getCount())
                            put("pinned_count", clipboardRepo.getPinnedCount())
                            put("success", success)
                            entryIndex?.let { put("entry_index", it) }
                            contentLength?.let { put("content_length", it) }
                        }
                    )
                }
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                clipboardR,
                android.content.IntentFilter().apply {
                    addAction("dev.devkey.keyboard.CLIPBOARD_ADD")
                    addAction("dev.devkey.keyboard.CLIPBOARD_PASTE")
                    addAction("dev.devkey.keyboard.CLIPBOARD_PIN")
                    addAction("dev.devkey.keyboard.CLIPBOARD_CLEAR_ALL")
                    addAction("dev.devkey.keyboard.CLIPBOARD_CLEAR_UNPINNED")
                },
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )

            val macroR = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    coroutineScope.launch {
                        when (i.action) {
                            "dev.devkey.keyboard.MACRO_START_RECORD" -> {
                                val name = macroName(i)
                                if (name == null) {
                                    logMacroState("macro_record_start", false)
                                    return@launch
                                }
                                debugMacroName.value = name
                                macroEngine.startRecording()
                                modeManager.setMode(KeyboardMode.MacroRecording)
                                logMacroState("macro_record_start", true, recording = true)
                            }
                            "dev.devkey.keyboard.MACRO_INJECT_KEY" -> {
                                val key = i.getStringExtra("key").orEmpty()
                                val keyCode = i.getIntExtra("keyCode", key.firstOrNull()?.code ?: 0)
                                val modifiers = i.getStringExtra("modifiers")
                                    ?.split(",")
                                    ?.map { it.trim().lowercase() }
                                    ?.filter { it.isNotEmpty() }
                                    ?: emptyList()
                                macroEngine.captureKey(key, keyCode, modifiers)
                                logMacroState(
                                    "macro_inject_key",
                                    macroEngine.isRecording,
                                    stepCount = macroEngine.getCapturedSteps().size,
                                    modifierCount = modifiers.size,
                                    recording = macroEngine.isRecording
                                )
                            }
                            "dev.devkey.keyboard.MACRO_STOP_RECORD" -> {
                                val name = debugMacroName.value
                                val steps = macroEngine.stopRecording()
                                debugMacroName.value = null
                                modeManager.setMode(KeyboardMode.Normal)
                                val saved = name != null
                                if (name != null) macroRepo.saveMacroReplacing(name, steps)
                                logMacroState(
                                    "macro_record_stop",
                                    saved,
                                    stepCount = steps.size,
                                    recording = false
                                )
                            }
                            "dev.devkey.keyboard.MACRO_CANCEL_RECORD" -> {
                                macroEngine.cancelRecording()
                                debugMacroName.value = null
                                modeManager.setMode(KeyboardMode.Normal)
                                logMacroState("macro_record_cancel", true, recording = false)
                            }
                            "dev.devkey.keyboard.MACRO_REPLAY" -> {
                                val macro = macroName(i)?.let { macroRepo.getMacroByName(it) }
                                val steps = macro?.let { MacroSerializer.deserialize(it.keySequence) } ?: emptyList()
                                if (macro != null) {
                                    macroEngine.replay(steps, bridge, modifierState)
                                    macroRepo.incrementUsage(macro.id)
                                    modeManager.setMode(KeyboardMode.Normal)
                                }
                                logMacroState(
                                    "macro_replay",
                                    macro != null,
                                    stepCount = steps.size,
                                    usageCount = if (macro != null) macro.usageCount + 1 else 0
                                )
                            }
                            "dev.devkey.keyboard.MACRO_DELETE" -> {
                                val deleted = macroName(i)?.let { macroRepo.deleteMacrosByName(it) } ?: 0
                                logMacroState("macro_delete", deleted > 0, affectedCount = deleted)
                            }
                            "dev.devkey.keyboard.MACRO_RENAME" -> {
                                val oldName = i.getStringExtra("old_name")?.takeIf { it.isNotBlank() }
                                val newName = i.getStringExtra("new_name")?.takeIf { it.isNotBlank() }
                                val updated = if (oldName != null && newName != null) {
                                    macroRepo.updateMacroName(oldName, newName)
                                } else {
                                    0
                                }
                                logMacroState("macro_rename", updated > 0, affectedCount = updated)
                            }
                        }
                    }
                }

                private fun macroName(intent: android.content.Intent): String? =
                    intent.getStringExtra("name")?.take(64)?.takeIf { it.isNotBlank() }

                private suspend fun logMacroState(
                    event: String,
                    success: Boolean,
                    stepCount: Int? = null,
                    modifierCount: Int? = null,
                    affectedCount: Int? = null,
                    usageCount: Int? = null,
                    recording: Boolean? = null
                ) {
                    DevKeyLogger.ui(
                        event,
                        buildMap {
                            put("macro_count", macroRepo.getCount())
                            put("success", success)
                            stepCount?.let { put("step_count", it) }
                            modifierCount?.let { put("modifier_count", it) }
                            affectedCount?.let { put("affected_count", it) }
                            usageCount?.let { put("usage_count", it) }
                            recording?.let { put("recording", it) }
                        }
                    )
                }
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                macroR,
                android.content.IntentFilter().apply {
                    addAction("dev.devkey.keyboard.MACRO_START_RECORD")
                    addAction("dev.devkey.keyboard.MACRO_INJECT_KEY")
                    addAction("dev.devkey.keyboard.MACRO_STOP_RECORD")
                    addAction("dev.devkey.keyboard.MACRO_CANCEL_RECORD")
                    addAction("dev.devkey.keyboard.MACRO_REPLAY")
                    addAction("dev.devkey.keyboard.MACRO_DELETE")
                    addAction("dev.devkey.keyboard.MACRO_RENAME")
                },
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )

            onDispose {
                try { context.unregisterReceiver(resetR) } catch (_: IllegalArgumentException) {}
                try { context.unregisterReceiver(setR) } catch (_: IllegalArgumentException) {}
                try { context.unregisterReceiver(cmdR) } catch (_: IllegalArgumentException) {}
                try { context.unregisterReceiver(clipboardR) } catch (_: IllegalArgumentException) {}
                try { context.unregisterReceiver(macroR) } catch (_: IllegalArgumentException) {}
            }
        }
    }

    return KeyboardDependencies(bridge, modifierState, modeManager, macroRepo, clipboardRepo, macroEngine, commandModeDetector, voiceInputEngine)
}

/**
 * Observes the composing word via [SessionDependencies], debounces it, and
 * drives the prediction pipeline. When composing is empty, falls back to
 * next-word suggestions from the bigram/legacy pipeline.
 */
@Composable
fun rememberPredictions(): List<PredictionResult> {
    var predictions by remember { mutableStateOf<List<PredictionResult>>(emptyList()) }

    @OptIn(FlowPreview::class)
    val debouncedComposingFlow = remember {
        SessionDependencies.composingWord
            .debounce(100L)
            .distinctUntilChanged()
    }
    val composingWord by debouncedComposingFlow.collectAsState(initial = "")

    val nextWordSuggestions by SessionDependencies.nextWordSuggestions.collectAsState()

    LaunchedEffect(composingWord) {
        val pe = SessionDependencies.predictionEngine
        predictions = if (pe != null && composingWord.isNotEmpty()) pe.predict(composingWord) else emptyList()
    }

    // When composing is empty and PredictionEngine has no results, show next-word suggestions
    return if (predictions.isEmpty() && composingWord.isEmpty() && nextWordSuggestions.isNotEmpty()) {
        nextWordSuggestions.take(3).map { PredictionResult(word = it.toString()) }
    } else {
        predictions
    }
}
