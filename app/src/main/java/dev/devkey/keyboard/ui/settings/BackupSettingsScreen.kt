package dev.devkey.keyboard.ui.settings

import androidx.compose.runtime.Composable

@Composable
fun BackupSettingsScreen(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    SettingsSubScreen(title = "Backup", onBack = onBack) {
        item(key = "export_data") { ButtonSetting("Export Data", "Export macros, learned words, and settings to a JSON file", onClick = onExport) }
        item(key = "import_data") { ButtonSetting("Import Data", "Import data from a backup file", onClick = onImport) }
    }
}
