package dev.devkey.keyboard.ui.macro

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.devkey.keyboard.data.db.entity.MacroEntity
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Grid panel displaying all macros in a 4-column grid.
 *
 * Each cell shows the key combo in amber and macro name below.
 * Long-press opens an edit/delete dialog.
 * Includes a "+ Record" cell at the end.
 *
 * @param macros List of macros to display.
 * @param onMacroClick Callback when a macro cell is tapped.
 * @param onRecordClick Callback when the "+ Record" cell is tapped.
 * @param onEditMacro Callback when a macro name is edited.
 * @param onDeleteMacro Callback when a macro is deleted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MacroGridPanel(
    macros: List<MacroEntity>,
    onMacroClick: (MacroEntity) -> Unit,
    onRecordClick: () -> Unit,
    onEditMacro: (MacroEntity) -> Unit,
    onDeleteMacro: (MacroEntity) -> Unit
) {
    var longPressedMacro by remember { mutableStateOf<MacroEntity?>(null) }
    var editDialogMacro by remember { mutableStateOf<MacroEntity?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = DevKeyTheme.macroGridMaxHeight)
            .background(DevKeyTheme.kbBg)
            .padding(horizontal = DevKeyTheme.macroGridPadH),
        horizontalArrangement = Arrangement.spacedBy(DevKeyTheme.macroGridSpacing),
        verticalArrangement = Arrangement.spacedBy(DevKeyTheme.macroGridSpacing)
    ) {
        items(macros) { macro ->
            MacroGridCell(
                macro = macro,
                onClick = { onMacroClick(macro) },
                onLongClick = { longPressedMacro = macro }
            )
        }

        // "+ Record" cell
        item {
            RecordCell(onClick = onRecordClick)
        }
    }

    // Long-press action dialog with Edit, Delete, and Cancel options
    longPressedMacro?.let { macro ->
        AlertDialog(
            onDismissRequest = { longPressedMacro = null },
            title = { Text(macro.name) },
            text = {
                Column {
                    TextButton(onClick = {
                        longPressedMacro = null
                        editDialogMacro = macro
                    }) {
                        Text("Edit name")
                    }
                    TextButton(onClick = {
                        onDeleteMacro(macro)
                        longPressedMacro = null
                    }) {
                        Text("Delete", color = DevKeyTheme.macroRecordingRed)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    longPressedMacro = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit name dialog
    editDialogMacro?.let { macro ->
        var editName by remember(macro.id) { mutableStateOf(macro.name) }
        AlertDialog(
            onDismissRequest = { editDialogMacro = null },
            title = { Text("Edit macro name") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEditMacro(macro.copy(name = editName.trim()))
                        editDialogMacro = null
                    },
                    enabled = editName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editDialogMacro = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MacroGridCell(
    macro: MacroEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cellShape = RoundedCornerShape(DevKeyTheme.macroCellRadius)
    Column(
        modifier = Modifier
            .height(DevKeyTheme.macroGridCellHeight)
            .clip(cellShape)
            .background(DevKeyTheme.chipBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(DevKeyTheme.macroCellPad),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Key combo in amber
        Text(
            text = formatFromJson(macro.keySequence),
            color = DevKeyTheme.macroAmber,
            fontSize = DevKeyTheme.macroChipTextSize,
            maxLines = 1
        )
        // Macro name in grey
        Text(
            text = macro.name,
            color = DevKeyTheme.timestampText,
            fontSize = DevKeyTheme.clipboardTimestampSize,
            maxLines = 1
        )
    }
}

@Composable
private fun RecordCell(onClick: () -> Unit) {
    val cellShape = RoundedCornerShape(DevKeyTheme.macroCellRadius)
    Box(
        modifier = Modifier
            .height(DevKeyTheme.macroGridCellHeight)
            .clip(cellShape)
            .border(DevKeyTheme.dividerThickness, DevKeyTheme.dashedBorder, cellShape)
            .clickable { onClick() }
            .padding(DevKeyTheme.macroCellPad),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+ Record",
            color = DevKeyTheme.dashedBorder,
            fontSize = DevKeyTheme.macroChipTextSize
        )
    }
}
