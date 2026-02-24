package dev.devkey.keyboard.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Central theme constants for the DevKey keyboard.
 * All color, dimension, and typography values are defined here.
 */
object DevKeyTheme {

    // ── Keyboard background ──
    val keyboardBackground = Color(0xFF1B1B1B)

    // ── Standard key colors ──
    val keyFill = Color(0xFF2A2A2A)
    val keyPressed = Color(0xFF3A3A3A)
    val keyText = Color(0xFFE0E0E0)
    val keyHint = Color(0xFF666666)

    // ── Special key colors (normal state) ──
    val escKeyFill = Color(0xFF3A2020)       // red-tinted
    val tabKeyFill = Color(0xFF203040)       // blue-tinted
    val ctrlKeyFill = Color(0xFF2A2A3A)      // purple-tinted
    val altKeyFill = Color(0xFF2A3A2A)       // green-tinted
    val arrowKeyFill = Color(0xFF2A2A3A)     // blue-tinted
    val enterKeyFill = Color(0xFF2A3A2A)     // green-tinted
    val backspaceKeyFill = Color(0xFF3A2020) // red-tinted
    val spaceKeyFill = Color(0xFF2A2A2A)
    val spaceKeyText = Color(0xFF666666)

    // ── Modifier active colors (ONE_SHOT / LOCKED brightened variants) ──
    val ctrlActiveKeyFill = Color(0xFF4A4A6A)
    val altActiveKeyFill = Color(0xFF4A6A4A)
    val shiftActiveKeyFill = Color(0xFF4A4A4A)

    // ── Dimensions ──
    val keyCornerRadius: Dp = 6.dp
    val keyGap: Dp = 4.dp
    val rowHeight: Dp = 48.dp
    val suggestionBarHeight: Dp = 40.dp
    val toolbarHeight: Dp = 36.dp

    // ── Typography ──
    val keyLabelSize: TextUnit = 18.sp
    val keyHintSize: TextUnit = 9.sp
    val suggestionTextSize: TextUnit = 14.sp

    // ── Divider / border colors ──
    val dividerColor = Color(0xFF333333)
    val suggestionText = Color(0xFFCCCCCC)
    val iconColor = Color(0xFF888888)
}
