package dev.devkey.keyboard.core

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.DELETE_ACCELERATE_AT
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.feature.prediction.AutocorrectEngine
import dev.devkey.keyboard.feature.prediction.AutocorrectResult
import dev.devkey.keyboard.feature.prediction.DictionaryProvider
import dev.devkey.keyboard.feature.prediction.LearningEngine
import dev.devkey.keyboard.testutil.FakeLearnedWordDao
import dev.devkey.keyboard.testutil.MockInputConnection
import dev.devkey.keyboard.testutil.resetSessionDependencies
import dev.devkey.keyboard.testutil.testImeState
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InputHandlersTest {

    private lateinit var state: ImeState
    private lateinit var mockIc: MockInputConnection
    private lateinit var icProvider: StubInputConnectionProvider
    private lateinit var mockCoordinator: SuggestionCoordinator
    private lateinit var mockPicker: SuggestionPicker
    private lateinit var mockModifierHandler: ModifierHandler
    private lateinit var mockKeyEventSender: KeyEventSender
    private lateinit var mockPuncHeuristics: PunctuationHeuristics
    private lateinit var handlers: InputHandlers

    @Before
    fun setUp() {
        state = testImeState(
            mOrientation = 1,
            mShowSuggestions = true,
            mPredictionOnForMode = true,
            mWordSeparators = " .,;:!?"
        )
        mockIc = MockInputConnection()
        icProvider = StubInputConnectionProvider(mockIc)
        mockCoordinator = mock()
        mockPicker = mock()
        mockModifierHandler = mock()
        mockKeyEventSender = mock()
        mockPuncHeuristics = mock()

        whenever(mockCoordinator.isPredictionWanted()).thenReturn(true)

        // handleCharacter accesses state.mKeyboardSwitcher!! — must be non-null
        state.mKeyboardSwitcher = mock()
        whenever(state.mKeyboardSwitcher!!.isAlphabetMode()).thenReturn(true)
        // processComposeKey accesses lateinit mComposeBuffer/mDeadAccentBuffer
        state.mComposeBuffer = dev.devkey.keyboard.compose.ComposeSequence({}, {}, { null })
        state.mDeadAccentBuffer = dev.devkey.keyboard.compose.ComposeSequence({}, {}, { null })

        handlers = InputHandlers(
            state, icProvider, mockCoordinator, mockPicker,
            mockModifierHandler, mockKeyEventSender, mockPuncHeuristics
        )
        resetSessionDependencies()
        TextEntryState.reset()
    }

    @After
    fun tearDown() {
        resetSessionDependencies()
        TextEntryState.reset()
    }

    // ── handleCharacter ─────────────────────────────────────────────

    @Test
    fun `handleCharacter first char starts prediction`() {
        state.mPredicting = false
        handlers.handleCharacter('a'.code, intArrayOf('a'.code))
        assertTrue("mPredicting should be true after first letter", state.mPredicting)
    }

    @Test
    fun `handleCharacter auto-capitalize`() {
        state.mAutoCapPref = true
        state.mPredicting = false
        // Configure shift caps mode so setFirstCharCapitalized is triggered
        whenever(mockModifierHandler.isShiftCapsMode()).thenReturn(true)
        handlers.handleCharacter('H'.code, intArrayOf('H'.code))
        assertTrue("mPredicting should be true", state.mPredicting)
        assertTrue(
            "First char capitalized should be tracked",
            state.mWord.isFirstCharCapitalized()
        )
    }

    @Test
    fun `handleCharacter modifier bypass commits typed`() {
        state.mModCtrl = true
        state.mPredicting = true
        state.mComposing.append("hel")
        state.mWord.add('h'.code, intArrayOf('h'.code))
        state.mWord.add('e'.code, intArrayOf('e'.code))
        state.mWord.add('l'.code, intArrayOf('l'.code))

        handlers.handleCharacter('c'.code, intArrayOf('c'.code))

        assertFalse("mPredicting should be false after modifier bypass", state.mPredicting)
    }

    // ── handleBackspace ─────────────────────────────────────────────

    @Test
    fun `handleBackspace composing trim`() {
        state.mPredicting = true
        state.mComposing.append("hel")
        state.mWord.add('h'.code, intArrayOf('h'.code))
        state.mWord.add('e'.code, intArrayOf('e'.code))
        state.mWord.add('l'.code, intArrayOf('l'.code))

        handlers.handleBackspace()

        assertEquals("Composing should lose last char", 2, state.mComposing.length)
        assertEquals("Composing content", "he", state.mComposing.toString())
    }

    @Test
    fun `handleBackspace composing empty delete`() {
        state.mPredicting = true
        state.mComposing.append("h")
        state.mWord.add('h'.code, intArrayOf('h'.code))

        handlers.handleBackspace()

        assertFalse("mPredicting should be false when composing emptied", state.mPredicting)
        assertEquals(0, state.mComposing.length)
    }

    @Test
    fun `handleBackspace UNDO_COMMIT revert`() {
        // Set up state so TextEntryState transitions to UNDO_COMMIT on backspace
        TextEntryState.acceptedDefault("test", "test")
        // Now TextEntryState is ACCEPTED_DEFAULT; backspace() will move to UNDO_COMMIT

        state.mPredicting = false
        state.mComposing.append("test")
        state.mCommittedLength = 4

        handlers.handleBackspace()

        // revertLastWord was called — mPredicting should now be true
        assertTrue("mPredicting should be true after revert", state.mPredicting)
    }

    @Test
    fun `handleBackspace accelerated delete`() {
        state.mPredicting = false
        state.mDeleteCount = DELETE_ACCELERATE_AT + 1

        handlers.handleBackspace()

        // When deleteChar is true and mDeleteCount > DELETE_ACCELERATE_AT,
        // sendDownUpKeyEvents should be called twice (once for normal, once for accelerated)
        // The first call is via icProvider.sendDownUpKeyEvents, the second also via icProvider
        // Since we're not predicting and no UNDO_COMMIT, it takes the deleteChar path
        // We verify via the StubInputConnectionProvider's call count
        assertEquals(
            "sendDownUpKeyEvents should be called twice for accelerated delete",
            2, icProvider.sendDownUpKeyEventsCalls
        )
    }

    @Test
    fun `handleBackspace mEnteredText match`() {
        state.mPredicting = false
        state.mEnteredText = "xy"
        // Configure IC to return matching text before cursor
        mockIc = MockInputConnection(textBeforeCursor = "xy")
        icProvider = StubInputConnectionProvider(mockIc)
        handlers = InputHandlers(
            state, icProvider, mockCoordinator, mockPicker,
            mockModifierHandler, mockKeyEventSender, mockPuncHeuristics
        )

        handlers.handleBackspace()

        // The mEnteredText path calls deleteSurroundingText on the IC
        // and does NOT call sendDownUpKeyEvents
        assertEquals(
            "sendDownUpKeyEvents should not be called for mEnteredText path",
            0, icProvider.sendDownUpKeyEventsCalls
        )
    }

    // ── commitTyped ─────────────────────────────────────────────────

    @Test
    fun `commitTyped commits composing`() {
        state.mPredicting = true
        state.mComposing.append("hello")
        state.mWord.add('h'.code, intArrayOf('h'.code))

        handlers.commitTyped(mockIc, true)

        assertFalse("mPredicting should be false after commit", state.mPredicting)
        assertEquals("IC commitText should have been called", 1, mockIc.commitTextCount)
    }

    @Test
    fun `commitTyped not-predicting no-op`() {
        state.mPredicting = false

        handlers.commitTyped(mockIc, true)

        assertEquals("IC commitText should NOT be called", 0, mockIc.commitTextCount)
    }

    // ── revertLastWord ──────────────────────────────────────────────

    @Test
    fun `revertLastWord restores composing`() {
        state.mPredicting = false
        state.mComposing.append("hello")
        state.mCommittedLength = 5

        handlers.revertLastWord(false)

        assertTrue("mPredicting should be true after revert", state.mPredicting)
    }

    // ── handleSeparator autocorrect ─────────────────────────────────

    @Test
    fun `handleSeparator mAutoCorrectOn picks default suggestion`() {
        state.mPredicting = true
        state.mAutoCorrectOn = true
        state.mComposing.append("hel")
        state.mWord.add('h'.code, intArrayOf('h'.code))
        state.mWord.add('e'.code, intArrayOf('e'.code))
        state.mWord.add('l'.code, intArrayOf('l'.code))

        whenever(mockPicker.pickDefaultSuggestion()).thenReturn(true)

        handlers.handleSeparator(32)

        verify(mockPicker).pickDefaultSuggestion()
    }

    @Test
    fun `handleSeparator autocorrect aggressive auto-apply`() {
        val mockDictProvider = mock<DictionaryProvider>()
        whenever(mockDictProvider.isValidWord(any())).thenReturn(false)
        whenever(mockDictProvider.getFuzzyCorrections(any(), any())).thenReturn(listOf("the"))
        val acEngine = AutocorrectEngine(mockDictProvider)
        acEngine.aggressiveness = AutocorrectEngine.Aggressiveness.AGGRESSIVE

        val learnEngine = LearningEngine(FakeLearnedWordDao())
        kotlinx.coroutines.runBlocking { learnEngine.initialize() }

        SessionDependencies.autocorrectEngine = acEngine
        SessionDependencies.learningEngine = learnEngine

        state.mPredicting = true
        state.mAutoCorrectOn = false
        state.mComposing.append("teh")
        state.mWord.add('t'.code, intArrayOf('t'.code))
        state.mWord.add('e'.code, intArrayOf('e'.code))
        state.mWord.add('h'.code, intArrayOf('h'.code))

        handlers.handleSeparator(32)

        // Aggressive auto-apply commits text via IC: finishComposingText, deleteSurroundingText, commitText
        assertTrue("IC commitText should have been called", mockIc.commitTextCount >= 1)
    }

    @Test
    fun `handleSeparator autocorrect suggestion non-auto-apply`() {
        val mockDictProvider = mock<DictionaryProvider>()
        whenever(mockDictProvider.isValidWord(any())).thenReturn(false)
        whenever(mockDictProvider.getFuzzyCorrections(any(), any())).thenReturn(listOf("the"))
        val acEngine = AutocorrectEngine(mockDictProvider)
        acEngine.aggressiveness = AutocorrectEngine.Aggressiveness.MILD

        val learnEngine = LearningEngine(FakeLearnedWordDao())
        kotlinx.coroutines.runBlocking { learnEngine.initialize() }

        SessionDependencies.autocorrectEngine = acEngine
        SessionDependencies.learningEngine = learnEngine

        state.mPredicting = true
        state.mAutoCorrectOn = false
        state.mComposing.append("teh")
        state.mWord.add('t'.code, intArrayOf('t'.code))
        state.mWord.add('e'.code, intArrayOf('e'.code))
        state.mWord.add('h'.code, intArrayOf('h'.code))

        handlers.handleSeparator(32)

        val pending = SessionDependencies.pendingCorrection.value
        assertTrue(
            "pendingCorrection should be set for MILD aggressiveness",
            pending is AutocorrectResult.Suggestion
        )
    }

    @Test
    fun `handleSeparator autocorrect OFF path`() {
        val mockDictProvider = mock<DictionaryProvider>()
        val acEngine = AutocorrectEngine(mockDictProvider)
        acEngine.aggressiveness = AutocorrectEngine.Aggressiveness.OFF

        val learnEngine = LearningEngine(FakeLearnedWordDao())
        kotlinx.coroutines.runBlocking { learnEngine.initialize() }

        SessionDependencies.autocorrectEngine = acEngine
        SessionDependencies.learningEngine = learnEngine

        state.mPredicting = true
        state.mAutoCorrectOn = false
        state.mComposing.append("teh")
        state.mWord.add('t'.code, intArrayOf('t'.code))
        state.mWord.add('e'.code, intArrayOf('e'.code))
        state.mWord.add('h'.code, intArrayOf('h'.code))

        val commitCountBefore = mockIc.commitTextCount

        handlers.handleSeparator(32)

        // OFF path falls through to commitTyped — only one commitText (the typed word itself)
        val pending = SessionDependencies.pendingCorrection.value
        assertNull("pendingCorrection should be null when OFF", pending)
        // getFuzzyCorrections should never be called when OFF
        verify(mockDictProvider, never()).getFuzzyCorrections(any(), any())
    }

    @Test
    fun `handleSeparator revert separator guard skips autocorrect`() {
        state.mPredicting = true
        state.mAutoCorrectOn = true
        state.mComposing.append("hel")
        state.mWord.add('h'.code, intArrayOf('h'.code))
        state.mWord.add('e'.code, intArrayOf('e'.code))
        state.mWord.add('l'.code, intArrayOf('l'.code))
        // Set mJustRevertedSeparator to a CharSequence whose first char code matches the separator
        state.mJustRevertedSeparator = " "

        handlers.handleSeparator(32)

        // When mJustRevertedSeparator matches the separator code, pickDefaultSuggestion is NOT called
        verify(mockPicker, never()).pickDefaultSuggestion()
    }

    @Test
    fun `commitTyped manual vs accepted TextEntryState`() {
        // Test manual=true path
        state.mPredicting = true
        state.mComposing.append("hello")
        state.mWord.add('h'.code, intArrayOf('h'.code))

        handlers.commitTyped(mockIc, true)

        assertEquals(
            "After manual commit, TextEntryState should be START",
            TextEntryState.State.START,
            TextEntryState.getState()
        )

        // Reset for manual=false path
        TextEntryState.reset()
        state.mPredicting = true
        state.mComposing.clear()
        state.mComposing.append("world")
        state.mWord.reset()
        state.mWord.add('w'.code, intArrayOf('w'.code))

        val commitCountBefore = mockIc.commitTextCount
        handlers.commitTyped(mockIc, false)

        assertTrue(
            "IC commitText should have been called for manual=false",
            mockIc.commitTextCount > commitCountBefore
        )
    }

    // ── Test doubles ────────────────────────────────────────────────

    private class StubInputConnectionProvider(
        private val ic: InputConnection?
    ) : InputConnectionProvider {
        var sendDownUpKeyEventsCalls = 0
            private set

        override val inputConnection: InputConnection? get() = ic
        override val editorInfo: EditorInfo? get() = null
        override fun sendDownUpKeyEvents(keyEventCode: Int) { sendDownUpKeyEventsCalls++ }
        override fun sendKeyChar(c: Char) {}
    }
}
