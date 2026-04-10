package dev.devkey.keyboard.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.devkey.keyboard.data.repository.SettingsRepository

@Composable
fun VoiceInputSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val voiceModel by settingsRepository.observeString(SettingsRepository.KEY_VOICE_MODEL, "tiny").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_VOICE_MODEL, "tiny"))
    val voiceAutoStopTimeout by settingsRepository.observeInt(SettingsRepository.KEY_VOICE_AUTO_STOP_TIMEOUT, 3).collectAsState(initial = settingsRepository.getInt(SettingsRepository.KEY_VOICE_AUTO_STOP_TIMEOUT, 3))

    val voiceModelOptions = listOf("tiny" to "Tiny (fast, less accurate)", "base" to "Base (slower, more accurate)")

    SettingsSubScreen(title = "Voice Input", onBack = onBack) {
        item(key = "voice_model") { DropdownSetting("Voice Model", voiceModel, voiceModelOptions) { settingsRepository.setString(SettingsRepository.KEY_VOICE_MODEL, it) } }
        item(key = "voice_auto_stop_timeout") { SliderSetting("Auto-Stop Timeout", voiceAutoStopTimeout.toFloat(), 1f, 10f, 1f, { "${it.toInt()} sec" }) { settingsRepository.setInt(SettingsRepository.KEY_VOICE_AUTO_STOP_TIMEOUT, it.toInt()) } }
    }
}
