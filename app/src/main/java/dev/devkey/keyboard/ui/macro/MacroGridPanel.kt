package dev.devkey.keyboard.ui.macro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.data.db.entity.MacroEntity
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions

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
@Composable
fun MacroGridPanel(
    macros: List<MacroEntity>,
    onMacroClick: (MacroEntity) -> Unit,
    onRecordClick: () -> Unit,
    onEditMacro: (MacroEntity) -> Unit,
    onDeleteMacro: (MacroEntity) -> Unit
) {
    // Phase 4.7 — structural panel_opened emit for E2E smoke tests.
    // PRIVACY: panel identifier only — NEVER log macro names or bodies.
    LaunchedEffect(Unit) {
        DevKeyLogger.ui("panel_opened", mapOf("panel" to "macros"))
    }

    var longPressedMacro by remember { mutableStateOf<MacroEntity?>(null) }
    var editDialogMacro by remember { mutableStateOf<MacroEntity?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = DevKeyThemeDimensions.macroGridMaxHeight)
            .background(DevKeyThemeColors.kbBg)
            .padding(horizontal = DevKeyThemeDimensions.macroGridPadH),
        horizontalArrangement = Arrangement.spacedBy(DevKeyThemeDimensions.macroGridSpacing),
        verticalArrangement = Arrangement.spacedBy(DevKeyThemeDimensions.macroGridSpacing)
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
                        Text("Delete", color = DevKeyThemeColors.macroRecordingRed)
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
