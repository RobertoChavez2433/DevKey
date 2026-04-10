package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.data.db.entity.LearnedWordEntity
import dev.devkey.keyboard.feature.prediction.LearningEngine
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import dev.devkey.keyboard.ui.theme.DevKeyTheme
import kotlinx.coroutines.launch

/**
 * Screen for managing user-added custom dictionary words.
 * Words added here suppress autocorrect (e.g. kubectl, useState).
 */
@Composable
fun CustomDictionaryScreen(
    database: DevKeyDatabase,
    onBack: () -> Unit
) {
    val dao = remember { database.learnedWordDao() }
    val customWords by dao.getUserAddedWords().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var deletingWord by remember { mutableStateOf<LearnedWordEntity?>(null) }

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
                .padding(horizontal = DevKeyTheme.managerBarPadH, vertical = DevKeyTheme.managerBarPadV),
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
                text = "Custom Dictionary",
                color = DevKeyTheme.keyText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add word",
                    tint = DevKeyTheme.keyText
                )
            }
        }

        // Info text
        Text(
            text = "Words added here won't be autocorrected (e.g. kubectl, useState, nginx).",
            color = DevKeyTheme.settingsDescriptionColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = DevKeyTheme.managerRowPadH, vertical = DevKeyTheme.managerInfoPadV)
        )
        HorizontalDivider(color = DevKeyTheme.settingsDividerColor)

        if (customWords.isEmpty()) {
            Text(
                text = "No custom words added yet.",
                color = DevKeyTheme.settingsDescriptionColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(DevKeyTheme.settingsTilePad)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = customWords,
                    key = { it.id }
                ) { wordEntity ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DevKeyTheme.settingsRowPadH, vertical = DevKeyTheme.settingsRowPadVLg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = wordEntity.word,
                            color = DevKeyTheme.keyText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { deletingWord = wordEntity }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove",
                                tint = DevKeyTheme.macroRecordingRed
                            )
                        }
                    }
                    HorizontalDivider(color = DevKeyTheme.settingsDividerColor)
                }
            }
        }
    }

    // Add word dialog
    if (showAddDialog) {
        var newWord by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Custom Word") },
            text = {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it },
                    label = { Text("Word") },
                    placeholder = { Text("e.g. kubectl") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val word = newWord.trim()
                        if (word.isNotBlank()) {
                            coroutineScope.launch {
                                val le = SessionDependencies.learningEngine
                                if (le != null) {
                                    le.addCustomWord(word)
                                } else {
                                    // Fallback: write directly to DAO
                                    dao.insert(
                                        LearnedWordEntity(
                                            word = word, frequency = 1,
                                            isUserAdded = true
                                        )
                                    )
                                }
                            }
                        }
                        showAddDialog = false
                    },
                    enabled = newWord.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation
    deletingWord?.let { word ->
        AlertDialog(
            onDismissRequest = { deletingWord = null },
            title = { Text("Remove Word") },
            text = { Text("Remove \"${word.word}\" from your custom dictionary?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val le = SessionDependencies.learningEngine
                            if (le != null) {
                                le.removeCustomWord(word.word)
                            } else {
                                dao.delete(word)
                            }
                        }
                        deletingWord = null
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingWord = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
