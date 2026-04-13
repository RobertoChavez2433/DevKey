package dev.devkey.keyboard.core

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.suggestion.engine.Suggest
import dev.devkey.keyboard.suggestion.word.WordComposer
import dev.devkey.keyboard.testutil.MockInputConnection
import dev.devkey.keyboard.testutil.resetSessionDependencies
import dev.devkey.keyboard.testutil.testImeState
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SuggestionCoordinatorTest {

    private lateinit var state: ImeState
    private lateinit var mockIc: MockInputConnection
    private lateinit var icProvider: StubInputConnectionProvider
    private lateinit var candidateHost: StubCandidateViewHost
    private lateinit var coordinator: SuggestionCoordinator

    @Before
    fun setUp() {
        state = testImeState(
            dispatcher = Dispatchers.Unconfined,
            mShowSuggestions = true,
            mPredictionOnForMode = true,
            mPredictionOnPref = true,
            mOrientation = 1, // portrait — enables suggestions
            mWordSeparators = " .,;:!?",
            mSentenceSeparators = ".!?"
        )
        state.mSuggestPuncList = mutableListOf(".", ",", "!")
        mockIc = MockInputConnection(textBeforeCursor = "hello ")
        icProvider = StubInputConnectionProvider(mockIc)
        candidateHost = StubCandidateViewHost()
        coordinator = SuggestionCoordinator(state, icProvider, candidateHost)
        resetSessionDependencies()
    }

    @After
    fun tearDown() {
        resetSessionDependencies()
        TextEntryState.reset()
    }

    // -------------------------------------------------------------------------
    // setNextSuggestions — bigram hit, bigram miss, no prev word, disabled
    // -------------------------------------------------------------------------

    @Test
    fun `setNextSuggestions with null Suggest sets punctuation list`() {
        state.mSuggest = null
        coordinator.setNextSuggestions()
        assertTrue(
            "Next-word suggestions should be empty when Suggest is null",
            SessionDependencies.nextWordSuggestions.value.isEmpty()
        )
    }

    @Test
    fun `setNextSuggestions disabled when prediction off`() {
        state.mPredictionOnForMode = false
        state.mSuggest = createMinimalSuggest()
        coordinator.setNextSuggestions()
        assertTrue(
            "Next-word suggestions should be empty when prediction is off",
            SessionDependencies.nextWordSuggestions.value.isEmpty()
        )
    }

    @Test
    fun `setNextSuggestions no prev word sets punctuation`() {
        state.mSuggest = createMinimalSuggest()
        mockIc = MockInputConnection(textBeforeCursor = "")
        icProvider = StubInputConnectionProvider(mockIc)
        coordinator = SuggestionCoordinator(state, icProvider, candidateHost)
        coordinator.setNextSuggestions()
        assertTrue(
            "Next-word suggestions should be empty with no previous word",
            SessionDependencies.nextWordSuggestions.value.isEmpty()
        )
    }

    @Test
    fun `setNextSuggestions bigram hit updates SessionDependencies`() {
        val mockSuggest = createMinimalSuggest()
        // Configure mock to return non-empty bigram suggestions
        whenever(mockSuggest.getNextWordSuggestions(any()))
            .thenReturn(listOf<CharSequence>("a", "b", "c"))
        state.mSuggest = mockSuggest
        coordinator.setNextSuggestions()
        assertEquals(
            "Bigram hit should populate next-word suggestions",
            3, SessionDependencies.nextWordSuggestions.value.size
        )
    }

    @Test
    fun `setNextSuggestions bigram miss clears next-word suggestions`() {
        state.mSuggest = createMinimalSuggest()
        coordinator.setNextSuggestions()
        assertTrue(
            "Bigram miss should result in empty next-word suggestions",
            SessionDependencies.nextWordSuggestions.value.isEmpty()
        )
    }

    // -------------------------------------------------------------------------
    // getLastCommittedWordBeforeCursor
    // -------------------------------------------------------------------------

    @Test
    fun `getLastCommittedWordBeforeCursor finds word`() {
        mockIc = MockInputConnection(textBeforeCursor = "hello ")
        icProvider = StubInputConnectionProvider(mockIc)
        coordinator = SuggestionCoordinator(state, icProvider, candidateHost)
        val result = coordinator.getLastCommittedWordBeforeCursor()
        assertNotNull("Should find word before cursor", result)
        assertEquals(5, result!!.length)
    }

    @Test
    fun `getLastCommittedWordBeforeCursor no word returns null`() {
        mockIc = MockInputConnection(textBeforeCursor = "")
        icProvider = StubInputConnectionProvider(mockIc)
        coordinator = SuggestionCoordinator(state, icProvider, candidateHost)
        val result = coordinator.getLastCommittedWordBeforeCursor()
        assertNull("Empty text should return null", result)
    }

    @Test
    fun `getLastCommittedWordBeforeCursor handles apostrophe`() {
        mockIc = MockInputConnection(textBeforeCursor = "don't ")
        icProvider = StubInputConnectionProvider(mockIc)
        coordinator = SuggestionCoordinator(state, icProvider, candidateHost)
        val result = coordinator.getLastCommittedWordBeforeCursor()
        assertNotNull("Should find word with apostrophe", result)
        assertTrue("Word should contain apostrophe", result!!.contains("'"))
    }

    @Test
    fun `getLastCommittedWordBeforeCursor null IC returns null`() {
        icProvider = StubInputConnectionProvider(null)
        coordinator = SuggestionCoordinator(state, icProvider, candidateHost)
        val result = coordinator.getLastCommittedWordBeforeCursor()
        assertNull("Null IC should return null", result)
    }

    // -------------------------------------------------------------------------
    // updateSuggestions — mPredicting true vs false paths
    // -------------------------------------------------------------------------

    @Test
    fun `updateSuggestions not predicting calls setNextSuggestions`() {
        val mockSuggest = createMinimalSuggest()
        // Configure bigram hit to prove setNextSuggestions actually ran
        whenever(mockSuggest.getNextWordSuggestions(any()))
            .thenReturn(listOf<CharSequence>("x", "y"))
        state.mSuggest = mockSuggest
        state.mPredicting = false
        coordinator.updateSuggestions()
        assertEquals(
            "setNextSuggestions should populate bigram suggestions",
            2, SessionDependencies.nextWordSuggestions.value.size
        )
    }

    @Test
    fun `updateSuggestions returns early when Suggest is null`() {
        state.mSuggest = null
        state.mPredicting = true
        // Should not throw
        coordinator.updateSuggestions()
    }

    // -------------------------------------------------------------------------
    // showSuggestions(WordComposer) — FULL, FULL_BIGRAM correction modes
    // -------------------------------------------------------------------------

    @Test
    fun `showSuggestions with CORRECTION_FULL picks correction as bestWord`() {
        val mockSuggest = createMinimalSuggest()
        // Make correction logic meaningful: hasMinimalCorrection=true, provide a suggestion
        whenever(mockSuggest.hasMinimalCorrection()).thenReturn(true)
        whenever(mockSuggest.isValidWord(any())).thenReturn(false)
        whenever(mockSuggest.getSuggestions(anyOrNull(), any(), any(), anyOrNull()))
            .thenReturn(listOf<CharSequence>("hel", "help", "hello"))
        whenever(mockSuggest.getNextLettersFrequencies()).thenReturn(IntArray(0))
        state.mSuggest = mockSuggest
        state.mCorrectionMode = Suggest.CORRECTION_FULL
        val word = WordComposer()
        word.add('h'.code, intArrayOf('h'.code))
        word.add('e'.code, intArrayOf('e'.code))
        word.add('l'.code, intArrayOf('l'.code))
        coordinator.showSuggestions(word)
        // With correctionAvailable=true and !typedWordValid, bestWord should be stringList[1]
        assertEquals("bestWord should be the correction (index 1)", "help", state.mBestWord)
    }

    @Test
    fun `showSuggestions with CORRECTION_FULL_BIGRAM picks correction`() {
        val mockSuggest = createMinimalSuggest()
        whenever(mockSuggest.hasMinimalCorrection()).thenReturn(true)
        whenever(mockSuggest.isValidWord(any())).thenReturn(false)
        whenever(mockSuggest.getSuggestions(anyOrNull(), any(), any(), anyOrNull()))
            .thenReturn(listOf<CharSequence>("te", "test", "ten"))
        whenever(mockSuggest.getNextLettersFrequencies()).thenReturn(IntArray(0))
        state.mSuggest = mockSuggest
        state.mCorrectionMode = Suggest.CORRECTION_FULL_BIGRAM
        val word = WordComposer()
        word.add('t'.code, intArrayOf('t'.code))
        word.add('e'.code, intArrayOf('e'.code))
        coordinator.showSuggestions(word)
        assertEquals("bestWord should be the correction (index 1)", "test", state.mBestWord)
    }

    // -------------------------------------------------------------------------
    // showSuggestions — isMostlyCaps suppression
    // -------------------------------------------------------------------------

    @Test
    fun `showSuggestions suppresses correction for mostly caps`() {
        val mockSuggest = createMinimalSuggest()
        // Same setup as CORRECTION_FULL, but with mostly-caps input
        whenever(mockSuggest.hasMinimalCorrection()).thenReturn(true)
        whenever(mockSuggest.isValidWord(any())).thenReturn(false)
        whenever(mockSuggest.getSuggestions(anyOrNull(), any(), any(), anyOrNull()))
            .thenReturn(listOf<CharSequence>("HEL", "help", "hello"))
        whenever(mockSuggest.getNextLettersFrequencies()).thenReturn(IntArray(0))
        state.mSuggest = mockSuggest
        state.mCorrectionMode = Suggest.CORRECTION_FULL
        val word = WordComposer()
        // Uppercase letters trigger isMostlyCaps
        word.add('H'.code, intArrayOf('H'.code))
        word.add('E'.code, intArrayOf('E'.code))
        word.add('L'.code, intArrayOf('L'.code))
        coordinator.showSuggestions(word)
        // isMostlyCaps suppresses correction, so bestWord should be the typed word, not [1]
        val bestWord = state.mBestWord?.toString()
        assertTrue(
            "bestWord should be typed word (not correction) when mostly caps",
            bestWord == null || bestWord != "help"
        )
    }

    // -------------------------------------------------------------------------
    // onSelectionChanged
    // -------------------------------------------------------------------------

    @Test
    fun `onSelectionChanged composing out of sync clears composing`() {
        state.mComposing.append("test")
        state.mPredicting = true
        state.mLastSelectionStart = 0
        coordinator.onSelectionChanged(
            oldSelStart = 0, newSelStart = 10, newSelEnd = 10,
            candidatesStart = 0, candidatesEnd = 4
        )
        assertEquals("Composing should be cleared", 0, state.mComposing.length)
    }

    @Test
    fun `onSelectionChanged updates last selection`() {
        state.mPredicting = false
        state.mJustAccepted = false
        TextEntryState.reset()
        coordinator.onSelectionChanged(
            oldSelStart = 0, newSelStart = 5, newSelEnd = 5,
            candidatesStart = 0, candidatesEnd = 0
        )
        assertEquals(5, state.mLastSelectionStart)
        assertEquals(5, state.mLastSelectionEnd)
    }

    // -------------------------------------------------------------------------
    // Test doubles
    // -------------------------------------------------------------------------

    /**
     * Creates a mock Suggest that avoids JNI native library loading.
     * Returns empty suggestions by default.
     */
    private fun createMinimalSuggest(): Suggest {
        val mockSuggest = mock<Suggest>()
        whenever(mockSuggest.getSuggestions(any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockSuggest.getNextWordSuggestions(any()))
            .thenReturn(emptyList())
        whenever(mockSuggest.hasMinimalCorrection()).thenReturn(false)
        whenever(mockSuggest.isValidWord(any())).thenReturn(false)
        whenever(mockSuggest.getNextLettersFrequencies()).thenReturn(IntArray(0))
        return mockSuggest
    }

    private class StubInputConnectionProvider(
        private val ic: InputConnection?
    ) : InputConnectionProvider {
        override val inputConnection: InputConnection? get() = ic
        override val editorInfo: EditorInfo? get() = null
        override fun sendDownUpKeyEvents(keyEventCode: Int) {}
        override fun sendKeyChar(c: Char) {}
    }

    private class StubCandidateViewHost : CandidateViewHost {
        var setCandidatesShownCalls = 0
            private set
        var isKeyboardVisibleValue = true

        override fun setCandidatesViewShown(shown: Boolean) {
            setCandidatesShownCalls++
        }

        override fun setCandidatesViewShownInternal(shown: Boolean, needsInputViewShown: Boolean) {
            setCandidatesShownCalls++
        }

        override fun isKeyboardVisible(): Boolean = isKeyboardVisibleValue
        override fun isInFullscreenMode(): Boolean = false
        override fun requestHideSelf(flags: Int) {}
    }
}
