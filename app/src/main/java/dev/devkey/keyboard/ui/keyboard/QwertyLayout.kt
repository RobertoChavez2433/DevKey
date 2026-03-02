package dev.devkey.keyboard.ui.keyboard

import dev.devkey.keyboard.Keyboard

/**
 * Defines the QWERTY keyboard layouts for DevKey.
 *
 * Supports three layout modes:
 * - FULL: 6-row layout with number row, QWERTY, home, Z, space, and utility rows
 * - COMPACT: 4-row clean SwiftKey layout with no long-press on letter keys
 * - COMPACT_DEV: 4-row layout with numbers/symbols on long-press
 *
 * Uses keycodes from [Keyboard] for standard keys, and [KeyCodes] for special keys.
 */
object QwertyLayout {

    // DPAD keycodes
    private const val KEYCODE_DPAD_UP = -19
    private const val KEYCODE_DPAD_DOWN = -20
    private const val KEYCODE_DPAD_LEFT = -21
    private const val KEYCODE_DPAD_RIGHT = -22

    private val fullLayout: KeyboardLayoutData by lazy { buildFullLayout() }
    private val compactLayout: KeyboardLayoutData by lazy { buildCompactLayout() }
    private val compactDevLayout: KeyboardLayoutData by lazy { buildCompactDevLayout() }

    /**
     * Returns the appropriate layout for the given [LayoutMode].
     */
    fun getLayout(mode: LayoutMode): KeyboardLayoutData = when (mode) {
        LayoutMode.FULL -> fullLayout
        LayoutMode.COMPACT -> compactLayout
        LayoutMode.COMPACT_DEV -> compactDevLayout
    }

    /**
     * Builds the 6-row FULL layout.
     *
     * Row 0: Number row (` 1 2 3 4 5 6 7 8 9 0)
     * Row 1: QWERTY row with symbol long-press
     * Row 2: Home row (A-L) with symbol long-press
     * Row 3: Z row (Shift + letters + smart backspace/esc)
     * Row 4: Space row (123 emoji , Space . Enter)
     * Row 5: Utility row (Ctrl Alt Tab [spacer] arrows)
     */
    private fun buildFullLayout(): KeyboardLayoutData {
        val numberRow = KeyRowData(
            keys = listOf(
                KeyData(
                    primaryLabel = "`",
                    primaryCode = '`'.code,
                    longPressLabel = "~",
                    longPressCode = '~'.code,
                    type = KeyType.SPECIAL,
                    weight = 1.0f
                ),
                KeyData("1", '1'.code, type = KeyType.NUMBER),
                KeyData("2", '2'.code, type = KeyType.NUMBER),
                KeyData("3", '3'.code, type = KeyType.NUMBER),
                KeyData("4", '4'.code, type = KeyType.NUMBER),
                KeyData("5", '5'.code, type = KeyType.NUMBER),
                KeyData("6", '6'.code, type = KeyType.NUMBER),
                KeyData("7", '7'.code, type = KeyType.NUMBER),
                KeyData("8", '8'.code, type = KeyType.NUMBER),
                KeyData("9", '9'.code, type = KeyType.NUMBER),
                KeyData("0", '0'.code, type = KeyType.NUMBER)
            )
        )

        val qwertyRow = KeyRowData(
            keys = listOf(
                KeyData("q", 'q'.code, longPressLabel = "!", longPressCode = '!'.code),
                KeyData("w", 'w'.code, longPressLabel = "@", longPressCode = '@'.code),
                KeyData("e", 'e'.code, longPressLabel = "#", longPressCode = '#'.code),
                KeyData("r", 'r'.code, longPressLabel = "$", longPressCode = '$'.code),
                KeyData("t", 't'.code, longPressLabel = "%", longPressCode = '%'.code),
                KeyData("y", 'y'.code, longPressLabel = "^", longPressCode = '^'.code),
                KeyData("u", 'u'.code, longPressLabel = "&", longPressCode = '&'.code),
                KeyData("i", 'i'.code, longPressLabel = "*", longPressCode = '*'.code),
                KeyData("o", 'o'.code, longPressLabel = "(", longPressCode = '('.code),
                KeyData("p", 'p'.code, longPressLabel = ")", longPressCode = ')'.code)
            )
        )

        val zRow = KeyRowData(
            keys = listOf(
                KeyData(
                    primaryLabel = "Shift",
                    primaryCode = Keyboard.KEYCODE_SHIFT,
                    type = KeyType.MODIFIER,
                    weight = 1.5f
                ),
                KeyData("z", 'z'.code, longPressLabel = ";", longPressCode = ';'.code),
                KeyData("x", 'x'.code, longPressLabel = ":", longPressCode = ':'.code),
                KeyData("c", 'c'.code, longPressLabel = "/", longPressCode = '/'.code),
                KeyData("v", 'v'.code, longPressLabel = "?", longPressCode = '?'.code),
                KeyData("b", 'b'.code, longPressLabel = "<", longPressCode = '<'.code),
                KeyData("n", 'n'.code, longPressLabel = ">", longPressCode = '>'.code),
                KeyData("m", 'm'.code, longPressLabel = "_", longPressCode = '_'.code),
                KeyData(
                    primaryLabel = "\u232B",  // ⌫ erase to the left symbol
                    primaryCode = KeyCodes.SMART_BACK_ESC,
                    longPressLabel = "Esc",
                    longPressCode = KeyCodes.ESCAPE,
                    type = KeyType.ACTION,
                    weight = 1.5f,
                    isRepeatable = true
                )
            )
        )

        val spaceRow = buildSpaceRow()

        val utilityRow = KeyRowData(
            keys = listOf(
                KeyData(
                    primaryLabel = "Ctrl",
                    primaryCode = KeyCodes.CTRL_LEFT,
                    type = KeyType.UTILITY,
                    weight = 1.5f
                ),
                KeyData(
                    primaryLabel = "Alt",
                    primaryCode = KeyCodes.ALT_LEFT,
                    type = KeyType.UTILITY,
                    weight = 1.5f
                ),
                KeyData(
                    primaryLabel = "Tab",
                    primaryCode = KeyCodes.TAB,
                    type = KeyType.UTILITY,
                    weight = 1.5f
                ),
                // Spacer
                KeyData(
                    primaryLabel = "",
                    primaryCode = 0,
                    type = KeyType.SPECIAL,
                    weight = 1.0f
                ),
                KeyData(
                    primaryLabel = "\u2190", // left arrow
                    primaryCode = KEYCODE_DPAD_LEFT,
                    type = KeyType.UTILITY,
                    weight = 1.2f,
                    isRepeatable = true
                ),
                KeyData(
                    primaryLabel = "\u2193", // down arrow
                    primaryCode = KEYCODE_DPAD_DOWN,
                    type = KeyType.UTILITY,
                    weight = 1.2f
                ),
                KeyData(
                    primaryLabel = "\u2191", // up arrow
                    primaryCode = KEYCODE_DPAD_UP,
                    type = KeyType.UTILITY,
                    weight = 1.2f
                ),
                KeyData(
                    primaryLabel = "\u2192", // right arrow
                    primaryCode = KEYCODE_DPAD_RIGHT,
                    type = KeyType.UTILITY,
                    weight = 1.2f,
                    isRepeatable = true
                )
            )
        )

        return KeyboardLayoutData(
            rows = listOf(numberRow, qwertyRow, buildHomeRow(), zRow, spaceRow, utilityRow)
        )
    }

    /**
     * Builds the 4-row COMPACT layout (clean SwiftKey, NO long-press on letter keys).
     */
    private fun buildCompactLayout(): KeyboardLayoutData {
        val qwertyRow = KeyRowData(
            keys = listOf(
                KeyData("q", 'q'.code),
                KeyData("w", 'w'.code),
                KeyData("e", 'e'.code),
                KeyData("r", 'r'.code),
                KeyData("t", 't'.code),
                KeyData("y", 'y'.code),
                KeyData("u", 'u'.code),
                KeyData("i", 'i'.code),
                KeyData("o", 'o'.code),
                KeyData("p", 'p'.code)
            )
        )

        val homeRow = KeyRowData(
            keys = listOf(
                KeyData("a", 'a'.code),
                KeyData("s", 's'.code),
                KeyData("d", 'd'.code),
                KeyData("f", 'f'.code),
                KeyData("g", 'g'.code),
                KeyData("h", 'h'.code),
                KeyData("j", 'j'.code),
                KeyData("k", 'k'.code),
                KeyData("l", 'l'.code)
            )
        )

        val zRow = KeyRowData(
            keys = listOf(
                KeyData(
                    primaryLabel = "Shift",
                    primaryCode = Keyboard.KEYCODE_SHIFT,
                    type = KeyType.MODIFIER,
                    weight = 1.5f
                ),
                KeyData("z", 'z'.code),
                KeyData("x", 'x'.code),
                KeyData("c", 'c'.code),
                KeyData("v", 'v'.code),
                KeyData("b", 'b'.code),
                KeyData("n", 'n'.code),
                KeyData("m", 'm'.code),
                KeyData(
                    primaryLabel = "Del",
                    primaryCode = Keyboard.KEYCODE_DELETE,
                    type = KeyType.ACTION,
                    weight = 1.5f,
                    isRepeatable = true
                )
            )
        )

        return KeyboardLayoutData(
            rows = listOf(qwertyRow, homeRow, zRow, buildSpaceRow())
        )
    }

    /**
     * Builds the 4-row COMPACT_DEV layout (numbers + symbols on long-press).
     */
    private fun buildCompactDevLayout(): KeyboardLayoutData {
        val qwertyRow = KeyRowData(
            keys = listOf(
                KeyData("q", 'q'.code, longPressLabel = "1", longPressCode = '1'.code),
                KeyData("w", 'w'.code, longPressLabel = "2", longPressCode = '2'.code),
                KeyData("e", 'e'.code, longPressLabel = "3", longPressCode = '3'.code),
                KeyData("r", 'r'.code, longPressLabel = "4", longPressCode = '4'.code),
                KeyData("t", 't'.code, longPressLabel = "5", longPressCode = '5'.code),
                KeyData("y", 'y'.code, longPressLabel = "6", longPressCode = '6'.code),
                KeyData("u", 'u'.code, longPressLabel = "7", longPressCode = '7'.code),
                KeyData("i", 'i'.code, longPressLabel = "8", longPressCode = '8'.code),
                KeyData("o", 'o'.code, longPressLabel = "9", longPressCode = '9'.code),
                KeyData("p", 'p'.code, longPressLabel = "0", longPressCode = '0'.code)
            )
        )

        val zRow = KeyRowData(
            keys = listOf(
                KeyData(
                    primaryLabel = "Shift",
                    primaryCode = Keyboard.KEYCODE_SHIFT,
                    type = KeyType.MODIFIER,
                    weight = 1.5f
                ),
                KeyData("z", 'z'.code, longPressLabel = ";", longPressCode = ';'.code),
                KeyData("x", 'x'.code, longPressLabel = ":", longPressCode = ':'.code),
                KeyData("c", 'c'.code, longPressLabel = "/", longPressCode = '/'.code),
                KeyData("v", 'v'.code, longPressLabel = "?", longPressCode = '?'.code),
                KeyData("b", 'b'.code, longPressLabel = "<", longPressCode = '<'.code),
                KeyData("n", 'n'.code, longPressLabel = ">", longPressCode = '>'.code),
                KeyData("m", 'm'.code, longPressLabel = "_", longPressCode = '_'.code),
                KeyData(
                    primaryLabel = "\u232B",  // ⌫ erase to the left symbol
                    primaryCode = KeyCodes.SMART_BACK_ESC,
                    longPressLabel = "Esc",
                    longPressCode = KeyCodes.ESCAPE,
                    type = KeyType.ACTION,
                    weight = 1.5f,
                    isRepeatable = true
                )
            )
        )

        return KeyboardLayoutData(
            rows = listOf(qwertyRow, buildHomeRow(), zRow, buildSpaceRow())
        )
    }

    /**
     * Builds the home row (A-L keys with symbol long-press assignments).
     * Shared between the Full and Compact Dev layouts.
     */
    private fun buildHomeRow(): KeyRowData = KeyRowData(
        keys = listOf(
            KeyData("a", 'a'.code, longPressLabel = "~", longPressCode = '~'.code),
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
     * Builds the space row: 123 emoji , Space . Enter
     * Shared across all layout modes.
     */
    private fun buildSpaceRow(): KeyRowData = KeyRowData(
        keys = listOf(
            KeyData(
                primaryLabel = "123",
                primaryCode = KeyCodes.SYMBOLS,
                type = KeyType.TOGGLE,
                weight = 1.0f
            ),
            KeyData(
                primaryLabel = "\u263A",  // smiley face (emoji key)
                primaryCode = KeyCodes.EMOJI,
                type = KeyType.TOGGLE,
                weight = 0.8f
            ),
            KeyData(
                primaryLabel = ",",
                primaryCode = ','.code,
                type = KeyType.LETTER,
                weight = 0.8f
            ),
            KeyData(
                primaryLabel = " ",
                primaryCode = ' '.code,
                type = KeyType.SPACEBAR,
                weight = 4.0f
            ),
            KeyData(
                primaryLabel = ".",
                primaryCode = '.'.code,
                type = KeyType.LETTER,
                weight = 0.8f
            ),
            KeyData(
                primaryLabel = "Enter",
                primaryCode = KeyCodes.ENTER,
                type = KeyType.ACTION,
                weight = 1.6f
            )
        )
    )
}
