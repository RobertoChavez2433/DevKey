package dev.devkey.keyboard.ui.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.LocalContext
import dev.devkey.keyboard.core.KeyboardActionBridge
import dev.devkey.keyboard.core.KeyboardModeManager
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.feature.macro.MacroEngine
import dev.devkey.keyboard.feature.macro.MacroRepository
import dev.devkey.keyboard.feature.macro.MacroSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun RegisterMacroDebugReceiver(
    macroRepo: MacroRepository,
    macroEngine: MacroEngine,
    debugMacroName: MutableState<String?>,
    modeManager: KeyboardModeManager,
    bridge: KeyboardActionBridge,
    modifierState: ModifierStateManager,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    DisposableEffect(macroRepo, macroEngine, modeManager, bridge, modifierState) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                coroutineScope.launch {
                    when (i.action) {
                        "dev.devkey.keyboard.MACRO_START_RECORD" ->
                            handleMacroStart(i, macroEngine, debugMacroName, modeManager, macroRepo)
                        "dev.devkey.keyboard.MACRO_INJECT_KEY" ->
                            handleMacroInject(i, macroEngine, macroRepo)
                        "dev.devkey.keyboard.MACRO_STOP_RECORD" ->
                            handleMacroStop(macroEngine, debugMacroName, modeManager, macroRepo)
                        "dev.devkey.keyboard.MACRO_CANCEL_RECORD" ->
                            handleMacroCancel(macroEngine, debugMacroName, modeManager, macroRepo)
                        "dev.devkey.keyboard.MACRO_REPLAY" ->
                            handleMacroReplay(i, macroRepo, macroEngine, bridge, modifierState, modeManager)
                        "dev.devkey.keyboard.MACRO_DELETE" ->
                            handleMacroDelete(i, macroRepo)
                        "dev.devkey.keyboard.MACRO_RENAME" ->
                            handleMacroRename(i, macroRepo)
                    }
                }
            }
        }
        context.registerDebugReceiver(
            receiver,
            IntentFilter().apply {
                addAction("dev.devkey.keyboard.MACRO_START_RECORD")
                addAction("dev.devkey.keyboard.MACRO_INJECT_KEY")
                addAction("dev.devkey.keyboard.MACRO_STOP_RECORD")
                addAction("dev.devkey.keyboard.MACRO_CANCEL_RECORD")
                addAction("dev.devkey.keyboard.MACRO_REPLAY")
                addAction("dev.devkey.keyboard.MACRO_DELETE")
                addAction("dev.devkey.keyboard.MACRO_RENAME")
            }
        )
        onDispose { context.unregisterReceiverSafely(receiver) }
    }
}

private suspend fun handleMacroStart(
    intent: Intent,
    macroEngine: MacroEngine,
    debugMacroName: MutableState<String?>,
    modeManager: KeyboardModeManager,
    macroRepo: MacroRepository
) {
    val name = intent.macroName()
    if (name == null) {
        logMacroState(macroRepo, "macro_record_start", false)
        return
    }
    debugMacroName.value = name
    macroEngine.startRecording()
    modeManager.setMode(KeyboardMode.MacroRecording)
    logMacroState(macroRepo, "macro_record_start", true, recording = true)
}

private suspend fun handleMacroInject(
    intent: Intent,
    macroEngine: MacroEngine,
    macroRepo: MacroRepository
) {
    val key = intent.getStringExtra("key").orEmpty()
    val keyCode = intent.getIntExtra("keyCode", key.firstOrNull()?.code ?: 0)
    val modifiers = intent.getStringExtra("modifiers")
        ?.split(",")
        ?.map { it.trim().lowercase() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()
    macroEngine.captureKey(key, keyCode, modifiers)
    logMacroState(
        macroRepo,
        "macro_inject_key",
        macroEngine.isRecording,
        stepCount = macroEngine.getCapturedSteps().size,
        modifierCount = modifiers.size,
        recording = macroEngine.isRecording
    )
}

private suspend fun handleMacroStop(
    macroEngine: MacroEngine,
    debugMacroName: MutableState<String?>,
    modeManager: KeyboardModeManager,
    macroRepo: MacroRepository
) {
    val name = debugMacroName.value
    val steps = macroEngine.stopRecording()
    debugMacroName.value = null
    modeManager.setMode(KeyboardMode.Normal)
    val saved = name != null
    if (name != null) macroRepo.saveMacroReplacing(name, steps)
    logMacroState(macroRepo, "macro_record_stop", saved, stepCount = steps.size, recording = false)
}

private suspend fun handleMacroCancel(
    macroEngine: MacroEngine,
    debugMacroName: MutableState<String?>,
    modeManager: KeyboardModeManager,
    macroRepo: MacroRepository
) {
    macroEngine.cancelRecording()
    debugMacroName.value = null
    modeManager.setMode(KeyboardMode.Normal)
    logMacroState(macroRepo, "macro_record_cancel", true, recording = false)
}

private suspend fun handleMacroReplay(
    intent: Intent,
    macroRepo: MacroRepository,
    macroEngine: MacroEngine,
    bridge: KeyboardActionBridge,
    modifierState: ModifierStateManager,
    modeManager: KeyboardModeManager
) {
    val macro = intent.macroName()?.let { macroRepo.getMacroByName(it) }
    val steps = macro?.let { MacroSerializer.deserialize(it.keySequence) } ?: emptyList()
    if (macro != null) {
        macroEngine.replay(steps, bridge, modifierState)
        macroRepo.incrementUsage(macro.id)
        modeManager.setMode(KeyboardMode.Normal)
    }
    logMacroState(
        macroRepo,
        "macro_replay",
        macro != null,
        stepCount = steps.size,
        usageCount = if (macro != null) macro.usageCount + 1 else 0
    )
}

private suspend fun handleMacroDelete(intent: Intent, macroRepo: MacroRepository) {
    val deleted = intent.macroName()?.let { macroRepo.deleteMacrosByName(it) } ?: 0
    logMacroState(macroRepo, "macro_delete", deleted > 0, affectedCount = deleted)
}

private suspend fun handleMacroRename(intent: Intent, macroRepo: MacroRepository) {
    val oldName = intent.getStringExtra("old_name")?.takeIf { it.isNotBlank() }
    val newName = intent.getStringExtra("new_name")?.takeIf { it.isNotBlank() }
    val updated = if (oldName != null && newName != null) {
        macroRepo.updateMacroName(oldName, newName)
    } else {
        0
    }
    logMacroState(macroRepo, "macro_rename", updated > 0, affectedCount = updated)
}

private fun Intent.macroName(): String? =
    getStringExtra("name")?.take(64)?.takeIf { it.isNotBlank() }

private suspend fun logMacroState(
    macroRepo: MacroRepository,
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
