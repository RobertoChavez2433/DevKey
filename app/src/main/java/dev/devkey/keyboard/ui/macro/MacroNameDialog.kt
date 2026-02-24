package dev.devkey.keyboard.ui.macro

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Dialog for naming a newly recorded macro.
 *
 * Shows a text field for the macro name with Save and Cancel buttons.
 * Save is disabled when the name is blank.
 *
 * @param onSave Callback with the trimmed macro name.
 * @param onCancel Callback to dismiss without saving.
 */
@Composable
fun MacroNameDialog(
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Name this macro") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Macro name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}
