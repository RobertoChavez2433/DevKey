package dev.devkey.keyboard.core

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.compose.ComposeSequence
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.keyboard.switcher.KeyboardSwitcher
import dev.devkey.keyboard.testutil.MockInputConnection
import dev.devkey.keyboard.testutil.testImeState
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ModifierHandlerTest {

    private lateinit var settings: SettingsRepository
    private lateinit var state: ImeState
    private lateinit var mockIc: MockInputConnection
    private lateinit var icProvider: InputConnectionProvider
    private lateinit var feedbackManager: FeedbackManager
    private lateinit var keyEventSender: KeyEventSender
    private lateinit var keyboardSwitcher: KeyboardSwitcher
    private lateinit var handler: ModifierHandler

    private var currentShiftState = Keyboard.SHIFT_OFF

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        settings = SettingsRepository(prefs)

        state = testImeState(mOrientation = 1)
        state.mComposeBuffer = ComposeSequence({}, {}, { null })
        state.mDeadAccentBuffer = ComposeSequence({}, {}, { null })

        mockIc = MockInputConnection()

        keyboardSwitcher = mock()
        currentShiftState = Keyboard.SHIFT_OFF
        whenever(keyboardSwitcher.isAlphabetMode()).thenReturn(true)
        whenever(keyboardSwitcher.getShiftState()).thenAnswer { currentShiftState }
        whenever(keyboardSwitcher.setShiftState(any())).thenAnswer { invocation ->
            currentShiftState = invocation.getArgument(0)
            Unit
        }
        state.mKeyboardSwitcher = keyboardSwitcher

        icProvider = object : InputConnectionProvider {
            override val inputConnection: InputConnection get() = mockIc
            override val editorInfo: EditorInfo? get() = null
            override fun sendDownUpKeyEvents(keyEventCode: Int) {}
            override fun sendKeyChar(c: Char) {}
        }

        feedbackManager = mock()
        keyEventSender = mock()

        handler = ModifierHandler(state, icProvider, feedbackManager, keyEventSender, settings)
    }

    @Test
    fun `nextShiftState via press release OFF to ON to LOCKED to OFF with capsLock`() {
        settings.capsLock = true
        currentShiftState = Keyboard.SHIFT_OFF

        // Tap 1: OFF -> ON (press starts multitouch, release commits)
        handler.onPress(Keyboard.KEYCODE_SHIFT)
        handler.onRelease(Keyboard.KEYCODE_SHIFT)
        assertEquals("First tap should go to SHIFT_ON", Keyboard.SHIFT_ON, currentShiftState)

        // Tap 2: ON -> CAPS_LOCKED (capsLock=true)
        handler.onPress(Keyboard.KEYCODE_SHIFT)
        handler.onRelease(Keyboard.KEYCODE_SHIFT)
        assertEquals(
            "Second tap should go to SHIFT_CAPS_LOCKED",
            Keyboard.SHIFT_CAPS_LOCKED,
            currentShiftState
        )

        // Tap 3: CAPS_LOCKED -> OFF
        handler.onPress(Keyboard.KEYCODE_SHIFT)
        handler.onRelease(Keyboard.KEYCODE_SHIFT)
        assertEquals("Third tap should go to SHIFT_OFF", Keyboard.SHIFT_OFF, currentShiftState)
    }

    @Test
    fun `nextShiftState via press release OFF to ON to LOCKED to OFF without capsLock`() {
        settings.capsLock = false
        currentShiftState = Keyboard.SHIFT_OFF

        // Tap 1: OFF -> ON
        handler.onPress(Keyboard.KEYCODE_SHIFT)
        handler.onRelease(Keyboard.KEYCODE_SHIFT)
        assertEquals("First tap should go to SHIFT_ON", Keyboard.SHIFT_ON, currentShiftState)

        // Tap 2: ON -> LOCKED (capsLock=false)
        handler.onPress(Keyboard.KEYCODE_SHIFT)
        handler.onRelease(Keyboard.KEYCODE_SHIFT)
        assertEquals(
            "Second tap should go to SHIFT_LOCKED",
            Keyboard.SHIFT_LOCKED,
            currentShiftState
        )

        // Tap 3: LOCKED -> OFF
        handler.onPress(Keyboard.KEYCODE_SHIFT)
        handler.onRelease(Keyboard.KEYCODE_SHIFT)
        assertEquals("Third tap should go to SHIFT_OFF", Keyboard.SHIFT_OFF, currentShiftState)
    }

    @Test
    fun `onPress SHIFT starts multitouch shift`() {
        currentShiftState = Keyboard.SHIFT_OFF
        handler.onPress(Keyboard.KEYCODE_SHIFT)
        // After press, shift state should change (startMultitouchShift sets SHIFT_ON)
        assertEquals("Shift state should be ON after press", Keyboard.SHIFT_ON, currentShiftState)
    }

    @Test
    fun `onRelease SHIFT commits multitouch shift`() {
        currentShiftState = Keyboard.SHIFT_OFF
        handler.onPress(Keyboard.KEYCODE_SHIFT)
        handler.onRelease(Keyboard.KEYCODE_SHIFT)
        // After press+release (not chording), commitMultitouchShift is called
        assertEquals(
            "Shift state should be ON after tap",
            Keyboard.SHIFT_ON,
            currentShiftState
        )
    }

    @Test
    fun `onPress CTRL toggles mModCtrl`() {
        assertFalse("mModCtrl should start false", state.mModCtrl)
        handler.onPress(KeyCodes.CTRL_LEFT)
        assertTrue("mModCtrl should be true after press", state.mModCtrl)
    }

    @Test
    fun `onRelease CTRL chording reset sends key up`() {
        // Press Ctrl, then press another key (to trigger chording), then release Ctrl
        handler.onPress(KeyCodes.CTRL_LEFT)
        assertTrue(state.mModCtrl)

        // Simulate another key pressed while Ctrl is held -> chording
        state.mCtrlKeyState.onOtherKeyPressed()
        assertTrue("Should be chording", state.mCtrlKeyState.isChording())

        handler.onRelease(KeyCodes.CTRL_LEFT)
        // When chording, onRelease sets mModCtrl to false
        assertFalse("mModCtrl should be false after chording release", state.mModCtrl)
        // Verify sendCtrlKey was called twice (down on press, up on release)
        verify(keyEventSender, org.mockito.kotlin.times(2)).sendCtrlKey(any(), any(), any())
    }

    @Test
    fun `updateShiftKeyState caps mode ON from EditorInfo`() {
        state.mAutoCapActive = true
        val editorInfo = EditorInfo().apply {
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
        }

        // Use an IC that returns caps mode
        val capsIc = object : MockInputConnection() {
            override fun getCursorCapsMode(reqModes: Int): Int = EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val capsProvider = object : InputConnectionProvider {
            override val inputConnection: InputConnection get() = capsIc
            override val editorInfo: EditorInfo get() = editorInfo
            override fun sendDownUpKeyEvents(keyEventCode: Int) {}
            override fun sendKeyChar(c: Char) {}
        }

        val capsHandler = ModifierHandler(state, capsProvider, feedbackManager, keyEventSender, settings)
        currentShiftState = Keyboard.SHIFT_OFF
        capsHandler.updateShiftKeyState(editorInfo)

        assertEquals(
            "Shift state should be SHIFT_CAPS for auto-cap",
            Keyboard.SHIFT_CAPS,
            currentShiftState
        )
    }

    @Test
    fun `updateShiftKeyState chording active skips auto-cap`() {
        state.mAutoCapActive = true
        val editorInfo = EditorInfo().apply {
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val capsIc = object : MockInputConnection() {
            override fun getCursorCapsMode(reqModes: Int): Int = EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val capsProvider = object : InputConnectionProvider {
            override val inputConnection: InputConnection get() = capsIc
            override val editorInfo: EditorInfo get() = editorInfo
            override fun sendDownUpKeyEvents(keyEventCode: Int) {}
            override fun sendKeyChar(c: Char) {}
        }

        // Simulate chording: press Shift, then another key
        state.mShiftKeyState.onPress()
        state.mShiftKeyState.onOtherKeyPressed()
        assertTrue("Should be chording", state.mShiftKeyState.isChording())

        currentShiftState = Keyboard.SHIFT_OFF
        state.mSavedShiftState = Keyboard.SHIFT_OFF

        val capsHandler = ModifierHandler(state, capsProvider, feedbackManager, keyEventSender, settings)
        capsHandler.updateShiftKeyState(editorInfo)

        // When chording, the shift state is set based on mSavedShiftState, not auto-cap
        // With mSavedShiftState=SHIFT_OFF (not SHIFT_LOCKED), it becomes SHIFT_ON
        assertEquals(
            "Shift state should be SHIFT_ON when chording (not auto-capped)",
            Keyboard.SHIFT_ON,
            currentShiftState
        )
    }
}
