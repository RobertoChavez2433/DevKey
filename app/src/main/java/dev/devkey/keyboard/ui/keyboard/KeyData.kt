package dev.devkey.keyboard.ui.keyboard

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    SPACEBAR
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
 * @param rowHeight The height of this row (default 48.dp).
 */
data class KeyRowData(
    val keys: List<KeyData>,
    val rowHeight: Dp = 48.dp
)

/**
 * Data class representing a complete keyboard layout.
 *
 * @param rows The list of key rows from top to bottom.
 */
data class KeyboardLayoutData(
    val rows: List<KeyRowData>
)
