package dev.devkey.keyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.ui.theme.DevKeyTheme
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions

/**
 * Composable that renders the full keyboard grid.
 *
 * Uses dynamic height based on screen size (percentage of screen height minus toolbar).
 * Each row is sized according to weight ratios from DevKeyTheme tokens.
 *
 * @param layout The keyboard layout data containing all rows.
 * @param layoutMode The active layout mode (used for row weight calculation).
 * @param modifierState The modifier state manager.
 * @param onKeyAction Callback when a key action fires.
 * @param onKeyPress Callback when a key is pressed.
 * @param onKeyRelease Callback when a key is released.
 */
@Composable
fun KeyboardView(
    layout: KeyboardLayoutData,
    layoutMode: LayoutMode = LayoutMode.FULL,
    modifierState: ModifierStateManager,
    onKeyAction: (Int) -> Unit,
    onKeyPress: (Int) -> Unit,
    onKeyRelease: (Int) -> Unit,
    ctrlHeld: Boolean = false,
    heightPercent: Float = 0.40f,
    showHints: Boolean = false,
    hintBright: Boolean = false
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val rawHeight = (screenHeightDp * heightPercent).dp - DevKeyThemeDimensions.toolbarHeight
    val keyAreaHeight = rawHeight.coerceAtLeast(DevKeyThemeDimensions.keyAreaMinHeight)

    // Compute per-row heights based on weight ratios
    val rowHeights = remember(layout, layoutMode, keyAreaHeight) {
        computeRowHeights(layout, layoutMode, keyAreaHeight)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(keyAreaHeight)
            .background(DevKeyThemeColors.kbBg)
            .padding(horizontal = DevKeyThemeDimensions.kbPadH),
        verticalArrangement = Arrangement.spacedBy(DevKeyThemeDimensions.keyGap)
    ) {
        for ((index, row) in layout.rows.withIndex()) {
            val rowHeight = rowHeights.getOrElse(index) { DevKeyThemeDimensions.rowHeightFallback }
            KeyRow(
                row = row,
                modifierState = modifierState,
                onKeyAction = onKeyAction,
                onKeyPress = onKeyPress,
                onKeyRelease = onKeyRelease,
                ctrlHeld = ctrlHeld,
                showHints = showHints,
                hintBright = hintBright,
                rowHeight = rowHeight,
                modifier = Modifier
            )
        }
    }
}

/**
 * Compute row heights using theme weight tokens.
 *
 * For FULL mode (6 rows): number, letter, letter, letter, space, utility
 * For COMPACT/COMPACT_DEV (4 rows): equal weight rows
 */
private fun computeRowHeights(
    layout: KeyboardLayoutData,
    layoutMode: LayoutMode,
    totalHeight: Dp
): List<Dp> {
    val rowCount = layout.rows.size
    if (rowCount == 0) return emptyList()

    val totalGap = DevKeyThemeDimensions.keyGap * (rowCount - 1)
    val availableHeight = totalHeight - totalGap

    val weights = when (layoutMode) {
        LayoutMode.FULL -> listOf(
            DevKeyTheme.rowUtilityWeight,   // row 0: utility (Ctrl/Alt/Tab/arrows)
            DevKeyTheme.rowNumberWeight,    // row 1: number
            DevKeyTheme.rowLetterWeight,    // row 2: qwerty
            DevKeyTheme.rowLetterWeight,    // row 3: home
            DevKeyTheme.rowLetterWeight,    // row 4: z
            DevKeyTheme.rowSpaceWeight      // row 5: space
        )
        LayoutMode.COMPACT, LayoutMode.COMPACT_DEV -> List(rowCount) {
            DevKeyTheme.rowCompactWeight
        }
    }

    val totalWeight = weights.sum()

    return weights.map { weight ->
        availableHeight * (weight / totalWeight)
    }
}
