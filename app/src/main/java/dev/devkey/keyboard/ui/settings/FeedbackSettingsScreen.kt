package dev.devkey.keyboard.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.devkey.keyboard.data.repository.SettingsRepository

@Composable
fun FeedbackSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val vibrateOn by settingsRepository.observeBoolean(SettingsRepository.KEY_VIBRATE_ON, false).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_VIBRATE_ON, false))
    val vibrateLen by settingsRepository.observeInt(SettingsRepository.KEY_VIBRATE_LEN, 20).collectAsState(initial = settingsRepository.getInt(SettingsRepository.KEY_VIBRATE_LEN, 20))
    val soundOn by settingsRepository.observeBoolean(SettingsRepository.KEY_SOUND_ON, false).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_SOUND_ON, false))
    val clickMethod by settingsRepository.observeString(SettingsRepository.KEY_CLICK_METHOD, "0").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_CLICK_METHOD, "0"))
    val clickVolume by settingsRepository.observeFloat(SettingsRepository.KEY_CLICK_VOLUME, 0.5f).collectAsState(initial = settingsRepository.getFloat(SettingsRepository.KEY_CLICK_VOLUME, 0.5f))
    val popupOn by settingsRepository.observeBoolean(SettingsRepository.KEY_POPUP_ON, true).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_POPUP_ON, true))

    val clickMethodOptions = listOf("0" to "System default", "1" to "SoundPool", "2" to "AudioManager")

    SettingsSubScreen(title = "Feedback", onBack = onBack) {
        item(key = "vibrate_on") { ToggleSetting("Vibrate on Keypress", checked = vibrateOn) { settingsRepository.setBoolean(SettingsRepository.KEY_VIBRATE_ON, it) } }
        item(key = "vibrate_len") { SliderSetting("Vibrate Duration", vibrateLen.toFloat(), 0f, 100f, 5f, { "${it.toInt()} ms" }) { settingsRepository.setInt(SettingsRepository.KEY_VIBRATE_LEN, it.toInt()) } }
        item(key = "sound_on") { ToggleSetting("Sound on Keypress", checked = soundOn) { settingsRepository.setBoolean(SettingsRepository.KEY_SOUND_ON, it) } }
        item(key = "click_method") { DropdownSetting("Click Method", clickMethod, clickMethodOptions) { settingsRepository.setString(SettingsRepository.KEY_CLICK_METHOD, it) } }
        item(key = "click_volume") { SliderSetting("Click Volume", clickVolume, 0f, 1.0f, 0.05f, { "${(it * 100).toInt()}%" }) { settingsRepository.setFloat(SettingsRepository.KEY_CLICK_VOLUME, it) } }
        item(key = "popup_on") { ToggleSetting("Popup on Keypress", "Show enlarged key preview when pressing", popupOn) { settingsRepository.setBoolean(SettingsRepository.KEY_POPUP_ON, it) } }
    }
}
