package dev.devkey.keyboard.core

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.testutil.MockInputConnection
import dev.devkey.keyboard.testutil.resetSessionDependencies
import dev.devkey.keyboard.testutil.testImeState
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InputDispatcherTest {

    private lateinit var state: ImeState
    private lateinit var mockIc: MockInputConnection
    private lateinit var icProvider: StubInputConnectionProvider
    private lateinit var mockHandlers: InputHandlers
    private lateinit var mockKeyEventSender: KeyEventSender
    private lateinit var mockCandidateHost: CandidateViewHost
    private var commitTypedCalled = false
    private lateinit var dispatcher: InputDispatcher

    @Before
    fun setUp() {
        state = testImeState(
            mOrientation = 1,
            mShowSuggestions = true,
            mPredictionOnForMode = true,
            mWordSeparators = " .,;:!?"
        )
        // InputDispatcher.onKey accesses state.mKeyboardSwitcher!!.onKey() — must be non-null
        state.mKeyboardSwitcher = mock()
        // processComposeKey accesses lateinit mComposeBuffer/mDeadAccentBuffer
        state.mComposeBuffer = dev.devkey.keyboard.compose.ComposeSequence({}, {}, { null })
        state.mDeadAccentBuffer = dev.devkey.keyboard.compose.ComposeSequence({}, {}, { null })

        mockIc = MockInputConnection()
        icProvider = StubInputConnectionProvider(mockIc)
        mockHandlers = mock()
        mockKeyEventSender = mock()
        mockCandidateHost = mock()
        commitTypedCalled = false

        dispatcher = InputDispatcher(
            state = state,
            handlers = mockHandlers,
            icProvider = icProvider,
            candidateViewHost = mockCandidateHost,
            keyEventSender = mockKeyEventSender,
            handleClose = {},
            isShowingOptionDialog = { false },
            onOptionKeyPressed = {},
            onOptionKeyLongPressed = {},
            toggleLanguage = { _, _ -> },
            updateShiftKeyState = { _ -> },
            commitTyped = { _, _ -> commitTypedCalled = true },
            maybeRemovePreviousPeriod = { _ -> },
            abortCorrection = { _ -> },
            getShiftState = { Keyboard.SHIFT_OFF }
        )
        resetSessionDependencies()
        TextEntryState.reset()
    }

    @After
    fun tearDown() {
        resetSessionDependencies()
        TextEntryState.reset()
    }

    // ── onKey routing ───────────────────────────────────────────────

    @Test
    fun `onKey DELETE routes to handleBackspace`() {
        dispatcher.onKey(Keyboard.KEYCODE_DELETE, null, 0, 0)
        verify(mockHandlers).handleBackspace()
    }

    @Test
    fun `onKey ASCII letter routes to handleDefault`() {
        dispatcher.onKey('a'.code, intArrayOf('a'.code), 0, 0)
        verify(mockHandlers).handleDefault(eq('a'.code), any())
    }

    @Test
    fun `onKey ESCAPE sends escape`() {
        dispatcher.onKey(KeyCodes.ESCAPE, null, 0, 0)
        verify(mockKeyEventSender).sendEscape()
    }

    @Test
    fun `onKey TAB sends tab`() {
        dispatcher.onKey(9, null, 0, 0)
        verify(mockKeyEventSender).sendTab()
    }

    @Test
    fun `onKey delete acceleration rapid deletes increase count`() {
        // First delete — sets mLastKeyTime
        dispatcher.onKey(Keyboard.KEYCODE_DELETE, null, 0, 0)
        val firstCount = state.mDeleteCount
        assertEquals("First delete should set count to 1", 1, firstCount)

        // Second delete — same uptimeMillis (rapid), so mDeleteCount should NOT reset
        // (the reset condition is: eventTime > mLastKeyTime + QUICK_PRESS)
        // SystemClock.uptimeMillis() in Robolectric returns a controlled value;
        // two calls in quick succession will be within QUICK_PRESS window
        dispatcher.onKey(Keyboard.KEYCODE_DELETE, null, 0, 0)
        assertEquals("Second rapid delete should increment count to 2", 2, state.mDeleteCount)
    }

    @Test
    fun `onText single-char separator commits typed first`() {
        state.mPredicting = true
        state.mComposing.append("hi")

        dispatcher.onText(" ")

        assertTrue("commitTyped lambda should have been called", commitTypedCalled)
    }

    // ── Test doubles ────────────────────────────────────────────────

    private class StubInputConnectionProvider(
        private val ic: InputConnection?
    ) : InputConnectionProvider {
        override val inputConnection: InputConnection? get() = ic
        override val editorInfo: EditorInfo? get() = null
        override fun sendDownUpKeyEvents(keyEventCode: Int) {}
        override fun sendKeyChar(c: Char) {}
    }
}
