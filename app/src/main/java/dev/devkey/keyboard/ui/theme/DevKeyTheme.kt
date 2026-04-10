package dev.devkey.keyboard.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Central theme shell for the DevKey keyboard.
 * Color, dimension, and typography tokens have been split into:
 *   - DevKeyThemeColors    — all Color vals
 *   - DevKeyThemeDimensions — all Dp vals
 *   - DevKeyThemeTypography — all TextUnit/sp vals
 *
 * This object retains only the const Float row weight vals and animation vals
 * that have no natural home in the typed sub-objects.
 */
object DevKeyTheme {

    // ══════════════════════════════════════════
    // Row height weight tokens (used as ratios, NOT fixed dp)
    // ══════════════════════════════════════════
    /** Full mode total: 34+38+38+38+38+30 = 216 */
    const val rowNumberWeight = 34f
    const val rowLetterWeight = 38f
    const val rowSpaceWeight = 38f
    const val rowUtilityWeight = 30f
    /** Compact mode: all rows uniform at equal weight */
    const val rowCompactWeight = 42f

    // ══════════════════════════════════════════
    // Animation tokens
    // ══════════════════════════════════════════
    const val pressDownMs = 40
    const val pressUpMs = 180
    const val pressScale = 0.92f
    val pressEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
