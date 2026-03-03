package dev.devkey.keyboard.core

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.Keyboard
import dev.devkey.keyboard.ChordeTracker
import dev.devkey.keyboard.ui.keyboard.KeyCodes

/**
 * Encapsulates all key event synthesis logic extracted from LatinIME.
 *
 * Handles sending modified key events (Ctrl, Alt, Meta, Shift combinations),
 * VT escape sequences for terminal emulators (ConnectBot), and ASCII-to-KeyCode mapping.
 *
 * Dependencies on the IME service are provided via lambda/interface providers
 * so this class can be unit-tested without Android framework dependencies.
 */
class KeyEventSender(
    private val inputConnectionProvider: () -> InputConnection?,
    private val editorInfoProvider: () -> EditorInfo?,
    private val modCtrlProvider: () -> Boolean,
    private val modAltProvider: () -> Boolean,
    private val modMetaProvider: () -> Boolean,
    private val shiftKeyStateProvider: () -> ChordeTracker,
    private val ctrlKeyStateProvider: () -> ChordeTracker,
    private val altKeyStateProvider: () -> ChordeTracker,
    private val metaKeyStateProvider: () -> ChordeTracker,
    private val connectbotTabHackProvider: () -> Boolean,
    private val shiftModProvider: () -> Boolean,
    private val setModCtrl: (Boolean) -> Unit,
    private val setModAlt: (Boolean) -> Unit,
    private val setModMeta: (Boolean) -> Unit,
    private val resetShift: () -> Unit,
    private val getShiftState: () -> Int,
    private val commitTyped: (InputConnection?, Boolean) -> Unit,
    private val sendKeyCharFn: (Char) -> Unit,
    private val sendDownUpKeyEventsFn: (Int) -> Unit,
    private val settings: SettingsRepository,
    private val ctrlAToastAction: () -> Unit
) {

    companion object {
        // ASCII-to-KeyCode lookup table
        private const val KF_MASK = 0xffff
        private const val KF_SHIFTABLE = 0x10000
        private const val KF_UPPER = 0x20000
        private const val KF_LETTER = 0x40000

        internal val asciiToKeyCode = IntArray(128).also { table ->
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

        // VT escape sequence maps, thread-safe lazy initialization
        private val escSequenceMap: Map<Int, String> by lazy { buildEscSequences() }
        private val ctrlSequenceMap: Map<Int, Int> by lazy { buildCtrlSequences() }

        private fun buildEscSequences(): Map<Int, String> {
            val esc = HashMap<Int, String>()
            // VT escape sequences without leading Escape
            esc[-KeyCodes.KEYCODE_HOME] = "[1~"
            esc[-KeyCodes.KEYCODE_END] = "[4~"
            esc[-KeyCodes.KEYCODE_PAGE_UP] = "[5~"
            esc[-KeyCodes.KEYCODE_PAGE_DOWN] = "[6~"
            esc[-KeyCodes.KEYCODE_FKEY_F1] = "OP"
            esc[-KeyCodes.KEYCODE_FKEY_F2] = "OQ"
            esc[-KeyCodes.KEYCODE_FKEY_F3] = "OR"
            esc[-KeyCodes.KEYCODE_FKEY_F4] = "OS"
            esc[-KeyCodes.KEYCODE_FKEY_F5] = "[15~"
            esc[-KeyCodes.KEYCODE_FKEY_F6] = "[17~"
            esc[-KeyCodes.KEYCODE_FKEY_F7] = "[18~"
            esc[-KeyCodes.KEYCODE_FKEY_F8] = "[19~"
            esc[-KeyCodes.KEYCODE_FKEY_F9] = "[20~"
            esc[-KeyCodes.KEYCODE_FKEY_F10] = "[21~"
            esc[-KeyCodes.KEYCODE_FKEY_F11] = "[23~"
            esc[-KeyCodes.KEYCODE_FKEY_F12] = "[24~"
            esc[-KeyCodes.KEYCODE_FORWARD_DEL] = "[3~"
            esc[-KeyCodes.KEYCODE_INSERT] = "[2~"
            return esc
        }

        private fun buildCtrlSequences(): Map<Int, Int> {
            val ctrl = HashMap<Int, Int>()
            // Special ConnectBot hack: Ctrl-1 to Ctrl-0 for F1-F10.
            ctrl[-KeyCodes.KEYCODE_FKEY_F1] = KeyEvent.KEYCODE_1
            ctrl[-KeyCodes.KEYCODE_FKEY_F2] = KeyEvent.KEYCODE_2
            ctrl[-KeyCodes.KEYCODE_FKEY_F3] = KeyEvent.KEYCODE_3
            ctrl[-KeyCodes.KEYCODE_FKEY_F4] = KeyEvent.KEYCODE_4
            ctrl[-KeyCodes.KEYCODE_FKEY_F5] = KeyEvent.KEYCODE_5
            ctrl[-KeyCodes.KEYCODE_FKEY_F6] = KeyEvent.KEYCODE_6
            ctrl[-KeyCodes.KEYCODE_FKEY_F7] = KeyEvent.KEYCODE_7
            ctrl[-KeyCodes.KEYCODE_FKEY_F8] = KeyEvent.KEYCODE_8
            ctrl[-KeyCodes.KEYCODE_FKEY_F9] = KeyEvent.KEYCODE_9
            ctrl[-KeyCodes.KEYCODE_FKEY_F10] = KeyEvent.KEYCODE_0
            return ctrl
        }

        /**
         * Returns the ESC_SEQUENCES map.
         * Exposed for testing.
         */
        fun getEscSequences(): Map<Int, String> = escSequenceMap

        /**
         * Returns the CTRL_SEQUENCES map.
         * Exposed for testing.
         */
        fun getCtrlSequences(): Map<Int, Int> = ctrlSequenceMap
    }

    /**
     * Checks whether the current editor is a ConnectBot-like terminal emulator.
     */
    fun isConnectbot(): Boolean {
        val ei = editorInfoProvider() ?: return false
        val pkg = ei.packageName ?: return false
        return (pkg.equals("org.connectbot", ignoreCase = true)
                || pkg.equals("org.woltage.irssiconnectbot", ignoreCase = true)
                || pkg.equals("com.pslib.connectbot", ignoreCase = true)
                || pkg.equals("sk.vx.connectbot", ignoreCase = true))
                && ei.inputType == 0
    }

    /**
     * Computes the KeyEvent meta state flags from the current modifier state.
     */
    fun getMetaState(shifted: Boolean): Int {
        var meta = 0
        if (shifted) meta = meta or (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
        if (modCtrlProvider()) meta = meta or (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
        if (modAltProvider()) meta = meta or (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
        if (modMetaProvider()) meta = meta or (KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON)
        return meta
    }

    /**
     * Sends a key-down event to the given InputConnection.
     */
    fun sendKeyDown(ic: InputConnection?, key: Int, meta: Int) {
        val now = System.currentTimeMillis()
        ic?.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0, meta))
    }

    /**
     * Sends a key-up event to the given InputConnection.
     */
    fun sendKeyUp(ic: InputConnection?, key: Int, meta: Int) {
        val now = System.currentTimeMillis()
        ic?.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0, meta))
    }

    /**
     * Sends a modified key down+up sequence, wrapping with modifier key events.
     *
     * The [InputConnection] is fetched once here and passed through to
     * [sendModifierKeysDown] / [sendModifierKeysUp] to avoid redundant provider calls.
     */
    fun sendModifiedKeyDownUp(key: Int, shifted: Boolean) {
        val ic = inputConnectionProvider()
        val meta = getMetaState(shifted)
        sendModifierKeysDown(shifted, ic)
        sendKeyDown(ic, key, meta)
        sendKeyUp(ic, key, meta)
        sendModifierKeysUp(shifted, ic)
    }

    /**
     * Sends a modified key down+up sequence, using the current shift state.
     */
    fun sendModifiedKeyDownUp(key: Int) {
        sendModifiedKeyDownUp(key, shiftModProvider())
    }

    private fun delayChordingCtrlModifier(): Boolean = settings.chordingCtrlKey == 0
    private fun delayChordingAltModifier(): Boolean = settings.chordingAltKey == 0
    private fun delayChordingMetaModifier(): Boolean = settings.chordingMetaKey == 0

    private fun sendShiftKey(ic: InputConnection?, isDown: Boolean) {
        val key = KeyEvent.KEYCODE_SHIFT_LEFT
        val meta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (isDown) {
            sendKeyDown(ic, key, meta)
        } else {
            sendKeyUp(ic, key, meta)
        }
    }

    fun sendCtrlKey(ic: InputConnection?, isDown: Boolean, chording: Boolean) {
        if (chording && delayChordingCtrlModifier()) return
        var key = settings.chordingCtrlKey
        if (key == 0) key = KeyEvent.KEYCODE_CTRL_LEFT
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (isDown) {
            sendKeyDown(ic, key, meta)
        } else {
            sendKeyUp(ic, key, meta)
        }
    }

    fun sendAltKey(ic: InputConnection?, isDown: Boolean, chording: Boolean) {
        if (chording && delayChordingAltModifier()) return
        var key = settings.chordingAltKey
        if (key == 0) key = KeyEvent.KEYCODE_ALT_LEFT
        val meta = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (isDown) {
            sendKeyDown(ic, key, meta)
        } else {
            sendKeyUp(ic, key, meta)
        }
    }

    fun sendMetaKey(ic: InputConnection?, isDown: Boolean, chording: Boolean) {
        if (chording && delayChordingMetaModifier()) return
        var key = settings.chordingMetaKey
        if (key == 0) key = KeyEvent.KEYCODE_META_LEFT
        val meta = KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        if (isDown) {
            sendKeyDown(ic, key, meta)
        } else {
            sendKeyUp(ic, key, meta)
        }
    }

    /**
     * Sends modifier key-down events for the active modifiers.
     *
     * Accepts an optional pre-fetched [ic] to avoid redundant [inputConnectionProvider] calls
     * when invoked from [sendModifiedKeyDownUp].
     */
    fun sendModifierKeysDown(shifted: Boolean, ic: InputConnection? = inputConnectionProvider()) {
        if (shifted) {
            sendShiftKey(ic, true)
        }
        if (modCtrlProvider() && (!ctrlKeyStateProvider().isChording() || delayChordingCtrlModifier())) {
            sendCtrlKey(ic, true, false)
        }
        if (modAltProvider() && (!altKeyStateProvider().isChording() || delayChordingAltModifier())) {
            sendAltKey(ic, true, false)
        }
        if (modMetaProvider() && (!metaKeyStateProvider().isChording() || delayChordingMetaModifier())) {
            sendMetaKey(ic, true, false)
        }
    }

    /**
     * Handles modifier key-up events and resets modifier state as needed.
     *
     * Accepts an optional pre-fetched [ic] to avoid redundant [inputConnectionProvider] calls
     * when invoked from [sendModifiedKeyDownUp].
     */
    fun handleModifierKeysUp(shifted: Boolean, sendKey: Boolean, ic: InputConnection? = inputConnectionProvider()) {
        if (modMetaProvider() && (!metaKeyStateProvider().isChording() || delayChordingMetaModifier())) {
            if (sendKey) sendMetaKey(ic, false, false)
            if (!metaKeyStateProvider().isChording()) setModMeta(false)
        }
        if (modAltProvider() && (!altKeyStateProvider().isChording() || delayChordingAltModifier())) {
            if (sendKey) sendAltKey(ic, false, false)
            if (!altKeyStateProvider().isChording()) setModAlt(false)
        }
        if (modCtrlProvider() && (!ctrlKeyStateProvider().isChording() || delayChordingCtrlModifier())) {
            if (sendKey) sendCtrlKey(ic, false, false)
            if (!ctrlKeyStateProvider().isChording()) setModCtrl(false)
        }
        if (shifted) {
            if (sendKey) sendShiftKey(ic, false)
            val shiftState = getShiftState()
            if (!(shiftKeyStateProvider().isChording() || shiftState == Keyboard.SHIFT_LOCKED)) {
                resetShift()
            }
        }
    }

    /**
     * Sends modifier key-up events for the active modifiers.
     *
     * Accepts an optional pre-fetched [ic] to avoid redundant [inputConnectionProvider] calls
     * when invoked from [sendModifiedKeyDownUp].
     */
    fun sendModifierKeysUp(shifted: Boolean, ic: InputConnection? = inputConnectionProvider()) {
        handleModifierKeysUp(shifted, true, ic)
    }

    /**
     * Sends a special key (function key, arrow, etc.), using VT escape sequences
     * for ConnectBot terminal emulators when appropriate.
     */
    fun sendSpecialKey(code: Int) {
        if (!isConnectbot()) {
            commitTyped(inputConnectionProvider(), true)
            sendModifiedKeyDownUp(code)
            return
        }

        val ic = inputConnectionProvider()
        var ctrlseq: Int? = null
        if (connectbotTabHackProvider()) {
            ctrlseq = ctrlSequenceMap[code]
        }
        val seq = escSequenceMap[code]

        if (ctrlseq != null) {
            if (modAltProvider()) {
                // send ESC prefix for "Alt"
                ic?.commitText(27.toChar().toString(), 1)
            }
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, ctrlseq))
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, ctrlseq))
        } else if (seq != null) {
            if (modAltProvider()) {
                // send ESC prefix for "Alt"
                ic?.commitText(27.toChar().toString(), 1)
            }
            // send ESC prefix of escape sequence
            ic?.commitText(27.toChar().toString(), 1)
            ic?.commitText(seq, 1)
        } else {
            // send key code, let connectbot handle it
            sendDownUpKeyEventsFn(code)
        }
        handleModifierKeysUp(false, false)
    }

    /**
     * Sends a Tab key event, using the ConnectBot tab hack when appropriate.
     */
    fun sendTab() {
        val ic = inputConnectionProvider()
        val tabHack = isConnectbot() && connectbotTabHackProvider()

        if (tabHack) {
            if (modAltProvider()) {
                // send ESC prefix
                ic?.commitText(27.toChar().toString(), 1)
            }
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_I))
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_I))
        } else {
            sendModifiedKeyDownUp(KeyEvent.KEYCODE_TAB)
        }
    }

    /**
     * Sends an Escape key event, using character 27 for ConnectBot terminals.
     */
    fun sendEscape() {
        if (isConnectbot()) {
            sendKeyCharFn(27.toChar())
        } else {
            sendModifiedKeyDownUp(KeyEvent.KEYCODE_ESCAPE)
        }
    }

    /**
     * Sends a character as a key event, applying modifier state (Ctrl, Alt, Meta, Shift).
     *
     * For ConnectBot terminals, sends escape-prefixed control codes.
     * For regular apps, sends proper KeyEvent sequences with modifier meta state.
     *
     * The Ctrl-A special case with Toast notification is handled via the
     * [ctrlAToastAction] callback to keep Android UI dependencies out of this class.
     */
    fun sendModifiableKeyChar(ch: Char) {
        val modShift = shiftModProvider()
        if ((modShift || modCtrlProvider() || modAltProvider() || modMetaProvider()) && ch.code > 0 && ch.code < 127) {
            val ic = inputConnectionProvider()
            if (isConnectbot()) {
                if (modAltProvider()) {
                    // send ESC prefix
                    ic?.commitText(27.toChar().toString(), 1)
                }
                if (modCtrlProvider()) {
                    val code = ch.code and 31
                    if (code == 9) {
                        sendTab()
                    } else {
                        ic?.commitText(code.toChar().toString(), 1)
                    }
                } else {
                    ic?.commitText(ch.toString(), 1)
                }
                handleModifierKeysUp(false, false)
                return
            }

            // Non-ConnectBot

            // Restrict Shift modifier to ENTER and SPACE, supporting Shift-Enter etc.
            // Note that most special keys such as DEL or cursor keys aren't handled
            // by this charcode-based method.

            val combinedCode = asciiToKeyCode[ch.code]
            if (combinedCode > 0) {
                val code = combinedCode and KF_MASK
                val shiftable = (combinedCode and KF_SHIFTABLE) > 0
                val upper = (combinedCode and KF_UPPER) > 0
                val letter = (combinedCode and KF_LETTER) > 0
                val shifted = modShift && (upper || shiftable)
                if (letter && !modCtrlProvider() && !modAltProvider() && !modMetaProvider()) {
                    // Try workaround for issue 179 where letters don't get upcased
                    ic?.commitText(ch.toString(), 1)
                    handleModifierKeysUp(false, false)
                } else if ((ch == 'a' || ch == 'A') && modCtrlProvider()) {
                    // Special case for Ctrl-A to work around accidental select-all-then-replace.
                    if (settings.ctrlAOverride == 0) {
                        // Ignore Ctrl-A, treat Ctrl-Alt-A as Ctrl-A.
                        if (modAltProvider()) {
                            val isChordingAlt = altKeyStateProvider().isChording()
                            setModAlt(false)
                            sendModifiedKeyDownUp(code, shifted)
                            if (isChordingAlt) setModAlt(true)
                        } else {
                            ctrlAToastAction()
                            // Clear the Ctrl modifier (and others)
                            sendModifierKeysDown(shifted)
                            sendModifierKeysUp(shifted)
                            return // ignore the key
                        }
                    } else if (settings.ctrlAOverride == 1) {
                        // Clear the Ctrl modifier (and others)
                        sendModifierKeysDown(shifted)
                        sendModifierKeysUp(shifted)
                        return // ignore the key
                    } else {
                        // Standard Ctrl-A behavior.
                        sendModifiedKeyDownUp(code, shifted)
                    }
                } else {
                    sendModifiedKeyDownUp(code, shifted)
                }
                return
            }
        }

        if (ch in '0'..'9') {
            // Clear any lingering meta states so digits are never shifted accidentally.
            // This prevents apps that track meta state internally from misinterpreting digits
            // typed after a modifier key was released.
            val ic = inputConnectionProvider()
            ic?.clearMetaKeyStates(
                KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON or KeyEvent.META_SYM_ON
            )
        }

        // Default handling for anything else, including unmodified ENTER and SPACE.
        sendKeyCharFn(ch)
    }
}
