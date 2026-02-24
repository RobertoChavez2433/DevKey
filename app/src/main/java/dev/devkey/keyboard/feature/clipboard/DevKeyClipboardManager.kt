package dev.devkey.keyboard.feature.clipboard

import android.content.ClipboardManager
import android.content.Context
import dev.devkey.keyboard.core.KeyboardActionBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manages system clipboard listening and integrates with the clipboard history repository.
 *
 * Listens for clipboard changes and stores new entries in the Room database.
 *
 * @param context The application context.
 * @param repository The clipboard repository for database operations.
 */
class DevKeyClipboardManager(
    private val context: Context,
    private val repository: ClipboardRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val systemClipboardManager: ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = systemClipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(context)?.toString()
            if (!text.isNullOrBlank()) {
                scope.launch {
                    repository.addEntry(text)
                }
            }
        }
    }

    /**
     * Starts listening for system clipboard changes.
     */
    fun startListening() {
        systemClipboardManager.addPrimaryClipChangedListener(listener)
    }

    /**
     * Stops listening for system clipboard changes.
     */
    fun stopListening() {
        systemClipboardManager.removePrimaryClipChangedListener(listener)
    }

    /**
     * Pastes a clipboard entry by sending its content through the keyboard bridge.
     */
    fun pasteEntry(content: String, bridge: KeyboardActionBridge) {
        bridge.onText(content)
    }
}
