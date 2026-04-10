package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

@Composable
fun SettingsCategory(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = DevKeyThemeDimensions.settingsCategoryPadTop,
                bottom = DevKeyThemeDimensions.settingsCategoryPadBottom,
                start = DevKeyThemeDimensions.settingsCategoryPadH,
                end = DevKeyThemeDimensions.settingsCategoryPadH
            )
    ) {
        Text(
            text = title,
            color = DevKeyThemeColors.settingsCategoryColor,
            style = MaterialTheme.typography.titleSmall,
            fontSize = DevKeyThemeTypography.fontSettingsCategory
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = DevKeyThemeTypography.fontSettingsSubtitle,
                color = DevKeyThemeColors.keyHint,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(DevKeyThemeDimensions.settingsCategorySpacerH))
        HorizontalDivider(color = DevKeyThemeColors.settingsDividerColor, thickness = DevKeyThemeDimensions.dividerThickness)
    }
}

@Composable
fun ToggleSetting(
    title: String,
    description: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = DevKeyThemeDimensions.settingsRowPadH, vertical = DevKeyThemeDimensions.settingsRowPadVLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) DevKeyThemeColors.keyText else DevKeyThemeColors.settingsDescriptionColor,
                style = MaterialTheme.typography.bodyLarge
            )
            if (description != null) {
                Text(
                    text = description,
                    color = DevKeyThemeColors.settingsDescriptionColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(modifier = Modifier.width(DevKeyThemeDimensions.settingsTrailingSpacerW))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun ButtonSetting(
    title: String,
    description: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DevKeyThemeDimensions.settingsRowPadH, vertical = DevKeyThemeDimensions.settingsRowPadVLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = DevKeyThemeColors.keyText,
                style = MaterialTheme.typography.bodyLarge
            )
            if (description != null) {
                Text(
                    text = description,
                    color = DevKeyThemeColors.settingsDescriptionColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun TextInputSetting(
    title: String,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editValue by remember(currentValue) { mutableStateOf(currentValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = DevKeyThemeDimensions.settingsRowPadH, vertical = DevKeyThemeDimensions.settingsRowPadVLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = DevKeyThemeColors.keyText,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = currentValue,
            color = DevKeyThemeColors.settingsDescriptionColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = title) },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(editValue)
                    showDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
