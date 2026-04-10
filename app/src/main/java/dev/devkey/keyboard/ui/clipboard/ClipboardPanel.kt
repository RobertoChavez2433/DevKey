package dev.devkey.keyboard.ui.clipboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.data.db.entity.ClipboardHistoryEntity
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

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
@Composable
fun ClipboardPanel(
    entries: List<ClipboardHistoryEntity>,
    onPaste: (ClipboardHistoryEntity) -> Unit,
    onPin: (Long) -> Unit,
    onUnpin: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    // Phase 4.6 — structural panel_opened emit for E2E smoke tests.
    // PRIVACY: panel identifier only — NEVER log clipboard contents.
    LaunchedEffect(Unit) {
        DevKeyLogger.ui("panel_opened", mapOf("panel" to "clipboard"))
    }

    val actions = remember(onPaste, onPin, onUnpin, onDelete, onClearAll) {
        ClipboardActions(
            onPaste = onPaste,
            onPin = onPin,
            onUnpin = onUnpin,
            onDelete = onDelete,
            onClearAll = onClearAll
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = DevKeyThemeDimensions.clipboardPanelMaxHeight)
            .background(DevKeyThemeColors.clipboardPanelBg)
    ) {
        ClipboardEntryList(
            entries = entries,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            actions = actions
        )

        // Clear All button
        TextButton(
            onClick = { showClearAllDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DevKeyThemeDimensions.clipboardPadH)
        ) {
            Text(
                text = "Clear All",
                color = DevKeyThemeColors.timestampText,
                fontSize = DevKeyThemeTypography.clipboardTimestampSize
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
