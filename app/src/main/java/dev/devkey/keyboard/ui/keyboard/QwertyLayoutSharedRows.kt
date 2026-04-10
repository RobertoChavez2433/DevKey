package dev.devkey.keyboard.ui.keyboard

// DPAD keycodes shared across layout builders
internal const val KEYCODE_DPAD_UP = -19
internal const val KEYCODE_DPAD_DOWN = -20
internal const val KEYCODE_DPAD_LEFT = -21
internal const val KEYCODE_DPAD_RIGHT = -22

/**
 * Builds the home row for the FULL layout (A-L with symbol long-press assignments).
 * 'a' gets accented vowel longPressCodes; others retain their existing symbol long-press.
 */
internal fun buildFullHomeRow(): KeyRowData = KeyRowData(
    keys = listOf(
        KeyData(
            primaryLabel = "a",
            primaryCode = 'a'.code,
            longPressLabel = "à",
            longPressCode = 'à'.code,
            longPressCodes = listOf('à'.code, 'á'.code, 'â'.code, 'ã'.code, 'ä'.code, 'å'.code, 'æ'.code)
        ),
        KeyData("s", 's'.code, longPressLabel = "|", longPressCode = '|'.code),
        KeyData("d", 'd'.code, longPressLabel = "\\", longPressCode = '\\'.code),
        KeyData("f", 'f'.code, longPressLabel = "{", longPressCode = '{'.code),
        KeyData("g", 'g'.code, longPressLabel = "}", longPressCode = '}'.code),
        KeyData("h", 'h'.code, longPressLabel = "[", longPressCode = '['.code),
        KeyData("j", 'j'.code, longPressLabel = "]", longPressCode = ']'.code),
        KeyData("k", 'k'.code, longPressLabel = "\"", longPressCode = '"'.code),
        KeyData("l", 'l'.code, longPressLabel = "'", longPressCode = '\''.code)
    )
)

/**
 * Builds the space row: 123 ☺ 🎤 , Space .?! Enter
 * Shared across all layout modes.
 *
 * SwiftKey parity:
 *  - Bottom row: 123 ☺ , [Space] . ↵ — six keys.
 *  - Comma long-press hint is 🎤 (mic), fires KEYCODE_VOICE.
 *  - `.` key long-press shows `!?` hint per SwiftKey reference.
 */
internal fun buildSpaceRow(): KeyRowData = KeyRowData(
    keys = listOf(
        KeyData(
            primaryLabel = "123",
            primaryCode = KeyCodes.SYMBOLS,
            type = KeyType.TOGGLE,
            weight = 1.0f
        ),
        KeyData(
            // WHY: SwiftKey renders the emoji key as a monochrome outline glyph,
            //      not a colored system emoji. The variation selector U+FE0E forces
            //      text-style rendering so Android uses the sans-serif font's outline
            //      smiley glyph (painted with the key text color) instead of the
            //      colored EmojiCompat fallback.
            primaryLabel = "\u263A\uFE0E",  // smiley face — text-style variation
            primaryCode = KeyCodes.EMOJI,
            type = KeyType.TOGGLE,
            weight = 0.8f
        ),
        KeyData(
            // SwiftKey parity: comma with mic long-press hint.
            // The mic icon is the long-press indicator on the comma key,
            // not a standalone button.
            primaryLabel = ",",
            primaryCode = ','.code,
            longPressLabel = "\uD83C\uDFA4",  // 🎤 mic hint
            longPressCode = KeyCodes.KEYCODE_VOICE,
            type = KeyType.LETTER,
            weight = 0.8f
        ),
        KeyData(
            primaryLabel = " ",
            primaryCode = ' '.code,
            type = KeyType.SPACEBAR,
            weight = 3.2f
        ),
        KeyData(
            primaryLabel = ".",
            primaryCode = '.'.code,
            longPressLabel = ",!?",
            longPressCode = '?'.code,
            longPressCodes = listOf(','.code, '!'.code, '?'.code),
            type = KeyType.LETTER,
            weight = 0.8f
        ),
        KeyData(
            // SwiftKey parity — return/enter glyph ↵
            primaryLabel = "\u21B5",  // ↵ downwards arrow with corner leftwards
            primaryCode = KeyCodes.ENTER,
            type = KeyType.ACTION,
            weight = 1.6f
        )
    )
)
