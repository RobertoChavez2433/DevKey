package dev.devkey.keyboard.ui.keyboard

import dev.devkey.keyboard.Keyboard

/**
 * Defines the full 5-row QWERTY keyboard layout for DevKey.
 *
 * Uses keycodes from [Keyboard] for standard keys, and local constants
 * matching LatinKeyboardView's package-private keycodes for special keys.
 */
object QwertyLayout {

    // DPAD keycodes (no shared constants needed for these)
    private const val KEYCODE_DPAD_UP = -19
    private const val KEYCODE_DPAD_DOWN = -20
    private const val KEYCODE_DPAD_LEFT = -21
    private const val KEYCODE_DPAD_RIGHT = -22

    val layout: KeyboardLayoutData by lazy { buildLayout() }
    val compactLayout: KeyboardLayoutData by lazy { buildCompactLayout() }

    /**
     * Returns the appropriate layout based on compact mode setting.
     */
    fun getLayout(compactMode: Boolean): KeyboardLayoutData {
        return if (compactMode) compactLayout else layout
    }

    private fun buildLayout(): KeyboardLayoutData {
        val numberRow = KeyRowData(
            keys = listOf(
                KeyData(
                    primaryLabel = "Esc",
                    primaryCode = KeyCodes.ESCAPE,
                    longPressLabel = "`",
                    longPressCode = '`'.code,
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
                KeyData(
                    primaryLabel = "Tab",
                    primaryCode = KeyCodes.TAB,
                    longPressLabel = "0",
                    longPressCode = '0'.code,
                    type = KeyType.SPECIAL,
                    weight = 1.0f
                )
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
                    primaryLabel = "Del",
                    primaryCode = Keyboard.KEYCODE_DELETE,
                    type = KeyType.ACTION,
                    weight = 1.5f,
                    isRepeatable = true
                )
            )
        )

        return KeyboardLayoutData(
            rows = listOf(numberRow, qwertyRow, buildHomeRow(), zRow, buildBottomRow())
        )
    }

    /**
     * Builds a compact 4-row layout (no number row).
     *
     * Remaps:
     * - QWERTY row long-press: digits 1-9,0 instead of symbols
     * - Shift long-press: Esc (keycode -111)
     * - Backspace long-press: Tab (keycode 9, ASCII HT)
     */
    private fun buildCompactLayout(): KeyboardLayoutData {
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
                    longPressLabel = "Esc",
                    longPressCode = KeyCodes.ESCAPE,
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
                    primaryLabel = "Del",
                    primaryCode = Keyboard.KEYCODE_DELETE,
                    longPressLabel = "Tab",
                    longPressCode = KeyCodes.TAB,
                    type = KeyType.ACTION,
                    weight = 1.5f,
                    isRepeatable = true
                )
            )
        )

        return KeyboardLayoutData(
            rows = listOf(qwertyRow, buildHomeRow(), zRow, buildBottomRow())
        )
    }

    /**
     * Builds the home row (A–L keys with symbol long-press assignments).
     * Shared between the full and compact layouts.
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
     * Builds the bottom row (Ctrl, Alt, Space, arrow keys, Enter).
     * Shared between the full and compact layouts.
     */
    private fun buildBottomRow(): KeyRowData = KeyRowData(
        keys = listOf(
            KeyData(
                primaryLabel = "Ctrl",
                primaryCode = KeyCodes.CTRL_LEFT,
                type = KeyType.MODIFIER,
                weight = 1.2f
            ),
            KeyData(
                primaryLabel = "Alt",
                primaryCode = KeyCodes.ALT_LEFT,
                type = KeyType.MODIFIER,
                weight = 1.2f
            ),
            KeyData(
                primaryLabel = " ",
                primaryCode = ' '.code,
                type = KeyType.SPACEBAR,
                weight = 5.0f
            ),
            KeyData(
                primaryLabel = "\u2190", // left arrow
                primaryCode = KEYCODE_DPAD_LEFT,
                type = KeyType.ARROW,
                weight = 1.0f,
                isRepeatable = true
            ),
            KeyData(
                primaryLabel = "\u2191", // up arrow
                primaryCode = KEYCODE_DPAD_UP,
                type = KeyType.ARROW,
                weight = 1.0f
            ),
            KeyData(
                primaryLabel = "\u2193", // down arrow
                primaryCode = KEYCODE_DPAD_DOWN,
                type = KeyType.ARROW,
                weight = 1.0f
            ),
            KeyData(
                primaryLabel = "\u2192", // right arrow
                primaryCode = KEYCODE_DPAD_RIGHT,
                type = KeyType.ARROW,
                weight = 1.0f,
                isRepeatable = true
            ),
            KeyData(
                primaryLabel = "Enter",
                primaryCode = KeyCodes.ENTER,
                type = KeyType.ACTION,
                weight = 1.5f
            )
        )
    )
}
