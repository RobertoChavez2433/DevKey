package dev.devkey.keyboard.ui.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Typography tokens (TextUnit/sp) for the DevKey keyboard theme.
 * All TextUnit-type vals extracted from DevKeyTheme.
 *
 * HARD RULE: every consumer of a typography value in ui/ MUST reference a named token
 * declared here. No inline literals anywhere in ui/ except inside theme files.
 */
object DevKeyThemeTypography {

    // ══════════════════════════════════════════
    // Typography tokens — key surface
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    // ══════════════════════════════════════════

    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    val fontKey: TextUnit = 22.sp
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    // Special keys match letter key font per findings
    val fontKeySpecial: TextUnit = 22.sp
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    val fontKeyHint: TextUnit = 10.sp
    val fontKeyUtility: TextUnit = 10.sp
    val fontVoiceMic: TextUnit = 18.sp

    // ── Typography (non-key) ──
    val suggestionTextSize: TextUnit = 14.sp

    // ── Session 3 Typography ──
    val ctrlModeShortcutLabelSize: TextUnit = 10.sp
    val macroChipTextSize: TextUnit = 12.sp
    val clipboardPreviewTextSize: TextUnit = 13.sp
    val clipboardTimestampSize: TextUnit = 11.sp

    // ── Session 4 Typography ──
    val voiceStatusSize: TextUnit = 16.sp
    val cmdBadgeTextSize: TextUnit = 10.sp

    // ── Phase 5.4: Long-press multi-char popup ──
    /** Typography for candidate labels */
    val fontPopupCandidate: TextUnit = 16.sp

    // ══════════════════════════════════════════
    // CtrlModeBanner typography
    // ══════════════════════════════════════════
    /** Font size for the Ctrl Mode banner instruction text */
    val fontCtrlModeBanner: TextUnit = 11.sp

    // ══════════════════════════════════════════
    // Toolbar dimensions and typography
    // ══════════════════════════════════════════
    /** Font size for toolbar icon/label buttons */
    val fontToolbarIcon: TextUnit = 16.sp

    // ══════════════════════════════════════════
    // Settings UI dimensions and typography
    // ══════════════════════════════════════════
    /** Font size for the category title text */
    val fontSettingsCategory: TextUnit = 14.sp
    /** Font size for category subtitle / hint text */
    val fontSettingsSubtitle: TextUnit = 11.sp
    /** Font size for sub-screen titles and back-arrow glyph */
    val fontSettingsScreenTitle: TextUnit = 20.sp
    /** Font size for category-screen section title ("Settings") */
    val fontSettingsSectionTitle: TextUnit = 24.sp
    /** Font size for category tile primary label */
    val fontSettingsTileTitle: TextUnit = 16.sp
    /** Font size for category tile description */
    val fontSettingsTileDescription: TextUnit = 13.sp

    // ══════════════════════════════════════════
    // Welcome screen dimensions and typography
    // ══════════════════════════════════════════
    /** Title font size on the welcome screen */
    val fontWelcomeTitle: TextUnit = 36.sp
    /** Subtitle/body font size on the welcome screen */
    val fontWelcomeSubtitle: TextUnit = 16.sp
    /** Step label description font size */
    val fontWelcomeDescription: TextUnit = 13.sp
    /** Arrow glyph font size at the right edge of a step row */
    val fontWelcomeArrow: TextUnit = 24.sp
    /** Font size for the step indicator number / check glyph */
    val fontWelcomeStepIndicator: TextUnit = 14.sp
}
