package dev.devkey.keyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

/**
 * Renders the text label(s) for a key, including Ctrl Mode shortcut sub-label.
 *
 * In Ctrl Mode with a known shortcut, a secondary label is rendered below the
 * primary letter. Outside Ctrl Mode the primary label is centered.
 */
@Composable
internal fun KeyLabel(
    displayLabel: String,
    textSize: androidx.compose.ui.unit.TextUnit,
    textColor: Color,
    ctrlHeld: Boolean,
    key: KeyData,
    ctrlShortcut: ShortcutInfo?
) {
    if (ctrlHeld && key.type == KeyType.LETTER && ctrlShortcut != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = displayLabel,
                color = textColor,
                fontSize = textSize
            )
            Text(
                text = ctrlShortcut.label,
                color = DevKeyThemeColors.keyText,
                fontSize = DevKeyThemeTypography.ctrlModeShortcutLabelSize
            )
        }
    } else {
        Text(
            text = displayLabel,
            color = textColor,
            fontSize = textSize
        )
    }
}

/**
 * Renders the long-press hint glyph centered at the top of the key.
 *
 * WHY: SwiftKey renders the long-press hint glyph horizontally centered above
 * the primary letter (not top-left, not top-right). Direct pixel comparison
 * against .claude/test-flows/swiftkey-reference/compact-dark-cropped.png.
 *
 * Hidden in Ctrl Mode for letter and number keys.
 */
@Composable
internal fun KeyHintLabel(
    key: KeyData,
    showHints: Boolean,
    hintBright: Boolean,
    ctrlHeld: Boolean,
    boxScope: androidx.compose.foundation.layout.BoxScope
) {
    if (showHints &&
        key.longPressLabel != null &&
        !(ctrlHeld && (key.type == KeyType.LETTER || key.type == KeyType.NUMBER))
    ) {
        with(boxScope) {
            Text(
                text = key.longPressLabel,
                color = if (hintBright) DevKeyThemeColors.keyText.copy(alpha = 0.7f) else DevKeyThemeColors.keyHint,
                fontSize = DevKeyThemeTypography.fontKeyHint,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = DevKeyThemeDimensions.hintLabelPadTop)
            )
        }
    }
}

/**
 * Renders the locked-state indicator dot at the bottom center of a modifier key.
 */
@Composable
internal fun KeyLockDot(
    isModifierLocked: Boolean,
    boxScope: androidx.compose.foundation.layout.BoxScope
) {
    if (isModifierLocked) {
        with(boxScope) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = DevKeyThemeDimensions.lockDotPadBottom)
                    .size(DevKeyThemeDimensions.lockDotSize)
                    .clip(CircleShape)
                    .background(DevKeyThemeColors.keyText)
            )
        }
    }
}

/**
 * Derive the display text for a key. Letter keys always show uppercase;
 * spacebars show nothing; all others show their raw primaryLabel.
 */
internal fun resolveDisplayLabel(key: KeyData): String = when {
    key.type == KeyType.LETTER -> key.primaryLabel.uppercase()
    key.type == KeyType.SPACEBAR -> ""
    else -> key.primaryLabel
}

/**
 * Derive the font size for a key based on its type.
 */
internal fun resolveTextSize(key: KeyData) = when (key.type) {
    KeyType.UTILITY -> DevKeyThemeTypography.fontKeyUtility
    KeyType.SPECIAL -> DevKeyThemeTypography.fontKeySpecial
    KeyType.TOGGLE -> DevKeyThemeTypography.fontKeySpecial
    else -> DevKeyThemeTypography.fontKey
}

/**
 * Derive the text color override for Ctrl Mode; null means use the default.
 */
internal fun resolveCtrlTextColor(
    ctrlHeld: Boolean,
    key: KeyData,
    ctrlShortcut: ShortcutInfo?
): Color? = when {
    ctrlHeld && key.type == KeyType.LETTER && ctrlShortcut != null -> DevKeyThemeColors.keyText
    ctrlHeld && key.type == KeyType.LETTER -> DevKeyThemeColors.ctrlModeDimmed
    ctrlHeld && key.type == KeyType.NUMBER -> DevKeyThemeColors.ctrlModeFullDim
    else -> null
}
