package dev.devkey.keyboard.core

import android.util.Log
import dev.devkey.keyboard.ui.keyboard.KeyboardMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the current keyboard mode using StateFlow.
 *
 * This replaces the previous mutableStateOf approach which had issues with
 * Compose snapshot propagation from pointerInput coroutine scopes.
 * StateFlow guarantees emission on the main thread and reliable collection
 * via collectAsState().
 */
class KeyboardModeManager {

    private val _mode = MutableStateFlow<KeyboardMode>(KeyboardMode.Normal)

    /** Observable keyboard mode state. Collect with collectAsState() in Compose. */
    val mode: StateFlow<KeyboardMode> = _mode.asStateFlow()

    /** Current mode value (non-reactive read). */
    val currentMode: KeyboardMode get() = _mode.value

    /**
     * Toggle between the target mode and Normal.
     *
     * If the current mode matches [target], switches back to Normal.
     * Otherwise, switches to [target].
     */
    fun toggleMode(target: KeyboardMode) {
        val before = _mode.value
        _mode.value = if (_mode.value == target) KeyboardMode.Normal else target
        Log.d("DevKeyMode", "toggleMode: $before -> ${_mode.value} (post-write)")
    }

    /**
     * Directly set the keyboard mode.
     */
    fun setMode(mode: KeyboardMode) {
        val before = _mode.value
        _mode.value = mode
        Log.d("DevKeyMode", "setMode: $before -> ${_mode.value}")
    }
}
