package dev.devkey.keyboard.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.devkey.keyboard.core.KeyboardActionBridge
import dev.devkey.keyboard.core.KeyboardActionListener
import dev.devkey.keyboard.core.KeyboardModeManager
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.data.db.DevKeyDatabase
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
    LaunchedEffect(voiceInputEngine) {
        voiceInputEngine.warmOfflineModelIfAllowed()
    }

    RegisterDebugKeyboardReceivers(
        layoutMode = layoutMode,
        modeManager = modeManager,
        modifierState = modifierState,
        voiceInputEngine = voiceInputEngine,
        commandModeDetector = commandModeDetector,
        clipboardRepo = clipboardRepo,
        macroRepo = macroRepo,
        macroEngine = macroEngine,
        bridge = bridge
    )

    return KeyboardDependencies(bridge, modifierState, modeManager, macroRepo, clipboardRepo, macroEngine, commandModeDetector, voiceInputEngine)
}

/**
 * Observes the composing word via [SessionDependencies], debounces it, and
 * drives the prediction pipeline. When composing is empty, falls back to
 * next-word suggestions from the smart-text boundary.
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
