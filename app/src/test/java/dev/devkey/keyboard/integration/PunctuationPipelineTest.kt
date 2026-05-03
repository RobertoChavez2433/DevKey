package dev.devkey.keyboard.integration

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.core.ImeState
import dev.devkey.keyboard.core.InputConnectionProvider
import dev.devkey.keyboard.core.InputHandlers
import dev.devkey.keyboard.core.KeyEventSender
import dev.devkey.keyboard.core.ModifierHandler
import dev.devkey.keyboard.core.PunctuationHeuristics
import dev.devkey.keyboard.core.SuggestionCoordinator
import dev.devkey.keyboard.core.SuggestionPicker
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.testutil.MockInputConnection
import dev.devkey.keyboard.testutil.resetSessionDependencies
import dev.devkey.keyboard.testutil.testImeState
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
 * Integration test wiring real [PunctuationHeuristics] + [InputHandlers].
 * Mock IC and other collaborators.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PunctuationPipelineTest {

    private lateinit var state: ImeState
    private lateinit var mockIc: MockInputConnection
    private lateinit var icProvider: TestIcProvider
    private lateinit var mockCoordinator: SuggestionCoordinator
    private lateinit var mockPicker: SuggestionPicker
    private lateinit var mockModifierHandler: ModifierHandler
    private lateinit var mockKeyEventSender: KeyEventSender
    private lateinit var mockSettings: SettingsRepository
    private lateinit var puncHeuristics: PunctuationHeuristics
    private lateinit var handlers: InputHandlers

    @Before
    fun setUp() {
        state = testImeState(
            mOrientation = 1, // portrait
            mShowSuggestions = true,
            mPredictionOnForMode = true,
            mWordSeparators = " .,;:!?"
        )
        state.mKeyboardSwitcher = mock()
        whenever(state.mKeyboardSwitcher!!.isAlphabetMode()).thenReturn(true)
        state.mComposeBuffer =
            dev.devkey.keyboard.compose.ComposeSequence({}, {}, { null })
        state.mDeadAccentBuffer =
            dev.devkey.keyboard.compose.ComposeSequence({}, {}, { null })

        mockSettings = mock()
        mockSettings.suggestedPunctuation = ".,!?"

        mockCoordinator = mock()
        whenever(mockCoordinator.isPredictionWanted()).thenReturn(true)
        mockPicker = mock()
        whenever(mockPicker.pickDefaultSuggestion()).thenAnswer {
            state.mPredicting = false
            TextEntryState.acceptedDefault(state.mComposing, state.mComposing)
            true
        }
        mockModifierHandler = mock()
        mockKeyEventSender = mock()

        resetSessionDependencies()
        TextEntryState.reset()
    }

    @After
    fun tearDown() {
        resetSessionDependencies()
        TextEntryState.reset()
    }

    private fun wireWithIc(ic: MockInputConnection) {
        mockIc = ic
        icProvider = TestIcProvider(ic)
        puncHeuristics = PunctuationHeuristics(
            icProvider = { ic },
            settings = mockSettings,
            updateShiftKeyState = {}
        )
        puncHeuristics.sentenceSeparators = ".!?"
        handlers = InputHandlers(
            state, icProvider, mockCoordinator, mockPicker,
            mockModifierHandler, mockKeyEventSender, puncHeuristics
        )
    }

    // ── Test 1: doubleSpace via handleSeparator ────────────────────

    @Test
    fun `handleSeparator then doubleSpace transforms IC`() {
        // Simulate: user typed a word then two spaces.
        // After the first space, the composing buffer is committed.
        // The second space triggers doubleSpace() via handleSeparator.
        //
        // For doubleSpace to fire, IC.getTextBeforeCursor(3) must return
        // letter + space + space. We wire the IC accordingly.
        val ic = MockInputConnection(textBeforeCursor = "a  ")
        wireWithIc(ic)

        // Put state into prediction mode with a word composed
        state.mPredicting = true
        state.mComposing.append("a")
        state.mWord.add('a'.code, intArrayOf('a'.code))
        state.mAutoCorrectOn = true

        val commitCountBefore = ic.commitTextCount

        // First space: commits the word via handleSeparator
        handlers.handleSeparator(' '.code)

        // Second space: should trigger doubleSpace path
        handlers.handleSeparator(' '.code)

        assertTrue(
            "IC commitText call count should increase after double-space pipeline",
            ic.commitTextCount > commitCountBefore
        )
    }

    // ── Test 2: swapPunctuationAndSpace via handleSeparator ────────

    @Test
    fun `handleSeparator then swapPunctuationAndSpace chain`() {
        // Simulate: after a word was accepted (space committed), user types period.
        // The period triggers the swap path when TextEntryState is ACCEPTED_DEFAULT.
        // For swap, IC.getTextBeforeCursor(2) must return " ." (space + period).
        val ic = MockInputConnection(textBeforeCursor = " .")
        wireWithIc(ic)

        // Pre-set state as if a word was just accepted via space
        state.mPredicting = false
        TextEntryState.acceptedDefault("hi", "hi")

        val commitCountBefore = ic.commitTextCount

        // Period: should trigger swap via PUNCTUATION_AFTER_ACCEPTED path
        handlers.handleSeparator('.'.code)

        assertTrue(
            "IC should have been modified by swap pipeline",
            ic.commitTextCount > commitCountBefore
        )
    }

    // ── Test double ────────────────────────────────────────────────

    private class TestIcProvider(
        private val ic: InputConnection?
    ) : InputConnectionProvider {
        override val inputConnection: InputConnection? get() = ic
        override val editorInfo: EditorInfo? get() = null
        override fun sendDownUpKeyEvents(keyEventCode: Int) {}
        override fun sendKeyChar(c: Char) {}
    }
}
