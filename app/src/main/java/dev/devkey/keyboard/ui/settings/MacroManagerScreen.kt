package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.data.db.entity.MacroEntity
import dev.devkey.keyboard.feature.macro.MacroRepository
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
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
            .background(DevKeyThemeColors.kbBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DevKeyThemeColors.keyBg)
                .padding(
                    horizontal = DevKeyThemeDimensions.managerBarPadH,
                    vertical = DevKeyThemeDimensions.managerBarPadV
                ),
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
                text = "Manage Macros",
                color = DevKeyThemeColors.keyText,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (macros.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DevKeyThemeDimensions.managerFullPad),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No macros saved. Record macros from the keyboard toolbar.",
                    color = DevKeyThemeColors.settingsDescriptionColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = macros, key = { it.id }) { macro ->
                    MacroListItem(
                        macro = macro,
                        onEdit = {
                            editingMacro = macro
                            editName = macro.name
                        },
                        onDelete = { deletingMacro = macro }
                    )
                    HorizontalDivider(color = DevKeyThemeColors.settingsDividerColor)
                }
            }
        }
    }

    editingMacro?.let { macro ->
        MacroRenameDialog(
            macro = macro,
            editName = editName,
            onNameChange = { editName = it },
            onConfirm = {
                coroutineScope.launch { macroRepository.updateMacroName(macro.id, editName) }
                editingMacro = null
            },
            onDismiss = { editingMacro = null }
        )
    }

    deletingMacro?.let { macro ->
        MacroDeleteDialog(
            macro = macro,
            onConfirm = {
                coroutineScope.launch { macroRepository.deleteMacro(macro.id) }
                deletingMacro = null
            },
            onDismiss = { deletingMacro = null }
        )
    }
}
