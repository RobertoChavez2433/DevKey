package dev.devkey.keyboard.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color tokens for the DevKey keyboard theme.
 * All Color-type vals extracted from DevKeyTheme.
 *
 * Uses a teal monochrome modifier theme with tokenized surface, text,
 * and modifier-state color values.
 *
 * HARD RULE: every consumer of a color value in ui/ MUST reference a named token
 * declared here. No inline literals anywhere in ui/ except inside theme files.
 */
object DevKeyThemeColors {

    // ══════════════════════════════════════════
    // Dark palette — surface tokens
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    // ══════════════════════════════════════════

    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    val kbBg = Color(0xFF141416)
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    val keyBg = Color(0xFF2C2C30)
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    // CRITICAL: modifier/utility keys match letter-key fill per findings — keyBgSpecial == keyBg
    val keyBgSpecial = keyBg
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    val keyPressed = Color(0xFF3A3A3E)

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
    // Dark palette — text tokens
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    // ══════════════════════════════════════════

    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    val keyText = Color(0xFFF2F2F2)
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    // CRITICAL: special text matches primary text per findings
    val keyTextSpecial = keyText
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    val keyHint = Color(0xFF8C8C90)

    // Space bar watermark color
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    val spaceBarWatermark = Color(0xFF707075)

    // Candidate strip inherits kbBg background and keyText foreground per findings.
    // No separate token needed; consumers reference kbBg / keyText directly.

    // ══════════════════════════════════════════
    // Modifier active colors (ONE_SHOT / LOCKED brightened variants)
    // ══════════════════════════════════════════
    val modBgShiftActive = Color(0xFF245A5F)
    val modBgCtrlActive = Color(0xFF1E4A4E)
    val modBgAltActive = Color(0xFF1E4A4E)

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

    // ── Session 4: Voice colors ──
    val voicePanelBg = Color(0xFF1E1E2E)
    val voiceMicActive = Color(0xFF4CAF50)
    val voiceMicInactive = Color(0xFF666666)
    val voiceWaveform = Color(0xFF4CAF50)
    val voiceStatusText = Color(0xFFCCCCCC)

    // ── Session 4: Command mode colors ──
    val cmdBadgeBg = Color(0xFF3A3A2A)
    val cmdBadgeText = Color(0xFFFFB300)

    // ── Session 5: Settings colors ──
    val settingsCategoryColor = Color(0xFF82B1FF)
    val settingsDescriptionColor = Color(0xFF888888)
    val settingsDividerColor = Color(0xFF333333)

    // ── Phase 5.4: Long-press multi-char popup ──
    /** Background surface for the candidate popup row */
    val popupBg = Color(0xFF3A3A42)
    /** Background for the currently-highlighted candidate */
    val popupSelectedBg = Color(0xFF5EC4CC)
    /** Text color for un-selected candidates */
    val popupCandidateText = Color(0xFFF2F2F2)
    /** Text color for the selected candidate (dark for contrast against teal) */
    val popupSelectedText = Color(0xFF141416)

    // ══════════════════════════════════════════
    // Welcome screen colors
    // ══════════════════════════════════════════
    /** Background color for a completed setup step indicator circle */
    val setupCompleteGreen = Color(0xFF4CAF50)
    /** Text color on a completed step indicator (check mark on green bg) */
    val setupCompleteText = Color(0xFFFFFFFF)
}
