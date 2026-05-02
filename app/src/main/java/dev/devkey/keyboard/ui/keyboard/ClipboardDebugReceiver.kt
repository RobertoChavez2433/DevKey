package dev.devkey.keyboard.ui.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import dev.devkey.keyboard.core.KeyboardActionBridge
import dev.devkey.keyboard.core.KeyboardModeManager
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.feature.clipboard.ClipboardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun RegisterClipboardDebugReceiver(
    clipboardRepo: ClipboardRepository,
    bridge: KeyboardActionBridge,
    modeManager: KeyboardModeManager,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    DisposableEffect(clipboardRepo, bridge, modeManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                coroutineScope.launch {
                    when (i.action) {
                        "dev.devkey.keyboard.CLIPBOARD_ADD" -> handleClipboardAdd(i, clipboardRepo)
                        "dev.devkey.keyboard.CLIPBOARD_PASTE" -> handleClipboardPaste(i, clipboardRepo, bridge, modeManager)
                        "dev.devkey.keyboard.CLIPBOARD_PIN" -> handleClipboardPin(i, clipboardRepo)
                        "dev.devkey.keyboard.CLIPBOARD_CLEAR_ALL" -> {
                            clipboardRepo.clearEverything()
                            logClipboardState(clipboardRepo, "clipboard_clear_all", null, null, true)
                        }
                        "dev.devkey.keyboard.CLIPBOARD_CLEAR_UNPINNED" -> {
                            clipboardRepo.clearAll()
                            logClipboardState(clipboardRepo, "clipboard_clear_unpinned", null, null, true)
                        }
                    }
                }
            }
        }
        context.registerDebugReceiver(
            receiver,
            IntentFilter().apply {
                addAction("dev.devkey.keyboard.CLIPBOARD_ADD")
                addAction("dev.devkey.keyboard.CLIPBOARD_PASTE")
                addAction("dev.devkey.keyboard.CLIPBOARD_PIN")
                addAction("dev.devkey.keyboard.CLIPBOARD_CLEAR_ALL")
                addAction("dev.devkey.keyboard.CLIPBOARD_CLEAR_UNPINNED")
            }
        )
        onDispose { context.unregisterReceiverSafely(receiver) }
    }
}

private suspend fun handleClipboardAdd(intent: Intent, clipboardRepo: ClipboardRepository) {
    val index = intent.getIntExtra("entry_index", 0)
    val lengthHint = intent.getIntExtra("length_hint", 24).coerceIn(1, 5000)
    val specialChars = intent.getBooleanExtra("special_chars", false)
    val content = if (specialChars) {
        "clip-$index @#\$% [] {} <>"
    } else {
        "clip-$index-" + "x".repeat(lengthHint)
    }
    clipboardRepo.addEntry(content)
    logClipboardState(clipboardRepo, "clipboard_add", index, lengthHint, true)
}

private suspend fun handleClipboardPaste(
    intent: Intent,
    clipboardRepo: ClipboardRepository,
    bridge: KeyboardActionBridge,
    modeManager: KeyboardModeManager
) {
    val index = intent.getIntExtra("entry_index", 0)
    val entry = clipboardRepo.getAllSnapshot().getOrNull(index)
    if (entry != null) {
        bridge.onText(entry.content)
        modeManager.setMode(KeyboardMode.Normal)
    }
    logClipboardState(clipboardRepo, "clipboard_paste", index, entry?.content?.length ?: 0, entry != null)
}

private suspend fun handleClipboardPin(intent: Intent, clipboardRepo: ClipboardRepository) {
    val index = intent.getIntExtra("entry_index", 0)
    val pinned = clipboardRepo.pinByIndex(index)
    logClipboardState(clipboardRepo, "clipboard_pin", index, null, pinned)
}

private suspend fun logClipboardState(
    clipboardRepo: ClipboardRepository,
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
