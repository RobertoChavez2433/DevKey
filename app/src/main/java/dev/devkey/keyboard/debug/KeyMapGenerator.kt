package dev.devkey.keyboard.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.View
import dev.devkey.keyboard.ui.keyboard.KeyBounds
import dev.devkey.keyboard.ui.keyboard.LayoutMode

/**
 * Generates ADB-ready key-name-to-screen-pixel mappings for automated testing.
 *
 * Two modes:
 * 1. Precise: Uses View.getLocationOnScreen() for exact keyboard Y offset
 * 2. Fallback: Calculates from screen dimensions (less accurate with non-standard system bars)
 *
 * Geometry is handled by [KeyCoordinateCalculator].
 */
object KeyMapGenerator {

    private const val TAG = "DevKeyMap"

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
    fun getKeyMap(context: Context, keyboardView: View, layoutMode: LayoutMode): List<KeyBounds> =
        KeyCoordinateCalculator.boundsFromView(context, keyboardView, layoutMode)

    /**
     * Get key map using calculated fallback (no View reference needed).
     *
     * Less accurate with non-standard system bars, but works without a view reference.
     *
     * @param context Application context for display metrics
     * @param layoutMode The active layout mode
     * @return List of KeyBounds with estimated screen coordinates
     */
    fun getKeyMapCalculated(context: Context, layoutMode: LayoutMode): List<KeyBounds> =
        KeyCoordinateCalculator.boundsCalculated(context, layoutMode)

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
        val dm = KeyCoordinateCalculator.getDisplayMetrics(context)
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

        // WHY: Phase 3 defect — tests driving Symbols mode (ABC return, 123 round-trip)
        //      need coordinates for keys that only exist in SymbolsLayout (ABC=-200, @, #, $...).
        //      Symbols uses the SAME keyboard area but a different row structure, so we re-run
        //      computeKeyBounds with SymbolsLayout + equal row weights (it has no rowWeights
        //      override in DevKeyTheme — symbols UI renders with default equal-height rows).
        //      We prefix with `SYM_KEY` so the harness can distinguish overlapping labels.
        val symbolsBounds = KeyCoordinateCalculator.symbolsBounds(
            context,
            keyboardView,
            keyboardWidthPx = dm.widthPixels.toFloat()
        )
        for (kb in symbolsBounds) {
            Log.d(TAG, "SYM_KEY label=${kb.adbLabel} code=${kb.code} x=${kb.centerX.toInt()} y=${kb.centerY.toInt()}")
        }

        Log.d(TAG, "=== End Key Map (${bounds.size} keys, ${symbolsBounds.size} symbols keys) ===")
        // WHY: Phase 2 Python harness replaces its `time.sleep(0.3)` after the DUMP_KEY_MAP
        //      broadcast with a wait_for on this event — see tools/e2e/lib/keyboard.py load_key_map.
        DevKeyLogger.ime(
            "keymap_dump_complete",
            mapOf(
                "mode" to layoutMode.name,
                "key_count" to bounds.size
            )
        )
    }
}
