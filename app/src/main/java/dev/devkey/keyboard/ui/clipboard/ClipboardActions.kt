package dev.devkey.keyboard.ui.clipboard

import dev.devkey.keyboard.data.db.entity.ClipboardHistoryEntity

/**
 * Bundles all clipboard action callbacks passed through the panel.
 *
 * @param onPaste  Called when the user taps an entry to paste it.
 * @param onPin    Called to pin an entry by its id.
 * @param onUnpin  Called to unpin an entry by its id.
 * @param onDelete Called to delete an entry by its id.
 * @param onClearAll Called to clear all clipboard history.
 */
data class ClipboardActions(
    val onPaste: (ClipboardHistoryEntity) -> Unit,
    val onPin: (Long) -> Unit,
    val onUnpin: (Long) -> Unit,
    val onDelete: (Long) -> Unit,
    val onClearAll: () -> Unit
)

/**
 * Formats a timestamp into a human-readable relative time string.
 */
internal fun formatRelativeTime(timestamp: Long): String {
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
