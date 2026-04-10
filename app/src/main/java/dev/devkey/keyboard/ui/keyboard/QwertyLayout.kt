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
    private val compactLayoutNoNumbers: KeyboardLayoutData by lazy {
        // Drop the first row (number row) from the full compact layout.
        KeyboardLayoutData(rows = compactLayout.rows.drop(1))
    }
    private val compactDevLayout: KeyboardLayoutData by lazy { buildCompactDevLayout() }
    private val compactDevLayoutWithNumbers: KeyboardLayoutData by lazy {
        // Prepend the compact layout's number row onto compact_dev.
        KeyboardLayoutData(rows = listOf(compactLayout.rows.first()) + compactDevLayout.rows)
    }

    /**
     * Returns the appropriate layout for the given [LayoutMode].
     *
     * @param includeNumberRow When true, COMPACT and COMPACT_DEV layouts include the
     *        `1 2 3 4 5 6 7 8 9 0` number row at the top. FULL always has its own
     *        number row regardless. Controlled by `SettingsRepository.KEY_SHOW_NUMBER_ROW`.
     */
    fun getLayout(mode: LayoutMode, includeNumberRow: Boolean = true): KeyboardLayoutData = when (mode) {
        LayoutMode.FULL -> fullLayout
        LayoutMode.COMPACT -> if (includeNumberRow) compactLayout else compactLayoutNoNumbers
        LayoutMode.COMPACT_DEV -> if (includeNumberRow) compactDevLayoutWithNumbers else compactDevLayout
    }

    /**
     * Builds the 6-row FULL layout.
     *
     * Row 0: Utility row (Ctrl Alt Tab [spacer] arrows) — FULL-only top row
     * Row 1: Number row (` 1 2 3 4 5 6 7 8 9 0)
     * Row 2: QWERTY row with symbol long-press
     * Row 3: Home row (A-L) with symbol long-press
     * Row 4: Z row (Shift + letters + smart backspace/esc)
     * Row 5: Space row (123 emoji , Space . Enter)
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
                    // SwiftKey parity — shift key uses a chevron-up glyph instead of "Shift" text.
                    primaryLabel = "\u21E7",  // ⇧ upwards white arrow
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
                KeyData(
                    primaryLabel = "\u21E7",  // ⇧ shift
                    primaryCode = Keyboard.KEYCODE_SHIFT,
                    type = KeyType.MODIFIER,
                    weight = 1.5f
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
            rows = listOf(utilityRow, numberRow, qwertyRow, buildFullHomeRow(), zRow, spaceRow)
        )
    }

    /**
     * Builds the COMPACT layout — now 5 rows matching SwiftKey reference:
     *   number row + qwerty + home + shift-letters + space row.
     *
     * Long-press data tuned from SwiftKey reference capture
     * (.claude/test-flows/swiftkey-reference/compact-dark-findings.md).
     * Hint labels now show the plain shift-symbol SwiftKey glyph
     * (~, <, >, {, @) directly — not accented vowels. Accented vowels remain
     * reachable via FULL / COMPACT_DEV or a future dedicated accent mode.
     */
    private fun buildCompactLayout(): KeyboardLayoutData {
        // SwiftKey parity — number row `1 2 3 4 5 6 7 8 9 0` at the very top.
        val numberRow = KeyRowData(
            keys = listOf(
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
                KeyData("q", 'q'.code, longPressLabel = "%", longPressCode = '%'.code),
                KeyData("w", 'w'.code, longPressLabel = "^", longPressCode = '^'.code),
                // e — SwiftKey reference shows `~`. Accent popup deferred to FULL/COMPACT_DEV.
                KeyData("e", 'e'.code, longPressLabel = "~", longPressCode = '~'.code),
                KeyData("r", 'r'.code, longPressLabel = "|", longPressCode = '|'.code),
                KeyData("t", 't'.code, longPressLabel = "[", longPressCode = '['.code),
                KeyData("y", 'y'.code, longPressLabel = "]", longPressCode = ']'.code),
                KeyData("u", 'u'.code, longPressLabel = "<", longPressCode = '<'.code),
                KeyData("i", 'i'.code, longPressLabel = ">", longPressCode = '>'.code),
                KeyData("o", 'o'.code, longPressLabel = "{", longPressCode = '{'.code),
                KeyData("p", 'p'.code, longPressLabel = "}", longPressCode = '}'.code)
            )
        )

        val homeRow = KeyRowData(
            keys = listOf(
                // a — SwiftKey reference shows `@`.
                KeyData("a", 'a'.code, longPressLabel = "@", longPressCode = '@'.code),
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
                    // SwiftKey parity — chevron-up glyph instead of "Shift" text.
                    primaryLabel = "\u21E7",  // ⇧
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
                    // SwiftKey parity — erase-to-left glyph instead of "Del" text.
                    primaryLabel = "\u232B",  // ⌫
                    primaryCode = Keyboard.KEYCODE_DELETE,
                    type = KeyType.ACTION,
                    weight = 1.5f,
                    isRepeatable = true
                )
            )
        )

        // SwiftKey parity — full 5-row layout. Callers that want the 4-row
        // "no-number-row" look use `getLayout(COMPACT, includeNumberRow = false)`
        // which is controlled by `SettingsRepository.KEY_SHOW_NUMBER_ROW`.
        return KeyboardLayoutData(
            rows = listOf(numberRow, qwertyRow, homeRow, zRow, buildSpaceRow())
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
                    // SwiftKey parity — chevron-up glyph instead of "Shift" text.
                    primaryLabel = "\u21E7",  // ⇧
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
     * Builds the space row: 123 ☺ 🎤 , Space .?! Enter
     * Shared across all layout modes.
     *
     * SwiftKey parity:
     *  - Bottom row: 123 ☺ , [Space] . ↵ — six keys.
     *  - Comma long-press hint is 🎤 (mic), fires KEYCODE_VOICE.
     *  - `.` key long-press shows `!?` hint per SwiftKey reference.
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
}
