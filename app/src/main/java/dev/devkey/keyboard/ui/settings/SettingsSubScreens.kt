package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Reusable sub-screen header with back navigation.
 */
@Composable
private fun SubScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DevKeyTheme.keyboardBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\u2190",
            color = DevKeyTheme.settingsCategoryColor,
            fontSize = 20.sp,
            modifier = Modifier
                .clickable { onBack() }
                .padding(end = 16.dp)
        )
        Text(
            text = title,
            color = DevKeyTheme.keyText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Reusable sub-screen wrapper that provides the standard Scaffold + Column + SubScreenHeader
 * + LazyColumn structure used by all settings sub-screens.
 */
@Composable
private fun SettingsSubScreen(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Scaffold(containerColor = DevKeyTheme.keyboardBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DevKeyTheme.keyboardBackground)
        ) {
            SubScreenHeader(title = title, onBack = onBack)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DevKeyTheme.keyboardBackground),
                content = content
            )
        }
    }
}

// ────────────────────────────────────────
// 1. Keyboard View
// ────────────────────────────────────────
@Composable
fun KeyboardViewSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val heightPortrait by settingsRepository.observeInt(
        SettingsRepository.KEY_HEIGHT_PORTRAIT, SettingsRepository.DEFAULT_HEIGHT_PORTRAIT
    ).collectAsState(initial = settingsRepository.getInt(SettingsRepository.KEY_HEIGHT_PORTRAIT, SettingsRepository.DEFAULT_HEIGHT_PORTRAIT))

    val heightLandscape by settingsRepository.observeInt(
        SettingsRepository.KEY_HEIGHT_LANDSCAPE, SettingsRepository.DEFAULT_HEIGHT_LANDSCAPE
    ).collectAsState(initial = settingsRepository.getInt(SettingsRepository.KEY_HEIGHT_LANDSCAPE, SettingsRepository.DEFAULT_HEIGHT_LANDSCAPE))

    val keyboardModePortrait by settingsRepository.observeString(
        SettingsRepository.KEY_KEYBOARD_MODE_PORTRAIT, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_KEYBOARD_MODE_PORTRAIT, "0"))

    val keyboardModeLandscape by settingsRepository.observeString(
        SettingsRepository.KEY_KEYBOARD_MODE_LANDSCAPE, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_KEYBOARD_MODE_LANDSCAPE, "0"))

    val layoutModeValue by settingsRepository.observeString(
        SettingsRepository.KEY_LAYOUT_MODE, "full"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_LAYOUT_MODE, "full"))

    val suggestionsInLandscape by settingsRepository.observeBoolean(
        SettingsRepository.KEY_SUGGESTIONS_IN_LANDSCAPE, true
    ).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_SUGGESTIONS_IN_LANDSCAPE, true))

    val hintMode by settingsRepository.observeString(
        SettingsRepository.KEY_HINT_MODE, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_HINT_MODE, "0"))

    val labelScale by settingsRepository.observeFloat(
        SettingsRepository.KEY_LABEL_SCALE, 1.0f
    ).collectAsState(initial = settingsRepository.getFloat(SettingsRepository.KEY_LABEL_SCALE, 1.0f))

    val candidateScale by settingsRepository.observeFloat(
        SettingsRepository.KEY_CANDIDATE_SCALE, 1.0f
    ).collectAsState(initial = settingsRepository.getFloat(SettingsRepository.KEY_CANDIDATE_SCALE, 1.0f))

    val topRowScale by settingsRepository.observeFloat(
        SettingsRepository.KEY_TOP_ROW_SCALE, 1.0f
    ).collectAsState(initial = settingsRepository.getFloat(SettingsRepository.KEY_TOP_ROW_SCALE, 1.0f))

    val keyboardLayout by settingsRepository.observeString(
        SettingsRepository.KEY_KEYBOARD_LAYOUT, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_KEYBOARD_LAYOUT, "0"))

    val renderMode by settingsRepository.observeString(
        SettingsRepository.KEY_RENDER_MODE, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_RENDER_MODE, "0"))

    val layoutModeOptions = listOf("full" to "Full (6-row)", "compact" to "Compact (4-row)", "compact_dev" to "Compact Dev (4-row + long-press)")
    val keyboardModeOptions = listOf("0" to "Full 5-row", "1" to "Compact 4-row", "2" to "Phone-style")
    val hintModeOptions = listOf("0" to "Hidden", "1" to "Visible (dim)", "2" to "Visible (bright)")
    val keyboardLayoutOptions = listOf("0" to "QWERTY", "1" to "AZERTY", "2" to "QWERTZ", "3" to "Dvorak", "4" to "Colemak")
    val renderModeOptions = listOf("0" to "Auto", "1" to "Software", "2" to "Hardware")

    SettingsSubScreen(title = "Keyboard View", onBack = onBack) {
        item(key = "height_portrait") { SliderSetting("Height (Portrait)", heightPortrait.toFloat(), 15f, 75f, 1f, { "${it.toInt()}%" }) { settingsRepository.setInt(SettingsRepository.KEY_HEIGHT_PORTRAIT, it.toInt()) } }
        item(key = "height_landscape") { SliderSetting("Height (Landscape)", heightLandscape.toFloat(), 15f, 75f, 1f, { "${it.toInt()}%" }) { settingsRepository.setInt(SettingsRepository.KEY_HEIGHT_LANDSCAPE, it.toInt()) } }
        item(key = "keyboard_mode_portrait") { DropdownSetting("Keyboard Mode (Portrait)", keyboardModePortrait, keyboardModeOptions) { settingsRepository.setString(SettingsRepository.KEY_KEYBOARD_MODE_PORTRAIT, it) } }
        item(key = "keyboard_mode_landscape") { DropdownSetting("Keyboard Mode (Landscape)", keyboardModeLandscape, keyboardModeOptions) { settingsRepository.setString(SettingsRepository.KEY_KEYBOARD_MODE_LANDSCAPE, it) } }
        item(key = "layout_mode") { DropdownSetting("Layout Mode", layoutModeValue, layoutModeOptions) { settingsRepository.setString(SettingsRepository.KEY_LAYOUT_MODE, it) } }
        item(key = "suggestions_in_landscape") { ToggleSetting("Suggestions in Landscape", checked = suggestionsInLandscape) { settingsRepository.setBoolean(SettingsRepository.KEY_SUGGESTIONS_IN_LANDSCAPE, it) } }
        item(key = "hint_mode") { DropdownSetting("Hint Mode", hintMode, hintModeOptions) { settingsRepository.setString(SettingsRepository.KEY_HINT_MODE, it) } }
        item(key = "label_scale") { SliderSetting("Label Scale", labelScale, 0.5f, 2.0f, 0.1f, { "%.1fx".format(it) }) { settingsRepository.setFloat(SettingsRepository.KEY_LABEL_SCALE, it) } }
        item(key = "candidate_scale") { SliderSetting("Candidate Scale", candidateScale, 0.5f, 2.0f, 0.1f, { "%.1fx".format(it) }) { settingsRepository.setFloat(SettingsRepository.KEY_CANDIDATE_SCALE, it) } }
        item(key = "top_row_scale") { SliderSetting("Top Row Scale", topRowScale, 0.5f, 2.0f, 0.1f, { "%.1fx".format(it) }) { settingsRepository.setFloat(SettingsRepository.KEY_TOP_ROW_SCALE, it) } }
        item(key = "keyboard_layout") { DropdownSetting("Keyboard Layout", keyboardLayout, keyboardLayoutOptions) { settingsRepository.setString(SettingsRepository.KEY_KEYBOARD_LAYOUT, it) } }
        item(key = "render_mode") { DropdownSetting("Render Mode", renderMode, renderModeOptions) { settingsRepository.setString(SettingsRepository.KEY_RENDER_MODE, it) } }
    }
}

// ────────────────────────────────────────
// 2. Key Behavior
// ────────────────────────────────────────
@Composable
fun KeyBehaviorSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val autoCap by settingsRepository.observeBoolean(SettingsRepository.KEY_AUTO_CAP, true).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_AUTO_CAP, true))
    val capsLock by settingsRepository.observeBoolean(SettingsRepository.KEY_CAPS_LOCK, true).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_CAPS_LOCK, true))
    val shiftLockModifiers by settingsRepository.observeBoolean(SettingsRepository.KEY_SHIFT_LOCK_MODIFIERS, false).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_SHIFT_LOCK_MODIFIERS, false))
    val ctrlAOverride by settingsRepository.observeString(SettingsRepository.KEY_CTRL_A_OVERRIDE, "0").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_CTRL_A_OVERRIDE, "0"))
    val chordingCtrl by settingsRepository.observeString(SettingsRepository.KEY_CHORDING_CTRL, "0").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_CHORDING_CTRL, "0"))
    val chordingAlt by settingsRepository.observeString(SettingsRepository.KEY_CHORDING_ALT, "0").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_CHORDING_ALT, "0"))
    val chordingMeta by settingsRepository.observeString(SettingsRepository.KEY_CHORDING_META, "0").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_CHORDING_META, "0"))
    val slideKeys by settingsRepository.observeString(SettingsRepository.KEY_SLIDE_KEYS, "0").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_SLIDE_KEYS, "0"))

    val ctrlAOverrideOptions = listOf("0" to "Default (Select All)", "1" to "Home (beginning of line)")
    val chordingOptions = listOf("0" to "Disabled", "1" to "Enabled")
    val slideKeysOptions = listOf("0" to "Disabled", "1" to "Enabled")

    SettingsSubScreen(title = "Key Behavior", onBack = onBack) {
        item(key = "auto_cap") { ToggleSetting("Auto Capitalization", checked = autoCap) { settingsRepository.setBoolean(SettingsRepository.KEY_AUTO_CAP, it) } }
        item(key = "caps_lock") { ToggleSetting("Caps Lock", "Double-tap Shift for Caps Lock", capsLock) { settingsRepository.setBoolean(SettingsRepository.KEY_CAPS_LOCK, it) } }
        item(key = "shift_lock_modifiers") { ToggleSetting("Shift Lock Modifiers", "Shift also locks Ctrl/Alt/Meta", shiftLockModifiers) { settingsRepository.setBoolean(SettingsRepository.KEY_SHIFT_LOCK_MODIFIERS, it) } }
        item(key = "ctrl_a_override") { DropdownSetting("Ctrl+A Override", ctrlAOverride, ctrlAOverrideOptions) { settingsRepository.setString(SettingsRepository.KEY_CTRL_A_OVERRIDE, it) } }
        item(key = "chording_ctrl") { DropdownSetting("Chording Ctrl", chordingCtrl, chordingOptions) { settingsRepository.setString(SettingsRepository.KEY_CHORDING_CTRL, it) } }
        item(key = "chording_alt") { DropdownSetting("Chording Alt", chordingAlt, chordingOptions) { settingsRepository.setString(SettingsRepository.KEY_CHORDING_ALT, it) } }
        item(key = "chording_meta") { DropdownSetting("Chording Meta", chordingMeta, chordingOptions) { settingsRepository.setString(SettingsRepository.KEY_CHORDING_META, it) } }
        item(key = "slide_keys") { DropdownSetting("Slide Keys", slideKeys, slideKeysOptions) { settingsRepository.setString(SettingsRepository.KEY_SLIDE_KEYS, it) } }
    }
}

// ────────────────────────────────────────
// 3. Actions
// ────────────────────────────────────────
@Composable
fun ActionsSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val swipeUp by settingsRepository.observeString(SettingsRepository.KEY_SWIPE_UP, "0").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_SWIPE_UP, "0"))
    val swipeDown by settingsRepository.observeString(SettingsRepository.KEY_SWIPE_DOWN, "0").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_SWIPE_DOWN, "0"))
    val swipeLeft by settingsRepository.observeString(SettingsRepository.KEY_SWIPE_LEFT, "0").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_SWIPE_LEFT, "0"))
    val swipeRight by settingsRepository.observeString(SettingsRepository.KEY_SWIPE_RIGHT, "0").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_SWIPE_RIGHT, "0"))
    val volUp by settingsRepository.observeString(SettingsRepository.KEY_VOL_UP, "0").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_VOL_UP, "0"))
    val volDown by settingsRepository.observeString(SettingsRepository.KEY_VOL_DOWN, "0").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_VOL_DOWN, "0"))

    val swipeOptions = listOf("0" to "None", "1" to "Close keyboard", "2" to "Switch to voice", "3" to "Cursor left", "4" to "Cursor right", "5" to "Cursor up", "6" to "Cursor down", "7" to "Send Shift", "8" to "Send Tab", "9" to "Page up", "10" to "Page down", "11" to "Home", "12" to "End")
    val volumeOptions = listOf("0" to "Default (volume)", "1" to "Cursor up/down")

    SettingsSubScreen(title = "Actions", onBack = onBack) {
        item(key = "swipe_up") { DropdownSetting("Swipe Up", swipeUp, swipeOptions) { settingsRepository.setString(SettingsRepository.KEY_SWIPE_UP, it) } }
        item(key = "swipe_down") { DropdownSetting("Swipe Down", swipeDown, swipeOptions) { settingsRepository.setString(SettingsRepository.KEY_SWIPE_DOWN, it) } }
        item(key = "swipe_left") { DropdownSetting("Swipe Left", swipeLeft, swipeOptions) { settingsRepository.setString(SettingsRepository.KEY_SWIPE_LEFT, it) } }
        item(key = "swipe_right") { DropdownSetting("Swipe Right", swipeRight, swipeOptions) { settingsRepository.setString(SettingsRepository.KEY_SWIPE_RIGHT, it) } }
        item(key = "vol_up") { DropdownSetting("Volume Up", volUp, volumeOptions) { settingsRepository.setString(SettingsRepository.KEY_VOL_UP, it) } }
        item(key = "vol_down") { DropdownSetting("Volume Down", volDown, volumeOptions) { settingsRepository.setString(SettingsRepository.KEY_VOL_DOWN, it) } }
    }
}

// ────────────────────────────────────────
// 4. Feedback
// ────────────────────────────────────────
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

// ────────────────────────────────────────
// 5. Prediction & Autocorrect
// ────────────────────────────────────────
@Composable
fun PredictionSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val quickFixes by settingsRepository.observeBoolean(SettingsRepository.KEY_QUICK_FIXES, true).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_QUICK_FIXES, true))
    val showSuggestions by settingsRepository.observeBoolean(SettingsRepository.KEY_SHOW_SUGGESTIONS, true).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_SHOW_SUGGESTIONS, true))
    val autoComplete by settingsRepository.observeBoolean(SettingsRepository.KEY_AUTO_COMPLETE, true).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_AUTO_COMPLETE, true))
    val suggestedPunctuation by settingsRepository.observeString(SettingsRepository.KEY_SUGGESTED_PUNCTUATION, ".,!?").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_SUGGESTED_PUNCTUATION, ".,!?"))
    val autocorrectLevel by settingsRepository.observeString(SettingsRepository.KEY_AUTOCORRECT_LEVEL, "mild").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_AUTOCORRECT_LEVEL, "mild"))

    val autocorrectLevelOptions = listOf("off" to "Off", "mild" to "Mild", "aggressive" to "Aggressive")

    SettingsSubScreen(title = "Prediction & Autocorrect", onBack = onBack) {
        item(key = "prediction_subtitle") {
            // Subtitle preserved from Phase 5 (BUG-04)
            Text(
                text = "Controls the legacy suggestion engine. Suggestion bar is not shown in current UI.",
                fontSize = 11.sp,
                color = DevKeyTheme.keyHint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item(key = "quick_fixes") { ToggleSetting("Quick Fixes", "Auto-fix common typos (e.g. i -> I)", quickFixes) { settingsRepository.setBoolean(SettingsRepository.KEY_QUICK_FIXES, it) } }
        item(key = "show_suggestions") { ToggleSetting("Show Suggestions", checked = showSuggestions) { settingsRepository.setBoolean(SettingsRepository.KEY_SHOW_SUGGESTIONS, it) } }
        item(key = "auto_complete") { ToggleSetting("Auto-Complete", "Accept suggestion on spacebar", autoComplete, enabled = showSuggestions) { settingsRepository.setBoolean(SettingsRepository.KEY_AUTO_COMPLETE, it) } }
        item(key = "suggested_punctuation") { TextInputSetting("Suggested Punctuation", suggestedPunctuation) { settingsRepository.setString(SettingsRepository.KEY_SUGGESTED_PUNCTUATION, it) } }
        item(key = "autocorrect_level") { DropdownSetting("Autocorrect Level", autocorrectLevel, autocorrectLevelOptions) { settingsRepository.setString(SettingsRepository.KEY_AUTOCORRECT_LEVEL, it) } }
    }
}

// ────────────────────────────────────────
// 6. Macros
// ────────────────────────────────────────
@Composable
fun MacrosSettingsScreen(
    settingsRepository: SettingsRepository,
    onNavigateToMacroManager: () -> Unit,
    onBack: () -> Unit
) {
    val macroDisplayMode by settingsRepository.observeString(SettingsRepository.KEY_MACRO_DISPLAY_MODE, "chips").collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_MACRO_DISPLAY_MODE, "chips"))
    val macroDisplayModeOptions = listOf("chips" to "Chips", "grid" to "Grid")

    SettingsSubScreen(title = "Macros", onBack = onBack) {
        item(key = "macro_display_mode") { DropdownSetting("Display Mode", macroDisplayMode, macroDisplayModeOptions) { settingsRepository.setString(SettingsRepository.KEY_MACRO_DISPLAY_MODE, it) } }
        item(key = "manage_macros") { ButtonSetting("Manage Macros", "Edit or delete saved macros", onClick = onNavigateToMacroManager) }
    }
}

// ────────────────────────────────────────
// 7. Voice Input
// ────────────────────────────────────────
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

// ────────────────────────────────────────
// 8. Command Mode
// ────────────────────────────────────────
@Composable
fun CommandModeSettingsScreen(
    settingsRepository: SettingsRepository,
    onNavigateToCommandApps: () -> Unit,
    onBack: () -> Unit
) {
    val commandAutoDetect by settingsRepository.observeBoolean(SettingsRepository.KEY_COMMAND_AUTO_DETECT, true).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_COMMAND_AUTO_DETECT, true))

    SettingsSubScreen(title = "Command Mode", onBack = onBack) {
        item(key = "command_auto_detect") { ToggleSetting("Auto-Detect Terminals", "Automatically enable command mode in terminal apps", commandAutoDetect) { settingsRepository.setBoolean(SettingsRepository.KEY_COMMAND_AUTO_DETECT, it) } }
        item(key = "manage_pinned_apps") { ButtonSetting("Manage Pinned Apps", "Override auto-detection for specific apps", onClick = onNavigateToCommandApps) }
    }
}

// ────────────────────────────────────────
// 9. Backup
// ────────────────────────────────────────
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

// ────────────────────────────────────────
// 10. About
// ────────────────────────────────────────
@Composable
fun AboutSettingsScreen(
    versionName: String,
    onBack: () -> Unit
) {
    SettingsSubScreen(title = "About", onBack = onBack) {
        item(key = "version") { ButtonSetting("Version", versionName) { /* no-op */ } }
    }
}
