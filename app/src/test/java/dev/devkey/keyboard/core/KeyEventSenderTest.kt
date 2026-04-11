package dev.devkey.keyboard.core

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.content.Context
import dev.devkey.keyboard.data.repository.SettingsRepository
import org.robolectric.RuntimeEnvironment
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.core.modifier.ChordeTracker
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for KeyEventSender.
 *
 * Tests cover:
 * - sendModifiableKeyChar with and without active modifiers
 * - sendModifiedKeyDownUp key event sequences
 * - getMetaState for all modifier combinations
 * - ASCII-to-KeyCode lookup table correctness
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class KeyEventSenderTest {

    private lateinit var settings: SettingsRepository
    private lateinit var shiftKeyState: ChordeTracker
    private lateinit var ctrlKeyState: ChordeTracker
    private lateinit var altKeyState: ChordeTracker
    private lateinit var metaKeyState: ChordeTracker

    // Mutable state for testing
    private var modCtrl = false
    private var modAlt = false
    private var modMeta = false
    private var shiftMod = false
    private var shiftState = Keyboard.SHIFT_OFF
    private var lastEditorInfo: EditorInfo? = null

    // Tracking for side effects
    private var commitTypedCalled = false
    private var lastSendKeyChar: Char? = null
    private var lastSendDownUpKeyEvents: Int? = null
    private var ctrlAToastShown = false
    private var lastSetModCtrl: Boolean? = null
    private var lastSetModAlt: Boolean? = null
    private var lastSetModMeta: Boolean? = null
    private var resetShiftCalled = false

    // Captured key events
    private val sentKeyEvents = mutableListOf<CapturedKeyEvent>()

    data class CapturedKeyEvent(
        val action: Int,
        val keyCode: Int,
        val metaState: Int
    )

    private lateinit var mockInputConnection: InputConnection
    private lateinit var sender: KeyEventSender

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        settings = SettingsRepository(prefs)
        shiftKeyState = ChordeTracker()
        ctrlKeyState = ChordeTracker()
        altKeyState = ChordeTracker()
        metaKeyState = ChordeTracker()

        modCtrl = false
        modAlt = false
        modMeta = false
        shiftMod = false
        shiftState = Keyboard.SHIFT_OFF
        lastEditorInfo = null

        commitTypedCalled = false
        lastSendKeyChar = null
        lastSendDownUpKeyEvents = null
        ctrlAToastShown = false
        lastSetModCtrl = null
        lastSetModAlt = null
        lastSetModMeta = null
        resetShiftCalled = false
        sentKeyEvents.clear()

        mockInputConnection = createMockInputConnection()

        sender = createSender(mockInputConnection)
    }

    private fun createMockInputConnection(): InputConnection {
        return object : InputConnection {
            override fun getTextBeforeCursor(n: Int, flags: Int) = ""
            override fun getTextAfterCursor(n: Int, flags: Int) = ""
            override fun getSelectedText(flags: Int) = null
            override fun getCursorCapsMode(reqModes: Int) = 0
            override fun getExtractedText(request: android.view.inputmethod.ExtractedTextRequest?, flags: Int) = null
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) = true
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int) = true
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int) = true
            override fun setComposingRegion(start: Int, end: Int) = true
            override fun finishComposingText() = true
            override fun commitText(text: CharSequence?, newCursorPosition: Int) = true
            override fun commitCompletion(text: android.view.inputmethod.CompletionInfo?) = true
            override fun commitCorrection(correctionInfo: android.view.inputmethod.CorrectionInfo?) = true
            override fun setSelection(start: Int, end: Int) = true
            override fun performEditorAction(editorAction: Int) = true
            override fun performContextMenuAction(id: Int) = true
            override fun beginBatchEdit() = true
            override fun endBatchEdit() = true
            override fun sendKeyEvent(event: KeyEvent?): Boolean {
                if (event != null) {
                    sentKeyEvents.add(CapturedKeyEvent(event.action, event.keyCode, event.metaState))
                }
                return true
            }
            override fun clearMetaKeyStates(states: Int) = true
            override fun reportFullscreenMode(enabled: Boolean) = true
            override fun performPrivateCommand(action: String?, data: android.os.Bundle?) = true
            override fun requestCursorUpdates(cursorUpdateMode: Int) = true
            override fun getHandler() = null
            override fun closeConnection() {}
            override fun commitContent(inputContentInfo: android.view.inputmethod.InputContentInfo, flags: Int, opts: android.os.Bundle?) = true
        }
    }

    private fun createSender(
        ic: InputConnection? = mockInputConnection,
        editorInfo: EditorInfo? = lastEditorInfo
    ): KeyEventSender {
        return KeyEventSender(
            inputConnectionProvider = { ic },
            editorInfoProvider = { editorInfo },
            modCtrlProvider = { modCtrl },
            modAltProvider = { modAlt },
            modMetaProvider = { modMeta },
            shiftKeyStateProvider = { shiftKeyState },
            ctrlKeyStateProvider = { ctrlKeyState },
            altKeyStateProvider = { altKeyState },
            metaKeyStateProvider = { metaKeyState },
            shiftModProvider = { shiftMod },
            setModCtrl = { v -> lastSetModCtrl = v; modCtrl = v },
            setModAlt = { v -> lastSetModAlt = v; modAlt = v },
            setModMeta = { v -> lastSetModMeta = v; modMeta = v },
            resetShift = { resetShiftCalled = true },
            getShiftState = { shiftState },
            commitTyped = { _, _ -> commitTypedCalled = true },
            sendKeyCharFn = { ch -> lastSendKeyChar = ch },
            sendDownUpKeyEventsFn = { code -> lastSendDownUpKeyEvents = code },
            settings = settings,
            ctrlAToastAction = { ctrlAToastShown = true }
        )
    }

    // --- getMetaState tests ---

    @Test
    fun `getMetaState returns zero when no modifiers active`() {
        assertEquals(0, sender.getMetaState(false))
    }

    @Test
    fun `getMetaState includes SHIFT flags when shifted`() {
        val meta = sender.getMetaState(true)
        assertTrue(meta and KeyEvent.META_SHIFT_ON != 0)
        assertTrue(meta and KeyEvent.META_SHIFT_LEFT_ON != 0)
    }

    @Test
    fun `getMetaState includes CTRL flags when Ctrl active`() {
        modCtrl = true
        val meta = sender.getMetaState(false)
        assertTrue(meta and KeyEvent.META_CTRL_ON != 0)
        assertTrue(meta and KeyEvent.META_CTRL_LEFT_ON != 0)
    }

    @Test
    fun `getMetaState includes ALT flags when Alt active`() {
        modAlt = true
        val meta = sender.getMetaState(false)
        assertTrue(meta and KeyEvent.META_ALT_ON != 0)
        assertTrue(meta and KeyEvent.META_ALT_LEFT_ON != 0)
    }

    @Test
    fun `getMetaState includes META flags when Meta active`() {
        modMeta = true
        val meta = sender.getMetaState(false)
        assertTrue(meta and KeyEvent.META_META_ON != 0)
        assertTrue(meta and KeyEvent.META_META_LEFT_ON != 0)
    }

    @Test
    fun `getMetaState combines all modifiers correctly`() {
        modCtrl = true
        modAlt = true
        modMeta = true
        val meta = sender.getMetaState(true)
        assertTrue(meta and KeyEvent.META_SHIFT_ON != 0)
        assertTrue(meta and KeyEvent.META_CTRL_ON != 0)
        assertTrue(meta and KeyEvent.META_ALT_ON != 0)
        assertTrue(meta and KeyEvent.META_META_ON != 0)
    }

    @Test
    fun `getMetaState does not include SHIFT when not shifted`() {
        val meta = sender.getMetaState(false)
        assertTrue(meta and KeyEvent.META_SHIFT_ON == 0)
    }

    // --- sendKeyDown and sendKeyUp tests ---

    @Test
    fun `sendKeyDown sends ACTION_DOWN KeyEvent`() {
        sender.sendKeyDown(mockInputConnection, KeyEvent.KEYCODE_A, 0)
        assertEquals(1, sentKeyEvents.size)
        assertEquals(KeyEvent.ACTION_DOWN, sentKeyEvents[0].action)
        assertEquals(KeyEvent.KEYCODE_A, sentKeyEvents[0].keyCode)
        assertEquals(0, sentKeyEvents[0].metaState)
    }

    @Test
    fun `sendKeyUp sends ACTION_UP KeyEvent`() {
        sender.sendKeyUp(mockInputConnection, KeyEvent.KEYCODE_A, 0)
        assertEquals(1, sentKeyEvents.size)
        assertEquals(KeyEvent.ACTION_UP, sentKeyEvents[0].action)
        assertEquals(KeyEvent.KEYCODE_A, sentKeyEvents[0].keyCode)
    }

    @Test
    fun `sendKeyDown with meta state preserves meta flags`() {
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        sender.sendKeyDown(mockInputConnection, KeyEvent.KEYCODE_C, meta)
        assertEquals(meta, sentKeyEvents[0].metaState)
    }

    @Test
    fun `sendKeyDown with null InputConnection does not crash`() {
        sender.sendKeyDown(null, KeyEvent.KEYCODE_A, 0)
        assertEquals(0, sentKeyEvents.size)
    }

    @Test
    fun `sendKeyUp with null InputConnection does not crash`() {
        sender.sendKeyUp(null, KeyEvent.KEYCODE_A, 0)
        assertEquals(0, sentKeyEvents.size)
    }

    // --- sendModifiedKeyDownUp tests ---

    @Test
    fun `sendModifiedKeyDownUp sends DOWN then UP`() {
        sender.sendModifiedKeyDownUp(KeyEvent.KEYCODE_V, false)
        // Should have at least DOWN + UP for the key itself
        val downs = sentKeyEvents.filter { it.action == KeyEvent.ACTION_DOWN && it.keyCode == KeyEvent.KEYCODE_V }
        val ups = sentKeyEvents.filter { it.action == KeyEvent.ACTION_UP && it.keyCode == KeyEvent.KEYCODE_V }
        assertEquals(1, downs.size)
        assertEquals(1, ups.size)
    }

    @Test
    fun `sendModifiedKeyDownUp with Ctrl sends correct meta state`() {
        modCtrl = true
        sender.sendModifiedKeyDownUp(KeyEvent.KEYCODE_C, false)
        val keyDown = sentKeyEvents.first { it.keyCode == KeyEvent.KEYCODE_C && it.action == KeyEvent.ACTION_DOWN }
        assertTrue(keyDown.metaState and KeyEvent.META_CTRL_ON != 0)
    }

    @Test
    fun `sendModifiedKeyDownUp with shift sends shift key events`() {
        sender.sendModifiedKeyDownUp(KeyEvent.KEYCODE_A, true)
        // Should include SHIFT_LEFT down before the main key and up after
        val shiftDown = sentKeyEvents.indexOfFirst { it.keyCode == KeyEvent.KEYCODE_SHIFT_LEFT && it.action == KeyEvent.ACTION_DOWN }
        val keyDown = sentKeyEvents.indexOfFirst { it.keyCode == KeyEvent.KEYCODE_A && it.action == KeyEvent.ACTION_DOWN }
        val keyUp = sentKeyEvents.indexOfFirst { it.keyCode == KeyEvent.KEYCODE_A && it.action == KeyEvent.ACTION_UP }
        val shiftUp = sentKeyEvents.indexOfFirst { it.keyCode == KeyEvent.KEYCODE_SHIFT_LEFT && it.action == KeyEvent.ACTION_UP }
        assertTrue("SHIFT_LEFT DOWN should be present", shiftDown >= 0)
        assertTrue("Key A DOWN should come after SHIFT DOWN", keyDown > shiftDown)
        assertTrue("Key A UP should come after Key A DOWN", keyUp > keyDown)
        assertTrue("SHIFT_LEFT UP should come after Key A UP", shiftUp > keyUp)
    }

    // --- sendModifiableKeyChar tests ---

    @Test
    fun `sendModifiableKeyChar with no modifiers sends via sendKeyChar`() {
        sender.sendModifiableKeyChar('x')
        assertEquals('x', lastSendKeyChar)
    }

    @Test
    fun `sendModifiableKeyChar with Ctrl sends key event instead of char`() {
        modCtrl = true
        sentKeyEvents.clear()
        sender.sendModifiableKeyChar('c')
        // Should have sent key events, not sendKeyChar
        assertTrue("Key events should be generated", sentKeyEvents.isNotEmpty())
    }

    @Test
    fun `sendModifiableKeyChar with Ctrl for letter sends modified key down-up`() {
        modCtrl = true
        sender.sendModifiableKeyChar('v')
        // For a letter with Ctrl active, it should send the keycode via sendModifiedKeyDownUp
        val vDown = sentKeyEvents.any { it.keyCode == KeyEvent.KEYCODE_V && it.action == KeyEvent.ACTION_DOWN }
        assertTrue("Should send KEYCODE_V DOWN event", vDown)
    }

    @Test
    fun `sendModifiableKeyChar outside ASCII range uses sendKeyChar`() {
        // Character 200 is outside the 0-127 ASCII range
        val ch = 200.toChar()
        sender.sendModifiableKeyChar(ch)
        assertEquals(ch, lastSendKeyChar)
    }

    // --- Ctrl-A special case tests ---

    @Test
    fun `sendModifiableKeyChar Ctrl-A with override 0 shows toast when not Alt`() {
        modCtrl = true
        settings.ctrlAOverride = 0
        sender.sendModifiableKeyChar('a')
        assertTrue("Ctrl-A override should show toast", ctrlAToastShown)
    }

    @Test
    fun `sendModifiableKeyChar Ctrl-A with override 1 ignores key silently`() {
        modCtrl = true
        settings.ctrlAOverride = 1
        sender.sendModifiableKeyChar('a')
        assertFalse("Should NOT show toast with override 1", ctrlAToastShown)
    }

    @Test
    fun `sendModifiableKeyChar Ctrl-A with override 2 sends standard Ctrl-A`() {
        modCtrl = true
        settings.ctrlAOverride = 2
        sender.sendModifiableKeyChar('a')
        val aDown = sentKeyEvents.any { it.keyCode == KeyEvent.KEYCODE_A && it.action == KeyEvent.ACTION_DOWN }
        assertTrue("Should send KEYCODE_A with override 2", aDown)
    }

    @Test
    fun `sendModifiableKeyChar Ctrl-Alt-A with override 0 sends Ctrl-A`() {
        modCtrl = true
        modAlt = true
        settings.ctrlAOverride = 0
        sender.sendModifiableKeyChar('a')
        assertFalse("Should NOT show toast when Alt also pressed", ctrlAToastShown)
        val aDown = sentKeyEvents.any { it.keyCode == KeyEvent.KEYCODE_A && it.action == KeyEvent.ACTION_DOWN }
        assertTrue("Should send KEYCODE_A for Ctrl-Alt-A", aDown)
    }

    // --- sendTab tests ---

    @Test
    fun `sendTab sends TAB keycode`() {
        sender.sendTab()
        val tabDown = sentKeyEvents.any { it.keyCode == KeyEvent.KEYCODE_TAB && it.action == KeyEvent.ACTION_DOWN }
        assertTrue("Should send KEYCODE_TAB", tabDown)
    }

    // --- sendEscape tests ---

    @Test
    fun `sendEscape sends KEYCODE_ESCAPE`() {
        sender.sendEscape()
        val escDown = sentKeyEvents.any { it.keyCode == 111 && it.action == KeyEvent.ACTION_DOWN }
        assertTrue("Should send KEYCODE_ESCAPE (111)", escDown)
    }

    // --- sendSpecialKey tests ---

    @Test
    fun `sendSpecialKey commits typed and sends key`() {
        sender.sendSpecialKey(KeyEvent.KEYCODE_HOME)
        assertTrue("Should commit typed text", commitTypedCalled)
        assertTrue("Should send key events", sentKeyEvents.isNotEmpty())
    }

    // --- ASCII to KeyCode table tests ---

    @Test
    fun `asciiToKeyCode maps lowercase letters to KEYCODE_A through KEYCODE_Z`() {
        for (i in 0..25) {
            val entry = CharToKeyCodeMapper.asciiToKeyCode['a'.code + i]
            val keyCode = entry and CharToKeyCodeMapper.KF_MASK
            assertEquals("Letter '${('a' + i)}' should map to KEYCODE_A + $i",
                KeyEvent.KEYCODE_A + i, keyCode)
        }
    }

    @Test
    fun `asciiToKeyCode maps uppercase letters to KEYCODE_A through KEYCODE_Z with KF_UPPER`() {
        for (i in 0..25) {
            val entry = CharToKeyCodeMapper.asciiToKeyCode['A'.code + i]
            val keyCode = entry and CharToKeyCodeMapper.KF_MASK
            val isUpper = (entry and CharToKeyCodeMapper.KF_UPPER) > 0
            assertEquals(KeyEvent.KEYCODE_A + i, keyCode)
            assertTrue("Uppercase letter should have KF_UPPER flag", isUpper)
        }
    }

    @Test
    fun `asciiToKeyCode maps digits 0-9 to KEYCODE_0 through KEYCODE_9`() {
        for (i in 0..9) {
            val entry = CharToKeyCodeMapper.asciiToKeyCode['0'.code + i]
            assertEquals(KeyEvent.KEYCODE_0 + i, entry)
        }
    }

    @Test
    fun `asciiToKeyCode maps space to KEYCODE_SPACE with KF_SHIFTABLE`() {
        val entry = CharToKeyCodeMapper.asciiToKeyCode[' '.code]
        val keyCode = entry and CharToKeyCodeMapper.KF_MASK
        val isShiftable = (entry and CharToKeyCodeMapper.KF_SHIFTABLE) > 0
        assertEquals(KeyEvent.KEYCODE_SPACE, keyCode)
        assertTrue("Space should be shiftable", isShiftable)
    }

    @Test
    fun `asciiToKeyCode maps newline to KEYCODE_ENTER with KF_SHIFTABLE`() {
        val entry = CharToKeyCodeMapper.asciiToKeyCode['\n'.code]
        val keyCode = entry and CharToKeyCodeMapper.KF_MASK
        val isShiftable = (entry and CharToKeyCodeMapper.KF_SHIFTABLE) > 0
        assertEquals(KeyEvent.KEYCODE_ENTER, keyCode)
        assertTrue("Enter should be shiftable", isShiftable)
    }

    @Test
    fun `asciiToKeyCode maps common punctuation correctly`() {
        assertEquals(KeyEvent.KEYCODE_COMMA, CharToKeyCodeMapper.asciiToKeyCode[','.code] and CharToKeyCodeMapper.KF_MASK)
        assertEquals(KeyEvent.KEYCODE_PERIOD, CharToKeyCodeMapper.asciiToKeyCode['.'.code] and CharToKeyCodeMapper.KF_MASK)
        assertEquals(KeyEvent.KEYCODE_MINUS, CharToKeyCodeMapper.asciiToKeyCode['-'.code] and CharToKeyCodeMapper.KF_MASK)
        assertEquals(KeyEvent.KEYCODE_EQUALS, CharToKeyCodeMapper.asciiToKeyCode['='.code] and CharToKeyCodeMapper.KF_MASK)
        assertEquals(KeyEvent.KEYCODE_SLASH, CharToKeyCodeMapper.asciiToKeyCode['/'.code] and CharToKeyCodeMapper.KF_MASK)
        assertEquals(KeyEvent.KEYCODE_SEMICOLON, CharToKeyCodeMapper.asciiToKeyCode[';'.code] and CharToKeyCodeMapper.KF_MASK)
    }

    @Test
    fun `asciiToKeyCode maps bracket characters correctly`() {
        assertEquals(KeyEvent.KEYCODE_LEFT_BRACKET, CharToKeyCodeMapper.asciiToKeyCode['['.code] and CharToKeyCodeMapper.KF_MASK)
        assertEquals(KeyEvent.KEYCODE_RIGHT_BRACKET, CharToKeyCodeMapper.asciiToKeyCode[']'.code] and CharToKeyCodeMapper.KF_MASK)
        assertEquals(KeyEvent.KEYCODE_BACKSLASH, CharToKeyCodeMapper.asciiToKeyCode['\\'.code] and CharToKeyCodeMapper.KF_MASK)
    }

    @Test
    fun `asciiToKeyCode has no entry for unmapped characters`() {
        assertEquals(0, CharToKeyCodeMapper.asciiToKeyCode['!'.code])
        assertEquals(0, CharToKeyCodeMapper.asciiToKeyCode['?'.code])
        assertEquals(0, CharToKeyCodeMapper.asciiToKeyCode['^'.code])
        assertEquals(0, CharToKeyCodeMapper.asciiToKeyCode['~'.code])
    }

    // --- sendModifierKeysDown/Up tests ---

    @Test
    fun `sendModifierKeysDown with Ctrl sends CTRL_LEFT down`() {
        modCtrl = true
        sender.sendModifierKeysDown(false)
        val ctrlDown = sentKeyEvents.any { it.keyCode == KeyEvent.KEYCODE_CTRL_LEFT && it.action == KeyEvent.ACTION_DOWN }
        assertTrue("Should send CTRL_LEFT DOWN", ctrlDown)
    }

    @Test
    fun `sendModifierKeysDown with shift sends SHIFT_LEFT down`() {
        sender.sendModifierKeysDown(true)
        val shiftDown = sentKeyEvents.any { it.keyCode == KeyEvent.KEYCODE_SHIFT_LEFT && it.action == KeyEvent.ACTION_DOWN }
        assertTrue("Should send SHIFT_LEFT DOWN", shiftDown)
    }

    @Test
    fun `handleModifierKeysUp with Ctrl resets Ctrl state`() {
        modCtrl = true
        sender.handleModifierKeysUp(false, true)
        assertEquals(false, lastSetModCtrl)
    }

    @Test
    fun `handleModifierKeysUp with shift and no shift lock calls resetShift`() {
        shiftState = Keyboard.SHIFT_OFF
        sender.handleModifierKeysUp(true, true)
        assertTrue("Should call resetShift", resetShiftCalled)
    }

    @Test
    fun `handleModifierKeysUp with SHIFT_LOCKED does not call resetShift`() {
        shiftState = Keyboard.SHIFT_LOCKED
        sender.handleModifierKeysUp(true, true)
        assertFalse("Should NOT call resetShift when shift locked", resetShiftCalled)
    }

    // --- Chording key behavior ---

    @Test
    fun `sendCtrlKey with chording and delay returns without sending`() {
        settings.chordingCtrlKey = 0 // delay = true
        sender.sendCtrlKey(mockInputConnection, true, true)
        assertTrue("Should not send any events when chording is delayed", sentKeyEvents.isEmpty())
    }

    @Test
    fun `sendCtrlKey with chording and no delay sends key`() {
        settings.chordingCtrlKey = KeyEvent.KEYCODE_CTRL_LEFT
        sender.sendCtrlKey(mockInputConnection, true, true)
        assertTrue("Should send key when chording is not delayed", sentKeyEvents.isNotEmpty())
    }

    @Test
    fun `sendAltKey with chording and delay returns without sending`() {
        settings.chordingAltKey = 0
        sender.sendAltKey(mockInputConnection, true, true)
        assertTrue("Should not send any events when chording is delayed", sentKeyEvents.isEmpty())
    }

    @Test
    fun `sendMetaKey with chording and delay returns without sending`() {
        settings.chordingMetaKey = 0
        sender.sendMetaKey(mockInputConnection, true, true)
        assertTrue("Should not send any events when chording is delayed", sentKeyEvents.isEmpty())
    }
}
