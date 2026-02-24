package dev.devkey.keyboard.ui.keyboard

/**
 * Type of visual highlight for a Ctrl Mode shortcut key.
 */
enum class HighlightType {
    /** Standard blue/purple tint for most shortcuts. */
    NORMAL,
    /** Red tint for Copy/Paste operations. */
    RED
}

/**
 * Information about a keyboard shortcut displayed during Ctrl Mode.
 *
 * @param label The human-readable shortcut label (e.g., "Copy", "Sel All").
 * @param highlight The visual highlight type for the key background.
 */
data class ShortcutInfo(
    val label: String,
    val highlight: HighlightType = HighlightType.NORMAL
)

/**
 * Static mapping of letter keys to their Ctrl+key shortcut information.
 * Used to render the Ctrl Mode overlay on the keyboard.
 */
object CtrlShortcutMap {

    val shortcuts: Map<String, ShortcutInfo> = mapOf(
        "a" to ShortcutInfo("Sel All"),
        "c" to ShortcutInfo("Copy", HighlightType.RED),
        "v" to ShortcutInfo("Paste", HighlightType.RED),
        "x" to ShortcutInfo("Cut"),
        "z" to ShortcutInfo("Undo"),
        "s" to ShortcutInfo("Save"),
        "f" to ShortcutInfo("Find"),
        "n" to ShortcutInfo("New"),
        "o" to ShortcutInfo("Open"),
        "p" to ShortcutInfo("Print"),
        "q" to ShortcutInfo("Quit"),
        "w" to ShortcutInfo("Close"),
        "r" to ShortcutInfo("Redo"),
        "t" to ShortcutInfo("Tab"),
        "y" to ShortcutInfo("Redo"),
        "d" to ShortcutInfo("Dup"),
        "h" to ShortcutInfo("Replace"),
        "l" to ShortcutInfo("GoTo")
    )

    /**
     * Looks up the shortcut info for a given key letter.
     *
     * @param key The lowercase letter to look up.
     * @return The shortcut info, or null if no shortcut is defined.
     */
    fun getShortcut(key: String): ShortcutInfo? = shortcuts[key.lowercase()]
}
