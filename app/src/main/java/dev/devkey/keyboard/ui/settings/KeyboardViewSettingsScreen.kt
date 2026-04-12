package dev.devkey.keyboard.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.devkey.keyboard.data.repository.SettingsRepository

@Composable
fun KeyboardViewSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val heightPortrait by settingsRepository.observeInt(
        SettingsRepository.KEY_HEIGHT_PORTRAIT,
        SettingsRepository.DEFAULT_HEIGHT_PORTRAIT
    ).collectAsState(
        initial = settingsRepository.getInt(
            SettingsRepository.KEY_HEIGHT_PORTRAIT,
            SettingsRepository.DEFAULT_HEIGHT_PORTRAIT
        )
    )

    val heightLandscape by settingsRepository.observeInt(
        SettingsRepository.KEY_HEIGHT_LANDSCAPE,
        SettingsRepository.DEFAULT_HEIGHT_LANDSCAPE
    ).collectAsState(
        initial = settingsRepository.getInt(
            SettingsRepository.KEY_HEIGHT_LANDSCAPE,
            SettingsRepository.DEFAULT_HEIGHT_LANDSCAPE
        )
    )

    val keyboardModePortrait by settingsRepository.observeString(
        SettingsRepository.KEY_KEYBOARD_MODE_PORTRAIT, "0"
    ).collectAsState(
        initial = settingsRepository.getString(
            SettingsRepository.KEY_KEYBOARD_MODE_PORTRAIT, "0"
        )
    )

    val keyboardModeLandscape by settingsRepository.observeString(
        SettingsRepository.KEY_KEYBOARD_MODE_LANDSCAPE, "0"
    ).collectAsState(
        initial = settingsRepository.getString(
            SettingsRepository.KEY_KEYBOARD_MODE_LANDSCAPE, "0"
        )
    )

    val layoutModeValue by settingsRepository.observeString(
        SettingsRepository.KEY_LAYOUT_MODE, "full"
    ).collectAsState(
        initial = settingsRepository.getString(
            SettingsRepository.KEY_LAYOUT_MODE, "full"
        )
    )

    val suggestionsInLandscape by settingsRepository.observeBoolean(
        SettingsRepository.KEY_SUGGESTIONS_IN_LANDSCAPE, true
    ).collectAsState(
        initial = settingsRepository.getBoolean(
            SettingsRepository.KEY_SUGGESTIONS_IN_LANDSCAPE, true
        )
    )

    val hintMode by settingsRepository.observeString(
        SettingsRepository.KEY_HINT_MODE, "0"
    ).collectAsState(
        initial = settingsRepository.getString(
            SettingsRepository.KEY_HINT_MODE, "0"
        )
    )

    val labelScale by settingsRepository.observeFloat(
        SettingsRepository.KEY_LABEL_SCALE, 1.0f
    ).collectAsState(
        initial = settingsRepository.getFloat(
            SettingsRepository.KEY_LABEL_SCALE, 1.0f
        )
    )

    val candidateScale by settingsRepository.observeFloat(
        SettingsRepository.KEY_CANDIDATE_SCALE, 1.0f
    ).collectAsState(
        initial = settingsRepository.getFloat(
            SettingsRepository.KEY_CANDIDATE_SCALE, 1.0f
        )
    )

    val topRowScale by settingsRepository.observeFloat(
        SettingsRepository.KEY_TOP_ROW_SCALE, 1.0f
    ).collectAsState(
        initial = settingsRepository.getFloat(
            SettingsRepository.KEY_TOP_ROW_SCALE, 1.0f
        )
    )

    val keyboardLayout by settingsRepository.observeString(
        SettingsRepository.KEY_KEYBOARD_LAYOUT, "0"
    ).collectAsState(
        initial = settingsRepository.getString(
            SettingsRepository.KEY_KEYBOARD_LAYOUT, "0"
        )
    )

    val renderMode by settingsRepository.observeString(
        SettingsRepository.KEY_RENDER_MODE, "0"
    ).collectAsState(
        initial = settingsRepository.getString(
            SettingsRepository.KEY_RENDER_MODE, "0"
        )
    )

    val layoutModeOptions = listOf(
        "full" to "Full (6-row)",
        "compact" to "Compact (4-row)",
        "compact_dev" to "Compact Dev (4-row + long-press)"
    )
    val keyboardModeOptions = listOf(
        "0" to "Full 5-row",
        "1" to "Compact 4-row",
        "2" to "Phone-style"
    )
    val hintModeOptions = listOf(
        "0" to "Hidden",
        "1" to "Visible (dim)",
        "2" to "Visible (bright)"
    )
    val keyboardLayoutOptions = listOf(
        "0" to "QWERTY",
        "1" to "AZERTY",
        "2" to "QWERTZ",
        "3" to "Dvorak",
        "4" to "Colemak"
    )
    val renderModeOptions = listOf(
        "0" to "Auto",
        "1" to "Software",
        "2" to "Hardware"
    )

    SettingsSubScreen(title = "Keyboard View", onBack = onBack) {
        item(key = "height_portrait") {
            SliderSetting(
                "Height (Portrait)",
                heightPortrait.toFloat(),
                15f, 75f, 1f,
                { "${it.toInt()}%" }
            ) {
                settingsRepository.setInt(
                    SettingsRepository.KEY_HEIGHT_PORTRAIT,
                    it.toInt()
                )
            }
        }
        item(key = "height_landscape") {
            SliderSetting(
                "Height (Landscape)",
                heightLandscape.toFloat(),
                15f, 75f, 1f,
                { "${it.toInt()}%" }
            ) {
                settingsRepository.setInt(
                    SettingsRepository.KEY_HEIGHT_LANDSCAPE,
                    it.toInt()
                )
            }
        }
        item(key = "keyboard_mode_portrait") {
            DropdownSetting(
                "Keyboard Mode (Portrait)",
                keyboardModePortrait,
                keyboardModeOptions
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_KEYBOARD_MODE_PORTRAIT,
                    it
                )
            }
        }
        item(key = "keyboard_mode_landscape") {
            DropdownSetting(
                "Keyboard Mode (Landscape)",
                keyboardModeLandscape,
                keyboardModeOptions
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_KEYBOARD_MODE_LANDSCAPE,
                    it
                )
            }
        }
        item(key = "layout_mode") {
            DropdownSetting(
                "Layout Mode",
                layoutModeValue,
                layoutModeOptions
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_LAYOUT_MODE, it
                )
            }
        }
        item(key = "suggestions_in_landscape") {
            ToggleSetting(
                "Suggestions in Landscape",
                checked = suggestionsInLandscape
            ) {
                settingsRepository.setBoolean(
                    SettingsRepository.KEY_SUGGESTIONS_IN_LANDSCAPE,
                    it
                )
            }
        }
        item(key = "hint_mode") {
            DropdownSetting(
                "Hint Mode",
                hintMode,
                hintModeOptions
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_HINT_MODE, it
                )
            }
        }
        item(key = "label_scale") {
            SliderSetting(
                "Label Scale",
                labelScale,
                0.5f, 2.0f, 0.1f,
                { "%.1fx".format(it) }
            ) {
                settingsRepository.setFloat(
                    SettingsRepository.KEY_LABEL_SCALE, it
                )
            }
        }
        item(key = "candidate_scale") {
            SliderSetting(
                "Candidate Scale",
                candidateScale,
                0.5f, 2.0f, 0.1f,
                { "%.1fx".format(it) }
            ) {
                settingsRepository.setFloat(
                    SettingsRepository.KEY_CANDIDATE_SCALE, it
                )
            }
        }
        item(key = "top_row_scale") {
            SliderSetting(
                "Top Row Scale",
                topRowScale,
                0.5f, 2.0f, 0.1f,
                { "%.1fx".format(it) }
            ) {
                settingsRepository.setFloat(
                    SettingsRepository.KEY_TOP_ROW_SCALE, it
                )
            }
        }
        item(key = "keyboard_layout") {
            DropdownSetting(
                "Keyboard Layout",
                keyboardLayout,
                keyboardLayoutOptions
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_KEYBOARD_LAYOUT, it
                )
            }
        }
        item(key = "render_mode") {
            DropdownSetting(
                "Render Mode",
                renderMode,
                renderModeOptions
            ) {
                settingsRepository.setString(
                    SettingsRepository.KEY_RENDER_MODE, it
                )
            }
        }
    }
}
