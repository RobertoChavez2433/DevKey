package dev.devkey.keyboard.ui.keyboard

/**
 * Defines the symbols keyboard layout.
 *
 * Contains 5 rows of symbols, numbers, and special characters.
 * The bottom row includes an "ABC" button to switch back to the QWERTY layout.
 */
object SymbolsLayout {

    /** Custom keycode for the ABC button to switch back to alpha keyboard. */
    const val KEYCODE_ALPHA = -200

    val layout: KeyboardLayoutData by lazy { buildLayout() }

    private fun buildLayout(): KeyboardLayoutData {
        // Long-press data added per plan Phase 5.3 + spec §4.2.
        // Defaults to common SwiftKey-style popup content for symbol keys.
        // Retune against actual reference captures in Phase 6 if needed.

        // Row 1: Numbers 1-0
        val numberRow = KeyRowData(
            keys = listOf(
                KeyData("1", '1'.code,
                    longPressLabel = "\u00B9", longPressCode = '\u00B9'.code,
                    longPressCodes = listOf('\u00B9'.code),
                    type = KeyType.NUMBER),
                KeyData("2", '2'.code,
                    longPressLabel = "\u00B2", longPressCode = '\u00B2'.code,
                    longPressCodes = listOf('\u00B2'.code),
                    type = KeyType.NUMBER),
                KeyData("3", '3'.code,
                    longPressLabel = "\u00B3", longPressCode = '\u00B3'.code,
                    longPressCodes = listOf('\u00B3'.code),
                    type = KeyType.NUMBER),
                KeyData("4", '4'.code,
                    longPressLabel = "\u2074", longPressCode = '\u2074'.code,
                    longPressCodes = listOf('\u2074'.code),
                    type = KeyType.NUMBER),
                KeyData("5", '5'.code,
                    longPressLabel = "\u2075", longPressCode = '\u2075'.code,
                    longPressCodes = listOf('\u2075'.code),
                    type = KeyType.NUMBER),
                KeyData("6", '6'.code,
                    longPressLabel = "\u2076", longPressCode = '\u2076'.code,
                    longPressCodes = listOf('\u2076'.code),
                    type = KeyType.NUMBER),
                KeyData("7", '7'.code,
                    longPressLabel = "\u2077", longPressCode = '\u2077'.code,
                    longPressCodes = listOf('\u2077'.code),
                    type = KeyType.NUMBER),
                KeyData("8", '8'.code,
                    longPressLabel = "\u2078", longPressCode = '\u2078'.code,
                    longPressCodes = listOf('\u2078'.code),
                    type = KeyType.NUMBER),
                KeyData("9", '9'.code,
                    longPressLabel = "\u2079", longPressCode = '\u2079'.code,
                    longPressCodes = listOf('\u2079'.code),
                    type = KeyType.NUMBER),
                KeyData("0", '0'.code,
                    longPressLabel = "\u2070", longPressCode = '\u2070'.code,
                    longPressCodes = listOf('\u2070'.code),
                    type = KeyType.NUMBER)
            )
        )

        // Row 2: Common symbols
        val symbolRow1 = KeyRowData(
            keys = listOf(
                KeyData("@", '@'.code, type = KeyType.SPECIAL),
                KeyData("#", '#'.code, type = KeyType.SPECIAL),
                KeyData("$", '$'.code,
                    longPressLabel = "\u00A2", longPressCode = '\u00A2'.code,
                    longPressCodes = listOf('\u00A2'.code, '\u20AC'.code, '\u00A3'.code, '\u00A5'.code, '\u20B9'.code),
                    type = KeyType.SPECIAL),
                KeyData("_", '_'.code, type = KeyType.SPECIAL),
                KeyData("&", '&'.code, type = KeyType.SPECIAL),
                KeyData("-", '-'.code,
                    longPressLabel = "_", longPressCode = '_'.code,
                    longPressCodes = listOf('_'.code, '\u2014'.code, '\u2013'.code, '~'.code),
                    type = KeyType.SPECIAL),
                KeyData("+", '+'.code,
                    longPressLabel = "\u00B1", longPressCode = '\u00B1'.code,
                    longPressCodes = listOf('\u00B1'.code, '\u00D7'.code, '\u00F7'.code),
                    type = KeyType.SPECIAL),
                KeyData("(", '('.code,
                    longPressLabel = "[", longPressCode = '['.code,
                    longPressCodes = listOf('['.code, '{'.code, '<'.code),
                    type = KeyType.SPECIAL),
                KeyData(")", ')'.code,
                    longPressLabel = "]", longPressCode = ']'.code,
                    longPressCodes = listOf(']'.code, '}'.code, '>'.code),
                    type = KeyType.SPECIAL),
                KeyData("/", '/'.code,
                    longPressLabel = "\\", longPressCode = '\\'.code,
                    longPressCodes = listOf('\\'.code, '|'.code),
                    type = KeyType.SPECIAL)
            )
        )

        // Row 3: Punctuation
        val symbolRow2 = KeyRowData(
            keys = listOf(
                KeyData("*", '*'.code,
                    longPressLabel = "\u00D7", longPressCode = '\u00D7'.code,
                    longPressCodes = listOf('\u00D7'.code, '\u2022'.code, '\u00B0'.code),
                    type = KeyType.SPECIAL),
                KeyData("\"", '"'.code,
                    longPressLabel = "\u00AB", longPressCode = '\u00AB'.code,
                    longPressCodes = listOf('\u00AB'.code, '\u00BB'.code, '\u201E'.code, '\u201C'.code, '\u201D'.code),
                    type = KeyType.SPECIAL),
                KeyData("'", '\''.code,
                    longPressLabel = "\u2018", longPressCode = '\u2018'.code,
                    longPressCodes = listOf('\u2018'.code, '\u2019'.code, '\u201A'.code),
                    type = KeyType.SPECIAL),
                KeyData(":", ':'.code, type = KeyType.SPECIAL),
                KeyData(";", ';'.code, type = KeyType.SPECIAL),
                KeyData("!", '!'.code, longPressLabel = "\u00A1", longPressCode = '\u00A1'.code, type = KeyType.SPECIAL),
                KeyData("?", '?'.code, longPressLabel = "\u00BF", longPressCode = '\u00BF'.code, type = KeyType.SPECIAL),
                KeyData(",", ','.code, type = KeyType.SPECIAL)
            )
        )

        // Row 4: Extended symbols
        val symbolRow3 = KeyRowData(
            keys = listOf(
                KeyData("~", '~'.code, longPressLabel = "\u20AC", longPressCode = '\u20AC'.code, type = KeyType.SPECIAL),
                KeyData("`", '`'.code, longPressLabel = "\u00A3", longPressCode = '\u00A3'.code, type = KeyType.SPECIAL),
                KeyData("|", '|'.code, longPressLabel = "\u00A5", longPressCode = '\u00A5'.code, type = KeyType.SPECIAL),
                KeyData("\u2022", '\u2022'.code, longPressLabel = "\u00A2", longPressCode = '\u00A2'.code, type = KeyType.SPECIAL),
                KeyData("\u221A", '\u221A'.code, longPressLabel = "\u20A9", longPressCode = '\u20A9'.code, type = KeyType.SPECIAL),
                KeyData("\u03C0", '\u03C0'.code, type = KeyType.SPECIAL),
                KeyData("\u00F7", '\u00F7'.code, type = KeyType.SPECIAL),
                KeyData("\u00D7", '\u00D7'.code, type = KeyType.SPECIAL),
                KeyData("\u00B6", '\u00B6'.code, type = KeyType.SPECIAL),
                KeyData("\u2206", '\u2206'.code, type = KeyType.SPECIAL)
            )
        )

        // Row 5: Bottom row with ABC, emoji, comma, space, period, enter
        val bottomRow = KeyRowData(
            keys = listOf(
                KeyData(
                    primaryLabel = "ABC",
                    primaryCode = KEYCODE_ALPHA,
                    type = KeyType.ACTION,
                    weight = 1.0f
                ),
                KeyData(
                    primaryLabel = "\u263A",  // smiley face (emoji key)
                    primaryCode = KeyCodes.EMOJI,
                    type = KeyType.TOGGLE,
                    weight = 0.8f
                ),
                KeyData(",", ','.code, type = KeyType.SPECIAL, weight = 0.8f),
                KeyData(
                    primaryLabel = " ",
                    primaryCode = ' '.code,
                    type = KeyType.SPACEBAR,
                    weight = 4.0f
                ),
                KeyData(".", '.'.code,
                    longPressLabel = ",", longPressCode = ','.code,
                    longPressCodes = listOf(','.code, ';'.code, ':'.code, '!'.code, '?'.code, '\u2026'.code),
                    type = KeyType.SPECIAL, weight = 0.8f),
                KeyData(
                    primaryLabel = "Enter",
                    primaryCode = KeyCodes.ENTER,
                    type = KeyType.ACTION,
                    weight = 1.6f
                )
            )
        )

        return KeyboardLayoutData(
            rows = listOf(numberRow, symbolRow1, symbolRow2, symbolRow3, bottomRow)
        )
    }
}
