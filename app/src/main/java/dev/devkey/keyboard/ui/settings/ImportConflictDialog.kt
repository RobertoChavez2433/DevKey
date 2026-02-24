package dev.devkey.keyboard.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.devkey.keyboard.data.export.ImportManager

@Composable
fun ImportConflictDialog(
    onStrategy: (ImportManager.ConflictStrategy) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Data") },
        text = { Text("How should conflicts be handled?") },
        confirmButton = {
            TextButton(onClick = { onStrategy(ImportManager.ConflictStrategy.REPLACE) }) {
                Text("Replace All")
            }
        },
        dismissButton = {
            TextButton(onClick = { onStrategy(ImportManager.ConflictStrategy.MERGE) }) {
                Text("Merge")
            }
        }
    )
}
