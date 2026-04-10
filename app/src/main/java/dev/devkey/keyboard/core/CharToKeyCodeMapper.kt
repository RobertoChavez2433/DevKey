package dev.devkey.keyboard.core

import android.view.KeyEvent

/**
 * ASCII character to Android KeyEvent keycode lookup table.
 *
 * Each entry in [asciiToKeyCode] is a keycode value optionally OR'd with
 * flag bits ([KF_SHIFTABLE], [KF_UPPER], [KF_LETTER]) that describe how the
 * character should be sent as a key event with modifier state.
 */
internal object CharToKeyCodeMapper {

    const val KF_MASK = 0xffff
    const val KF_SHIFTABLE = 0x10000
    const val KF_UPPER = 0x20000
    const val KF_LETTER = 0x40000

    val asciiToKeyCode = IntArray(128).also { table ->
        // Include RETURN in this set even though it's not printable.
        table['\n'.code] = KeyEvent.KEYCODE_ENTER or KF_SHIFTABLE

        // Non-alphanumeric ASCII codes which have their own keys
        table[' '.code] = KeyEvent.KEYCODE_SPACE or KF_SHIFTABLE
        table['#'.code] = KeyEvent.KEYCODE_POUND
        table['\''.code] = KeyEvent.KEYCODE_APOSTROPHE
        table['*'.code] = KeyEvent.KEYCODE_STAR
        table['+'.code] = KeyEvent.KEYCODE_PLUS
        table[','.code] = KeyEvent.KEYCODE_COMMA
        table['-'.code] = KeyEvent.KEYCODE_MINUS
        table['.'.code] = KeyEvent.KEYCODE_PERIOD
        table['/'.code] = KeyEvent.KEYCODE_SLASH
        table[';'.code] = KeyEvent.KEYCODE_SEMICOLON
        table['='.code] = KeyEvent.KEYCODE_EQUALS
        table['@'.code] = KeyEvent.KEYCODE_AT
        table['['.code] = KeyEvent.KEYCODE_LEFT_BRACKET
        table['\\'.code] = KeyEvent.KEYCODE_BACKSLASH
        table[']'.code] = KeyEvent.KEYCODE_RIGHT_BRACKET
        table['`'.code] = KeyEvent.KEYCODE_GRAVE

        for (i in 0..25) {
            table['a'.code + i] = KeyEvent.KEYCODE_A + i or KF_LETTER
            table['A'.code + i] = KeyEvent.KEYCODE_A + i or KF_UPPER or KF_LETTER
        }

        for (i in 0..9) {
            table['0'.code + i] = KeyEvent.KEYCODE_0 + i
        }
    }
}
