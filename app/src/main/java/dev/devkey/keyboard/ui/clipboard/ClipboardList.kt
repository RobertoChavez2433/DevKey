package dev.devkey.keyboard.ui.clipboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.data.db.entity.ClipboardHistoryEntity
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

/**
 * Renders the search bar, empty state, and the pinned/unpinned entry list.
 *
 * @param entries  All clipboard entries (already filtered by the caller if needed).
 * @param searchQuery Current search text.
 * @param onSearchQueryChange Called when the user edits the search field.
 * @param actions  Clipboard action callbacks forwarded to each entry row.
 */
@Composable
internal fun ColumnScope.ClipboardEntryList(
    entries: List<ClipboardHistoryEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    actions: ClipboardActions
) {
    val filteredEntries = if (searchQuery.isBlank()) {
        entries
    } else {
        entries.filter { it.content.contains(searchQuery, ignoreCase = true) }
    }

    val pinnedEntries = filteredEntries.filter { it.isPinned }
    val unpinnedEntries = filteredEntries.filter { !it.isPinned }

    // Search bar
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = {
            Text(
                text = "\uD83D\uDD0D Search clipboard...",
                color = DevKeyThemeColors.timestampText,
                fontSize = DevKeyThemeTypography.clipboardPreviewTextSize
            )
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = DevKeyThemeDimensions.clipboardPadH,
                vertical = DevKeyThemeDimensions.clipboardEntryPadV
            ),
        shape = RoundedCornerShape(DevKeyThemeDimensions.clipboardSearchRadius)
    )

    if (filteredEntries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DevKeyThemeDimensions.clipboardEntryHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No clipboard history",
                color = DevKeyThemeColors.timestampText,
                fontSize = DevKeyThemeTypography.clipboardPreviewTextSize
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            items(pinnedEntries, key = { it.id }) { entry ->
                ClipboardEntry(entry = entry, actions = actions)
            }
            items(unpinnedEntries, key = { it.id }) { entry ->
                ClipboardEntry(entry = entry, actions = actions)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipboardEntry(
    entry: ClipboardHistoryEntity,
    actions: ClipboardActions
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DevKeyThemeDimensions.clipboardEntryHeight)
            .combinedClickable(
                onClick = { actions.onPaste(entry) },
                onLongClick = { showContextMenu = true }
            )
            .padding(
                horizontal = DevKeyThemeDimensions.clipboardPadH,
                vertical = DevKeyThemeDimensions.clipboardEntryPadV
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Content preview with optional pin icon
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DevKeyThemeDimensions.clipboardEntrySpacing)
            ) {
                if (entry.isPinned) {
                    Text(
                        text = "\uD83D\uDCCC", // 📌
                        color = DevKeyThemeColors.pinIcon,
                        fontSize = DevKeyThemeTypography.clipboardPreviewTextSize
                    )
                }
                Text(
                    text = entry.content.take(60),
                    color = DevKeyThemeColors.keyText,
                    fontSize = DevKeyThemeTypography.clipboardPreviewTextSize,
                    maxLines = 1
                )
            }

            // Relative timestamp
            Text(
                text = formatRelativeTime(entry.timestamp),
                color = DevKeyThemeColors.timestampText,
                fontSize = DevKeyThemeTypography.clipboardTimestampSize
            )
        }

        // Context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            if (entry.isPinned) {
                DropdownMenuItem(
                    text = { Text("Unpin") },
                    onClick = {
                        actions.onUnpin(entry.id)
                        showContextMenu = false
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Pin") },
                    onClick = {
                        actions.onPin(entry.id)
                        showContextMenu = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    actions.onDelete(entry.id)
                    showContextMenu = false
                }
            )
        }
    }
}
