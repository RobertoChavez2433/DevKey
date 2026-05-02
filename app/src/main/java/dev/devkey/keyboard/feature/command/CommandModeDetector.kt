package dev.devkey.keyboard.feature.command

import dev.devkey.keyboard.debug.DevKeyLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

/**
 * Detects whether the keyboard should operate in command mode based on
 * the current foreground app.
 *
 * Detection priority:
 * 1. Manual user toggle (cleared on app switch)
 * 2. Per-app user override (from CommandModeRepository)
 * 3. Built-in terminal app detection
 * 4. Default: NORMAL
 *
 * @param repository Persistence for user-configured app overrides.
 */
class CommandModeDetector(private val repository: CommandModeRepository) {

    private val _inputMode = MutableStateFlow(InputMode.NORMAL)

    /** Observable input mode state. */
    val inputMode: StateFlow<InputMode> = _inputMode.asStateFlow()

    /** Manual override set by user toggle, cleared on app switch. */
    private var manualOverride: InputMode? = null

    /** Tracks the last-seen foreground package to detect app switches. */
    private var lastPackageName: String? = null

    companion object {
        /** Known terminal emulator packages for auto-detection. */
        val TERMINAL_PACKAGES = setOf(
            "com.termux",
            "org.connectbot",
            "com.sonelli.juicessh",
            "com.server.auditor",
            "com.offsec.nethunter",
            "jackpal.androidterm",
            "yarolegovich.materialterminal",
            "com.termoneplus",
            "com.googlecode.android_scripting"
        )
    }

    /**
     * Detect the appropriate input mode for the given foreground app.
     *
     * Called from onStartInputView when the keyboard is shown.
     *
     * @param packageName The foreground app's package name.
     */
    suspend fun detect(packageName: String?) {
        // Clear manual override on app switch
        if (packageName != lastPackageName) {
            manualOverride = null
        }
        lastPackageName = packageName

        // Priority 1: Manual override
        if (manualOverride != null) {
            _inputMode.value = manualOverride!!
            return
        }

        // Priority 2: User-configured per-app override
        val userOverride = repository.getMode(packageName)
        if (userOverride != null) {
            _inputMode.value = userOverride
            return
        }

        // Priority 3: Built-in terminal detection
        if (packageName != null && packageName in TERMINAL_PACKAGES) {
            // Phase 4.8 — structural command-mode emit for E2E smoke tests.
            // PRIVACY: trigger identifier only — NEVER log package name, command buffers, or typed content.
            DevKeyLogger.ime(
                "command_mode_auto_enabled",
                mapOf("trigger" to "terminal_detect")
            )
            _inputMode.value = InputMode.COMMAND
            return
        }

        // Default: NORMAL
        _inputMode.value = InputMode.NORMAL
    }

    /**
     * Synchronous wrapper for [detect], for use from Java code.
     * Only call from a background thread.
     *
     * @param packageName The foreground app's package name.
     */
    fun detectSync(packageName: String?) {
        runBlocking { detect(packageName) }
    }

    /**
     * Toggle manual command mode override.
     *
     * Cycles between COMMAND and NORMAL on each call.
     */
    fun toggleManualOverride() {
        manualOverride = if (manualOverride == null || manualOverride == InputMode.NORMAL) {
            InputMode.COMMAND
        } else {
            InputMode.NORMAL
        }
        _inputMode.value = manualOverride!!
        // Phase 4.8 — structural command-mode manual-toggle emit for E2E smoke tests.
        // PRIVACY: state-name only — NEVER log command buffers or typed content.
        if (manualOverride == InputMode.COMMAND) {
            DevKeyLogger.ime(
                "command_mode_manual_enabled",
                mapOf("trigger" to "user_toggle")
            )
        } else {
            DevKeyLogger.ime(
                "command_mode_manual_disabled",
                mapOf("trigger" to "user_toggle")
            )
        }
    }

    /**
     * Check whether the keyboard is currently in command mode.
     */
    fun isCommandMode(): Boolean = _inputMode.value == InputMode.COMMAND
}
