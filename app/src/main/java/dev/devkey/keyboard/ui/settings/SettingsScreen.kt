package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Main settings screen with all categories.
 * Reads/writes all settings via SettingsRepository (SharedPreferences wrapper).
 */
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    versionName: String,
    onNavigateToMacroManager: () -> Unit,
    onNavigateToCommandApps: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    // ── Keyboard View settings ──
    val heightPortrait by settingsRepository.observeInt(
        SettingsRepository.KEY_HEIGHT_PORTRAIT, 50
    ).collectAsState(initial = settingsRepository.getInt(SettingsRepository.KEY_HEIGHT_PORTRAIT, 50))

    val heightLandscape by settingsRepository.observeInt(
        SettingsRepository.KEY_HEIGHT_LANDSCAPE, 40
    ).collectAsState(initial = settingsRepository.getInt(SettingsRepository.KEY_HEIGHT_LANDSCAPE, 40))

    val keyboardModePortrait by settingsRepository.observeString(
        SettingsRepository.KEY_KEYBOARD_MODE_PORTRAIT, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_KEYBOARD_MODE_PORTRAIT, "0"))

    val keyboardModeLandscape by settingsRepository.observeString(
        SettingsRepository.KEY_KEYBOARD_MODE_LANDSCAPE, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_KEYBOARD_MODE_LANDSCAPE, "0"))

    val compactMode by settingsRepository.observeBoolean(
        SettingsRepository.KEY_COMPACT_MODE, false
    ).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_COMPACT_MODE, false))

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

    // ── Key Behavior settings ──
    val autoCap by settingsRepository.observeBoolean(
        SettingsRepository.KEY_AUTO_CAP, true
    ).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_AUTO_CAP, true))

    val capsLock by settingsRepository.observeBoolean(
        SettingsRepository.KEY_CAPS_LOCK, true
    ).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_CAPS_LOCK, true))

    val shiftLockModifiers by settingsRepository.observeBoolean(
        SettingsRepository.KEY_SHIFT_LOCK_MODIFIERS, false
    ).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_SHIFT_LOCK_MODIFIERS, false))

    val ctrlAOverride by settingsRepository.observeString(
        SettingsRepository.KEY_CTRL_A_OVERRIDE, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_CTRL_A_OVERRIDE, "0"))

    val chordingCtrl by settingsRepository.observeString(
        SettingsRepository.KEY_CHORDING_CTRL, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_CHORDING_CTRL, "0"))

    val chordingAlt by settingsRepository.observeString(
        SettingsRepository.KEY_CHORDING_ALT, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_CHORDING_ALT, "0"))

    val chordingMeta by settingsRepository.observeString(
        SettingsRepository.KEY_CHORDING_META, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_CHORDING_META, "0"))

    val slideKeys by settingsRepository.observeString(
        SettingsRepository.KEY_SLIDE_KEYS, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_SLIDE_KEYS, "0"))

    // ── Actions settings ──
    val swipeUp by settingsRepository.observeString(
        SettingsRepository.KEY_SWIPE_UP, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_SWIPE_UP, "0"))

    val swipeDown by settingsRepository.observeString(
        SettingsRepository.KEY_SWIPE_DOWN, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_SWIPE_DOWN, "0"))

    val swipeLeft by settingsRepository.observeString(
        SettingsRepository.KEY_SWIPE_LEFT, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_SWIPE_LEFT, "0"))

    val swipeRight by settingsRepository.observeString(
        SettingsRepository.KEY_SWIPE_RIGHT, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_SWIPE_RIGHT, "0"))

    val volUp by settingsRepository.observeString(
        SettingsRepository.KEY_VOL_UP, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_VOL_UP, "0"))

    val volDown by settingsRepository.observeString(
        SettingsRepository.KEY_VOL_DOWN, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_VOL_DOWN, "0"))

    // ── Feedback settings ──
    val vibrateOn by settingsRepository.observeBoolean(
        SettingsRepository.KEY_VIBRATE_ON, false
    ).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_VIBRATE_ON, false))

    val vibrateLen by settingsRepository.observeInt(
        SettingsRepository.KEY_VIBRATE_LEN, 20
    ).collectAsState(initial = settingsRepository.getInt(SettingsRepository.KEY_VIBRATE_LEN, 20))

    val soundOn by settingsRepository.observeBoolean(
        SettingsRepository.KEY_SOUND_ON, false
    ).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_SOUND_ON, false))

    val clickMethod by settingsRepository.observeString(
        SettingsRepository.KEY_CLICK_METHOD, "0"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_CLICK_METHOD, "0"))

    val clickVolume by settingsRepository.observeFloat(
        SettingsRepository.KEY_CLICK_VOLUME, 0.5f
    ).collectAsState(initial = settingsRepository.getFloat(SettingsRepository.KEY_CLICK_VOLUME, 0.5f))

    val popupOn by settingsRepository.observeBoolean(
        SettingsRepository.KEY_POPUP_ON, true
    ).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_POPUP_ON, true))

    // ── Prediction & Autocorrect settings ──
    val quickFixes by settingsRepository.observeBoolean(
        SettingsRepository.KEY_QUICK_FIXES, true
    ).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_QUICK_FIXES, true))

    val showSuggestions by settingsRepository.observeBoolean(
        SettingsRepository.KEY_SHOW_SUGGESTIONS, true
    ).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_SHOW_SUGGESTIONS, true))

    val autoComplete by settingsRepository.observeBoolean(
        SettingsRepository.KEY_AUTO_COMPLETE, true
    ).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_AUTO_COMPLETE, true))

    val suggestedPunctuation by settingsRepository.observeString(
        SettingsRepository.KEY_SUGGESTED_PUNCTUATION, ".,!?"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_SUGGESTED_PUNCTUATION, ".,!?"))

    val autocorrectLevel by settingsRepository.observeString(
        SettingsRepository.KEY_AUTOCORRECT_LEVEL, "mild"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_AUTOCORRECT_LEVEL, "mild"))

    // ── Macros settings ──
    val macroDisplayMode by settingsRepository.observeString(
        SettingsRepository.KEY_MACRO_DISPLAY_MODE, "chips"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_MACRO_DISPLAY_MODE, "chips"))

    // ── Voice Input settings ──
    val voiceModel by settingsRepository.observeString(
        SettingsRepository.KEY_VOICE_MODEL, "tiny"
    ).collectAsState(initial = settingsRepository.getString(SettingsRepository.KEY_VOICE_MODEL, "tiny"))

    val voiceAutoStopTimeout by settingsRepository.observeInt(
        SettingsRepository.KEY_VOICE_AUTO_STOP_TIMEOUT, 3
    ).collectAsState(initial = settingsRepository.getInt(SettingsRepository.KEY_VOICE_AUTO_STOP_TIMEOUT, 3))

    // ── Command Mode settings ──
    val commandAutoDetect by settingsRepository.observeBoolean(
        SettingsRepository.KEY_COMMAND_AUTO_DETECT, true
    ).collectAsState(initial = settingsRepository.getBoolean(SettingsRepository.KEY_COMMAND_AUTO_DETECT, true))

    // Dropdown options
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

    val ctrlAOverrideOptions = listOf(
        "0" to "Default (Select All)",
        "1" to "Home (beginning of line)"
    )

    val chordingOptions = listOf(
        "0" to "Disabled",
        "1" to "Enabled"
    )

    val slideKeysOptions = listOf(
        "0" to "Disabled",
        "1" to "Enabled"
    )

    val swipeOptions = listOf(
        "0" to "None",
        "1" to "Close keyboard",
        "2" to "Switch to voice",
        "3" to "Cursor left",
        "4" to "Cursor right",
        "5" to "Cursor up",
        "6" to "Cursor down",
        "7" to "Send Shift",
        "8" to "Send Tab",
        "9" to "Page up",
        "10" to "Page down",
        "11" to "Home",
        "12" to "End"
    )

    val volumeOptions = listOf(
        "0" to "Default (volume)",
        "1" to "Cursor up/down"
    )

    val clickMethodOptions = listOf(
        "0" to "System default",
        "1" to "SoundPool",
        "2" to "AudioManager"
    )

    val autocorrectLevelOptions = listOf(
        "off" to "Off",
        "mild" to "Mild",
        "aggressive" to "Aggressive"
    )

    val macroDisplayModeOptions = listOf(
        "chips" to "Chips",
        "grid" to "Grid"
    )

    val voiceModelOptions = listOf(
        "tiny" to "Tiny (fast, less accurate)",
        "base" to "Base (slower, more accurate)"
    )

    Scaffold(
        containerColor = DevKeyTheme.keyboardBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DevKeyTheme.keyboardBackground)
        ) {
            // ────────────────────────────────────────
            // 1. Keyboard View
            // ────────────────────────────────────────
            item(key = "cat_keyboard_view") {
                SettingsCategory(title = "Keyboard View")
            }
            item(key = "height_portrait") {
                SliderSetting(
                    title = "Height (Portrait)",
                    value = heightPortrait.toFloat(),
                    min = 15f,
                    max = 75f,
                    step = 1f,
                    displayFormat = { "${it.toInt()}%" },
                    onValueChange = { settingsRepository.setInt(SettingsRepository.KEY_HEIGHT_PORTRAIT, it.toInt()) }
                )
            }
            item(key = "height_landscape") {
                SliderSetting(
                    title = "Height (Landscape)",
                    value = heightLandscape.toFloat(),
                    min = 15f,
                    max = 75f,
                    step = 1f,
                    displayFormat = { "${it.toInt()}%" },
                    onValueChange = { settingsRepository.setInt(SettingsRepository.KEY_HEIGHT_LANDSCAPE, it.toInt()) }
                )
            }
            item(key = "mode_portrait") {
                DropdownSetting(
                    title = "Keyboard Mode (Portrait)",
                    currentValue = keyboardModePortrait,
                    options = keyboardModeOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_KEYBOARD_MODE_PORTRAIT, it) }
                )
            }
            item(key = "mode_landscape") {
                DropdownSetting(
                    title = "Keyboard Mode (Landscape)",
                    currentValue = keyboardModeLandscape,
                    options = keyboardModeOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_KEYBOARD_MODE_LANDSCAPE, it) }
                )
            }
            item(key = "compact_mode") {
                ToggleSetting(
                    title = "Compact Mode",
                    description = "Hide number row, remap long-press to digits",
                    checked = compactMode,
                    onCheckedChange = { settingsRepository.setBoolean(SettingsRepository.KEY_COMPACT_MODE, it) }
                )
            }
            item(key = "suggestions_landscape") {
                ToggleSetting(
                    title = "Suggestions in Landscape",
                    checked = suggestionsInLandscape,
                    onCheckedChange = { settingsRepository.setBoolean(SettingsRepository.KEY_SUGGESTIONS_IN_LANDSCAPE, it) }
                )
            }
            item(key = "hint_mode") {
                DropdownSetting(
                    title = "Hint Mode",
                    currentValue = hintMode,
                    options = hintModeOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_HINT_MODE, it) }
                )
            }
            item(key = "label_scale") {
                SliderSetting(
                    title = "Label Scale",
                    value = labelScale,
                    min = 0.5f,
                    max = 2.0f,
                    step = 0.1f,
                    displayFormat = { "%.1fx".format(it) },
                    onValueChange = { settingsRepository.setFloat(SettingsRepository.KEY_LABEL_SCALE, it) }
                )
            }
            item(key = "candidate_scale") {
                SliderSetting(
                    title = "Candidate Scale",
                    value = candidateScale,
                    min = 0.5f,
                    max = 2.0f,
                    step = 0.1f,
                    displayFormat = { "%.1fx".format(it) },
                    onValueChange = { settingsRepository.setFloat(SettingsRepository.KEY_CANDIDATE_SCALE, it) }
                )
            }
            item(key = "top_row_scale") {
                SliderSetting(
                    title = "Top Row Scale",
                    value = topRowScale,
                    min = 0.5f,
                    max = 2.0f,
                    step = 0.1f,
                    displayFormat = { "%.1fx".format(it) },
                    onValueChange = { settingsRepository.setFloat(SettingsRepository.KEY_TOP_ROW_SCALE, it) }
                )
            }
            item(key = "keyboard_layout") {
                DropdownSetting(
                    title = "Keyboard Layout",
                    currentValue = keyboardLayout,
                    options = keyboardLayoutOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_KEYBOARD_LAYOUT, it) }
                )
            }
            item(key = "render_mode") {
                DropdownSetting(
                    title = "Render Mode",
                    currentValue = renderMode,
                    options = renderModeOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_RENDER_MODE, it) }
                )
            }

            // ────────────────────────────────────────
            // 2. Key Behavior
            // ────────────────────────────────────────
            item(key = "cat_key_behavior") {
                SettingsCategory(title = "Key Behavior")
            }
            item(key = "auto_cap") {
                ToggleSetting(
                    title = "Auto Capitalization",
                    checked = autoCap,
                    onCheckedChange = { settingsRepository.setBoolean(SettingsRepository.KEY_AUTO_CAP, it) }
                )
            }
            item(key = "caps_lock") {
                ToggleSetting(
                    title = "Caps Lock",
                    description = "Double-tap Shift for Caps Lock",
                    checked = capsLock,
                    onCheckedChange = { settingsRepository.setBoolean(SettingsRepository.KEY_CAPS_LOCK, it) }
                )
            }
            item(key = "shift_lock_modifiers") {
                ToggleSetting(
                    title = "Shift Lock Modifiers",
                    description = "Shift also locks Ctrl/Alt/Meta",
                    checked = shiftLockModifiers,
                    onCheckedChange = { settingsRepository.setBoolean(SettingsRepository.KEY_SHIFT_LOCK_MODIFIERS, it) }
                )
            }
            item(key = "ctrl_a_override") {
                DropdownSetting(
                    title = "Ctrl+A Override",
                    currentValue = ctrlAOverride,
                    options = ctrlAOverrideOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_CTRL_A_OVERRIDE, it) }
                )
            }
            item(key = "chording_ctrl") {
                DropdownSetting(
                    title = "Chording Ctrl",
                    currentValue = chordingCtrl,
                    options = chordingOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_CHORDING_CTRL, it) }
                )
            }
            item(key = "chording_alt") {
                DropdownSetting(
                    title = "Chording Alt",
                    currentValue = chordingAlt,
                    options = chordingOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_CHORDING_ALT, it) }
                )
            }
            item(key = "chording_meta") {
                DropdownSetting(
                    title = "Chording Meta",
                    currentValue = chordingMeta,
                    options = chordingOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_CHORDING_META, it) }
                )
            }
            item(key = "slide_keys") {
                DropdownSetting(
                    title = "Slide Keys",
                    currentValue = slideKeys,
                    options = slideKeysOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_SLIDE_KEYS, it) }
                )
            }

            // ────────────────────────────────────────
            // 3. Actions
            // ────────────────────────────────────────
            item(key = "cat_actions") {
                SettingsCategory(title = "Actions")
            }
            item(key = "swipe_up") {
                DropdownSetting(
                    title = "Swipe Up",
                    currentValue = swipeUp,
                    options = swipeOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_SWIPE_UP, it) }
                )
            }
            item(key = "swipe_down") {
                DropdownSetting(
                    title = "Swipe Down",
                    currentValue = swipeDown,
                    options = swipeOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_SWIPE_DOWN, it) }
                )
            }
            item(key = "swipe_left") {
                DropdownSetting(
                    title = "Swipe Left",
                    currentValue = swipeLeft,
                    options = swipeOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_SWIPE_LEFT, it) }
                )
            }
            item(key = "swipe_right") {
                DropdownSetting(
                    title = "Swipe Right",
                    currentValue = swipeRight,
                    options = swipeOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_SWIPE_RIGHT, it) }
                )
            }
            item(key = "vol_up") {
                DropdownSetting(
                    title = "Volume Up",
                    currentValue = volUp,
                    options = volumeOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_VOL_UP, it) }
                )
            }
            item(key = "vol_down") {
                DropdownSetting(
                    title = "Volume Down",
                    currentValue = volDown,
                    options = volumeOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_VOL_DOWN, it) }
                )
            }

            // ────────────────────────────────────────
            // 4. Feedback
            // ────────────────────────────────────────
            item(key = "cat_feedback") {
                SettingsCategory(title = "Feedback")
            }
            item(key = "vibrate_on") {
                ToggleSetting(
                    title = "Vibrate on Keypress",
                    checked = vibrateOn,
                    onCheckedChange = { settingsRepository.setBoolean(SettingsRepository.KEY_VIBRATE_ON, it) }
                )
            }
            item(key = "vibrate_len") {
                SliderSetting(
                    title = "Vibrate Duration",
                    value = vibrateLen.toFloat(),
                    min = 0f,
                    max = 100f,
                    step = 5f,
                    displayFormat = { "${it.toInt()} ms" },
                    onValueChange = { settingsRepository.setInt(SettingsRepository.KEY_VIBRATE_LEN, it.toInt()) }
                )
            }
            item(key = "sound_on") {
                ToggleSetting(
                    title = "Sound on Keypress",
                    checked = soundOn,
                    onCheckedChange = { settingsRepository.setBoolean(SettingsRepository.KEY_SOUND_ON, it) }
                )
            }
            item(key = "click_method") {
                DropdownSetting(
                    title = "Click Method",
                    currentValue = clickMethod,
                    options = clickMethodOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_CLICK_METHOD, it) }
                )
            }
            item(key = "click_volume") {
                SliderSetting(
                    title = "Click Volume",
                    value = clickVolume,
                    min = 0f,
                    max = 1.0f,
                    step = 0.05f,
                    displayFormat = { "${(it * 100).toInt()}%" },
                    onValueChange = { settingsRepository.setFloat(SettingsRepository.KEY_CLICK_VOLUME, it) }
                )
            }
            item(key = "popup_on") {
                ToggleSetting(
                    title = "Popup on Keypress",
                    description = "Show enlarged key preview when pressing",
                    checked = popupOn,
                    onCheckedChange = { settingsRepository.setBoolean(SettingsRepository.KEY_POPUP_ON, it) }
                )
            }

            // ────────────────────────────────────────
            // 5. Prediction & Autocorrect
            // ────────────────────────────────────────
            item(key = "cat_prediction") {
                SettingsCategory(title = "Prediction & Autocorrect")
            }
            item(key = "quick_fixes") {
                ToggleSetting(
                    title = "Quick Fixes",
                    description = "Auto-fix common typos (e.g. i -> I)",
                    checked = quickFixes,
                    onCheckedChange = { settingsRepository.setBoolean(SettingsRepository.KEY_QUICK_FIXES, it) }
                )
            }
            item(key = "show_suggestions") {
                ToggleSetting(
                    title = "Show Suggestions",
                    checked = showSuggestions,
                    onCheckedChange = { settingsRepository.setBoolean(SettingsRepository.KEY_SHOW_SUGGESTIONS, it) }
                )
            }
            item(key = "auto_complete") {
                ToggleSetting(
                    title = "Auto-Complete",
                    description = "Accept suggestion on spacebar",
                    checked = autoComplete,
                    enabled = showSuggestions,
                    onCheckedChange = { settingsRepository.setBoolean(SettingsRepository.KEY_AUTO_COMPLETE, it) }
                )
            }
            item(key = "suggested_punctuation") {
                TextInputSetting(
                    title = "Suggested Punctuation",
                    currentValue = suggestedPunctuation,
                    onValueChange = { settingsRepository.setString(SettingsRepository.KEY_SUGGESTED_PUNCTUATION, it) }
                )
            }
            item(key = "autocorrect_level") {
                DropdownSetting(
                    title = "Autocorrect Level",
                    currentValue = autocorrectLevel,
                    options = autocorrectLevelOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_AUTOCORRECT_LEVEL, it) }
                )
            }

            // ────────────────────────────────────────
            // 6. Macros
            // ────────────────────────────────────────
            item(key = "cat_macros") {
                SettingsCategory(title = "Macros")
            }
            item(key = "macro_display_mode") {
                DropdownSetting(
                    title = "Display Mode",
                    currentValue = macroDisplayMode,
                    options = macroDisplayModeOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_MACRO_DISPLAY_MODE, it) }
                )
            }
            item(key = "manage_macros") {
                ButtonSetting(
                    title = "Manage Macros",
                    description = "Edit or delete saved macros",
                    onClick = onNavigateToMacroManager
                )
            }

            // ────────────────────────────────────────
            // 7. Voice Input
            // ────────────────────────────────────────
            item(key = "cat_voice") {
                SettingsCategory(title = "Voice Input")
            }
            item(key = "voice_model") {
                DropdownSetting(
                    title = "Voice Model",
                    currentValue = voiceModel,
                    options = voiceModelOptions,
                    onSelected = { settingsRepository.setString(SettingsRepository.KEY_VOICE_MODEL, it) }
                )
            }
            item(key = "voice_auto_stop") {
                SliderSetting(
                    title = "Auto-Stop Timeout",
                    value = voiceAutoStopTimeout.toFloat(),
                    min = 1f,
                    max = 10f,
                    step = 1f,
                    displayFormat = { "${it.toInt()} sec" },
                    onValueChange = { settingsRepository.setInt(SettingsRepository.KEY_VOICE_AUTO_STOP_TIMEOUT, it.toInt()) }
                )
            }

            // ────────────────────────────────────────
            // 8. Command Mode
            // ────────────────────────────────────────
            item(key = "cat_command") {
                SettingsCategory(title = "Command Mode")
            }
            item(key = "command_auto_detect") {
                ToggleSetting(
                    title = "Auto-Detect Terminals",
                    description = "Automatically enable command mode in terminal apps",
                    checked = commandAutoDetect,
                    onCheckedChange = { settingsRepository.setBoolean(SettingsRepository.KEY_COMMAND_AUTO_DETECT, it) }
                )
            }
            item(key = "manage_command_apps") {
                ButtonSetting(
                    title = "Manage Pinned Apps",
                    description = "Override auto-detection for specific apps",
                    onClick = onNavigateToCommandApps
                )
            }

            // ────────────────────────────────────────
            // 9. Backup
            // ────────────────────────────────────────
            item(key = "cat_backup") {
                SettingsCategory(title = "Backup")
            }
            item(key = "export_data") {
                ButtonSetting(
                    title = "Export Data",
                    description = "Export macros, learned words, and settings to a JSON file",
                    onClick = onExport
                )
            }
            item(key = "import_data") {
                ButtonSetting(
                    title = "Import Data",
                    description = "Import data from a backup file",
                    onClick = onImport
                )
            }

            // ────────────────────────────────────────
            // 10. About
            // ────────────────────────────────────────
            item(key = "cat_about") {
                SettingsCategory(title = "About")
            }
            item(key = "version") {
                ButtonSetting(
                    title = "Version",
                    description = versionName
                ) { /* no-op */ }
            }
            item(key = "built_on") {
                ButtonSetting(
                    title = "Built on Hacker's Keyboard",
                    description = "github.com/klausw/hackerskeyboard"
                ) { /* no-op */ }
            }
        }
    }
}
