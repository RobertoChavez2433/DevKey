package dev.devkey.keyboard.ui.keyboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.ui.theme.DevKeyTheme
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors

/**
 * Get the background and text colors for a key based on its type.
 *
 * WHY: SwiftKey parity — compact-dark-findings.md §"Critical observation":
 * Utility/modifier keys use the SAME fill color as letter keys. All key types
 * collapse to (keyBg, keyText) except the active-state highlight on modifiers.
 */
internal fun getKeyColors(_key: KeyData): Pair<Color, Color> =
    DevKeyThemeColors.keyBg to DevKeyThemeColors.keyText

/**
 * Get the active (modifier engaged) background color for a key.
 */
internal fun getActiveKeyColor(key: KeyData): Color = when (key.primaryCode) {
    Keyboard.KEYCODE_SHIFT -> DevKeyThemeColors.modBgShiftActive
    KeyCodes.CTRL_LEFT -> DevKeyThemeColors.modBgCtrlActive
    KeyCodes.ALT_LEFT -> DevKeyThemeColors.modBgAltActive
    else -> DevKeyThemeColors.keyBg
}

/**
 * Animates the background color of a key between its normal, pressed, and active states.
 */
@Composable
internal fun animateKeyBackgroundColor(
    isPressed: Boolean,
    isModifierActive: Boolean,
    activeColor: Color,
    normalBg: Color,
    ctrlHeld: Boolean,
    key: KeyData,
    ctrlShortcut: ShortcutInfo?
): Color {
    val targetColor = when {
        isPressed -> DevKeyThemeColors.keyPressed
        isModifierActive -> activeColor
        ctrlHeld && key.type == KeyType.LETTER && ctrlShortcut != null -> {
            when (ctrlShortcut.highlight) {
                HighlightType.RED -> DevKeyThemeColors.ctrlModeRedBg
                HighlightType.NORMAL -> DevKeyThemeColors.ctrlModeShortcutBg
            }
        }
        else -> normalBg
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(
            durationMillis = if (isPressed) DevKeyTheme.pressDownMs else DevKeyTheme.pressUpMs
        ),
        label = "keyBgColor"
    )
    return backgroundColor
}

/**
 * Animates the press scale of a key.
 */
@Composable
internal fun animateKeyScale(isPressed: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) DevKeyTheme.pressScale else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) DevKeyTheme.pressDownMs else DevKeyTheme.pressUpMs
        ),
        label = "keyScale"
    )
    return scale
}
