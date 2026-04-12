package dev.devkey.keyboard.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.devkey.keyboard.data.repository.SettingsRepository

@Composable
fun CommandModeSettingsScreen(
    settingsRepository: SettingsRepository,
    onNavigateToCommandApps: () -> Unit,
    onBack: () -> Unit
) {
    val commandAutoDetect by settingsRepository.observeBoolean(
        SettingsRepository.KEY_COMMAND_AUTO_DETECT, true
    ).collectAsState(
        initial = settingsRepository.getBoolean(
            SettingsRepository.KEY_COMMAND_AUTO_DETECT, true
        )
    )

    SettingsSubScreen(title = "Command Mode", onBack = onBack) {
        item(key = "command_auto_detect") {
            ToggleSetting(
                "Auto-Detect Terminals",
                "Automatically enable command mode in terminal apps",
                commandAutoDetect
            ) {
                settingsRepository.setBoolean(
                    SettingsRepository.KEY_COMMAND_AUTO_DETECT, it
                )
            }
        }
        item(key = "manage_pinned_apps") {
            ButtonSetting(
                "Manage Pinned Apps",
                "Override auto-detection for specific apps",
                onClick = onNavigateToCommandApps
            )
        }
    }
}
