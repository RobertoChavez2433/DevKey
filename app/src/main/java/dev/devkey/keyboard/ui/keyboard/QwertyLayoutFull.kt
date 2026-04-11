package dev.devkey.keyboard.ui.keyboard

import dev.devkey.keyboard.keyboard.model.Keyboard

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
internal fun buildFullLayout(): KeyboardLayoutData {
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

