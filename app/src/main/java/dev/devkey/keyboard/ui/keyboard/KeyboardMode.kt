package dev.devkey.keyboard.ui.keyboard

/**
 * Sealed class representing the current keyboard mode.
 *
 * Determines which UI surface is shown in the dynamic middle area
 * (suggestion bar replacement) and which keyboard layout is active.
 */
sealed class KeyboardMode {
    /** Default mode — suggestion bar visible, QWERTY layout. */
    object Normal : KeyboardMode()

    /** Macro chip strip replaces the suggestion bar. */
    object MacroChips : KeyboardMode()

    /** Macro grid panel shown below the suggestion bar. */
    object MacroGrid : KeyboardMode()

    /** Macro recording bar replaces toolbar + suggestion bar. */
    object MacroRecording : KeyboardMode()

    /** Clipboard history panel shown below the suggestion bar. */
    object Clipboard : KeyboardMode()

    /** Symbols keyboard layout replaces QWERTY. */
    object Symbols : KeyboardMode()

    /** Voice input mode — keyboard replaced with voice recording UI. */
    object Voice : KeyboardMode()
}
