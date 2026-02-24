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

    // ── Ctrl Mode colors ──
    val ctrlModeBannerBg = Color(0x882A2A5A)          // semi-transparent blue/purple for Ctrl Mode banner
    val ctrlModeShortcutBg = Color(0xFF3A3A6A)        // blue/purple tint for shortcut keys in Ctrl Mode
    val ctrlModeRedBg = Color(0xFF5A2A2A)             // red tint for Copy/Paste keys in Ctrl Mode
    val ctrlModeDimmed = Color(0x4DE0E0E0)            // 30% opacity text for non-shortcut keys
    val ctrlModeFullDim = Color(0x1AE0E0E0)           // 10% opacity text for number row

    // ── Macro colors ──
    val macroRecordingRed = Color(0xFFE53935)          // red for recording indicator
    val macroAmber = Color(0xFFFFB300)                 // amber for macro key combo labels in grid
    val chipBg = Color(0xFF333333)                     // macro chip background
    val chipBorder = Color(0xFF555555)                 // macro chip border
    val dashedBorder = Color(0xFF666666)               // dashed border for "+ Add" / "+ Record"

    // ── Clipboard colors ──
    val clipboardPanelBg = Color(0xFF222222)           // clipboard panel background
    val searchBarBg = Color(0xFF2A2A2A)                // search bar background
    val pinIcon = Color(0xFFFFB300)                    // amber pin icon color
    val timestampText = Color(0xFF888888)              // grey timestamp text

    // ── Session 3 Dimensions ──
    val ctrlModeBannerHeight: Dp = 24.dp
    val macroGridCellHeight: Dp = 56.dp
    val macroGridMaxHeight: Dp = 168.dp                // 3 rows of cells
    val clipboardPanelMaxHeight: Dp = 200.dp
    val clipboardEntryHeight: Dp = 48.dp
    val macroChipHeight: Dp = 32.dp
    val recordingBarHeight: Dp = 76.dp                 // toolbar + suggestion bar combined

    // ── Session 3 Typography ──
    val ctrlModeShortcutLabelSize: TextUnit = 10.sp
    val macroChipTextSize: TextUnit = 12.sp
    val clipboardPreviewTextSize: TextUnit = 13.sp
    val clipboardTimestampSize: TextUnit = 11.sp
}
