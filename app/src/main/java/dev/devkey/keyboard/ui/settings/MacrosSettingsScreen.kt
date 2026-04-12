package dev.devkey.keyboard.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.devkey.keyboard.data.repository.SettingsRepository

@Composable
fun MacrosSettingsScreen(
    settingsRepository: SettingsRepository,
    onNavigateToMacroManager: () -> Unit,
    onBack: () -> Unit
) {
    val macroDisplayMode by settingsRepository
        .observeString(
            SettingsRepository.KEY_MACRO_DISPLAY_MODE, "chips"
        )
        .collectAsState(
            initial = settingsRepository.getString(
                SettingsRepository.KEY_MACRO_DISPLAY_MODE, "chips"
            )
        )
    val macroDisplayModeOptions =
        listOf("chips" to "Chips", "grid" to "Grid")

    SettingsSubScreen(title = "Macros", onBack = onBack) {
        item(key = "macro_display_mode") {
            DropdownSetting(
                "Display Mode",
                macroDisplayMode,
                macroDisplayModeOptions
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_MACRO_DISPLAY_MODE, it
                )
            }
        }
        item(key = "manage_macros") {
            ButtonSetting(
                "Manage Macros",
                "Edit or delete saved macros",
                onClick = onNavigateToMacroManager
            )
        }
    }
}
