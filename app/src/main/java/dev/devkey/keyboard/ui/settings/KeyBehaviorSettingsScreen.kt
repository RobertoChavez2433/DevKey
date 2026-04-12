package dev.devkey.keyboard.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.devkey.keyboard.data.repository.SettingsRepository

@Composable
fun KeyBehaviorSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val autoCap by settingsRepository
        .observeBoolean(SettingsRepository.KEY_AUTO_CAP, true)
        .collectAsState(
            initial = settingsRepository
                .getBoolean(SettingsRepository.KEY_AUTO_CAP, true)
        )
    val capsLock by settingsRepository
        .observeBoolean(SettingsRepository.KEY_CAPS_LOCK, true)
        .collectAsState(
            initial = settingsRepository
                .getBoolean(SettingsRepository.KEY_CAPS_LOCK, true)
        )
    val shiftLockModifiers by settingsRepository
        .observeBoolean(
            SettingsRepository.KEY_SHIFT_LOCK_MODIFIERS, false
        )
        .collectAsState(
            initial = settingsRepository.getBoolean(
                SettingsRepository.KEY_SHIFT_LOCK_MODIFIERS, false
            )
        )
    val ctrlAOverride by settingsRepository
        .observeString(SettingsRepository.KEY_CTRL_A_OVERRIDE, "0")
        .collectAsState(
            initial = settingsRepository
                .getString(SettingsRepository.KEY_CTRL_A_OVERRIDE, "0")
        )
    val chordingCtrl by settingsRepository
        .observeString(SettingsRepository.KEY_CHORDING_CTRL, "0")
        .collectAsState(
            initial = settingsRepository
                .getString(SettingsRepository.KEY_CHORDING_CTRL, "0")
        )
    val chordingAlt by settingsRepository
        .observeString(SettingsRepository.KEY_CHORDING_ALT, "0")
        .collectAsState(
            initial = settingsRepository
                .getString(SettingsRepository.KEY_CHORDING_ALT, "0")
        )
    val chordingMeta by settingsRepository
        .observeString(SettingsRepository.KEY_CHORDING_META, "0")
        .collectAsState(
            initial = settingsRepository
                .getString(SettingsRepository.KEY_CHORDING_META, "0")
        )
    val slideKeys by settingsRepository
        .observeString(SettingsRepository.KEY_SLIDE_KEYS, "0")
        .collectAsState(
            initial = settingsRepository
                .getString(SettingsRepository.KEY_SLIDE_KEYS, "0")
        )

    val ctrlAOverrideOptions = listOf(
        "0" to "Default (Select All)",
        "1" to "Home (beginning of line)"
    )
    val chordingOptions = listOf("0" to "Disabled", "1" to "Enabled")
    val slideKeysOptions = listOf("0" to "Disabled", "1" to "Enabled")

    SettingsSubScreen(title = "Key Behavior", onBack = onBack) {
        item(key = "auto_cap") {
            ToggleSetting(
                "Auto Capitalization",
                checked = autoCap
            ) {
                settingsRepository.setBoolean(
                    SettingsRepository.KEY_AUTO_CAP, it
                )
            }
        }
        item(key = "caps_lock") {
            ToggleSetting(
                "Caps Lock",
                "Double-tap Shift for Caps Lock",
                capsLock
            ) {
                settingsRepository.setBoolean(
                    SettingsRepository.KEY_CAPS_LOCK, it
                )
            }
        }
        item(key = "shift_lock_modifiers") {
            ToggleSetting(
                "Shift Lock Modifiers",
                "Shift also locks Ctrl/Alt/Meta",
                shiftLockModifiers
            ) {
                settingsRepository.setBoolean(
                    SettingsRepository.KEY_SHIFT_LOCK_MODIFIERS, it
                )
            }
        }
        item(key = "ctrl_a_override") {
            DropdownSetting(
                "Ctrl+A Override",
                ctrlAOverride,
                ctrlAOverrideOptions
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_CTRL_A_OVERRIDE, it
                )
            }
        }
        item(key = "chording_ctrl") {
            DropdownSetting(
                "Chording Ctrl",
                chordingCtrl,
                chordingOptions
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_CHORDING_CTRL, it
                )
            }
        }
        item(key = "chording_alt") {
            DropdownSetting(
                "Chording Alt",
                chordingAlt,
                chordingOptions
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_CHORDING_ALT, it
                )
            }
        }
        item(key = "chording_meta") {
            DropdownSetting(
                "Chording Meta",
                chordingMeta,
                chordingOptions
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_CHORDING_META, it
                )
            }
        }
        item(key = "slide_keys") {
            DropdownSetting(
                "Slide Keys",
                slideKeys,
                slideKeysOptions
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_SLIDE_KEYS, it
                )
            }
        }
    }
}
