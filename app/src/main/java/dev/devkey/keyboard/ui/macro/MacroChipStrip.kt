package dev.devkey.keyboard.ui.macro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
 * Horizontal chip strip showing macros in the suggestion bar area.
 *
 * Displays macro chips sorted by usage count, with a collapse arrow on the left
 * and an "+ Add" chip at the end.
 *
 * @param macros List of macros to display (already sorted by usage count from DAO).
 * @param onMacroClick Callback when a macro chip is tapped.
 * @param onAddClick Callback when the "+ Add" chip is tapped.
 * @param onCollapse Callback when the collapse arrow is tapped.
 */
@Composable
fun MacroChipStrip(
    macros: List<MacroEntity>,
    onMacroClick: (MacroEntity) -> Unit,
    onAddClick: () -> Unit,
    onCollapse: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(DevKeyThemeDimensions.suggestionBarHeight)
            .background(DevKeyThemeColors.kbBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Collapse arrow
        Box(
            modifier = Modifier
                .clickable { onCollapse() }
                .padding(horizontal = DevKeyThemeDimensions.suggestionBarPadH)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\u25C0", // ◀
                color = DevKeyThemeColors.suggestionText,
                fontSize = DevKeyThemeTypography.suggestionTextSize
            )
        }

        // Macro chips in a scrollable row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(DevKeyThemeDimensions.chipStripSpacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = DevKeyThemeDimensions.suggestionBarPadH)
        ) {
            items(macros, key = { it.id }) { macro ->
                MacroChip(
                    macro = macro,
                    onClick = { onMacroClick(macro) }
                )
            }

            // "+ Add" chip
            item {
                AddChip(onClick = onAddClick)
            }
        }
    }
}

@Composable
private fun MacroChip(
    macro: MacroEntity,
    onClick: () -> Unit
) {
    val chipShape = RoundedCornerShape(DevKeyThemeDimensions.chipRadius)
    Row(
        modifier = Modifier
            .clip(chipShape)
            .background(DevKeyThemeColors.chipBg)
            .border(DevKeyThemeDimensions.dividerThickness, DevKeyThemeColors.chipBorder, chipShape)
            .clickable { onClick() }
            .padding(horizontal = DevKeyThemeDimensions.chipPadH, vertical = DevKeyThemeDimensions.chipPadV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DevKeyThemeDimensions.chipInnerSpacing)
    ) {
        Text(
            text = "\u26A1", // ⚡
            fontSize = DevKeyThemeTypography.macroChipTextSize,
            color = DevKeyThemeColors.keyText
        )
        Text(
            text = macro.name,
            fontSize = DevKeyThemeTypography.macroChipTextSize,
            color = DevKeyThemeColors.keyText
        )
    }
}

@Composable
private fun AddChip(onClick: () -> Unit) {
    val chipShape = RoundedCornerShape(DevKeyThemeDimensions.chipRadius)
    Box(
        modifier = Modifier
            .clip(chipShape)
            .border(DevKeyThemeDimensions.dividerThickness, DevKeyThemeColors.dashedBorder, chipShape)
            .clickable { onClick() }
            .padding(horizontal = DevKeyThemeDimensions.chipPadH, vertical = DevKeyThemeDimensions.chipPadV),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+ Add",
            fontSize = DevKeyThemeTypography.macroChipTextSize,
            color = DevKeyThemeColors.dashedBorder
        )
    }
}
