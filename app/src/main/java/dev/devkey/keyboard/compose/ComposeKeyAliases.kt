package dev.devkey.keyboard.compose

import dev.devkey.keyboard.ui.keyboard.KeyCodes

/**
 * Key code aliases and display names used by [ComposeSequence] for
 * compose key handling and sequence formatting.
 */
object ComposeKeyAliases {
    val UP: Char          = KeyCodes.KEYCODE_DPAD_UP.toChar()
    val DOWN: Char        = KeyCodes.KEYCODE_DPAD_DOWN.toChar()
    val LEFT: Char        = KeyCodes.KEYCODE_DPAD_LEFT.toChar()
    val RIGHT: Char       = KeyCodes.KEYCODE_DPAD_RIGHT.toChar()
    val COMPOSE: Char     = KeyCodes.KEYCODE_DPAD_CENTER.toChar()
    val PAGE_UP: Char     = KeyCodes.KEYCODE_PAGE_UP.toChar()
    val PAGE_DOWN: Char   = KeyCodes.KEYCODE_PAGE_DOWN.toChar()
    val ESCAPE: Char      = KeyCodes.ESCAPE.toChar()
    val DELETE: Char       = KeyCodes.KEYCODE_FORWARD_DEL.toChar()
    val CAPS_LOCK: Char   = KeyCodes.KEYCODE_CAPS_LOCK.toChar()
    val SCROLL_LOCK: Char = KeyCodes.KEYCODE_SCROLL_LOCK.toChar()
    val SYSRQ: Char       = KeyCodes.KEYCODE_SYSRQ.toChar()
    val BREAK: Char       = KeyCodes.KEYCODE_BREAK.toChar()
    val HOME: Char        = KeyCodes.KEYCODE_HOME.toChar()
    val END: Char         = KeyCodes.KEYCODE_END.toChar()
    val INSERT: Char      = KeyCodes.KEYCODE_INSERT.toChar()
    val F1: Char          = KeyCodes.KEYCODE_FKEY_F1.toChar()
    val F2: Char          = KeyCodes.KEYCODE_FKEY_F2.toChar()
    val F3: Char          = KeyCodes.KEYCODE_FKEY_F3.toChar()
    val F4: Char          = KeyCodes.KEYCODE_FKEY_F4.toChar()
    val F5: Char          = KeyCodes.KEYCODE_FKEY_F5.toChar()
    val F6: Char          = KeyCodes.KEYCODE_FKEY_F6.toChar()
    val F7: Char          = KeyCodes.KEYCODE_FKEY_F7.toChar()
    val F8: Char          = KeyCodes.KEYCODE_FKEY_F8.toChar()
    val F9: Char          = KeyCodes.KEYCODE_FKEY_F9.toChar()
    val F10: Char         = KeyCodes.KEYCODE_FKEY_F10.toChar()
    val F11: Char         = KeyCodes.KEYCODE_FKEY_F11.toChar()
    val F12: Char         = KeyCodes.KEYCODE_FKEY_F12.toChar()
    val NUM_LOCK: Char    = KeyCodes.KEYCODE_NUM_LOCK.toChar()

    val keyNames: Map<Char, String> = mapOf(
        '"' to "quot",
        UP to "\u2191",
        DOWN to "\u2193",
        LEFT to "\u2190",
        RIGHT to "\u2192",
        COMPOSE to "\u25EF",
        PAGE_UP to "PgUp",
        PAGE_DOWN to "PgDn",
        ESCAPE to "Esc",
        DELETE to "Del",
        CAPS_LOCK to "Caps",
        SCROLL_LOCK to "Scroll",
        SYSRQ to "SysRq",
        BREAK to "Break",
        HOME to "Home",
        END to "End",
        INSERT to "Insert",
        F1 to "F1", F2 to "F2", F3 to "F3", F4 to "F4",
        F5 to "F5", F6 to "F6", F7 to "F7", F8 to "F8",
        F9 to "F9", F10 to "F10", F11 to "F11", F12 to "F12",
        NUM_LOCK to "Num"
    )
}
