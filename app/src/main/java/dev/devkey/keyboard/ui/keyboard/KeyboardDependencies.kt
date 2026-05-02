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
                override fun onReceive(c: android.content.Context, i: android.content.Intent) =
                    commandModeDetector.toggleManualOverride()
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context, cmdR,
                android.content.IntentFilter("dev.devkey.keyboard.TOGGLE_COMMAND_MODE"),
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

            onDispose {
                try { context.unregisterReceiver(resetR) } catch (_: IllegalArgumentException) {}
                try { context.unregisterReceiver(setR) } catch (_: IllegalArgumentException) {}
                try { context.unregisterReceiver(cmdR) } catch (_: IllegalArgumentException) {}
                try { context.unregisterReceiver(clipboardR) } catch (_: IllegalArgumentException) {}
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
