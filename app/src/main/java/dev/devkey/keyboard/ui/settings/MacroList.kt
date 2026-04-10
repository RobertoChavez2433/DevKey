package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.data.db.entity.MacroEntity
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions

/**
 * A single row in the macro list showing name, usage count, and edit/delete actions.
 */
@Composable
internal fun MacroListItem(
    macro: MacroEntity,
    onEdit: () -> Unit,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = macro.name,
                color = DevKeyThemeColors.keyText,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(DevKeyThemeDimensions.managerItemSubtitleSpacerH))
            Text(
                text = "Used ${macro.usageCount} times",
                color = DevKeyThemeColors.settingsDescriptionColor,
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit",
                tint = DevKeyThemeColors.iconColor
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = DevKeyThemeColors.macroRecordingRed
            )
        }
    }
}
