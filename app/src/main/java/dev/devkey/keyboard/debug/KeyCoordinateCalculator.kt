package dev.devkey.keyboard.debug

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import androidx.preference.PreferenceManager
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.ui.keyboard.KeyBounds
import dev.devkey.keyboard.ui.keyboard.LayoutMode
import dev.devkey.keyboard.ui.keyboard.QwertyLayout
import dev.devkey.keyboard.ui.keyboard.SymbolsLayout
import dev.devkey.keyboard.ui.keyboard.computeKeyBounds
import dev.devkey.keyboard.ui.keyboard.getRowWeightsForMode

/**
 * Computes key screen coordinates for [KeyMapGenerator].
 *
 * Centralises all geometry: display metrics, key-area height, keyboard-top
 * offset, and the [computeKeyBounds] call so that [KeyMapGenerator] only
 * handles orchestration and logging.
 */
internal object KeyCoordinateCalculator {

    // Matches DevKeyTheme layout constants (in dp)
    private const val HORIZONTAL_PADDING_DP = 4f
    private const val ROW_GAP_DP = 4f
    private const val KEY_GAP_DP = 4f
    private const val TOOLBAR_HEIGHT_DP = 36f

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Returns key bounds with absolute screen coordinates using the view's
     * on-screen position for the keyboard-top offset.
     */
    fun boundsFromView(
        context: Context,
        keyboardView: View,
        layoutMode: LayoutMode
    ): List<KeyBounds> {
        val dm = getDisplayMetrics(context)
        val density = dm.density
        val keyAreaHeightPx = keyAreaHeightPx(context, dm, density)

        val location = IntArray(2)
        keyboardView.getLocationOnScreen(location)
        val keyboardTopY = location[1].toFloat()

        return rawBoundsForMode(context, layoutMode, dm.widthPixels.toFloat(), keyAreaHeightPx, density)
            .offsetY(keyboardTopY)
    }

    /**
     * Returns key bounds with estimated absolute screen coordinates when no
     * view reference is available.
     */
    fun boundsCalculated(
        context: Context,
        layoutMode: LayoutMode
    ): List<KeyBounds> {
        val dm = getDisplayMetrics(context)
        val density = dm.density
        val keyAreaHeightPx = keyAreaHeightPx(context, dm, density)
        val toolbarHeightPx = TOOLBAR_HEIGHT_DP * density
        val estimatedKeyboardTop = dm.heightPixels - toolbarHeightPx - keyAreaHeightPx

        return rawBoundsForMode(context, layoutMode, dm.widthPixels.toFloat(), keyAreaHeightPx, density)
            .offsetY(estimatedKeyboardTop)
    }

    /**
     * Returns symbol-layer bounds. Absolute coordinates use the view's
     * on-screen position when available, or the estimated fallback otherwise.
     */
    fun symbolsBounds(
        context: Context,
        keyboardView: View?,
        keyboardWidthPx: Float
    ): List<KeyBounds> {
        val dm = getDisplayMetrics(context)
        val density = dm.density
        val keyAreaHeightPx = keyAreaHeightPx(context, dm, density)

        // WHY: DevKeyKeyboard renders Symbols with layoutMode=FULL, passing
        //      FULL row weights to computeRowHeights. KeyBoundsCalculator takes
        //      the first N weights when there are more weights than rows, which
        //      matches the rendering behaviour exactly.
        val rawBounds = computeKeyBounds(
            layout = SymbolsLayout.layout,
            keyboardWidthPx = keyboardWidthPx,
            keyboardHeightPx = keyAreaHeightPx,
            horizontalPaddingPx = HORIZONTAL_PADDING_DP * density,
            rowGapPx = ROW_GAP_DP * density,
            keyGapPx = KEY_GAP_DP * density,
            rowWeights = getRowWeightsForMode(LayoutMode.FULL)
        )

        val keyboardTopY = if (keyboardView != null) {
            val location = IntArray(2)
            keyboardView.getLocationOnScreen(location)
            location[1].toFloat()
        } else {
            val toolbarHeightPx = TOOLBAR_HEIGHT_DP * density
            dm.heightPixels - toolbarHeightPx - keyAreaHeightPx
        }

        return rawBounds.offsetY(keyboardTopY)
    }

    /** Reads display metrics via WindowManager (includes system decorations). */
    @Suppress("DEPRECATION")
    fun getDisplayMetrics(context: Context): DisplayMetrics {
        val dm = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(dm)
        return dm
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /** Compute the usable key-area height in pixels from settings and screen size. */
    private fun keyAreaHeightPx(context: Context, dm: DisplayMetrics, density: Float): Float {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val heightKey = if (isLandscape) SettingsRepository.KEY_HEIGHT_LANDSCAPE else SettingsRepository.KEY_HEIGHT_PORTRAIT
        val heightDefault = if (isLandscape) SettingsRepository.DEFAULT_HEIGHT_LANDSCAPE else SettingsRepository.DEFAULT_HEIGHT_PORTRAIT
        val heightPercent = prefs.getInt(heightKey, heightDefault) / 100f

        val screenHeightDp = dm.heightPixels / density
        val rawHeightDp = screenHeightDp * heightPercent - TOOLBAR_HEIGHT_DP
        return rawHeightDp.coerceAtLeast(48f) * density
    }

    /** Run [computeKeyBounds] for a QWERTY [LayoutMode]. */
    private fun rawBoundsForMode(
        context: Context,
        layoutMode: LayoutMode,
        keyboardWidthPx: Float,
        keyAreaHeightPx: Float,
        density: Float
    ): List<KeyBounds> {
        val layout = QwertyLayout.getLayout(
            layoutMode,
            includeNumberRow = isNumberRowEnabled(context)
        )
        return computeKeyBounds(
            layout = layout,
            keyboardWidthPx = keyboardWidthPx,
            keyboardHeightPx = keyAreaHeightPx,
            horizontalPaddingPx = HORIZONTAL_PADDING_DP * density,
            rowGapPx = ROW_GAP_DP * density,
            keyGapPx = KEY_GAP_DP * density,
            rowWeights = getRowWeightsForMode(layoutMode)
        )
    }

    /** Read the `devkey_show_number_row` pref. Default true for SwiftKey parity. */
    private fun isNumberRowEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(SettingsRepository.KEY_SHOW_NUMBER_ROW, true)

    /** Shift all Y coordinates in a list of [KeyBounds] by [dy]. */
    private fun List<KeyBounds>.offsetY(dy: Float): List<KeyBounds> = map { kb ->
        kb.copy(top = kb.top + dy, bottom = kb.bottom + dy, centerY = kb.centerY + dy)
    }
}
