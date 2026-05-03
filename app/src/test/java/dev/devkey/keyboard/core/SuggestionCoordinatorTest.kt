package dev.devkey.keyboard.core

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.feature.smarttext.SmartTextCorrection
import dev.devkey.keyboard.feature.smarttext.SmartTextCorrectionRequest
import dev.devkey.keyboard.feature.smarttext.SmartTextEngine
import dev.devkey.keyboard.feature.smarttext.SmartTextNextWordRequest
import dev.devkey.keyboard.feature.smarttext.SmartTextNextWordResult
import dev.devkey.keyboard.feature.smarttext.SmartTextNextWordSource
import dev.devkey.keyboard.feature.smarttext.SmartTextSuggestion
import dev.devkey.keyboard.feature.smarttext.SmartTextSuggestionKind
import dev.devkey.keyboard.feature.smarttext.SmartTextSuggestionRequest
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
    private lateinit var smartTextEngine: FakeSmartTextEngine

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
        resetSessionDependencies()
        smartTextEngine = FakeSmartTextEngine()
        SessionDependencies.smartTextEngine = smartTextEngine
        coordinator = SuggestionCoordinator(state, icProvider, candidateHost)
    }

    @After
    fun tearDown() {
        resetSessionDependencies()
        TextEntryState.reset()
    }

    // -------------------------------------------------------------------------
    // setNextSuggestions — smart-text hit, pending, no prev word, disabled
    // -------------------------------------------------------------------------

    @Test
    fun `setNextSuggestions with null smart text engine sets punctuation list`() {
        SessionDependencies.smartTextEngine = null
        coordinator.setNextSuggestions()
        assertTrue(
            "Next-word suggestions should be empty when smart text is unavailable",
            SessionDependencies.nextWordSuggestions.value.isEmpty()
        )
    }

    @Test
    fun `setNextSuggestions disabled when prediction off`() {
        state.mPredictionOnForMode = false
        coordinator.setNextSuggestions()
        assertTrue(
            "Next-word suggestions should be empty when prediction is off",
            SessionDependencies.nextWordSuggestions.value.isEmpty()
        )
    }

    @Test
    fun `setNextSuggestions no prev word sets punctuation`() {
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
    fun `setNextSuggestions smart text hit updates SessionDependencies`() {
        smartTextEngine.nextWordResult = SmartTextNextWordResult(
            suggestions = listOf("a", "b", "c"),
            source = SmartTextNextWordSource.HIT,
        )
        coordinator.setNextSuggestions()
        assertEquals(
            "Smart-text hit should populate next-word suggestions",
            3, SessionDependencies.nextWordSuggestions.value.size
        )
    }

    @Test
    fun `setNextSuggestions smart text pending clears next-word suggestions`() {
        smartTextEngine.nextWordResult = SmartTextNextWordResult(
            suggestions = emptyList(),
            source = SmartTextNextWordSource.UNAVAILABLE,
        )
        coordinator.setNextSuggestions()
        assertTrue(
            "Smart-text pending should result in empty next-word suggestions",
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
        smartTextEngine.nextWordResult = SmartTextNextWordResult(
            suggestions = listOf("x", "y"),
            source = SmartTextNextWordSource.HIT,
        )
        state.mPredicting = false
        coordinator.updateSuggestions()
        assertEquals(
            "setNextSuggestions should populate smart-text suggestions",
            2, SessionDependencies.nextWordSuggestions.value.size
        )
    }

    @Test
    fun `updateSuggestions tolerates missing smart text engine`() {
        SessionDependencies.smartTextEngine = null
        state.mPredicting = true
        // Should not throw
        coordinator.updateSuggestions()
    }

    // -------------------------------------------------------------------------
    // showSuggestions(WordComposer) — donor candidate strip
    // -------------------------------------------------------------------------

    @Test
    fun `showSuggestions with autocorrect candidate picks correction as bestWord`() {
        smartTextEngine.candidateResults = listOf(
            SmartTextSuggestion("help", SmartTextSuggestionKind.AUTOCORRECT),
            SmartTextSuggestion("hello", SmartTextSuggestionKind.COMPLETION),
        )
        val word = WordComposer()
        word.add('h'.code, intArrayOf('h'.code))
        word.add('e'.code, intArrayOf('e'.code))
        word.add('l'.code, intArrayOf('l'.code))
        coordinator.showSuggestions(word)
        assertEquals("bestWord should be the correction", "help", state.mBestWord)
    }

    @Test
    fun `showSuggestions with completion only keeps typed word as bestWord`() {
        smartTextEngine.candidateResults = listOf(
            SmartTextSuggestion("test", SmartTextSuggestionKind.COMPLETION),
            SmartTextSuggestion("ten", SmartTextSuggestionKind.COMPLETION),
        )
        val word = WordComposer()
        word.add('t'.code, intArrayOf('t'.code))
        word.add('e'.code, intArrayOf('e'.code))
        coordinator.showSuggestions(word)
        assertEquals("bestWord should be the typed word", "te", state.mBestWord)
    }

    // -------------------------------------------------------------------------
    // showSuggestions — isMostlyCaps suppression
    // -------------------------------------------------------------------------

    @Test
    fun `showSuggestions suppresses correction for mostly caps`() {
        smartTextEngine.candidateResults = listOf(
            SmartTextSuggestion("help", SmartTextSuggestionKind.AUTOCORRECT),
            SmartTextSuggestion("hello", SmartTextSuggestionKind.COMPLETION),
        )
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

    private class FakeSmartTextEngine : SmartTextEngine {
        var candidateResults: List<SmartTextSuggestion> = emptyList()
        var nextWordResult = SmartTextNextWordResult(
            suggestions = emptyList(),
            source = SmartTextNextWordSource.UNAVAILABLE,
        )

        override fun correction(request: SmartTextCorrectionRequest): SmartTextCorrection? = null

        override fun candidateSuggestions(
            request: SmartTextSuggestionRequest,
        ): List<SmartTextSuggestion> = candidateResults.take(request.maxResults)

        override suspend fun suggestions(
            request: SmartTextSuggestionRequest,
        ): List<SmartTextSuggestion> = candidateSuggestions(request)

        override fun nextWordSuggestions(
            request: SmartTextNextWordRequest,
        ): SmartTextNextWordResult = nextWordResult
    }
}
