package dev.devkey.keyboard.integration

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.compose.ComposeSequence
import dev.devkey.keyboard.core.FeedbackManager
import dev.devkey.keyboard.core.ImeState
import dev.devkey.keyboard.core.InputConnectionProvider
import dev.devkey.keyboard.core.KeyEventSender
import dev.devkey.keyboard.core.ModifierHandler
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
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Integration test wiring real [ModifierHandler] + [KeyEventSender] with a
 * [MockInputConnection]. Only [KeyboardSwitcher] and [FeedbackManager] are
 * mocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ModifierPipelineTest {

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

        // Real KeyEventSender wired to the same MockInputConnection and state
        keyEventSender = KeyEventSender(
            inputConnectionProvider = { mockIc },
            modCtrlProvider = { state.mModCtrl },
            modAltProvider = { state.mModAlt },
            modMetaProvider = { state.mModMeta },
            shiftKeyStateProvider = { state.mShiftKeyState },
            ctrlKeyStateProvider = { state.mCtrlKeyState },
            altKeyStateProvider = { state.mAltKeyState },
            metaKeyStateProvider = { state.mMetaKeyState },
            shiftModProvider = {
                state.mShiftKeyState.isChording() ||
                    currentShiftState == Keyboard.SHIFT_LOCKED ||
                    currentShiftState == Keyboard.SHIFT_CAPS_LOCKED
            },
            setModCtrl = { v -> state.mModCtrl = v },
            setModAlt = { v -> state.mModAlt = v },
            setModMeta = { v -> state.mModMeta = v },
            resetShift = {
                currentShiftState = Keyboard.SHIFT_OFF
                keyboardSwitcher.setShiftState(Keyboard.SHIFT_OFF)
            },
            getShiftState = { currentShiftState },
            commitTyped = { _, _ -> },
            sendKeyCharFn = { _ -> },
            sendDownUpKeyEventsFn = { _ -> },
            settings = settings,
            ctrlAToastAction = { }
        )

        handler = ModifierHandler(state, icProvider, feedbackManager, keyEventSender, settings)
    }

    @Test
    fun `full press release Ctrl cycle sends key events on IC`() {
        settings.chordingCtrlKey = KeyEvent.KEYCODE_CTRL_LEFT

        handler.onPress(KeyCodes.CTRL_LEFT)
        assertTrue("mModCtrl should be true after press", state.mModCtrl)

        // Verify key down was sent
        val downEvents = mockIc.sendKeyEventCalls.filter {
            it.keyCode == KeyEvent.KEYCODE_CTRL_LEFT && it.action == KeyEvent.ACTION_DOWN
        }
        assertEquals("Ctrl DOWN should be sent on press", 1, downEvents.size)

        handler.onRelease(KeyCodes.CTRL_LEFT)

        // Verify key up was sent
        val upEvents = mockIc.sendKeyEventCalls.filter {
            it.keyCode == KeyEvent.KEYCODE_CTRL_LEFT && it.action == KeyEvent.ACTION_UP
        }
        assertEquals("Ctrl UP should be sent on release", 1, upEvents.size)
    }

    @Test
    fun `chording Ctrl down letter Ctrl up sends modified key event`() {
        settings.chordingCtrlKey = KeyEvent.KEYCODE_CTRL_LEFT

        // Press Ctrl
        handler.onPress(KeyCodes.CTRL_LEFT)
        assertTrue(state.mModCtrl)

        // Simulate another key pressed (triggers chording state)
        state.mCtrlKeyState.onOtherKeyPressed()

        // Send 'c' via the key event sender with Ctrl active
        keyEventSender.sendModifiedKeyDownUp(KeyEvent.KEYCODE_C, false)

        // Verify KEYCODE_C was sent with Ctrl meta state
        val cDownEvents = mockIc.sendKeyEventCalls.filter {
            it.keyCode == KeyEvent.KEYCODE_C && it.action == KeyEvent.ACTION_DOWN
        }
        assertTrue("KEYCODE_C DOWN should be sent", cDownEvents.isNotEmpty())
        val meta = cDownEvents[0].metaState
        assertTrue(
            "Ctrl meta flag should be present",
            meta and KeyEvent.META_CTRL_ON != 0
        )

        // Release Ctrl
        handler.onRelease(KeyCodes.CTRL_LEFT)
        assertFalse("mModCtrl should be false after chording release", state.mModCtrl)
    }

    @Test
    fun `shift one-shot tap shift type letter triggers uppercase state`() {
        currentShiftState = Keyboard.SHIFT_OFF

        // Tap shift (press + release without chording)
        handler.onPress(Keyboard.KEYCODE_SHIFT)
        handler.onRelease(Keyboard.KEYCODE_SHIFT)

        // After one-shot tap, shift should be ON
        assertEquals(
            "Shift should be ON after one-shot tap",
            Keyboard.SHIFT_ON,
            currentShiftState
        )

        // Simulate pressing a letter key — this should trigger onOtherKeyPressed
        // and transition shift to chording state for the next release
        handler.onPress('a'.code)

        // The shift key state should now be in the state where other key was pressed
        // After the letter is committed, handleModifierKeysUp would normally reset shift
        assertTrue(
            "Shift should still be ON or higher after letter press",
            currentShiftState >= Keyboard.SHIFT_ON
        )
    }
}
