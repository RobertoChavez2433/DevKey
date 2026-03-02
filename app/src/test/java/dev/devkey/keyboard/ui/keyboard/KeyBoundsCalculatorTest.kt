package dev.devkey.keyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [computeKeyBounds].
 *
 * Emulator spec: 1080x2400 screen, 2.625 density.
 * keyAreaHeight: (2400/2.625 * 0.40 - 36) * 2.625 = 865.5px
 * Gap updated from 2dp to 4dp per theme tokens.
 */
class KeyBoundsCalculatorTest {

    // Emulator constants
    private val screenWidthPx = 1080f
    private val density = 2.625f
    private val keyAreaHeightPx = 865.5f
    private val horizontalPaddingPx = 4f * density  // 4.dp
    private val rowGapPx = 4f * density              // 4.dp (updated from 2dp)
    private val keyGapPx = 4f * density              // 4.dp (updated from 2dp)

    private val fullLayout = QwertyLayout.getLayout(LayoutMode.FULL)
    private val compactLayout = QwertyLayout.getLayout(LayoutMode.COMPACT)

    private val fullRowWeights = getRowWeightsForMode(LayoutMode.FULL)
    private val compactRowWeights = getRowWeightsForMode(LayoutMode.COMPACT)

    private fun computeFull() = computeKeyBounds(
        layout = fullLayout,
        keyboardWidthPx = screenWidthPx,
        keyboardHeightPx = keyAreaHeightPx,
        horizontalPaddingPx = horizontalPaddingPx,
        rowGapPx = rowGapPx,
        keyGapPx = keyGapPx,
        rowWeights = fullRowWeights
    )

    private fun computeCompact() = computeKeyBounds(
        layout = compactLayout,
        keyboardWidthPx = screenWidthPx,
        keyboardHeightPx = keyAreaHeightPx,
        horizontalPaddingPx = horizontalPaddingPx,
        rowGapPx = rowGapPx,
        keyGapPx = keyGapPx,
        rowWeights = compactRowWeights
    )

    @Test
    fun `returns one KeyBounds per key in full layout`() {
        val bounds = computeFull()
        val expectedCount = fullLayout.rows.sumOf { it.keys.size }
        assertEquals(expectedCount, bounds.size)
    }

    @Test
    fun `returns one KeyBounds per key in compact layout`() {
        val bounds = computeCompact()
        val expectedCount = compactLayout.rows.sumOf { it.keys.size }
        assertEquals(expectedCount, bounds.size)
    }

    @Test
    fun `all keys have positive dimensions`() {
        val bounds = computeFull()
        for (kb in bounds) {
            assertTrue("Key '${kb.label}' width must be > 0", kb.right - kb.left > 0)
            assertTrue("Key '${kb.label}' height must be > 0", kb.bottom - kb.top > 0)
        }
    }

    @Test
    fun `full layout has 6 rows of bounds`() {
        val bounds = computeFull()
        val rowIndices = bounds.map { it.row }.toSet()
        assertEquals(6, rowIndices.size)
    }

    @Test
    fun `compact layout has 4 rows of bounds`() {
        val bounds = computeCompact()
        val rowIndices = bounds.map { it.row }.toSet()
        assertEquals(4, rowIndices.size)
    }

    @Test
    fun `utility row is shorter than letter rows in full layout`() {
        val bounds = computeFull()
        // Row 5 is utility, Row 1 is QWERTY (letter)
        val utilityRowHeight = bounds.filter { it.row == 5 }.let { keys ->
            if (keys.isEmpty()) 0f else keys[0].bottom - keys[0].top
        }
        val letterRowHeight = bounds.filter { it.row == 1 }.let { keys ->
            if (keys.isEmpty()) 0f else keys[0].bottom - keys[0].top
        }
        assertTrue(
            "Utility row ($utilityRowHeight) should be shorter than letter row ($letterRowHeight)",
            utilityRowHeight < letterRowHeight
        )
    }

    @Test
    fun `space bar is widest key in space row`() {
        val bounds = computeFull()
        val spaceRowIndex = 4 // space row in full layout
        val spaceRowKeys = bounds.filter { it.row == spaceRowIndex }
        val spaceKey = spaceRowKeys.find { it.label == " " }
        requireNotNull(spaceKey) { "Space key not found in space row" }

        val spaceWidth = spaceKey.right - spaceKey.left
        for (kb in spaceRowKeys) {
            if (kb.label != " ") {
                assertTrue(
                    "Space (${spaceWidth}) should be wider than '${kb.label}' (${kb.right - kb.left})",
                    spaceWidth > kb.right - kb.left
                )
            }
        }
    }

    @Test
    fun `key centers within keyboard bounds`() {
        val bounds = computeFull()
        for (kb in bounds) {
            assertTrue(
                "Key '${kb.label}' centerX=${kb.centerX} must be in [0, $screenWidthPx]",
                kb.centerX in 0f..screenWidthPx
            )
            assertTrue(
                "Key '${kb.label}' centerY=${kb.centerY} must be in [0, $keyAreaHeightPx]",
                kb.centerY in 0f..keyAreaHeightPx
            )
        }
    }

    @Test
    fun `shift key is 1_5x width of letter key`() {
        val bounds = computeFull()
        // Find shift and a letter key in the same row (row with z)
        val zRowIndex = fullLayout.rows.indexOfFirst { row ->
            row.keys.any { it.primaryLabel == "z" }
        }
        assertTrue("z row not found", zRowIndex >= 0)

        val zRowBounds = bounds.filter { it.row == zRowIndex }
        val shiftKey = zRowBounds.find { it.label == "Shift" }
        val zKey = zRowBounds.find { it.label == "z" }
        requireNotNull(shiftKey) { "Shift key not found" }
        requireNotNull(zKey) { "z key not found" }

        val shiftWidth = shiftKey.right - shiftKey.left
        val zWidth = zKey.right - zKey.left
        val ratio = shiftWidth / zWidth
        assertEquals("Shift should be 1.5x wider than z", 1.5f, ratio, 0.01f)
    }

    @Test
    fun `rows do not overlap vertically`() {
        val bounds = computeFull()
        val rowCount = fullLayout.rows.size
        for (r in 0 until rowCount - 1) {
            val currentRowBottom = bounds.filter { it.row == r }.maxOf { it.bottom }
            val nextRowTop = bounds.filter { it.row == r + 1 }.minOf { it.top }
            assertTrue(
                "Row $r bottom ($currentRowBottom) must be <= row ${r + 1} top ($nextRowTop)",
                currentRowBottom <= nextRowTop + 0.01f
            )
        }
    }

    @Test
    fun `keys within a row do not overlap horizontally`() {
        val bounds = computeFull()
        val rowCount = fullLayout.rows.size
        for (r in 0 until rowCount) {
            val rowKeys = bounds.filter { it.row == r }.sortedBy { it.left }
            for (i in 0 until rowKeys.size - 1) {
                assertTrue(
                    "Row $r: key '${rowKeys[i].label}' right (${rowKeys[i].right}) must be <= " +
                            "key '${rowKeys[i + 1].label}' left (${rowKeys[i + 1].left})",
                    rowKeys[i].right <= rowKeys[i + 1].left + 0.01f
                )
            }
        }
    }

    @Test
    fun `adbLabel normalizes space and arrows`() {
        val bounds = computeFull()
        val spaceKey = bounds.find { it.label == " " }
        requireNotNull(spaceKey) { "Space key not found" }
        assertEquals("Space", spaceKey.adbLabel)

        val leftArrow = bounds.find { it.label == "\u2190" }
        requireNotNull(leftArrow) { "Left arrow key not found" }
        assertEquals("ArrowLeft", leftArrow.adbLabel)

        val upArrow = bounds.find { it.label == "\u2191" }
        requireNotNull(upArrow) { "Up arrow key not found" }
        assertEquals("ArrowUp", upArrow.adbLabel)

        val downArrow = bounds.find { it.label == "\u2193" }
        requireNotNull(downArrow) { "Down arrow key not found" }
        assertEquals("ArrowDown", downArrow.adbLabel)

        val rightArrow = bounds.find { it.label == "\u2192" }
        requireNotNull(rightArrow) { "Right arrow key not found" }
        assertEquals("ArrowRight", rightArrow.adbLabel)
    }

    @Test
    fun `adbLabel normalizes smart backspace`() {
        val bounds = computeFull()
        val smartKey = bounds.find { it.label == "\u232B" }
        requireNotNull(smartKey) { "Smart backspace key not found" }
        assertEquals("Backspace", smartKey.adbLabel)
    }

    @Test
    fun `empty layout returns empty list`() {
        val emptyLayout = KeyboardLayoutData(rows = emptyList())
        val bounds = computeKeyBounds(
            layout = emptyLayout,
            keyboardWidthPx = screenWidthPx,
            keyboardHeightPx = keyAreaHeightPx,
            horizontalPaddingPx = horizontalPaddingPx,
            rowGapPx = rowGapPx,
            keyGapPx = keyGapPx
        )
        assertTrue("Empty layout should return empty list", bounds.isEmpty())
    }

    @Test
    fun `bottom row total width fills available space`() {
        val bounds = computeFull()
        val spaceRowIndex = 4 // space row in full layout
        val spaceRowKeys = bounds.filter { it.row == spaceRowIndex }.sortedBy { it.left }
        val totalKeyWidth = spaceRowKeys.sumOf { (it.right - it.left).toDouble() }.toFloat()
        val totalGaps = keyGapPx * (spaceRowKeys.size - 1)
        val availableWidth = screenWidthPx - (2 * horizontalPaddingPx)
        val expectedKeyWidth = availableWidth - totalGaps

        assertEquals(
            "Space row key widths should fill available space",
            expectedKeyWidth,
            totalKeyWidth,
            1.0f  // Allow 1px tolerance for floating point
        )
    }

    @Test
    fun `equal weight rows when rowWeights is null`() {
        // Without rowWeights, all rows should have equal height
        val bounds = computeKeyBounds(
            layout = fullLayout,
            keyboardWidthPx = screenWidthPx,
            keyboardHeightPx = keyAreaHeightPx,
            horizontalPaddingPx = horizontalPaddingPx,
            rowGapPx = rowGapPx,
            keyGapPx = keyGapPx,
            rowWeights = null
        )
        val row0Height = bounds.filter { it.row == 0 }.let { it[0].bottom - it[0].top }
        val row5Height = bounds.filter { it.row == 5 }.let { it[0].bottom - it[0].top }
        assertEquals("Without weights, rows should be equal height", row0Height, row5Height, 0.01f)
    }
}
