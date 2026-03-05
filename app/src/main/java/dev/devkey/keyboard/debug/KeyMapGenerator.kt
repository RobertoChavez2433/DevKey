package dev.devkey.keyboard.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.preference.PreferenceManager
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.ui.keyboard.KeyBounds
import dev.devkey.keyboard.ui.keyboard.LayoutMode
import dev.devkey.keyboard.ui.keyboard.QwertyLayout
import dev.devkey.keyboard.ui.keyboard.computeKeyBounds
import dev.devkey.keyboard.ui.keyboard.getRowWeightsForMode

/**
 * Generates ADB-ready key-name-to-screen-pixel mappings for automated testing.
 *
 * Two modes:
 * 1. Precise: Uses View.getLocationOnScreen() for exact keyboard Y offset
 * 2. Fallback: Calculates from screen dimensions (less accurate with non-standard system bars)
 */
object KeyMapGenerator {

    private const val TAG = "DevKeyMap"

    // Matches DevKeyTheme layout constants (in dp)
    private const val HORIZONTAL_PADDING_DP = 4f
    private const val ROW_GAP_DP = 4f
    private const val KEY_GAP_DP = 4f
    private const val TOOLBAR_HEIGHT_DP = 36f

    /**
     * Check if this is a debuggable build using ApplicationInfo flags.
     * Does not require BuildConfig, works with any build variant.
     */
    fun isDebugBuild(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /**
     * Get key map with precise coordinates using View.getLocationOnScreen().
     *
     * @param context Application context for display metrics
     * @param keyboardView The keyboard's root view (for getLocationOnScreen)
     * @param layoutMode The active layout mode
     * @return List of KeyBounds with absolute screen coordinates
     */
    fun getKeyMap(context: Context, keyboardView: View, layoutMode: LayoutMode): List<KeyBounds> {
        val dm = getDisplayMetrics(context)
        val density = dm.density

        val layout = QwertyLayout.getLayout(layoutMode)
        val keyboardWidthPx = dm.widthPixels.toFloat()

        // Get keyboard view's position on screen
        val location = IntArray(2)
        keyboardView.getLocationOnScreen(location)
        val keyboardTopY = location[1]

        // Calculate key area height (mirrors KeyboardView.kt)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val heightKey = if (isLandscape) SettingsRepository.KEY_HEIGHT_LANDSCAPE else SettingsRepository.KEY_HEIGHT_PORTRAIT
        val heightDefault = if (isLandscape) SettingsRepository.DEFAULT_HEIGHT_LANDSCAPE else SettingsRepository.DEFAULT_HEIGHT_PORTRAIT
        val heightPercent = prefs.getInt(heightKey, heightDefault) / 100f

        val screenHeightDp = dm.heightPixels / density
        val rawHeightDp = screenHeightDp * heightPercent - TOOLBAR_HEIGHT_DP
        val keyAreaHeightDp = rawHeightDp.coerceAtLeast(48f)
        val keyAreaHeightPx = keyAreaHeightDp * density

        val bounds = computeKeyBounds(
            layout = layout,
            keyboardWidthPx = keyboardWidthPx,
            keyboardHeightPx = keyAreaHeightPx,
            horizontalPaddingPx = HORIZONTAL_PADDING_DP * density,
            rowGapPx = ROW_GAP_DP * density,
            keyGapPx = KEY_GAP_DP * density,
            rowWeights = getRowWeightsForMode(layoutMode)
        )

        // Offset Y coordinates to absolute screen position
        return bounds.map { kb ->
            kb.copy(
                top = kb.top + keyboardTopY,
                bottom = kb.bottom + keyboardTopY,
                centerY = kb.centerY + keyboardTopY
            )
        }
    }

    /**
     * Get key map using calculated fallback (no View reference needed).
     *
     * Less accurate with non-standard system bars, but works without a view reference.
     *
     * @param context Application context for display metrics
     * @param layoutMode The active layout mode
     * @return List of KeyBounds with estimated screen coordinates
     */
    fun getKeyMapCalculated(context: Context, layoutMode: LayoutMode): List<KeyBounds> {
        val dm = getDisplayMetrics(context)
        val density = dm.density

        val layout = QwertyLayout.getLayout(layoutMode)
        val keyboardWidthPx = dm.widthPixels.toFloat()

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val heightKey = if (isLandscape) SettingsRepository.KEY_HEIGHT_LANDSCAPE else SettingsRepository.KEY_HEIGHT_PORTRAIT
        val heightDefault = if (isLandscape) SettingsRepository.DEFAULT_HEIGHT_LANDSCAPE else SettingsRepository.DEFAULT_HEIGHT_PORTRAIT
        val heightPercent = prefs.getInt(heightKey, heightDefault) / 100f

        val screenHeightDp = dm.heightPixels / density
        val rawHeightDp = screenHeightDp * heightPercent - TOOLBAR_HEIGHT_DP
        val keyAreaHeightDp = rawHeightDp.coerceAtLeast(48f)
        val keyAreaHeightPx = keyAreaHeightDp * density

        // Estimate keyboard top: screen bottom - toolbar - key area
        val toolbarHeightPx = TOOLBAR_HEIGHT_DP * density
        val estimatedKeyboardTop = dm.heightPixels - toolbarHeightPx - keyAreaHeightPx

        val bounds = computeKeyBounds(
            layout = layout,
            keyboardWidthPx = keyboardWidthPx,
            keyboardHeightPx = keyAreaHeightPx,
            horizontalPaddingPx = HORIZONTAL_PADDING_DP * density,
            rowGapPx = ROW_GAP_DP * density,
            keyGapPx = KEY_GAP_DP * density,
            rowWeights = getRowWeightsForMode(layoutMode)
        )

        return bounds.map { kb ->
            kb.copy(
                top = kb.top + estimatedKeyboardTop,
                bottom = kb.bottom + estimatedKeyboardTop,
                centerY = kb.centerY + estimatedKeyboardTop
            )
        }
    }

    /**
     * Polls until the keyboard view is laid out with valid dimensions, then dumps the key map.
     * Used from LaunchedEffect to replace the old fixed delay approach.
     *
     * @param context Application context
     * @param keyboardView The keyboard root view (must be attached)
     * @param layoutMode The active layout mode
     */
    suspend fun dumpToLogcatWhenReady(context: Context, keyboardView: View, layoutMode: LayoutMode) {
        var waited = 0L
        while (waited < 2000L && (!keyboardView.isLaidOut || keyboardView.height == 0)) {
            kotlinx.coroutines.delay(100L)
            waited += 100L
        }
        dumpToLogcat(context, keyboardView, layoutMode)
    }

    /**
     * Dump the full key map to logcat for ADB extraction.
     *
     * Usage: adb logcat -s DevKeyMap:D
     *
     * @param context Application context
     * @param keyboardView Optional view for precise mode; null falls back to calculated
     * @param layoutMode The active layout mode
     */
    fun dumpToLogcat(context: Context, keyboardView: View?, layoutMode: LayoutMode) {
        val dm = getDisplayMetrics(context)
        val density = dm.density

        val bounds = if (keyboardView != null) {
            getKeyMap(context, keyboardView, layoutMode)
        } else {
            getKeyMapCalculated(context, layoutMode)
        }

        Log.d(TAG, "=== DevKey Key Map ===")
        Log.d(TAG, "screen=${dm.widthPixels}x${dm.heightPixels} density=$density mode=$layoutMode")

        if (keyboardView != null) {
            val location = IntArray(2)
            keyboardView.getLocationOnScreen(location)
            Log.d(TAG, "keyboard_view_top=${location[1]}")
        } else {
            Log.d(TAG, "keyboard_view_top=estimated")
        }

        for (kb in bounds) {
            Log.d(TAG, "KEY label=${kb.adbLabel} code=${kb.code} x=${kb.centerX.toInt()} y=${kb.centerY.toInt()}")
        }

        Log.d(TAG, "=== End Key Map (${bounds.size} keys) ===")
    }

    @Suppress("DEPRECATION")
    private fun getDisplayMetrics(context: Context): DisplayMetrics {
        val dm = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(dm)
        return dm
    }
}
