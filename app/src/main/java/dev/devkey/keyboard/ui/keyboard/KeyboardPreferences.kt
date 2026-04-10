package dev.devkey.keyboard.ui.keyboard

import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
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
    val prefs: SharedPreferences
)

/**
 * Reads and observes all keyboard-relevant SharedPreferences, returning a
 * [KeyboardPreferences] that recomposes when any preference changes.
 */
@Composable
fun rememberKeyboardPreferences(): KeyboardPreferences {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val layoutModeStr = rememberPreference(prefs, SettingsRepository.KEY_LAYOUT_MODE, "full") { p, k, d ->
        p.getString(k, d) ?: d
    }
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
    val keyboardHeightPercent = rememberPreference(prefs, heightKey, heightDefault) { p, k, d -> p.getInt(k, d) }

    // Hint mode preference: "0" = Hidden, "1" = Visible (dim), "2" = Visible (bright)
    // WHY: Default "1" for SwiftKey parity — the reference keyboard shows a muted
    //      long-press hint glyph on every key. Previously defaulted to "0" which
    //      hid all hints and also mismatched `default_hint_mode` = 1 in
    //      res/values/strings.xml. Users can flip to "0" in Settings > Input if
    //      they want a cleaner look.
    val hintModeValue = rememberPreference(prefs, SettingsRepository.KEY_HINT_MODE, "1") { p, k, d ->
        p.getString(k, d) ?: d
    }

    // SwiftKey parity — default hides the toolbar row; users can enable via
    // Settings > Input > `devkey_show_toolbar`. See SettingsRepository.KEY_SHOW_TOOLBAR.
    val showToolbar = rememberPreference(prefs, SettingsRepository.KEY_SHOW_TOOLBAR, false) { p, k, d ->
        p.getBoolean(k, d)
    }
    // SwiftKey parity — number row on top of COMPACT / COMPACT_DEV, togglable via
    // Settings > Input > `devkey_show_number_row`. Default true matches the reference.
    val showNumberRow = rememberPreference(prefs, SettingsRepository.KEY_SHOW_NUMBER_ROW, true) { p, k, d ->
        p.getBoolean(k, d)
    }

    return KeyboardPreferences(
        layoutMode = layoutMode,
        keyboardHeightPercent = keyboardHeightPercent,
        showHints = hintModeValue.value != "0",
        hintBright = hintModeValue.value == "2",
        showToolbar = showToolbar,
        showNumberRow = showNumberRow,
        prefs = prefs
    )
}

/**
 * Helper composable that reads a SharedPreferences value and returns a [State] that
 * automatically updates whenever the preference changes.
 *
 * @param prefs The SharedPreferences instance to read from.
 * @param key The preference key to observe.
 * @param defaultValue The default value to use when the key is absent.
 * @param reader A function that reads the typed value from prefs.
 */
@Composable
internal fun <T> rememberPreference(
    prefs: SharedPreferences,
    key: String,
    defaultValue: T,
    reader: (SharedPreferences, String, T) -> T
): State<T> {
    val state = remember { mutableStateOf(reader(prefs, key, defaultValue)) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                state.value = reader(prefs, key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
