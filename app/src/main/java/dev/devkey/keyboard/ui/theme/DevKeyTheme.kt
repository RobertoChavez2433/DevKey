package dev.devkey.keyboard.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Central theme constants for the DevKey keyboard.
 * All color, dimension, typography, and animation values are defined here.
 *
 * Uses a teal monochrome modifier theme with tokenized surface, text,
 * spacing, row height, typography, and animation values.
 */
object DevKeyTheme {

    // ══════════════════════════════════════════
    // Surface tokens
    // ══════════════════════════════════════════
    val kbBg = Color(0xFF1B1B1F)
    val keyBg = Color(0xFF2C2C31)
    val keyBgSpecial = Color(0xFF232327)
    val keyPressed = Color(0xFF3A3A40)

    // ══════════════════════════════════════════
    // Teal monochrome modifier tokens
    // ══════════════════════════════════════════
    val modBgShift = Color(0xFF1A3538)
    val modTextShift = Color(0xFF5EC4CC)

    val modBgAction = Color(0xFF1A3538)
    val modTextAction = Color(0xFF5EC4CC)

    val modBgEnter = Color(0xFF1A3D40)
    val modTextEnter = Color(0xFF6ED4DC)

    val modBgToggle = Color(0xFF1E2A2C)
    val modTextToggle = Color(0xFF5AABB2)

    val modBgSysmod = Color(0xFF172628)
    val modTextSysmod = Color(0xFF4A9AA0)

    val modBgNav = Color(0xFF1A2425)
    val modTextNav = Color(0xFF3D8388)

    // ══════════════════════════════════════════
    // Text tokens
    // ══════════════════════════════════════════
    val keyText = Color(0xFFE8E8EC)
    val keyTextSpecial = Color(0xFFA0A0A8)
    val keyHint = Color(0xFF5A5A62)

    // ══════════════════════════════════════════
    // Spacing tokens
    // ══════════════════════════════════════════
    val keyGap: Dp = 4.dp
    val keyRadius: Dp = 6.dp
    val kbPadH: Dp = 4.dp
    val kbPadV: Dp = 5.dp

    // ══════════════════════════════════════════
    // Row height weight tokens (used as ratios, NOT fixed dp)
    // ══════════════════════════════════════════
    /** Full mode total: 34+38+38+38+38+30 = 216 */
    const val rowNumberWeight = 34f
    const val rowLetterWeight = 38f
    const val rowSpaceWeight = 38f
    const val rowUtilityWeight = 30f
    /** Compact mode total: 42+42+42+42 = 168 */
    const val rowCompactWeight = 42f

    // ══════════════════════════════════════════
    // Typography tokens
    // ══════════════════════════════════════════
    val fontKey: TextUnit = 14.sp
    val fontKeySpecial: TextUnit = 11.sp
    val fontKeyHint: TextUnit = 9.sp
    val fontKeyUtility: TextUnit = 10.sp
    val fontVoiceMic: TextUnit = 18.sp

    // ══════════════════════════════════════════
    // Animation tokens
    // ══════════════════════════════════════════
    const val pressDownMs = 40
    const val pressUpMs = 180
    const val pressScale = 0.92f
    val pressEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // ══════════════════════════════════════════
    // Modifier active colors (ONE_SHOT / LOCKED brightened variants)
    // ══════════════════════════════════════════
    val modBgShiftActive = Color(0xFF245A5F)
    val modBgCtrlActive = Color(0xFF1E4A4E)
    val modBgAltActive = Color(0xFF1E4A4E)

    // ══════════════════════════════════════════
    // Non-layout tokens (unchanged from previous sessions)
    // ══════════════════════════════════════════

    // ── Dimensions ──
    val suggestionBarHeight: Dp = 40.dp
    val toolbarHeight: Dp = 36.dp

    // ── Typography (non-key) ──
    val suggestionTextSize: TextUnit = 14.sp

    // ── Divider / border colors ──
    val dividerColor = Color(0xFF333333)
    val suggestionText = Color(0xFFCCCCCC)
    val iconColor = Color(0xFF888888)

    // ── Ctrl Mode colors ──
    val ctrlModeBannerBg = Color(0x882A2A5A)
    val ctrlModeShortcutBg = Color(0xFF3A3A6A)
    val ctrlModeRedBg = Color(0xFF5A2A2A)
    val ctrlModeDimmed = Color(0x4DE0E0E0)
    val ctrlModeFullDim = Color(0x1AE0E0E0)

    // ── Macro colors ──
    val macroRecordingRed = Color(0xFFE53935)
    val macroAmber = Color(0xFFFFB300)
    val chipBg = Color(0xFF333333)
    val chipBorder = Color(0xFF555555)
    val dashedBorder = Color(0xFF666666)

    // ── Clipboard colors ──
    val clipboardPanelBg = Color(0xFF222222)
    val pinIcon = Color(0xFFFFB300)
    val timestampText = Color(0xFF888888)

    // ── Session 3 Dimensions ──
    val ctrlModeBannerHeight: Dp = 24.dp
    val macroGridCellHeight: Dp = 56.dp
    val macroGridMaxHeight: Dp = 168.dp
    val clipboardPanelMaxHeight: Dp = 200.dp
    val clipboardEntryHeight: Dp = 48.dp
    val recordingBarHeight: Dp = 76.dp

    // ── Session 3 Typography ──
    val ctrlModeShortcutLabelSize: TextUnit = 10.sp
    val macroChipTextSize: TextUnit = 12.sp
    val clipboardPreviewTextSize: TextUnit = 13.sp
    val clipboardTimestampSize: TextUnit = 11.sp

    // ── Session 4: Voice colors ──
    val voicePanelBg = Color(0xFF1E1E2E)
    val voiceMicActive = Color(0xFF4CAF50)
    val voiceMicInactive = Color(0xFF666666)
    val voiceWaveform = Color(0xFF4CAF50)
    val voiceStatusText = Color(0xFFCCCCCC)

    // ── Session 4: Command mode colors ──
    val cmdBadgeBg = Color(0xFF3A3A2A)
    val cmdBadgeText = Color(0xFFFFB300)

    // ── Session 4 Dimensions ──
    val voicePanelHeight: Dp = 200.dp
    val voiceMicSize: Dp = 64.dp
    val cmdBadgeHeight: Dp = 20.dp
    val cmdBadgePadding: Dp = 4.dp

    // ── Session 4 Typography ──
    val voiceStatusSize: TextUnit = 16.sp
    val cmdBadgeTextSize: TextUnit = 10.sp

    // ── Session 5: Settings colors ──
    val settingsCategoryColor = Color(0xFF82B1FF)
    val settingsDescriptionColor = Color(0xFF888888)
    val settingsDividerColor = Color(0xFF333333)
}
