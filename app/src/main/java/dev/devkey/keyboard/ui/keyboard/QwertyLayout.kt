package dev.devkey.keyboard.ui.keyboard

import dev.devkey.keyboard.Keyboard

/**
 * Defines the QWERTY keyboard layouts for DevKey.
 *
 * Supports three layout modes:
 * - FULL: 6-row layout with number row, QWERTY, home, Z, space, and utility rows
 * - COMPACT: 4-row layout with digit long-press + accented vowel popups (option A)
 * - COMPACT_DEV: 4-row layout with hacker shift-symbol long-press
 *
 * Uses keycodes from [Keyboard] for standard keys, and [KeyCodes] for special keys.
 *
 * Long-press data added per plan Phase 5.2 + spec §4.2.
 * Templates are the default; SwiftKey reference screenshots for pixel-match
 * tuning land in Phase 6 (see .claude/test-flows/swiftkey-reference/).
 * Phase 5 decision: option A — COMPACT letter keys DO get long-press.
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

        // FULL qwerty row: existing shift-symbol long-press preserved;
        // vowels (e, u, i, o, y) get accented longPressCodes overlaid.
        val qwertyRow = KeyRowData(
            keys = listOf(
                KeyData("q", 'q'.code, longPressLabel = "!", longPressCode = '!'.code),
                KeyData("w", 'w'.code, longPressLabel = "@", longPressCode = '@'.code),
                KeyData(
                    primaryLabel = "e",
                    primaryCode = 'e'.code,
                    longPressLabel = "è",
                    longPressCode = 'è'.code,
                    longPressCodes = listOf('è'.code, 'é'.code, 'ê'.code, 'ë'.code, 'ē'.code)
                ),
                KeyData("r", 'r'.code, longPressLabel = "$", longPressCode = '$'.code),
                KeyData("t", 't'.code, longPressLabel = "%", longPressCode = '%'.code),
                KeyData(
                    primaryLabel = "y",
                    primaryCode = 'y'.code,
                    longPressLabel = "ý",
                    longPressCode = 'ý'.code,
                    longPressCodes = listOf('ý'.code, 'ÿ'.code)
                ),
                KeyData(
                    primaryLabel = "u",
                    primaryCode = 'u'.code,
                    longPressLabel = "ù",
                    longPressCode = 'ù'.code,
                    longPressCodes = listOf('ù'.code, 'ú'.code, 'û'.code, 'ü'.code, 'ū'.code)
                ),
                KeyData(
                    primaryLabel = "i",
                    primaryCode = 'i'.code,
                    longPressLabel = "ì",
                    longPressCode = 'ì'.code,
                    longPressCodes = listOf('ì'.code, 'í'.code, 'î'.code, 'ï'.code, 'ī'.code)
                ),
                KeyData(
                    primaryLabel = "o",
                    primaryCode = 'o'.code,
                    longPressLabel = "ò",
                    longPressCode = 'ò'.code,
                    longPressCodes = listOf('ò'.code, 'ó'.code, 'ô'.code, 'õ'.code, 'ö'.code, 'ø'.code, 'œ'.code)
                ),
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
                KeyData(
                    primaryLabel = "c",
                    primaryCode = 'c'.code,
                    longPressLabel = "ç",
                    longPressCode = 'ç'.code
                ),
                KeyData("v", 'v'.code, longPressLabel = "?", longPressCode = '?'.code),
                KeyData("b", 'b'.code, longPressLabel = "<", longPressCode = '<'.code),
                KeyData(
                    primaryLabel = "n",
                    primaryCode = 'n'.code,
                    longPressLabel = "ñ",
                    longPressCode = 'ñ'.code
                ),
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
            rows = listOf(numberRow, qwertyRow, buildFullHomeRow(), zRow, spaceRow, utilityRow)
        )
    }

    /**
     * Builds the 4-row COMPACT layout.
     *
     * Long-press data RETUNED from SwiftKey reference capture
     * (.claude/test-flows/swiftkey-reference/compact-dark-findings.md).
     * Reference wins over Phase 5.2 digit template per plan rule.
     * Vowels (a, e, i, o, u) KEEP their accent popups — richer than the
     * SwiftKey single-char default; additive §4.2 coverage.
     * Z-row corrected from cropped image re-read: z→_, x→$, c→", v→', b→:, n→;, m→/
     */
    private fun buildCompactLayout(): KeyboardLayoutData {
        val qwertyRow = KeyRowData(
            keys = listOf(
                KeyData("q", 'q'.code, longPressLabel = "%", longPressCode = '%'.code),
                KeyData("w", 'w'.code, longPressLabel = "^", longPressCode = '^'.code),
                // e — KEEP vowel accent popup (additive over SwiftKey ~ single-char)
                KeyData(
                    primaryLabel = "e",
                    primaryCode = 'e'.code,
                    longPressLabel = "è",
                    longPressCode = 'è'.code,
                    longPressCodes = listOf('è'.code, 'é'.code, 'ê'.code, 'ë'.code, 'ē'.code)
                ),
                KeyData("r", 'r'.code, longPressLabel = "|", longPressCode = '|'.code),
                KeyData("t", 't'.code, longPressLabel = "[", longPressCode = '['.code),
                // y — SwiftKey shows ]; drop ý/ÿ popup per reference
                KeyData("y", 'y'.code, longPressLabel = "]", longPressCode = ']'.code),
                // u — KEEP vowel accent popup
                KeyData(
                    primaryLabel = "u",
                    primaryCode = 'u'.code,
                    longPressLabel = "ù",
                    longPressCode = 'ù'.code,
                    longPressCodes = listOf('ù'.code, 'ú'.code, 'û'.code, 'ü'.code, 'ū'.code)
                ),
                // i — KEEP vowel accent popup
                KeyData(
                    primaryLabel = "i",
                    primaryCode = 'i'.code,
                    longPressLabel = "ì",
                    longPressCode = 'ì'.code,
                    longPressCodes = listOf('ì'.code, 'í'.code, 'î'.code, 'ï'.code, 'ī'.code)
                ),
                // o — KEEP vowel accent popup
                KeyData(
                    primaryLabel = "o",
                    primaryCode = 'o'.code,
                    longPressLabel = "ò",
                    longPressCode = 'ò'.code,
                    longPressCodes = listOf('ò'.code, 'ó'.code, 'ô'.code, 'õ'.code, 'ö'.code, 'ø'.code, 'œ'.code)
                ),
                KeyData("p", 'p'.code, longPressLabel = "}", longPressCode = '}'.code)
            )
        )

        val homeRow = KeyRowData(
            keys = listOf(
                // a — KEEP vowel accent popup
                KeyData(
                    primaryLabel = "a",
                    primaryCode = 'a'.code,
                    longPressLabel = "à",
                    longPressCode = 'à'.code,
                    longPressCodes = listOf('à'.code, 'á'.code, 'â'.code, 'ã'.code, 'ä'.code, 'å'.code, 'æ'.code)
                ),
                KeyData("s", 's'.code, longPressLabel = "#", longPressCode = '#'.code),
                KeyData("d", 'd'.code, longPressLabel = "&", longPressCode = '&'.code),
                KeyData("f", 'f'.code, longPressLabel = "*", longPressCode = '*'.code),
                KeyData("g", 'g'.code, longPressLabel = "-", longPressCode = '-'.code),
                KeyData("h", 'h'.code, longPressLabel = "+", longPressCode = '+'.code),
                KeyData("j", 'j'.code, longPressLabel = "=", longPressCode = '='.code),
                KeyData("k", 'k'.code, longPressLabel = "(", longPressCode = '('.code),
                KeyData("l", 'l'.code, longPressLabel = ")", longPressCode = ')'.code)
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
                KeyData("z", 'z'.code, longPressLabel = "_", longPressCode = '_'.code),
                KeyData("x", 'x'.code, longPressLabel = "\$", longPressCode = '$'.code),
                KeyData("c", 'c'.code, longPressLabel = "\"", longPressCode = '"'.code),
                KeyData("v", 'v'.code, longPressLabel = "'", longPressCode = '\''.code),
                KeyData("b", 'b'.code, longPressLabel = ":", longPressCode = ':'.code),
                KeyData("n", 'n'.code, longPressLabel = ";", longPressCode = ';'.code),
                KeyData("m", 'm'.code, longPressLabel = "/", longPressCode = '/'.code),
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
     * Builds the 4-row COMPACT_DEV layout (hacker shift-symbol long-press on every letter key).
     * Vowels get shift-symbols (NOT accents) — COMPACT_DEV philosophy is developer shortcuts.
     */
    private fun buildCompactDevLayout(): KeyboardLayoutData {
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

        val homeRow = KeyRowData(
            keys = listOf(
                KeyData("a", 'a'.code, longPressLabel = "~", longPressCode = '~'.code),
                KeyData("s", 's'.code, longPressLabel = "`", longPressCode = '`'.code),
                KeyData("d", 'd'.code, longPressLabel = "-", longPressCode = '-'.code),
                KeyData("f", 'f'.code, longPressLabel = "_", longPressCode = '_'.code),
                KeyData("g", 'g'.code, longPressLabel = "=", longPressCode = '='.code),
                KeyData("h", 'h'.code, longPressLabel = "+", longPressCode = '+'.code),
                KeyData("j", 'j'.code, longPressLabel = "{", longPressCode = '{'.code),
                KeyData("k", 'k'.code, longPressLabel = "}", longPressCode = '}'.code),
                KeyData("l", 'l'.code, longPressLabel = "|", longPressCode = '|'.code)
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
                KeyData("z", 'z'.code, longPressLabel = "[", longPressCode = '['.code),
                KeyData("x", 'x'.code, longPressLabel = "]", longPressCode = ']'.code),
                KeyData("c", 'c'.code, longPressLabel = "\\", longPressCode = '\\'.code),
                KeyData("v", 'v'.code, longPressLabel = ":", longPressCode = ':'.code),
                KeyData("b", 'b'.code, longPressLabel = ";", longPressCode = ';'.code),
                KeyData("n", 'n'.code, longPressLabel = "<", longPressCode = '<'.code),
                KeyData("m", 'm'.code, longPressLabel = ">", longPressCode = '>'.code),
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
            rows = listOf(qwertyRow, homeRow, zRow, buildSpaceRow())
        )
    }

    /**
     * Builds the home row for the FULL layout (A-L with symbol long-press assignments).
     * 'a' gets accented vowel longPressCodes; others retain their existing symbol long-press.
     */
    private fun buildFullHomeRow(): KeyRowData = KeyRowData(
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
     * Builds the space row: 123 emoji , Space . Enter
     * Shared across all layout modes.
     * The period key carries a multi-char long-press popup per Phase 5.2.
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
                longPressLabel = ",",
                longPressCode = ','.code,
                longPressCodes = listOf(','.code, ';'.code, ':'.code, '!'.code, '?'.code),
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
