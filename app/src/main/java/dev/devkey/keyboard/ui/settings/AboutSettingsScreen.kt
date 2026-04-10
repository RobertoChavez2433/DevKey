package dev.devkey.keyboard.ui.settings

import androidx.compose.runtime.Composable

@Composable
fun AboutSettingsScreen(
    versionName: String,
    onBack: () -> Unit
) {
    SettingsSubScreen(title = "About", onBack = onBack) {
        item(key = "version") { ButtonSetting("Version", versionName) { /* no-op */ } }
    }
}
