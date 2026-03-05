package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.devkey.keyboard.data.db.entity.CommandAppEntity
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.feature.command.CommandModeRepository
import dev.devkey.keyboard.feature.command.InputMode
import dev.devkey.keyboard.ui.theme.DevKeyTheme
import kotlinx.coroutines.launch

/**
 * Screen for managing command mode app overrides.
 * Users can add/remove per-app overrides for command vs normal mode.
 */
@Composable
fun CommandAppManagerScreen(
    repository: CommandModeRepository,
    onBack: () -> Unit
) {
    val overrides by repository.getAllOverrides().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var deletingApp by remember { mutableStateOf<CommandAppEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevKeyTheme.kbBg)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DevKeyTheme.keyBg)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = DevKeyTheme.keyText
                )
            }
            Text(
                text = "Command Mode Apps",
                color = DevKeyTheme.keyText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add app",
                    tint = DevKeyTheme.keyText
                )
            }
        }

        // Info text
        Text(
            text = "Apps listed here override automatic terminal detection.",
            color = DevKeyTheme.settingsDescriptionColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider(color = DevKeyTheme.settingsDividerColor)

        // Built-in terminals info
        Text(
            text = "Auto-detected terminal apps:",
            color = DevKeyTheme.settingsCategoryColor,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        Text(
            text = CommandModeDetector.TERMINAL_PACKAGES.joinToString(", "),
            color = DevKeyTheme.settingsDescriptionColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        HorizontalDivider(
            color = DevKeyTheme.settingsDividerColor,
            modifier = Modifier.padding(top = 8.dp)
        )

        // User overrides section
        if (overrides.isEmpty()) {
            Text(
                text = "No custom overrides configured.",
                color = DevKeyTheme.settingsDescriptionColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Text(
                text = "Custom overrides:",
                color = DevKeyTheme.settingsCategoryColor,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = overrides,
                    key = { it.packageName }
                ) { app ->
                    CommandAppListItem(
                        app = app,
                        onDelete = { deletingApp = app }
                    )
                    HorizontalDivider(color = DevKeyTheme.settingsDividerColor)
                }
            }
        }
    }

    // Add app dialog
    if (showAddDialog) {
        AddCommandAppDialog(
            onAdd = { packageName, mode ->
                coroutineScope.launch {
                    repository.setMode(packageName, mode)
                }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // Delete confirmation dialog
    deletingApp?.let { app ->
        AlertDialog(
            onDismissRequest = { deletingApp = null },
            title = { Text("Remove Override") },
            text = { Text("Remove override for ${app.packageName}? The app will revert to auto-detection.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.removeOverride(app.packageName)
                        }
                        deletingApp = null
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingApp = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CommandAppListItem(
    app: CommandAppEntity,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.packageName,
                color = DevKeyTheme.keyText,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (app.mode == "command") "Command Mode" else "Normal Mode",
                color = if (app.mode == "command") DevKeyTheme.cmdBadgeText else DevKeyTheme.settingsDescriptionColor,
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Remove",
                tint = DevKeyTheme.macroRecordingRed
            )
        }
    }
}

@Composable
private fun AddCommandAppDialog(
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
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Mode:",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { selectedMode = InputMode.COMMAND }
                    ) {
                        Text(
                            text = if (selectedMode == InputMode.COMMAND) "[Command]" else "Command",
                            color = if (selectedMode == InputMode.COMMAND)
                                DevKeyTheme.cmdBadgeText else DevKeyTheme.settingsDescriptionColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { selectedMode = InputMode.NORMAL }
                    ) {
                        Text(
                            text = if (selectedMode == InputMode.NORMAL) "[Normal]" else "Normal",
                            color = if (selectedMode == InputMode.NORMAL)
                                DevKeyTheme.keyText else DevKeyTheme.settingsDescriptionColor
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
