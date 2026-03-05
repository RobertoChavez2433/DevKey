package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import dev.devkey.keyboard.data.db.entity.MacroEntity
import dev.devkey.keyboard.feature.macro.MacroRepository
import dev.devkey.keyboard.ui.theme.DevKeyTheme
import kotlinx.coroutines.launch

/**
 * Macro management screen for editing and deleting saved macros.
 */
@Composable
fun MacroManagerScreen(
    macroRepository: MacroRepository,
    onBack: () -> Unit
) {
    val macros by macroRepository.getAllMacros().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var editingMacro by remember { mutableStateOf<MacroEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var deletingMacro by remember { mutableStateOf<MacroEntity?>(null) }

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
                text = "Manage Macros",
                color = DevKeyTheme.keyText,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (macros.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No macros saved. Record macros from the keyboard toolbar.",
                    color = DevKeyTheme.settingsDescriptionColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = macros,
                    key = { it.id }
                ) { macro ->
                    MacroListItem(
                        macro = macro,
                        onEdit = {
                            editingMacro = macro
                            editName = macro.name
                        },
                        onDelete = {
                            deletingMacro = macro
                        }
                    )
                    HorizontalDivider(color = DevKeyTheme.settingsDividerColor)
                }
            }
        }
    }

    // Edit name dialog
    editingMacro?.let { macro ->
        AlertDialog(
            onDismissRequest = { editingMacro = null },
            title = { Text("Rename Macro") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    singleLine = true,
                    label = { Text("Macro name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            macroRepository.updateMacroName(macro.id, editName)
                        }
                        editingMacro = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMacro = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    deletingMacro?.let { macro ->
        AlertDialog(
            onDismissRequest = { deletingMacro = null },
            title = { Text("Delete Macro") },
            text = { Text("Delete \"${macro.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            macroRepository.deleteMacro(macro.id)
                        }
                        deletingMacro = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMacro = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MacroListItem(
    macro: MacroEntity,
    onEdit: () -> Unit,
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
                text = macro.name,
                color = DevKeyTheme.keyText,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Used ${macro.usageCount} times",
                color = DevKeyTheme.settingsDescriptionColor,
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit",
                tint = DevKeyTheme.iconColor
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = DevKeyTheme.macroRecordingRed
            )
        }
    }
}
