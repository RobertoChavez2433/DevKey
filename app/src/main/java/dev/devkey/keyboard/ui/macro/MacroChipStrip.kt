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
import androidx.compose.ui.unit.dp
import dev.devkey.keyboard.data.db.entity.MacroEntity
import dev.devkey.keyboard.ui.theme.DevKeyTheme

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
            .height(DevKeyTheme.suggestionBarHeight)
            .background(DevKeyTheme.kbBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Collapse arrow
        Box(
            modifier = Modifier
                .clickable { onCollapse() }
                .padding(horizontal = 8.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\u25C0", // ◀
                color = DevKeyTheme.suggestionText,
                fontSize = DevKeyTheme.suggestionTextSize
            )
        }

        // Macro chips in a scrollable row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 8.dp)
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
    val chipShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .clip(chipShape)
            .background(DevKeyTheme.chipBg)
            .border(1.dp, DevKeyTheme.chipBorder, chipShape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "\u26A1", // ⚡
            fontSize = DevKeyTheme.macroChipTextSize,
            color = DevKeyTheme.keyText
        )
        Text(
            text = macro.name,
            fontSize = DevKeyTheme.macroChipTextSize,
            color = DevKeyTheme.keyText
        )
    }
}

@Composable
private fun AddChip(onClick: () -> Unit) {
    val chipShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .clip(chipShape)
            .border(1.dp, DevKeyTheme.dashedBorder, chipShape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+ Add",
            fontSize = DevKeyTheme.macroChipTextSize,
            color = DevKeyTheme.dashedBorder
        )
    }
}
