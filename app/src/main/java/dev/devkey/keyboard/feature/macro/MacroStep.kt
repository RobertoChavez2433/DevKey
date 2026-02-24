package dev.devkey.keyboard.feature.macro

/**
 * Represents a single step in a macro sequence.
 *
 * @param key The character key pressed (e.g., "c", "a").
 * @param keyCode The integer keycode (e.g., 99).
 * @param modifiers List of active modifiers (e.g., ["ctrl"], ["ctrl", "shift"]).
 */
data class MacroStep(
    val key: String,
    val keyCode: Int,
    val modifiers: List<String>
)
