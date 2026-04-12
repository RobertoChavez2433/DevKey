package dev.devkey.keyboard.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.devkey.keyboard.data.repository.SettingsRepository

@Composable
fun ActionsSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val swipeUp by settingsRepository.observeString(
        SettingsRepository.KEY_SWIPE_UP, "0"
    ).collectAsState(
        initial = settingsRepository.getString(SettingsRepository.KEY_SWIPE_UP, "0")
    )
    val swipeDown by settingsRepository.observeString(
        SettingsRepository.KEY_SWIPE_DOWN, "0"
    ).collectAsState(
        initial = settingsRepository.getString(SettingsRepository.KEY_SWIPE_DOWN, "0")
    )
    val swipeLeft by settingsRepository.observeString(
        SettingsRepository.KEY_SWIPE_LEFT, "0"
    ).collectAsState(
        initial = settingsRepository.getString(SettingsRepository.KEY_SWIPE_LEFT, "0")
    )
    val swipeRight by settingsRepository.observeString(
        SettingsRepository.KEY_SWIPE_RIGHT, "0"
    ).collectAsState(
        initial = settingsRepository.getString(SettingsRepository.KEY_SWIPE_RIGHT, "0")
    )
    val volUp by settingsRepository.observeString(
        SettingsRepository.KEY_VOL_UP, "0"
    ).collectAsState(
        initial = settingsRepository.getString(SettingsRepository.KEY_VOL_UP, "0")
    )
    val volDown by settingsRepository.observeString(
        SettingsRepository.KEY_VOL_DOWN, "0"
    ).collectAsState(
        initial = settingsRepository.getString(SettingsRepository.KEY_VOL_DOWN, "0")
    )

    val swipeOptions = listOf(
        "0" to "None", "1" to "Close keyboard",
        "2" to "Switch to voice", "3" to "Cursor left",
        "4" to "Cursor right", "5" to "Cursor up",
        "6" to "Cursor down", "7" to "Send Shift",
        "8" to "Send Tab", "9" to "Page up",
        "10" to "Page down", "11" to "Home", "12" to "End"
    )
    val volumeOptions = listOf(
        "0" to "Default (volume)", "1" to "Cursor up/down"
    )

    SettingsSubScreen(title = "Actions", onBack = onBack) {
        item(key = "swipe_up") {
            DropdownSetting("Swipe Up", swipeUp, swipeOptions) {
                settingsRepository.setString(SettingsRepository.KEY_SWIPE_UP, it)
            }
        }
        item(key = "swipe_down") {
            DropdownSetting("Swipe Down", swipeDown, swipeOptions) {
                settingsRepository.setString(SettingsRepository.KEY_SWIPE_DOWN, it)
            }
        }
        item(key = "swipe_left") {
            DropdownSetting("Swipe Left", swipeLeft, swipeOptions) {
                settingsRepository.setString(SettingsRepository.KEY_SWIPE_LEFT, it)
            }
        }
        item(key = "swipe_right") {
            DropdownSetting("Swipe Right", swipeRight, swipeOptions) {
                settingsRepository.setString(SettingsRepository.KEY_SWIPE_RIGHT, it)
            }
        }
        item(key = "vol_up") {
            DropdownSetting("Volume Up", volUp, volumeOptions) {
                settingsRepository.setString(SettingsRepository.KEY_VOL_UP, it)
            }
        }
        item(key = "vol_down") {
            DropdownSetting("Volume Down", volDown, volumeOptions) {
                settingsRepository.setString(SettingsRepository.KEY_VOL_DOWN, it)
            }
        }
    }
}
