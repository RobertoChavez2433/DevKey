package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.data.db.entity.MacroEntity

/**
 * Dialog for renaming an existing macro.
 */
@Composable
internal fun MacroRenameDialog(
    macro: MacroEntity,
    editName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Macro") },
        text = {
            OutlinedTextField(
                value = editName,
                onValueChange = onNameChange,
                singleLine = true,
                label = { Text("Macro name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for confirming deletion of a macro.
 */
@Composable
internal fun MacroDeleteDialog(
    macro: MacroEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Macro") },
        text = { Text("Delete \"${macro.name}\"? This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
