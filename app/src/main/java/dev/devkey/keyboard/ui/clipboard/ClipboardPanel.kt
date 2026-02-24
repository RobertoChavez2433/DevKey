package dev.devkey.keyboard.ui.clipboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import dev.devkey.keyboard.data.db.entity.ClipboardHistoryEntity
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Clipboard history panel with search, pinning, and paste functionality.
 *
 * Displayed below the suggestion bar when clipboard mode is active.
 * Supports search filtering, pin/unpin via long-press, and clear all.
 *
 * @param entries List of clipboard entries to display.
 * @param onPaste Callback when an entry is tapped to paste.
 * @param onPin Callback to pin an entry.
 * @param onUnpin Callback to unpin an entry.
 * @param onDelete Callback to delete an entry.
 * @param onClearAll Callback to clear all clipboard history.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardPanel(
    entries: List<ClipboardHistoryEntity>,
    onPaste: (ClipboardHistoryEntity) -> Unit,
    onPin: (Long) -> Unit,
    onUnpin: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val filteredEntries = if (searchQuery.isBlank()) {
        entries
    } else {
        entries.filter { it.content.contains(searchQuery, ignoreCase = true) }
    }

    // Separate pinned and unpinned
    val pinnedEntries = filteredEntries.filter { it.isPinned }
    val unpinnedEntries = filteredEntries.filter { !it.isPinned }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = DevKeyTheme.clipboardPanelMaxHeight)
            .background(DevKeyTheme.clipboardPanelBg)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "\uD83D\uDD0D Search clipboard...",
                    color = DevKeyTheme.timestampText,
                    fontSize = DevKeyTheme.clipboardPreviewTextSize
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)
        )

        if (filteredEntries.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DevKeyTheme.clipboardEntryHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No clipboard history",
                    color = DevKeyTheme.timestampText,
                    fontSize = DevKeyTheme.clipboardPreviewTextSize
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                // Pinned entries first
                items(pinnedEntries, key = { it.id }) { entry ->
                    ClipboardEntry(
                        entry = entry,
                        onPaste = onPaste,
                        onPin = onPin,
                        onUnpin = onUnpin,
                        onDelete = onDelete
                    )
                }
                // Unpinned entries
                items(unpinnedEntries, key = { it.id }) { entry ->
                    ClipboardEntry(
                        entry = entry,
                        onPaste = onPaste,
                        onPin = onPin,
                        onUnpin = onUnpin,
                        onDelete = onDelete
                    )
                }
            }
        }

        // Clear All button
        TextButton(
            onClick = { showClearAllDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = "Clear All",
                color = DevKeyTheme.timestampText,
                fontSize = DevKeyTheme.clipboardTimestampSize
            )
        }
    }

    // Clear all confirmation dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear clipboard history") },
            text = { Text("Clear all clipboard history? Pinned items will also be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearAllDialog = false
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipboardEntry(
    entry: ClipboardHistoryEntity,
    onPaste: (ClipboardHistoryEntity) -> Unit,
    onPin: (Long) -> Unit,
    onUnpin: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DevKeyTheme.clipboardEntryHeight)
            .combinedClickable(
                onClick = { onPaste(entry) },
                onLongClick = { showContextMenu = true }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
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
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (entry.isPinned) {
                    Text(
                        text = "\uD83D\uDCCC", // 📌
                        color = DevKeyTheme.pinIcon,
                        fontSize = DevKeyTheme.clipboardPreviewTextSize
                    )
                }
                Text(
                    text = entry.content.take(60),
                    color = DevKeyTheme.keyText,
                    fontSize = DevKeyTheme.clipboardPreviewTextSize,
                    maxLines = 1
                )
            }

            // Relative timestamp
            Text(
                text = formatRelativeTime(entry.timestamp),
                color = DevKeyTheme.timestampText,
                fontSize = DevKeyTheme.clipboardTimestampSize
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
                        onUnpin(entry.id)
                        showContextMenu = false
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Pin") },
                    onClick = {
                        onPin(entry.id)
                        showContextMenu = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    onDelete(entry.id)
                    showContextMenu = false
                }
            )
        }
    }
}

/**
 * Formats a timestamp into a human-readable relative time string.
 */
private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> "${days / 7}w"
    }
}
