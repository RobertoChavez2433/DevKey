package dev.devkey.keyboard.ui.keyboard

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.data.repository.SettingsRepository

/**
 * All preference-derived state needed by the keyboard composable.
 */
data class KeyboardPreferences(
    val layoutMode: LayoutMode,
    val keyboardHeightPercent: State<Int>,
    val showHints: Boolean,
    val hintBright: Boolean,
    val showToolbar: State<Boolean>,
    val showNumberRow: State<Boolean>,
    val voiceAutoStopTimeoutSeconds: State<Int>,
    val settings: SettingsRepository
)

/**
 * Reads and observes all keyboard-relevant settings, returning a
 * [KeyboardPreferences] that recomposes when any setting changes.
 */
@Composable
fun rememberKeyboardPreferences(): KeyboardPreferences {
    val configuration = LocalConfiguration.current
    val settings = remember { LatinIME.sKeyboardSettings }

    val layoutModeStr = settings.observeString(
        SettingsRepository.KEY_LAYOUT_MODE,
        "full"
    ).collectAsState(initial = settings.getString(SettingsRepository.KEY_LAYOUT_MODE, "full"))
    val layoutMode = remember(layoutModeStr.value) {
        when (layoutModeStr.value) {
            "compact" -> LayoutMode.COMPACT
            "compact_dev" -> LayoutMode.COMPACT_DEV
            else -> LayoutMode.FULL
        }
    }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val heightKey = if (isLandscape) SettingsRepository.KEY_HEIGHT_LANDSCAPE else SettingsRepository.KEY_HEIGHT_PORTRAIT
    val heightDefault = if (isLandscape) SettingsRepository.DEFAULT_HEIGHT_LANDSCAPE else SettingsRepository.DEFAULT_HEIGHT_PORTRAIT
    val keyboardHeightPercent = settings.observeInt(
        heightKey,
        heightDefault
    ).collectAsState(initial = settings.getInt(heightKey, heightDefault))

    // Hint mode preference: "0" = Hidden, "1" = Visible (dim), "2" = Visible (bright)
    // WHY: Default "1" for SwiftKey parity — the reference keyboard shows a muted
    //      long-press hint glyph on every key. Previously defaulted to "0" which
    //      hid all hints and also mismatched `default_hint_mode` = 1 in
    //      res/values/strings.xml. Users can flip to "0" in Settings > Input if
    //      they want a cleaner look.
    val hintModeValue = settings.observeString(
        SettingsRepository.KEY_HINT_MODE,
        "1"
    ).collectAsState(initial = settings.getString(SettingsRepository.KEY_HINT_MODE, "1"))

    // SwiftKey parity — default hides the toolbar row; users can enable via
    // Settings > Input > `devkey_show_toolbar`. See SettingsRepository.KEY_SHOW_TOOLBAR.
    val showToolbar = settings.observeBoolean(
        SettingsRepository.KEY_SHOW_TOOLBAR,
        false
    ).collectAsState(initial = settings.getBoolean(SettingsRepository.KEY_SHOW_TOOLBAR, false))
    // SwiftKey parity — number row on top of COMPACT / COMPACT_DEV, togglable via
    // Settings > Input > `devkey_show_number_row`. Default true matches the reference.
    val showNumberRow = settings.observeBoolean(
        SettingsRepository.KEY_SHOW_NUMBER_ROW,
        true
    ).collectAsState(initial = settings.getBoolean(SettingsRepository.KEY_SHOW_NUMBER_ROW, true))
    val voiceAutoStopTimeoutSeconds = settings.observeInt(
        SettingsRepository.KEY_VOICE_AUTO_STOP_TIMEOUT,
        3
    ).collectAsState(initial = settings.getInt(SettingsRepository.KEY_VOICE_AUTO_STOP_TIMEOUT, 3))

    return KeyboardPreferences(
        layoutMode = layoutMode,
        keyboardHeightPercent = keyboardHeightPercent,
        showHints = hintModeValue.value != "0",
        hintBright = hintModeValue.value == "2",
        showToolbar = showToolbar,
        showNumberRow = showNumberRow,
        voiceAutoStopTimeoutSeconds = voiceAutoStopTimeoutSeconds,
        settings = settings
    )
}
