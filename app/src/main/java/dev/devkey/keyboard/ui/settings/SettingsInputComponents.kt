package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

@Composable
fun SliderSetting(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float? = null,
    displayFormat: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    var localValue by remember(value) { mutableFloatStateOf(value) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DevKeyThemeDimensions.settingsRowPadH, vertical = DevKeyThemeDimensions.settingsRowPadVSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = DevKeyThemeColors.keyText,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = displayFormat(localValue),
                color = DevKeyThemeColors.settingsDescriptionColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        val steps = if (step != null && step > 0f) {
            ((max - min) / step).toInt() - 1
        } else {
            0
        }
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValueChange(localValue) },
            valueRange = min..max,
            steps = steps
        )
    }
}

@Composable
fun DropdownSetting(
    title: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentLabel = options.find { it.first == currentValue }?.second ?: currentValue

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
            text = currentLabel,
            color = DevKeyThemeColors.settingsDescriptionColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = title) },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = value == currentValue,
                                    onClick = {
                                        onSelected(value)
                                        showDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = DevKeyThemeDimensions.settingsRowPadVSm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = value == currentValue,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(DevKeyThemeDimensions.settingsTrailingSpacerW))
                            Text(text = label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

