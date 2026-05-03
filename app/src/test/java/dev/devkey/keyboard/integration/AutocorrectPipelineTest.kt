package dev.devkey.keyboard.integration

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.core.CandidateViewHost
import dev.devkey.keyboard.core.ImeState
import dev.devkey.keyboard.core.InputConnectionProvider
import dev.devkey.keyboard.core.InputHandlers
import dev.devkey.keyboard.core.KeyEventSender
import dev.devkey.keyboard.core.ModifierHandler
import dev.devkey.keyboard.core.PunctuationHeuristics
import dev.devkey.keyboard.core.SuggestionCoordinator
import dev.devkey.keyboard.core.SuggestionPicker
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.feature.prediction.AutocorrectResult
import dev.devkey.keyboard.feature.prediction.DictionaryProvider
import dev.devkey.keyboard.feature.prediction.LearningEngine
import dev.devkey.keyboard.feature.prediction.PredictionEngine
import dev.devkey.keyboard.feature.smarttext.AnySoftKeyboardDictionary
import dev.devkey.keyboard.feature.smarttext.AnySoftKeyboardSmartTextEngine
import dev.devkey.keyboard.feature.smarttext.SmartTextCorrectionLevel
import dev.devkey.keyboard.testutil.FakeLearnedWordDao
import dev.devkey.keyboard.testutil.MockInputConnection
import dev.devkey.keyboard.testutil.loadAnySoftKeyboardDictionaryWithWords
import dev.devkey.keyboard.testutil.resetSessionDependencies
import dev.devkey.keyboard.testutil.testImeState
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration test wiring real [InputHandlers] + donor-backed smart text.
 * Only [InputConnection] and collaborators around the smart-text boundary are stubbed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AutocorrectPipelineTest {

    private lateinit var state: ImeState
    private lateinit var mockIc: MockInputConnection
    private lateinit var icProvider: TestIcProvider
    private lateinit var candidateHost: TestCandidateHost
    private lateinit var coordinator: SuggestionCoordinator
    private lateinit var dictionaryProvider: DictionaryProvider
    private lateinit var learningEngine: LearningEngine
    private lateinit var predictionEngine: PredictionEngine
    private lateinit var donorDictionary: AnySoftKeyboardDictionary
    private lateinit var mockPicker: SuggestionPicker
    private lateinit var mockModifierHandler: ModifierHandler
    private lateinit var mockKeyEventSender: KeyEventSender
    private lateinit var mockPuncHeuristics: PunctuationHeuristics
    private lateinit var handlers: InputHandlers

    @Before
    fun setUp() = runBlocking {
        state = testImeState(
            dispatcher = Dispatchers.Unconfined,
            mShowSuggestions = true,
            mPredictionOnForMode = true,
            mPredictionOnPref = true,
            mOrientation = 1,
            mWordSeparators = " .,;:!?",
            mSentenceSeparators = ".!?"
        )
        state.mSuggestPuncList = mutableListOf(".", ",", "!")
        resetSessionDependencies()

        donorDictionary = loadTestDictionary()

        dictionaryProvider = DictionaryProvider()
        dictionaryProvider.donorDictionary = donorDictionary
        learningEngine = LearningEngine(FakeLearnedWordDao())
        learningEngine.initialize()
        val smartTextEngine = AnySoftKeyboardSmartTextEngine(
            dictionaryProvider,
            learningEngine,
            correctionLevel = { SessionDependencies.smartTextCorrectionLevel },
        )
        predictionEngine = PredictionEngine(smartTextEngine)

        SessionDependencies.dictionaryProvider = dictionaryProvider
        SessionDependencies.learningEngine = learningEngine
        SessionDependencies.smartTextEngine = smartTextEngine
        SessionDependencies.predictionEngine = predictionEngine

        mockIc = MockInputConnection(textBeforeCursor = "")
        icProvider = TestIcProvider(mockIc)
        candidateHost = TestCandidateHost()
        coordinator = SuggestionCoordinator(state, icProvider, candidateHost)
        mockPicker = mock()
        mockModifierHandler = mock()
        mockKeyEventSender = mock()
        mockPuncHeuristics = mock()

        state.mKeyboardSwitcher = mock()
        whenever(state.mKeyboardSwitcher!!.isAlphabetMode()).thenReturn(true)
        state.mComposeBuffer =
            dev.devkey.keyboard.compose.ComposeSequence({}, {}, { null })
        state.mDeadAccentBuffer =
            dev.devkey.keyboard.compose.ComposeSequence({}, {}, { null })

        SessionDependencies.triggerNextSuggestions = { coordinator.setNextSuggestions() }

        handlers = InputHandlers(
            state, icProvider, coordinator, mockPicker,
            mockModifierHandler, mockKeyEventSender, mockPuncHeuristics
        )
        TextEntryState.reset()
    }

    @After
    fun tearDown() {
        resetSessionDependencies()
        TextEntryState.reset()
    }

    // ─────────────────────────────────────────────────────────────────
    // Test 1: Misspelled word + space triggers correction
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `type misspelled word and space triggers correction`() {
        SessionDependencies.smartTextCorrectionLevel = SmartTextCorrectionLevel.AGGRESSIVE
        state.mPredicting = true
        state.mAutoCorrectOn = false
        state.mComposing.append("teh")
        state.mWord.add('t'.code, intArrayOf('t'.code))
        state.mWord.add('e'.code, intArrayOf('e'.code))
        state.mWord.add('h'.code, intArrayOf('h'.code))

        val commitCountBefore = mockIc.commitTextCount

        handlers.handleSeparator(32)

        assertTrue(
            "IC commitText should increase after aggressive correction",
            mockIc.commitTextCount > commitCountBefore
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // Test 2: Pending correction flow for non-auto-apply
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `pending correction flow for non-auto-apply`() {
        SessionDependencies.smartTextCorrectionLevel = SmartTextCorrectionLevel.MILD
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

    // ─────────────────────────────────────────────────────────────────
    // Test 3: Autocorrect then space crosses the smart-text next-word boundary
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `autocorrect then space keeps next-word boundary stable`() {
        SessionDependencies.smartTextCorrectionLevel = SmartTextCorrectionLevel.AGGRESSIVE
        state.mPredicting = true
        state.mAutoCorrectOn = false
        state.mComposing.append("teh")
        state.mWord.add('t'.code, intArrayOf('t'.code))
        state.mWord.add('e'.code, intArrayOf('e'.code))
        state.mWord.add('h'.code, intArrayOf('h'.code))

        // Provide text before cursor for next-word lookup after correction
        mockIc = MockInputConnection(textBeforeCursor = "the ")
        icProvider = TestIcProvider(mockIc)
        coordinator = SuggestionCoordinator(state, icProvider, candidateHost)
        SessionDependencies.triggerNextSuggestions = { coordinator.setNextSuggestions() }
        handlers = InputHandlers(
            state, icProvider, coordinator, mockPicker,
            mockModifierHandler, mockKeyEventSender, mockPuncHeuristics
        )

        handlers.handleSeparator(32)

        // The ASK wordlist import does not yet expose donor next-word ranking,
        // but correction + separator must still cross the boundary and settle cleanly.
        assertTrue(
            "nextWordSuggestions should remain empty until donor next-word ranking lands",
            SessionDependencies.nextWordSuggestions.value.isEmpty()
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private fun loadTestDictionary(): AnySoftKeyboardDictionary =
        loadAnySoftKeyboardDictionaryWithWords(
            "hello" to 1000,
            "help" to 900,
            "world" to 800,
            "the" to 2000,
            "them" to 1500,
            "then" to 1400,
            "there" to 1300,
            "test" to 700,
            "testing" to 600,
        )

    private class TestIcProvider(
        private val ic: InputConnection?
    ) : InputConnectionProvider {
        override val inputConnection: InputConnection? get() = ic
        override val editorInfo: EditorInfo? get() = null
        override fun sendDownUpKeyEvents(keyEventCode: Int) {}
        override fun sendKeyChar(c: Char) {}
    }

    private class TestCandidateHost : CandidateViewHost {
        var setCandidatesShownCalls = 0
            private set

        override fun setCandidatesViewShown(shown: Boolean) {
            setCandidatesShownCalls++
        }

        override fun setCandidatesViewShownInternal(
            shown: Boolean,
            needsInputViewShown: Boolean,
        ) {
            setCandidatesShownCalls++
        }

        override fun isKeyboardVisible(): Boolean = true
        override fun isInFullscreenMode(): Boolean = false
        override fun requestHideSelf(flags: Int) {}
    }
}
