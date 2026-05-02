package dev.devkey.keyboard.ui.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import dev.devkey.keyboard.core.KeyboardActionBridge
import dev.devkey.keyboard.core.KeyboardModeManager
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.debug.KeyMapGenerator
import dev.devkey.keyboard.feature.clipboard.ClipboardRepository
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.feature.macro.MacroEngine
import dev.devkey.keyboard.feature.macro.MacroRepository
import dev.devkey.keyboard.feature.voice.VoiceInputEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun RegisterDebugKeyboardReceivers(
    layoutMode: LayoutMode,
    modeManager: KeyboardModeManager,
    modifierState: ModifierStateManager,
    voiceInputEngine: VoiceInputEngine,
    commandModeDetector: CommandModeDetector,
    clipboardRepo: ClipboardRepository,
    macroRepo: MacroRepository,
    macroEngine: MacroEngine,
    bridge: KeyboardActionBridge
) {
    val context = LocalContext.current
    if (!KeyMapGenerator.isDebugBuild(context)) return

    val coroutineScope = rememberCoroutineScope()
    val debugMacroName = remember { mutableStateOf<String?>(null) }

    RegisterKeyMapDumpReceiver(layoutMode)
    RegisterKeyboardModeReceivers(modeManager, modifierState, voiceInputEngine, coroutineScope)
    RegisterCommandModeReceiver(commandModeDetector, coroutineScope)
    RegisterClipboardDebugReceiver(clipboardRepo, bridge, modeManager, coroutineScope)
    RegisterMacroDebugReceiver(
        macroRepo = macroRepo,
        macroEngine = macroEngine,
        debugMacroName = debugMacroName,
        modeManager = modeManager,
        bridge = bridge,
        modifierState = modifierState,
        coroutineScope = coroutineScope
    )
}

@Composable
private fun RegisterKeyMapDumpReceiver(layoutMode: LayoutMode) {
    val context = LocalContext.current
    val currentView = LocalView.current
    DisposableEffect(currentView, layoutMode) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) =
                KeyMapGenerator.dumpToLogcat(c, currentView, layoutMode)
        }
        context.registerDebugReceiver(receiver, IntentFilter("dev.devkey.keyboard.DUMP_KEY_MAP"))
        onDispose { context.unregisterReceiverSafely(receiver) }
    }
}

@Composable
private fun RegisterKeyboardModeReceivers(
    modeManager: KeyboardModeManager,
    modifierState: ModifierStateManager,
    voiceInputEngine: VoiceInputEngine,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    DisposableEffect(modeManager, modifierState, voiceInputEngine) {
        val resetReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (modeManager.mode.value == KeyboardMode.Voice) voiceInputEngine.cancelListening()
                modeManager.setMode(KeyboardMode.Normal)
                modifierState.resetAll()
                DevKeyLogger.ime("keyboard_mode_reset", mapOf("to" to "Normal"))
            }
        }
        val setReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val modeName = i.getStringExtra("mode") ?: return
                val mode = modeName.toKeyboardMode() ?: return
                modeManager.setMode(mode)
                if (mode == KeyboardMode.Voice) {
                    coroutineScope.launch { voiceInputEngine.startListening() }
                }
                DevKeyLogger.ime("keyboard_mode_set", mapOf("mode" to modeName.lowercase()))
            }
        }
        context.registerDebugReceiver(resetReceiver, IntentFilter("dev.devkey.keyboard.RESET_KEYBOARD_MODE"))
        context.registerDebugReceiver(setReceiver, IntentFilter("dev.devkey.keyboard.SET_KEYBOARD_MODE"))
        onDispose {
            context.unregisterReceiverSafely(resetReceiver)
            context.unregisterReceiverSafely(setReceiver)
        }
    }
}

@Composable
private fun RegisterCommandModeReceiver(
    commandModeDetector: CommandModeDetector,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    DisposableEffect(commandModeDetector) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
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
        context.registerDebugReceiver(
            receiver,
            IntentFilter().apply {
                addAction("dev.devkey.keyboard.TOGGLE_COMMAND_MODE")
                addAction("dev.devkey.keyboard.SIMULATE_FOCUS_CHANGE")
            }
        )
        onDispose { context.unregisterReceiverSafely(receiver) }
    }
}

internal fun Context.registerDebugReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
    ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
}

internal fun Context.unregisterReceiverSafely(receiver: BroadcastReceiver) {
    try {
        unregisterReceiver(receiver)
    } catch (_: IllegalArgumentException) {
    }
}

private fun String.toKeyboardMode(): KeyboardMode? = when (lowercase()) {
    "normal" -> KeyboardMode.Normal
    "clipboard" -> KeyboardMode.Clipboard
    "voice" -> KeyboardMode.Voice
    "symbols" -> KeyboardMode.Symbols
    "macro_chips" -> KeyboardMode.MacroChips
    "macro_grid" -> KeyboardMode.MacroGrid
    else -> {
        DevKeyLogger.error("set_keyboard_mode_rejected", mapOf("mode" to this))
        null
    }
}
