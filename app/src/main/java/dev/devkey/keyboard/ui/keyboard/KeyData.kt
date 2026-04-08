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
 * @param longPressCodes Optional list of keycodes for multi-char long-press popup.
 * @param type The key type, used for styling and behavior.
 * @param weight Flex weight for layout sizing (default 1.0f).
 * @param isRepeatable Whether the key repeats when held (default false).
 */
data class KeyData(
    val primaryLabel: String,
    val primaryCode: Int,
    val longPressLabel: String? = null,
    val longPressCode: Int? = null,
    // WHY: SwiftKey-style multi-char long-press popups (e.g. a → à á â ä)
    // need to present multiple candidates. When non-null, KeyView
    // opens a popup offering all of these as selectable options;
    // longPressCode remains the "primary" (first) long-press for
    // backward-compat and quick-flick dispatch.
    // FROM SPEC: §4.2 "Same long-press popup content on every key"
    // (user chose Phase 5 option A — applies to COMPACT letter keys as well).
    // NOTE: Defaulted to null so every existing KeyData(...) site in
    // the codebase remains source-compatible.
    val longPressCodes: List<Int>? = null,
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
 *
 * This is the single source of truth for all keycodes used by the keyboard.
 * The keycode list must stay in sync with res/values/keycodes.xml.
 *
 * Keycodes prefixed with KEYCODE_ match (negative) Android KeyEvent keycodes.
 * Custom keycodes (OPTIONS, VOICE, etc.) use arbitrary negative values.
 *
 * Java callers: access as KeyCodes.CONSTANT_NAME (const val compiles to public static final).
 */
object KeyCodes {
    // --- ASCII keycodes (match LatinIME.onKey() switch cases) ---
    const val TAB = 9              // ASCII HT
    const val ENTER = 10           // ASCII LF
    const val ASCII_SPACE = ' '.code  // 32
    const val ASCII_PERIOD = '.'.code // 46

    // --- Compose/internal keycodes (from Keyboard base class) ---
    const val DELETE = -5          // matches Keyboard.KEYCODE_DELETE
    const val SYMBOLS = -2         // alias for Keyboard.KEYCODE_MODE_CHANGE

    // --- DPAD keycodes (negative KeyEvent values) ---
    const val KEYCODE_DPAD_UP = -19
    const val KEYCODE_DPAD_DOWN = -20
    const val KEYCODE_DPAD_LEFT = -21
    const val KEYCODE_DPAD_RIGHT = -22
    const val KEYCODE_DPAD_CENTER = -23

    // --- Modifier keycodes (negative KeyEvent values) ---
    const val ALT_LEFT = -57
    const val CTRL_LEFT = -113
    const val KEYCODE_CAPS_LOCK = -115
    const val KEYCODE_META_LEFT = -117
    const val KEYCODE_FN = -119

    // --- Navigation keycodes (negative KeyEvent values) ---
    const val KEYCODE_PAGE_UP = -92
    const val KEYCODE_PAGE_DOWN = -93
    const val ESCAPE = -111
    const val KEYCODE_FORWARD_DEL = -112
    const val KEYCODE_SCROLL_LOCK = -116
    const val KEYCODE_SYSRQ = -120
    const val KEYCODE_BREAK = -121
    const val KEYCODE_HOME = -122
    const val KEYCODE_END = -123
    const val KEYCODE_INSERT = -124

    // --- Function keycodes ---
    const val KEYCODE_FKEY_F1 = -131
    const val KEYCODE_FKEY_F2 = -132
    const val KEYCODE_FKEY_F3 = -133
    const val KEYCODE_FKEY_F4 = -134
    const val KEYCODE_FKEY_F5 = -135
    const val KEYCODE_FKEY_F6 = -136
    const val KEYCODE_FKEY_F7 = -137
    const val KEYCODE_FKEY_F8 = -138
    const val KEYCODE_FKEY_F9 = -139
    const val KEYCODE_FKEY_F10 = -140
    const val KEYCODE_FKEY_F11 = -141
    const val KEYCODE_FKEY_F12 = -142
    const val KEYCODE_NUM_LOCK = -143

    // --- Custom keyboard keycodes ---
    const val KEYCODE_OPTIONS = -100
    const val KEYCODE_OPTIONS_LONGPRESS = -101
    const val KEYCODE_VOICE = -102
    const val KEYCODE_F1 = -103
    const val KEYCODE_NEXT_LANGUAGE = -104
    const val KEYCODE_PREV_LANGUAGE = -105
    const val KEYCODE_COMPOSE = -10024

    // --- Compose UI keycodes ---
    const val EMOJI = -300         // handled in DevKeyKeyboard.kt
    const val SMART_BACK_ESC = -301 // sent by smart backspace/esc key, resolved in DevKeyKeyboard.kt
}
