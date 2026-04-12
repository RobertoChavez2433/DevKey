package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

@Composable
fun PredictionSettingsScreen(
    settingsRepository: SettingsRepository,
    onNavigateToCustomDictionary: () -> Unit = {},
    onBack: () -> Unit
) {
    val quickFixes by settingsRepository
        .observeBoolean(SettingsRepository.KEY_QUICK_FIXES, true)
        .collectAsState(
            initial = settingsRepository
                .getBoolean(SettingsRepository.KEY_QUICK_FIXES, true)
        )
    val showSuggestions by settingsRepository
        .observeBoolean(
            SettingsRepository.KEY_SHOW_SUGGESTIONS, true
        )
        .collectAsState(
            initial = settingsRepository.getBoolean(
                SettingsRepository.KEY_SHOW_SUGGESTIONS, true
            )
        )
    val autoComplete by settingsRepository
        .observeBoolean(
            SettingsRepository.KEY_AUTO_COMPLETE, true
        )
        .collectAsState(
            initial = settingsRepository.getBoolean(
                SettingsRepository.KEY_AUTO_COMPLETE, true
            )
        )
    val suggestedPunctuation by settingsRepository
        .observeString(
            SettingsRepository.KEY_SUGGESTED_PUNCTUATION, ".,!?"
        )
        .collectAsState(
            initial = settingsRepository.getString(
                SettingsRepository.KEY_SUGGESTED_PUNCTUATION, ".,!?"
            )
        )
    val autocorrectLevel by settingsRepository
        .observeString(
            SettingsRepository.KEY_AUTOCORRECT_LEVEL, "mild"
        )
        .collectAsState(
            initial = settingsRepository.getString(
                SettingsRepository.KEY_AUTOCORRECT_LEVEL, "mild"
            )
        )

    val autocorrectLevelOptions = listOf(
        "off" to "Off",
        "mild" to "Mild",
        "aggressive" to "Aggressive"
    )

    SettingsSubScreen(title = "Prediction & Autocorrect", onBack = onBack) {
        item(key = "prediction_subtitle") {
            Text(
                text = "Controls suggestions and autocorrect behavior.",
                fontSize = DevKeyThemeTypography.fontSettingsSubtitle,
                color = DevKeyThemeColors.keyHint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(
                    horizontal = DevKeyThemeDimensions.settingsRowPadH,
                    vertical = DevKeyThemeDimensions.settingsRowPadVSm
                )
            )
        }
        item(key = "quick_fixes") {
            ToggleSetting(
                "Quick Fixes",
                "Auto-fix common typos (e.g. i -> I)",
                quickFixes
            ) {
                settingsRepository.setBoolean(
                    SettingsRepository.KEY_QUICK_FIXES, it
                )
            }
        }
        item(key = "show_suggestions") {
            ToggleSetting(
                "Show Suggestions",
                checked = showSuggestions
            ) {
                settingsRepository.setBoolean(
                    SettingsRepository.KEY_SHOW_SUGGESTIONS, it
                )
            }
        }
        item(key = "auto_complete") {
            ToggleSetting(
                "Auto-Complete",
                "Accept suggestion on spacebar",
                autoComplete,
                enabled = showSuggestions
            ) {
                settingsRepository.setBoolean(
                    SettingsRepository.KEY_AUTO_COMPLETE, it
                )
            }
        }
        item(key = "suggested_punctuation") {
            TextInputSetting(
                "Suggested Punctuation",
                suggestedPunctuation
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_SUGGESTED_PUNCTUATION,
                    it
                )
            }
        }
        item(key = "autocorrect_level") {
            DropdownSetting(
                "Autocorrect Level",
                autocorrectLevel,
                autocorrectLevelOptions
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_AUTOCORRECT_LEVEL, it
                )
            }
        }
        item(key = "custom_dictionary") {
            ButtonSetting(
                "Custom Dictionary",
                "Add words that won't be autocorrected",
                onClick = onNavigateToCustomDictionary
            )
        }
    }
}
