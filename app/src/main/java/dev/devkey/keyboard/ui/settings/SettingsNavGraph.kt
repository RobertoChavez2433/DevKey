package dev.devkey.keyboard.ui.settings

import androidx.compose.runtime.Composable
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.data.export.DevKeyBackup
import dev.devkey.keyboard.data.export.ImportManager
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.feature.command.CommandModeRepository
import dev.devkey.keyboard.feature.macro.MacroRepository

enum class SettingsNav {
    MAIN,
    KEYBOARD_VIEW,
    KEY_BEHAVIOR,
    ACTIONS,
    FEEDBACK,
    PREDICTION,
    MACROS,
    VOICE_INPUT,
    COMMAND_MODE,
    BACKUP,
    ABOUT,
    MACRO_MANAGER,
    COMMAND_APPS,
    CUSTOM_DICTIONARY
}

@Composable
internal fun SettingsNavGraph(
    currentNav: SettingsNav,
    onNavigate: (SettingsNav) -> Unit,
    settingsRepository: SettingsRepository,
    macroRepository: MacroRepository,
    commandModeRepository: CommandModeRepository,
    database: DevKeyDatabase,
    versionName: String,
    showConflictDialog: Boolean,
    pendingBackup: DevKeyBackup?,
    onStartExport: () -> Unit,
    onStartImport: () -> Unit,
    onConflictStrategy: (ImportManager.ConflictStrategy) -> Unit,
    onConflictDismiss: () -> Unit,
) {
    when (currentNav) {
        SettingsNav.MAIN -> {
            SettingsCategoryScreen(
                onNavigate = onNavigate
            )
        }

        SettingsNav.KEYBOARD_VIEW -> {
            KeyboardViewSettingsScreen(
                settingsRepository = settingsRepository,
                onBack = { onNavigate(SettingsNav.MAIN) }
            )
        }

        SettingsNav.KEY_BEHAVIOR -> {
            KeyBehaviorSettingsScreen(
                settingsRepository = settingsRepository,
                onBack = { onNavigate(SettingsNav.MAIN) }
            )
        }

        SettingsNav.ACTIONS -> {
            ActionsSettingsScreen(
                settingsRepository = settingsRepository,
                onBack = { onNavigate(SettingsNav.MAIN) }
            )
        }

        SettingsNav.FEEDBACK -> {
            FeedbackSettingsScreen(
                settingsRepository = settingsRepository,
                onBack = { onNavigate(SettingsNav.MAIN) }
            )
        }

        SettingsNav.PREDICTION -> {
            PredictionSettingsScreen(
                settingsRepository = settingsRepository,
                onNavigateToCustomDictionary = { onNavigate(SettingsNav.CUSTOM_DICTIONARY) },
                onBack = { onNavigate(SettingsNav.MAIN) }
            )
        }

        SettingsNav.MACROS -> {
            MacrosSettingsScreen(
                settingsRepository = settingsRepository,
                onNavigateToMacroManager = { onNavigate(SettingsNav.MACRO_MANAGER) },
                onBack = { onNavigate(SettingsNav.MAIN) }
            )
        }

        SettingsNav.VOICE_INPUT -> {
            VoiceInputSettingsScreen(
                settingsRepository = settingsRepository,
                onBack = { onNavigate(SettingsNav.MAIN) }
            )
        }

        SettingsNav.COMMAND_MODE -> {
            CommandModeSettingsScreen(
                settingsRepository = settingsRepository,
                onNavigateToCommandApps = { onNavigate(SettingsNav.COMMAND_APPS) },
                onBack = { onNavigate(SettingsNav.MAIN) }
            )
        }

        SettingsNav.BACKUP -> {
            BackupSettingsScreen(
                onExport = onStartExport,
                onImport = onStartImport,
                onBack = { onNavigate(SettingsNav.MAIN) }
            )

            if (showConflictDialog) {
                ImportConflictDialog(
                    onStrategy = onConflictStrategy,
                    onDismiss = onConflictDismiss
                )
            }
        }

        SettingsNav.ABOUT -> {
            AboutSettingsScreen(
                versionName = versionName,
                onBack = { onNavigate(SettingsNav.MAIN) }
            )
        }

        SettingsNav.MACRO_MANAGER -> {
            MacroManagerScreen(
                macroRepository = macroRepository,
                onBack = { onNavigate(SettingsNav.MACROS) }
            )
        }

        SettingsNav.COMMAND_APPS -> {
            CommandAppManagerScreen(
                repository = commandModeRepository,
                onBack = { onNavigate(SettingsNav.COMMAND_MODE) }
            )
        }

        SettingsNav.CUSTOM_DICTIONARY -> {
            CustomDictionaryScreen(
                database = database,
                onBack = { onNavigate(SettingsNav.PREDICTION) }
            )
        }
    }
}
