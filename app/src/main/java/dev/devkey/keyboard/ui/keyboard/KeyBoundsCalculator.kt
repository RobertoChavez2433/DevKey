package dev.devkey.keyboard.ui.keyboard

import dev.devkey.keyboard.ui.theme.DevKeyTheme

data class KeyBounds(
    val label: String,
    val code: Int,
    val row: Int,
    val col: Int,
    val left: Float,     // px from left edge
    val top: Float,      // px from key area top
    val right: Float,
    val bottom: Float,
    val centerX: Float,
    val centerY: Float
) {
    /** Normalized label for ADB scripts */
    val adbLabel: String get() = when {
        label == " " -> "Space"
        label == "\u2190" -> "ArrowLeft"
        label == "\u2191" -> "ArrowUp"
        label == "\u2193" -> "ArrowDown"
        label == "\u2192" -> "ArrowRight"
        label == "\u232B" -> "Backspace"
        code == -5 -> "Backspace"
        else -> label
    }
}

/**
 * Computes pixel bounds for every key in a layout.
 *
 * Mirrors Compose layout logic:
 * - KeyboardView: Column with Arrangement.spacedBy(rowGapPx), padding(horizontalPaddingPx)
 * - Rows: weighted heights based on [rowWeights] (or equal if null)
 * - Keys: weight-based horizontal sizing with Arrangement.spacedBy(keyGapPx)
 *
 * All inputs/outputs in pixels. Caller converts dp * density.
 *
 * @param rowWeights Optional list of weight floats per row. If null, rows are equal height.
 */
fun computeKeyBounds(
    layout: KeyboardLayoutData,
    keyboardWidthPx: Float,
    keyboardHeightPx: Float,
    horizontalPaddingPx: Float,
    rowGapPx: Float,
    keyGapPx: Float,
    rowWeights: List<Float>? = null
): List<KeyBounds> {
    val rowCount = layout.rows.size
    if (rowCount == 0) return emptyList()

    val totalRowGaps = rowGapPx * (rowCount - 1)
    val availableHeight = keyboardHeightPx - totalRowGaps
    val availableWidth = keyboardWidthPx - (2 * horizontalPaddingPx)

    // Compute per-row heights.
    // WHY: KeyboardView.computeRowHeights maps ALL weights and sums all of
    //      them, even if the active layout has fewer rows and the extra row
    //      height is not rendered. Symbols mode uses LayoutMode.FULL weights
    //      with a 5-row layout, so the denominator must include the unused
    //      sixth FULL weight or bottom-row coordinates land below the actual
    //      tappable key centers.
    val rowHeights = if (rowWeights != null && rowWeights.isNotEmpty()) {
        val effectiveWeights = if (rowWeights.size >= rowCount) {
            rowWeights.take(rowCount)
        } else {
            rowWeights + List(rowCount - rowWeights.size) { rowWeights.last() }
        }
        val totalWeight = if (rowWeights.size >= rowCount) {
            rowWeights.sum()
        } else {
            effectiveWeights.sum()
        }
        effectiveWeights.map { w -> availableHeight * (w / totalWeight) }
    } else {
        List(rowCount) { availableHeight / rowCount }
    }

    val result = mutableListOf<KeyBounds>()

    var rowTop = 0f
    for ((rowIndex, row) in layout.rows.withIndex()) {
        val rowHeight = rowHeights[rowIndex]
        val keyCount = row.keys.size
        if (keyCount == 0) {
            rowTop += rowHeight + rowGapPx
            continue
        }

        val totalKeyGaps = keyGapPx * (keyCount - 1)
        val totalWeight = row.keys.sumOf { it.weight.toDouble() }.toFloat()
        val weightedWidth = availableWidth - totalKeyGaps

        var keyLeft = horizontalPaddingPx
        for ((colIndex, key) in row.keys.withIndex()) {
            val keyWidth = (key.weight / totalWeight) * weightedWidth
            val keyRight = keyLeft + keyWidth
            val keyBottom = rowTop + rowHeight

            result.add(KeyBounds(
                label = key.primaryLabel,
                code = key.primaryCode,
                row = rowIndex,
                col = colIndex,
                left = keyLeft,
                top = rowTop,
                right = keyRight,
                bottom = keyBottom,
                centerX = (keyLeft + keyRight) / 2f,
                centerY = (rowTop + keyBottom) / 2f
            ))

            keyLeft = keyRight + keyGapPx
        }

        rowTop += rowHeight + rowGapPx
    }

    return result
}

/**
 * Get the row weight list for a given layout mode, matching DevKeyTheme tokens.
 */
fun getRowWeightsForMode(mode: LayoutMode): List<Float> = when (mode) {
    LayoutMode.FULL -> listOf(
        DevKeyTheme.rowUtilityWeight,   // row 0: utility (Ctrl/Alt/Tab/arrows) — top
        DevKeyTheme.rowNumberWeight,    // row 1: number
        DevKeyTheme.rowLetterWeight,    // row 2: qwerty
        DevKeyTheme.rowLetterWeight,    // row 3: home
        DevKeyTheme.rowLetterWeight,    // row 4: z
        DevKeyTheme.rowSpaceWeight      // row 5: space
    )
    LayoutMode.COMPACT, LayoutMode.COMPACT_DEV -> listOf(
        DevKeyTheme.rowCompactWeight,
        DevKeyTheme.rowCompactWeight,
        DevKeyTheme.rowCompactWeight,
        DevKeyTheme.rowCompactWeight
    )
}
