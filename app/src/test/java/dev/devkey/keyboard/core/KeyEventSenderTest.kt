package dev.devkey.keyboard.core

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.content.Context
import dev.devkey.keyboard.data.repository.SettingsRepository
import org.robolectric.RuntimeEnvironment
import dev.devkey.keyboard.Keyboard
import dev.devkey.keyboard.ChordeTracker
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
 * Comprehensive unit tests for KeyEventSender.
 *
 * Tests cover:
 * - VT escape sequence mapping (ESC_SEQUENCES)
 * - ConnectBot Ctrl sequence mapping (CTRL_SEQUENCES)
 * - Sign convention (negative keycodes)
 * - ConnectBot tab/escape hack mapping
 * - sendModifiableKeyChar with and without active modifiers
 * - sendModifiedKeyDownUp key event sequences
 * - getMetaState for all modifier combinations
 * - isConnectbot detection logic
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
    private var connectbotTabHack = false
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
        connectbotTabHack = false
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
            connectbotTabHackProvider = { connectbotTabHack },
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

    // --- ESC_SEQUENCES tests ---

    @Test
    fun `ESC_SEQUENCES contains correct VT codes for Home`() {
        val escSeq = KeyEventSender.getEscSequences()
        assertEquals("[1~", escSeq[-KeyCodes.KEYCODE_HOME])
    }

    @Test
    fun `ESC_SEQUENCES contains correct VT codes for End`() {
        val escSeq = KeyEventSender.getEscSequences()
        assertEquals("[4~", escSeq[-KeyCodes.KEYCODE_END])
    }

    @Test
    fun `ESC_SEQUENCES contains correct VT codes for Page Up`() {
        val escSeq = KeyEventSender.getEscSequences()
        assertEquals("[5~", escSeq[-KeyCodes.KEYCODE_PAGE_UP])
    }

    @Test
    fun `ESC_SEQUENCES contains correct VT codes for Page Down`() {
        val escSeq = KeyEventSender.getEscSequences()
        assertEquals("[6~", escSeq[-KeyCodes.KEYCODE_PAGE_DOWN])
    }

    @Test
    fun `ESC_SEQUENCES contains correct VT codes for F1 through F4`() {
        val escSeq = KeyEventSender.getEscSequences()
        assertEquals("OP", escSeq[-KeyCodes.KEYCODE_FKEY_F1])
        assertEquals("OQ", escSeq[-KeyCodes.KEYCODE_FKEY_F2])
        assertEquals("OR", escSeq[-KeyCodes.KEYCODE_FKEY_F3])
        assertEquals("OS", escSeq[-KeyCodes.KEYCODE_FKEY_F4])
    }

    @Test
    fun `ESC_SEQUENCES contains correct VT codes for F5 through F12`() {
        val escSeq = KeyEventSender.getEscSequences()
        assertEquals("[15~", escSeq[-KeyCodes.KEYCODE_FKEY_F5])
        assertEquals("[17~", escSeq[-KeyCodes.KEYCODE_FKEY_F6])
        assertEquals("[18~", escSeq[-KeyCodes.KEYCODE_FKEY_F7])
        assertEquals("[19~", escSeq[-KeyCodes.KEYCODE_FKEY_F8])
        assertEquals("[20~", escSeq[-KeyCodes.KEYCODE_FKEY_F9])
        assertEquals("[21~", escSeq[-KeyCodes.KEYCODE_FKEY_F10])
        assertEquals("[23~", escSeq[-KeyCodes.KEYCODE_FKEY_F11])
        assertEquals("[24~", escSeq[-KeyCodes.KEYCODE_FKEY_F12])
    }

    @Test
    fun `ESC_SEQUENCES contains correct VT codes for Forward Del and Insert`() {
        val escSeq = KeyEventSender.getEscSequences()
        assertEquals("[3~", escSeq[-KeyCodes.KEYCODE_FORWARD_DEL])
        assertEquals("[2~", escSeq[-KeyCodes.KEYCODE_INSERT])
    }

    @Test
    fun `ESC_SEQUENCES uses negated KeyCodes values as keys (positive because KeyCodes are negative)`() {
        val escSeq = KeyEventSender.getEscSequences()
        // KeyCodes constants are negative, so -KeyCodes.KEYCODE_HOME = -(-122) = 122 (positive)
        for (key in escSeq.keys) {
            assertTrue("ESC_SEQUENCES key $key should be positive (negated negative KeyCode)", key > 0)
        }
    }

    @Test
    fun `ESC_SEQUENCES has exactly 18 entries`() {
        val escSeq = KeyEventSender.getEscSequences()
        assertEquals(18, escSeq.size)
    }

    // --- CTRL_SEQUENCES tests ---

    @Test
    fun `CTRL_SEQUENCES maps F1-F10 to Ctrl-1 through Ctrl-0`() {
        val ctrlSeq = KeyEventSender.getCtrlSequences()
        assertEquals(KeyEvent.KEYCODE_1, ctrlSeq[-KeyCodes.KEYCODE_FKEY_F1])
        assertEquals(KeyEvent.KEYCODE_2, ctrlSeq[-KeyCodes.KEYCODE_FKEY_F2])
        assertEquals(KeyEvent.KEYCODE_3, ctrlSeq[-KeyCodes.KEYCODE_FKEY_F3])
        assertEquals(KeyEvent.KEYCODE_4, ctrlSeq[-KeyCodes.KEYCODE_FKEY_F4])
        assertEquals(KeyEvent.KEYCODE_5, ctrlSeq[-KeyCodes.KEYCODE_FKEY_F5])
        assertEquals(KeyEvent.KEYCODE_6, ctrlSeq[-KeyCodes.KEYCODE_FKEY_F6])
        assertEquals(KeyEvent.KEYCODE_7, ctrlSeq[-KeyCodes.KEYCODE_FKEY_F7])
        assertEquals(KeyEvent.KEYCODE_8, ctrlSeq[-KeyCodes.KEYCODE_FKEY_F8])
        assertEquals(KeyEvent.KEYCODE_9, ctrlSeq[-KeyCodes.KEYCODE_FKEY_F9])
        assertEquals(KeyEvent.KEYCODE_0, ctrlSeq[-KeyCodes.KEYCODE_FKEY_F10])
    }

    @Test
    fun `CTRL_SEQUENCES uses negated KeyCodes values as keys (positive because KeyCodes are negative)`() {
        val ctrlSeq = KeyEventSender.getCtrlSequences()
        // KeyCodes constants are negative, so -KeyCodes.KEYCODE_FKEY_F1 = -(-131) = 131 (positive)
        for (key in ctrlSeq.keys) {
            assertTrue("CTRL_SEQUENCES key $key should be positive (negated negative KeyCode)", key > 0)
        }
    }

    @Test
    fun `CTRL_SEQUENCES has exactly 10 entries`() {
        val ctrlSeq = KeyEventSender.getCtrlSequences()
        assertEquals(10, ctrlSeq.size)
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

    // --- isConnectbot tests ---

    @Test
    fun `isConnectbot returns false when editorInfo is null`() {
        val s = createSender(editorInfo = null)
        assertFalse(s.isConnectbot())
    }

    @Test
    fun `isConnectbot returns true for org_connectbot with inputType 0`() {
        val ei = EditorInfo()
        ei.packageName = "org.connectbot"
        ei.inputType = 0
        val s = createSender(editorInfo = ei)
        assertTrue(s.isConnectbot())
    }

    @Test
    fun `isConnectbot returns true for irssiconnectbot`() {
        val ei = EditorInfo()
        ei.packageName = "org.woltage.irssiconnectbot"
        ei.inputType = 0
        val s = createSender(editorInfo = ei)
        assertTrue(s.isConnectbot())
    }

    @Test
    fun `isConnectbot returns true for pslib connectbot`() {
        val ei = EditorInfo()
        ei.packageName = "com.pslib.connectbot"
        ei.inputType = 0
        val s = createSender(editorInfo = ei)
        assertTrue(s.isConnectbot())
    }

    @Test
    fun `isConnectbot returns true for vx connectbot`() {
        val ei = EditorInfo()
        ei.packageName = "sk.vx.connectbot"
        ei.inputType = 0
        val s = createSender(editorInfo = ei)
        assertTrue(s.isConnectbot())
    }

    @Test
    fun `isConnectbot returns false for non-zero inputType`() {
        val ei = EditorInfo()
        ei.packageName = "org.connectbot"
        ei.inputType = 1
        val s = createSender(editorInfo = ei)
        assertFalse(s.isConnectbot())
    }

    @Test
    fun `isConnectbot returns false for unknown package`() {
        val ei = EditorInfo()
        ei.packageName = "com.example.app"
        ei.inputType = 0
        val s = createSender(editorInfo = ei)
        assertFalse(s.isConnectbot())
    }

    @Test
    fun `isConnectbot is case-insensitive`() {
        val ei = EditorInfo()
        ei.packageName = "ORG.CONNECTBOT"
        ei.inputType = 0
        val s = createSender(editorInfo = ei)
        assertTrue(s.isConnectbot())
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
    fun `sendTab without ConnectBot sends TAB keycode`() {
        sender.sendTab()
        val tabDown = sentKeyEvents.any { it.keyCode == KeyEvent.KEYCODE_TAB && it.action == KeyEvent.ACTION_DOWN }
        assertTrue("Should send KEYCODE_TAB", tabDown)
    }

    @Test
    fun `sendTab with ConnectBot tab hack sends DPAD_CENTER and I`() {
        val ei = EditorInfo()
        ei.packageName = "org.connectbot"
        ei.inputType = 0
        connectbotTabHack = true
        val s = createSender(editorInfo = ei)
        s.sendTab()
        val centerDown = sentKeyEvents.any { it.keyCode == KeyEvent.KEYCODE_DPAD_CENTER && it.action == KeyEvent.ACTION_DOWN }
        val iDown = sentKeyEvents.any { it.keyCode == KeyEvent.KEYCODE_I && it.action == KeyEvent.ACTION_DOWN }
        assertTrue("Should send DPAD_CENTER", centerDown)
        assertTrue("Should send I key", iDown)
    }

    // --- sendEscape tests ---

    @Test
    fun `sendEscape without ConnectBot sends KEYCODE_ESCAPE`() {
        sender.sendEscape()
        val escDown = sentKeyEvents.any { it.keyCode == 111 && it.action == KeyEvent.ACTION_DOWN }
        assertTrue("Should send KEYCODE_ESCAPE (111)", escDown)
    }

    @Test
    fun `sendEscape with ConnectBot sends char 27`() {
        val ei = EditorInfo()
        ei.packageName = "org.connectbot"
        ei.inputType = 0
        val s = createSender(editorInfo = ei)
        s.sendEscape()
        assertEquals(27.toChar(), lastSendKeyChar)
    }

    // --- sendSpecialKey tests ---

    @Test
    fun `sendSpecialKey without ConnectBot commits typed and sends key`() {
        sender.sendSpecialKey(-KeyCodes.KEYCODE_HOME)
        assertTrue("Should commit typed text", commitTypedCalled)
        // Should send some key events
        assertTrue("Should send key events", sentKeyEvents.isNotEmpty())
    }

    // --- ASCII to KeyCode table tests ---

    @Test
    fun `asciiToKeyCode maps lowercase letters to KEYCODE_A through KEYCODE_Z`() {
        for (i in 0..25) {
            val entry = KeyEventSender.asciiToKeyCode['a'.code + i]
            val keyCode = entry and 0xffff // KF_MASK
            assertEquals("Letter '${('a' + i)}' should map to KEYCODE_A + $i",
                KeyEvent.KEYCODE_A + i, keyCode)
        }
    }

    @Test
    fun `asciiToKeyCode maps uppercase letters to KEYCODE_A through KEYCODE_Z with KF_UPPER`() {
        for (i in 0..25) {
            val entry = KeyEventSender.asciiToKeyCode['A'.code + i]
            val keyCode = entry and 0xffff
            val isUpper = (entry and 0x20000) > 0 // KF_UPPER
            assertEquals(KeyEvent.KEYCODE_A + i, keyCode)
            assertTrue("Uppercase letter should have KF_UPPER flag", isUpper)
        }
    }

    @Test
    fun `asciiToKeyCode maps digits 0-9 to KEYCODE_0 through KEYCODE_9`() {
        for (i in 0..9) {
            val entry = KeyEventSender.asciiToKeyCode['0'.code + i]
            assertEquals(KeyEvent.KEYCODE_0 + i, entry)
        }
    }

    @Test
    fun `asciiToKeyCode maps space to KEYCODE_SPACE with KF_SHIFTABLE`() {
        val entry = KeyEventSender.asciiToKeyCode[' '.code]
        val keyCode = entry and 0xffff
        val isShiftable = (entry and 0x10000) > 0
        assertEquals(KeyEvent.KEYCODE_SPACE, keyCode)
        assertTrue("Space should be shiftable", isShiftable)
    }

    @Test
    fun `asciiToKeyCode maps newline to KEYCODE_ENTER with KF_SHIFTABLE`() {
        val entry = KeyEventSender.asciiToKeyCode['\n'.code]
        val keyCode = entry and 0xffff
        val isShiftable = (entry and 0x10000) > 0
        assertEquals(KeyEvent.KEYCODE_ENTER, keyCode)
        assertTrue("Enter should be shiftable", isShiftable)
    }

    @Test
    fun `asciiToKeyCode maps common punctuation correctly`() {
        assertEquals(KeyEvent.KEYCODE_COMMA, KeyEventSender.asciiToKeyCode[','.code] and 0xffff)
        assertEquals(KeyEvent.KEYCODE_PERIOD, KeyEventSender.asciiToKeyCode['.'.code] and 0xffff)
        assertEquals(KeyEvent.KEYCODE_MINUS, KeyEventSender.asciiToKeyCode['-'.code] and 0xffff)
        assertEquals(KeyEvent.KEYCODE_EQUALS, KeyEventSender.asciiToKeyCode['='.code] and 0xffff)
        assertEquals(KeyEvent.KEYCODE_SLASH, KeyEventSender.asciiToKeyCode['/'.code] and 0xffff)
        assertEquals(KeyEvent.KEYCODE_SEMICOLON, KeyEventSender.asciiToKeyCode[';'.code] and 0xffff)
    }

    @Test
    fun `asciiToKeyCode maps bracket characters correctly`() {
        assertEquals(KeyEvent.KEYCODE_LEFT_BRACKET, KeyEventSender.asciiToKeyCode['['.code] and 0xffff)
        assertEquals(KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEventSender.asciiToKeyCode[']'.code] and 0xffff)
        assertEquals(KeyEvent.KEYCODE_BACKSLASH, KeyEventSender.asciiToKeyCode['\\'.code] and 0xffff)
    }

    @Test
    fun `asciiToKeyCode has no entry for unmapped characters`() {
        assertEquals(0, KeyEventSender.asciiToKeyCode['!'.code])
        assertEquals(0, KeyEventSender.asciiToKeyCode['?'.code])
        assertEquals(0, KeyEventSender.asciiToKeyCode['^'.code])
        assertEquals(0, KeyEventSender.asciiToKeyCode['~'.code])
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
