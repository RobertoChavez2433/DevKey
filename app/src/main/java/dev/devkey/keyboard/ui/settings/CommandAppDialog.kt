package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import dev.devkey.keyboard.feature.command.InputMode
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions

@Composable
internal fun AddCommandAppDialog(
    onAdd: (String, InputMode) -> Unit,
    onDismiss: () -> Unit
) {
    var packageName by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(InputMode.COMMAND) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add App Override") },
        text = {
            Column {
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package name") },
                    placeholder = { Text("com.example.app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(DevKeyThemeDimensions.managerDialogFieldSpacerH))
                Text(
                    text = "Mode:",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(DevKeyThemeDimensions.managerDialogLabelSpacerH))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { selectedMode = InputMode.COMMAND }
                    ) {
                        Text(
                            text = if (selectedMode == InputMode.COMMAND) "[Command]" else "Command",
                            color = if (selectedMode == InputMode.COMMAND)
                                DevKeyThemeColors.cmdBadgeText else DevKeyThemeColors.settingsDescriptionColor
                        )
                    }
                    Spacer(modifier = Modifier.width(DevKeyThemeDimensions.managerDialogButtonSpacerW))
                    TextButton(
                        onClick = { selectedMode = InputMode.NORMAL }
                    ) {
                        Text(
                            text = if (selectedMode == InputMode.NORMAL) "[Normal]" else "Normal",
                            color = if (selectedMode == InputMode.NORMAL)
                                DevKeyThemeColors.keyText else DevKeyThemeColors.settingsDescriptionColor
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (packageName.isNotBlank()) onAdd(packageName.trim(), selectedMode) },
                enabled = packageName.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
