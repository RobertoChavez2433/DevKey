package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.data.db.entity.CommandAppEntity
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.feature.command.CommandModeRepository
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
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
            .background(DevKeyThemeColors.kbBg)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DevKeyThemeColors.keyBg)
                .padding(horizontal = DevKeyThemeDimensions.managerBarPadH, vertical = DevKeyThemeDimensions.managerBarPadV),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = DevKeyThemeColors.keyText
                )
            }
            Text(
                text = "Command Mode Apps",
                color = DevKeyThemeColors.keyText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add app",
                    tint = DevKeyThemeColors.keyText
                )
            }
        }

        // Info text
        Text(
            text = "Apps listed here override automatic terminal detection.",
            color = DevKeyThemeColors.settingsDescriptionColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = DevKeyThemeDimensions.managerRowPadH, vertical = DevKeyThemeDimensions.managerInfoPadV)
        )
        HorizontalDivider(color = DevKeyThemeColors.settingsDividerColor)

        // Built-in terminals info
        Text(
            text = "Auto-detected terminal apps:",
            color = DevKeyThemeColors.settingsCategoryColor,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = DevKeyThemeDimensions.managerRowPadH, top = DevKeyThemeDimensions.managerSectionPadTop, bottom = DevKeyThemeDimensions.managerSectionPadBottom)
        )
        Text(
            text = CommandModeDetector.TERMINAL_PACKAGES.joinToString(", "),
            color = DevKeyThemeColors.settingsDescriptionColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = DevKeyThemeDimensions.managerRowPadH, vertical = DevKeyThemeDimensions.managerRowPadVSm)
        )
        HorizontalDivider(
            color = DevKeyThemeColors.settingsDividerColor,
            modifier = Modifier.padding(top = DevKeyThemeDimensions.managerDividerPadTop)
        )

        // User overrides section
        if (overrides.isEmpty()) {
            Text(
                text = "No custom overrides configured.",
                color = DevKeyThemeColors.settingsDescriptionColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(DevKeyThemeDimensions.settingsTilePad)
            )
        } else {
            Text(
                text = "Custom overrides:",
                color = DevKeyThemeColors.settingsCategoryColor,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = DevKeyThemeDimensions.managerRowPadH, top = DevKeyThemeDimensions.managerSectionPadTop, bottom = DevKeyThemeDimensions.managerSectionPadBottom)
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
                    HorizontalDivider(color = DevKeyThemeColors.settingsDividerColor)
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
