package dev.devkey.keyboard.core

import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.testutil.MockInputConnection
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
}
