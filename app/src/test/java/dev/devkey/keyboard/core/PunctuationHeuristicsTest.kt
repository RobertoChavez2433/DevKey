package dev.devkey.keyboard.core

import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.feature.smarttext.SmartTextCapitalizationDecision
import dev.devkey.keyboard.feature.smarttext.SmartTextCapitalizationRequest
import dev.devkey.keyboard.feature.smarttext.SmartTextCorrection
import dev.devkey.keyboard.feature.smarttext.SmartTextCorrectionRequest
import dev.devkey.keyboard.feature.smarttext.SmartTextEngine
import dev.devkey.keyboard.feature.smarttext.SmartTextPunctuationDecision
import dev.devkey.keyboard.feature.smarttext.SmartTextPunctuationReason
import dev.devkey.keyboard.feature.smarttext.SmartTextPunctuationRequest
import dev.devkey.keyboard.feature.smarttext.SmartTextSuggestion
import dev.devkey.keyboard.feature.smarttext.SmartTextSuggestionRequest
import dev.devkey.keyboard.testutil.MockInputConnection
import dev.devkey.keyboard.testutil.resetSessionDependencies
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PunctuationHeuristicsTest {

    private lateinit var mockSettings: SettingsRepository
    private var shiftUpdated: Boolean = false

    @Before
    fun setUp() {
        mockSettings = mock()
        mockSettings.suggestedPunctuation = ".,!?"
        shiftUpdated = false
        resetSessionDependencies()
    }

    @After
    fun tearDown() {
        resetSessionDependencies()
    }

    private fun createHeuristics(ic: MockInputConnection): PunctuationHeuristics {
        val heuristics = PunctuationHeuristics(
            icProvider = { ic },
            settings = mockSettings,
            updateShiftKeyState = { shiftUpdated = true }
        )
        heuristics.sentenceSeparators = ".!?"
        return heuristics
    }

    // ── swapPunctuationAndSpace ────────────────────────────────────

    @Test
    fun `swapPunctuationAndSpace swaps period`() {
        // IC returns "a ." — space followed by period (sentence separator)
        val ic = MockInputConnection(textBeforeCursor = "a .")
        val heuristics = createHeuristics(ic)

        heuristics.swapPunctuationAndSpace()

        assertEquals("deleteSurroundingText should be called once", 1, ic.deleteSurroundingTextCount)
        assertEquals("commitText should be called once", 1, ic.commitTextCount)
    }

    @Test
    fun `swapPunctuationAndSpace no swap for non-separator`() {
        // IC returns "a x" — 'x' is not a sentence separator
        val ic = MockInputConnection(textBeforeCursor = "a x")
        val heuristics = createHeuristics(ic)

        heuristics.swapPunctuationAndSpace()

        assertEquals("deleteSurroundingText should not be called", 0, ic.deleteSurroundingTextCount)
        assertEquals("commitText should not be called", 0, ic.commitTextCount)
    }

    @Test
    fun `swapPunctuationAndSpace obeys smart text adapter decision`() {
        val ic = MockInputConnection(textBeforeCursor = "a .")
        val heuristics = createHeuristics(ic)
        SessionDependencies.smartTextEngine = object : SmartTextEngine {
            override fun correction(request: SmartTextCorrectionRequest): SmartTextCorrection? = null
            override suspend fun suggestions(
                request: SmartTextSuggestionRequest,
            ): List<SmartTextSuggestion> = emptyList()

            override fun capitalization(
                request: SmartTextCapitalizationRequest,
            ): SmartTextCapitalizationDecision = SmartTextCapitalizationDecision(
                apply = false,
                reason = dev.devkey.keyboard.feature.smarttext.SmartTextCapitalizationReason.DEFAULT_RULE,
            )

            override fun punctuation(
                request: SmartTextPunctuationRequest,
            ): SmartTextPunctuationDecision = SmartTextPunctuationDecision(
                apply = false,
                reason = SmartTextPunctuationReason.NOT_PATTERN,
            )
        }

        heuristics.swapPunctuationAndSpace()

        assertEquals("deleteSurroundingText should not be called", 0, ic.deleteSurroundingTextCount)
        assertEquals("commitText should not be called", 0, ic.commitTextCount)
    }

    // ── reswapPeriodAndSpace ───────────────────────────────────────

    @Test
    fun `reswapPeriodAndSpace reswaps`() {
        // IC returns ". ." — period, space, period (3 chars)
        val ic = MockInputConnection(textBeforeCursor = ". .")
        val heuristics = createHeuristics(ic)

        heuristics.reswapPeriodAndSpace()

        assertEquals("deleteSurroundingText should be called once", 1, ic.deleteSurroundingTextCount)
        assertEquals("commitText should be called once", 1, ic.commitTextCount)
    }

    @Test
    fun `reswapPeriodAndSpace no reswap different pattern`() {
        // IC returns "a ." — does not start with period
        val ic = MockInputConnection(textBeforeCursor = "a .")
        val heuristics = createHeuristics(ic)

        heuristics.reswapPeriodAndSpace()

        assertEquals("deleteSurroundingText should not be called", 0, ic.deleteSurroundingTextCount)
        assertEquals("commitText should not be called", 0, ic.commitTextCount)
    }

    // ── doubleSpace ────────────────────────────────────────────────

    @Test
    fun `doubleSpace converts to period-space`() {
        // IC returns "a  " — letter + 2 spaces
        val ic = MockInputConnection(textBeforeCursor = "a  ")
        val heuristics = createHeuristics(ic)

        heuristics.doubleSpace()

        assertEquals("deleteSurroundingText should be called once", 1, ic.deleteSurroundingTextCount)
        assertEquals("commitText should be called once", 1, ic.commitTextCount)
    }

    @Test
    fun `doubleSpace commits period-space replacement`() {
        val ic = MutableInputConnection("a  ")
        val heuristics = createHeuristics(ic)

        heuristics.doubleSpace()

        assertEquals("a. ", ic.committedText)
    }

    @Test
    fun `doubleSpace no-op at start of field`() {
        // IC returns "  " — only spaces, no letter/digit preceding
        val ic = MockInputConnection(textBeforeCursor = "  ")
        val heuristics = createHeuristics(ic)

        heuristics.doubleSpace()

        assertEquals("deleteSurroundingText should not be called", 0, ic.deleteSurroundingTextCount)
        assertEquals("commitText should not be called", 0, ic.commitTextCount)
    }

    @Test
    fun `doubleSpace no-op after non-alphanumeric`() {
        // IC returns ".  " — period is not a letter/digit
        val ic = MockInputConnection(textBeforeCursor = ".  ")
        val heuristics = createHeuristics(ic)

        heuristics.doubleSpace()

        assertEquals("deleteSurroundingText should not be called", 0, ic.deleteSurroundingTextCount)
        assertEquals("commitText should not be called", 0, ic.commitTextCount)
    }

    @Test
    fun `swapPunctuationAndSpace commits punctuation before space`() {
        val ic = MutableInputConnection("hello .")
        val heuristics = createHeuristics(ic)

        heuristics.swapPunctuationAndSpace()

        assertEquals("hello. ", ic.committedText)
    }

    // ── maybeRemovePreviousPeriod ──────────────────────────────────

    @Test
    fun `maybeRemovePreviousPeriod removes trailing period`() {
        // IC returns "." before cursor and text starts with "."
        val ic = MockInputConnection(textBeforeCursor = ".")
        val heuristics = createHeuristics(ic)

        heuristics.maybeRemovePreviousPeriod(".")

        assertEquals("deleteSurroundingText should be called once", 1, ic.deleteSurroundingTextCount)
    }

    // ── removeTrailingSpace ────────────────────────────────────────

    @Test
    fun `removeTrailingSpace removes space`() {
        val ic = MockInputConnection(textBeforeCursor = " ")
        val heuristics = createHeuristics(ic)

        heuristics.removeTrailingSpace()

        assertEquals("deleteSurroundingText should be called once", 1, ic.deleteSurroundingTextCount)
    }

    @Test
    fun `removeTrailingSpace no-op for non-space`() {
        val ic = MockInputConnection(textBeforeCursor = "a")
        val heuristics = createHeuristics(ic)

        heuristics.removeTrailingSpace()

        assertEquals("deleteSurroundingText should not be called", 0, ic.deleteSurroundingTextCount)
    }

    private class MutableInputConnection(initialText: String) : MockInputConnection() {
        private val content = StringBuilder(initialText)

        val committedText: String
            get() = content.toString()

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            val len = n.coerceAtMost(content.length)
            return content.subSequence(content.length - len, content.length)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            super.deleteSurroundingText(beforeLength, afterLength)
            val start = (content.length - beforeLength).coerceAtLeast(0)
            content.delete(start, content.length)
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            super.commitText(text, newCursorPosition)
            content.append(text ?: "")
            return true
        }
    }
}
