package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.data.db.entity.LearnedWordEntity
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions

/**
 * Renders the list of custom dictionary words, or an empty-state message when the list is empty.
 *
 * @param words The words to display.
 * @param onDeleteWord Called when the user taps the delete icon for a word.
 */
@Composable
internal fun DictionaryWordList(
    words: List<LearnedWordEntity>,
    onDeleteWord: (LearnedWordEntity) -> Unit
) {
    if (words.isEmpty()) {
        Text(
            text = "No custom words added yet.",
            color = DevKeyThemeColors.settingsDescriptionColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(DevKeyThemeDimensions.settingsTilePad)
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = words,
                key = { it.id }
            ) { wordEntity ->
                DictionaryWordRow(wordEntity = wordEntity, onDelete = { onDeleteWord(wordEntity) })
            }
        }
    }
}

@Composable
private fun DictionaryWordRow(
    wordEntity: LearnedWordEntity,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = DevKeyThemeDimensions.settingsRowPadH,
                vertical = DevKeyThemeDimensions.settingsRowPadVLg
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = wordEntity.word,
            color = DevKeyThemeColors.keyText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Remove",
                tint = DevKeyThemeColors.macroRecordingRed
            )
        }
    }
    HorizontalDivider(color = DevKeyThemeColors.settingsDividerColor)
}
