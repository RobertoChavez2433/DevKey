package dev.devkey.keyboard.ui.macro

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.devkey.keyboard.data.db.entity.MacroEntity
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

/**
 * Individual macro grid cell composable.
 *
 * Shows the key combo in amber and macro name below. Supports tap and long-press.
 *
 * @param macro The macro to display.
 * @param onClick Callback when the cell is tapped.
 * @param onLongClick Callback when the cell is long-pressed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MacroGridCell(
    macro: MacroEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cellShape = RoundedCornerShape(DevKeyThemeDimensions.macroCellRadius)
    Column(
        modifier = Modifier
            .height(DevKeyThemeDimensions.macroGridCellHeight)
            .clip(cellShape)
            .background(DevKeyThemeColors.chipBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(DevKeyThemeDimensions.macroCellPad),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Key combo in amber
        Text(
            text = formatFromJson(macro.keySequence),
            color = DevKeyThemeColors.macroAmber,
            fontSize = DevKeyThemeTypography.macroChipTextSize,
            maxLines = 1
        )
        // Macro name in grey
        Text(
            text = macro.name,
            color = DevKeyThemeColors.timestampText,
            fontSize = DevKeyThemeTypography.clipboardTimestampSize,
            maxLines = 1
        )
    }
}

/**
 * The "+ Record" cell shown at the end of the macro grid.
 *
 * @param onClick Callback when the cell is tapped.
 */
@Composable
internal fun RecordCell(onClick: () -> Unit) {
    val cellShape = RoundedCornerShape(DevKeyThemeDimensions.macroCellRadius)
    Box(
        modifier = Modifier
            .height(DevKeyThemeDimensions.macroGridCellHeight)
            .clip(cellShape)
            .border(DevKeyThemeDimensions.dividerThickness, DevKeyThemeColors.dashedBorder, cellShape)
            .clickable { onClick() }
            .padding(DevKeyThemeDimensions.macroCellPad),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+ Record",
            color = DevKeyThemeColors.dashedBorder,
            fontSize = DevKeyThemeTypography.macroChipTextSize
        )
    }
}
