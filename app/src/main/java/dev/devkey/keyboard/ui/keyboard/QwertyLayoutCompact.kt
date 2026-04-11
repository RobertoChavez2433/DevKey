package dev.devkey.keyboard.ui.keyboard

import dev.devkey.keyboard.keyboard.model.Keyboard

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
internal fun buildCompactLayout(): KeyboardLayoutData {
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
internal fun buildCompactDevLayout(): KeyboardLayoutData {
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
