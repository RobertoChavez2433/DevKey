package dev.devkey.keyboard.ui.keyboard


/**
 * Type classification for keyboard keys. Determines visual styling and behavior.
 */
enum class KeyType {
    LETTER,
    NUMBER,
    MODIFIER,
    ACTION,
    ARROW,
    SPECIAL,
    SPACEBAR,
    UTILITY,
    TOGGLE
}

/**
 * Layout mode for the keyboard. Determines row structure and key arrangement.
 *
 * - COMPACT: 4-row clean SwiftKey layout, no long-press on letter keys
 * - COMPACT_DEV: 4-row layout with numbers/symbols on long-press
 * - FULL: 6-row layout with number row and utility row
 */
enum class LayoutMode {
    COMPACT,
    COMPACT_DEV,
    FULL
}

/**
 * Data class representing a single key on the keyboard.
 *
 * @param primaryLabel The label displayed on the key face.
 * @param primaryCode The keycode sent when the key is tapped.
 * @param longPressLabel Optional label for long-press hint (shown top-right).
 * @param longPressCode Optional keycode sent on long-press.
 * @param type The key type, used for styling and behavior.
 * @param weight Flex weight for layout sizing (default 1.0f).
 * @param isRepeatable Whether the key repeats when held (default false).
 */
data class KeyData(
    val primaryLabel: String,
    val primaryCode: Int,
    val longPressLabel: String? = null,
    val longPressCode: Int? = null,
    val type: KeyType = KeyType.LETTER,
    val weight: Float = 1.0f,
    val isRepeatable: Boolean = false
)

/**
 * Data class representing a row of keys.
 *
 * @param keys The list of keys in this row.
 */
data class KeyRowData(
    val keys: List<KeyData>
)

/**
 * Data class representing a complete keyboard layout.
 *
 * @param rows The list of key rows from top to bottom.
 */
data class KeyboardLayoutData(
    val rows: List<KeyRowData>
)

/**
 * Shared keycode constants for special keys used across keyboard components.
 */
object KeyCodes {
    const val CTRL_LEFT = -113
    const val ALT_LEFT = -57
    const val ESCAPE = -111
    const val TAB = 9      // ASCII HT, matches LatinIME.onKey() switch case
    const val ENTER = 10
    const val DELETE = -5          // matches Keyboard.KEYCODE_DELETE
    const val SYMBOLS = -2         // alias for Keyboard.KEYCODE_MODE_CHANGE, handled by LatinIME
    const val EMOJI = -300         // new, handled in DevKeyKeyboard.kt
    const val SMART_BACK_ESC = -301 // sent by smart backspace/esc key, resolved in DevKeyKeyboard.kt
}
