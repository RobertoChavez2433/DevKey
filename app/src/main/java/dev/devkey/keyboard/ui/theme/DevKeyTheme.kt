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
 *
 * HARD RULE: every consumer of a design value in ui/ MUST reference a named token
 * declared here. No inline literals anywhere in ui/ except inside this file.
 */
object DevKeyTheme {

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
    // Dark palette — spacing/shape tokens
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    // ══════════════════════════════════════════

    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    val keyGap: Dp = 3.dp
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    val keyRadius: Dp = 7.dp
    // TUNED from .claude/test-flows/swiftkey-reference/compact-dark-findings.md
    val kbPadH: Dp = 5.dp
    val kbPadV: Dp = 5.dp

    // ══════════════════════════════════════════
    // Light palette — NOT retuned for v1.0
    // Light palette is NOT retuned for v1.0 — spec §4.4.1 light-theme deferral.
    // Primary user does not use light mode. Re-tune when a light-mode SwiftKey
    // reference capture becomes available. The existing values are scaffolding,
    // not reference-matched.
    // ══════════════════════════════════════════

    // (no light-mode tokens at this time — all tokens above serve both modes
    //  until a light-mode capture is available)

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
    // KeyView — hint label padding tokens
    // ══════════════════════════════════════════
    /** Padding from the end edge for the long-press hint label */
    val hintLabelPadEnd: Dp = 3.dp
    /** Padding from the top edge for the long-press hint label */
    val hintLabelPadTop: Dp = 2.dp
    /** Bottom padding for the modifier-lock indicator dot */
    val lockDotPadBottom: Dp = 4.dp
    /** Size (width and height) of the modifier-lock indicator dot */
    val lockDotSize: Dp = 4.dp

    // ══════════════════════════════════════════
    // KeyboardView — row height fallback tokens
    // ══════════════════════════════════════════
    /** Minimum key area height — prevents zero-height layout on edge cases */
    val keyAreaMinHeight: Dp = 48.dp
    /** Fallback row height if weight table has fewer entries than rows */
    val rowHeightFallback: Dp = 40.dp

    // ══════════════════════════════════════════
    // Non-layout tokens (unchanged from previous sessions)
    // ══════════════════════════════════════════

    // ── Dimensions ──
    val suggestionBarHeight: Dp = 40.dp
    val toolbarHeight: Dp = 36.dp
    // Slim horizontal strip shown when the toolbar is hidden — lets users re-reveal
    // the toolbar without entering Settings. SwiftKey parity (collapsible toolbar).
    val chevronRowHeight: Dp = 14.dp
    val chevronRowIconPad: Dp = 6.dp

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

    // ── Phase 5.4: Long-press multi-char popup ──
    /** Background surface for the candidate popup row */
    val popupBg = Color(0xFF3A3A42)
    /** Background for the currently-highlighted candidate */
    val popupSelectedBg = Color(0xFF5EC4CC)
    /** Text color for un-selected candidates */
    val popupCandidateText = Color(0xFFF2F2F2)
    /** Text color for the selected candidate (dark for contrast against teal) */
    val popupSelectedText = Color(0xFF141416)
    /** Corner radius for the popup surface */
    val popupRadius: Dp = 8.dp
    /** Horizontal padding inside each candidate cell */
    val popupCellPadH: Dp = 10.dp
    /** Vertical padding inside each candidate cell */
    val popupCellPadV: Dp = 8.dp
    /** Typography for candidate labels */
    val fontPopupCandidate: TextUnit = 16.sp

    // Glyph-sized inner width for a popup candidate cell. Sum with popupCellPadH*2
    // to get the full cell width used for layout AND gesture hit-testing.
    val popupCellGlyphWidth: Dp = 24.dp

    // Inner height for a popup candidate cell. Sum with popupCellPadV*2 to get
    // full cell height.
    val popupCellGlyphHeight: Dp = 28.dp

    // Horizontal gap between the key center and the bottom of the popup row.
    val popupAnchorGap: Dp = 4.dp

    // ══════════════════════════════════════════
    // CtrlModeBanner typography
    // ══════════════════════════════════════════
    /** Font size for the Ctrl Mode banner instruction text */
    val fontCtrlModeBanner: TextUnit = 11.sp

    // ══════════════════════════════════════════
    // SuggestionBar / MacroChipStrip dimensions
    // ══════════════════════════════════════════
    /** Horizontal padding for suggestion bar content and chip strip arrow */
    val suggestionBarPadH: Dp = 8.dp
    /** Standard 1dp hairline divider / border thickness */
    val dividerThickness: Dp = 1.dp
    /** Zero height — used for collapsed containers (e.g. hidden suggestion bar) */
    val collapsedHeight: Dp = 0.dp

    // ══════════════════════════════════════════
    // Macro UI dimensions
    // ══════════════════════════════════════════
    /** Corner radius for macro chips */
    val chipRadius: Dp = 16.dp
    /** Horizontal padding inside a macro chip */
    val chipPadH: Dp = 8.dp
    /** Vertical padding inside a macro chip */
    val chipPadV: Dp = 4.dp
    /** Spacing between chips in the chip strip */
    val chipStripSpacing: Dp = 8.dp
    /** Spacing between individual items inside a chip */
    val chipInnerSpacing: Dp = 4.dp
    /** Top border height for the macro recording bar */
    val macroRecordingTopBorderH: Dp = 2.dp
    /** Spacing between the pulsing dot and "Recording..." text */
    val macroRecordingIndicatorSpacing: Dp = 6.dp
    /** Size of the pulsing red indicator dot */
    val macroRecordingDotSize: Dp = 8.dp
    /** Horizontal padding inside the recording bar content area */
    val macroRecordingBarPadH: Dp = 8.dp
    /** Vertical padding inside the recording bar content area */
    val macroRecordingBarPadV: Dp = 4.dp
    /** Spacing between step items in the captured steps row */
    val macroStepSpacing: Dp = 4.dp
    /** Horizontal padding around the step separator arrow */
    val macroStepArrowPadH: Dp = 2.dp
    /** Horizontal padding inside the macro grid */
    val macroGridPadH: Dp = 4.dp
    /** Corner radius for macro grid cells */
    val macroCellRadius: Dp = 8.dp
    /** Padding inside a macro grid cell */
    val macroCellPad: Dp = 4.dp
    /** Spacing between macro grid rows and columns */
    val macroGridSpacing: Dp = 4.dp

    // ══════════════════════════════════════════
    // Voice panel dimensions
    // ══════════════════════════════════════════
    /** Vertical spacer between status text and mic icon */
    val voiceSpacerLg: Dp = 16.dp
    /** Vertical spacer between mic and waveform / buttons */
    val voiceSpacerMd: Dp = 12.dp
    /** Width of the waveform canvas */
    val voiceWaveformWidth: Dp = 120.dp
    /** Height of the waveform canvas */
    val voiceWaveformHeight: Dp = 32.dp
    /** Size of the circular progress indicator during PROCESSING state */
    val voiceProgressSize: Dp = 24.dp
    /** Stroke width for the circular progress indicator */
    val voiceProgressStrokeWidth: Dp = 2.dp
    /** Horizontal spacing between Cancel and Done buttons */
    val voiceButtonSpacing: Dp = 32.dp

    // ══════════════════════════════════════════
    // Toolbar dimensions and typography
    // ══════════════════════════════════════════
    /** Font size for toolbar icon/label buttons */
    val fontToolbarIcon: TextUnit = 16.sp
    /** Corner radius for the CMD mode badge */
    val cmdBadgeRadius: Dp = 4.dp
    /** Padding from the overflow dot to the CMD badge */
    val cmdBadgeStartPad: Dp = 4.dp

    // ══════════════════════════════════════════
    // Clipboard panel dimensions
    // ══════════════════════════════════════════
    /** Horizontal padding inside clipboard search bar and entries */
    val clipboardPadH: Dp = 8.dp
    /** Vertical padding inside clipboard entries */
    val clipboardEntryPadV: Dp = 4.dp
    /** Corner radius for the clipboard search field */
    val clipboardSearchRadius: Dp = 8.dp
    /** Spacing between content elements inside a clipboard entry row */
    val clipboardEntrySpacing: Dp = 4.dp

    // ══════════════════════════════════════════
    // Settings UI dimensions and typography
    // ══════════════════════════════════════════
    /** Top padding for a settings category header */
    val settingsCategoryPadTop: Dp = 16.dp
    /** Bottom padding for a settings category header */
    val settingsCategoryPadBottom: Dp = 4.dp
    /** Horizontal padding for settings category header */
    val settingsCategoryPadH: Dp = 16.dp
    /** Spacer below category title text, above the divider */
    val settingsCategorySpacerH: Dp = 4.dp
    /** Standard horizontal padding for settings rows */
    val settingsRowPadH: Dp = 16.dp
    /** Standard vertical padding for primary settings rows (toggle/button/dropdown) */
    val settingsRowPadVLg: Dp = 12.dp
    /** Standard vertical padding for compact settings rows (slider/secondary) */
    val settingsRowPadVSm: Dp = 8.dp
    /** Spacer width between settings row content and trailing control */
    val settingsTrailingSpacerW: Dp = 8.dp
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
    /** Horizontal padding for the category screen list */
    val settingsCategoryScreenPadH: Dp = 16.dp
    /** Vertical spacing between category tiles */
    val settingsTileSpacing: Dp = 8.dp
    /** Top padding for the screen title header row */
    val settingsHeaderPadTop: Dp = 24.dp
    /** Bottom padding for the screen title header row */
    val settingsHeaderPadBottom: Dp = 16.dp
    /** Padding inside a category tile card */
    val settingsTilePad: Dp = 16.dp
    /** Corner radius for category tile cards */
    val settingsTileRadius: Dp = 12.dp
    /** Width of the trailing spacer in a category tile row */
    val settingsTileTrailingSpacerW: Dp = 8.dp
    /** Horizontal padding for the back arrow in sub-screen header */
    val settingsBackArrowPadEnd: Dp = 16.dp
    /** Bottom spacer height at the end of a category list */
    val settingsListBottomSpacerH: Dp = 16.dp

    // ══════════════════════════════════════════
    // Manager screen dimensions (Command / Macro)
    // ══════════════════════════════════════════
    /** Horizontal padding for manager screen top bar */
    val managerBarPadH: Dp = 4.dp
    /** Vertical padding for manager screen top bar */
    val managerBarPadV: Dp = 8.dp
    /** Horizontal padding for manager screen info text / list items */
    val managerRowPadH: Dp = 16.dp
    /** Vertical padding for manager screen info text rows */
    val managerInfoPadV: Dp = 8.dp
    /** Top padding for manager section labels */
    val managerSectionPadTop: Dp = 12.dp
    /** Bottom padding for manager section labels */
    val managerSectionPadBottom: Dp = 4.dp
    /** Padding for full-width manager items like empty state */
    val managerFullPad: Dp = 32.dp
    /** Small vertical spacer between title and subtitle in list items */
    val managerItemSubtitleSpacerH: Dp = 2.dp
    /** Top padding before a divider line inside manager dialogs */
    val managerDividerPadTop: Dp = 8.dp
    /** Vertical padding for manager horizontal spacing rows */
    val managerRowPadVSm: Dp = 4.dp
    /** Spacer between mode selector buttons in command app dialog */
    val managerDialogButtonSpacerW: Dp = 8.dp
    /** Vertical spacer inside command app dialog between form fields */
    val managerDialogFieldSpacerH: Dp = 16.dp
    /** Vertical spacer inside command app dialog before mode label */
    val managerDialogLabelSpacerH: Dp = 8.dp

    // ══════════════════════════════════════════
    // Welcome screen dimensions and typography
    // ══════════════════════════════════════════
    /** Outer padding for the welcome screen column */
    val welcomeScreenPad: Dp = 32.dp
    /** Large vertical spacer (top of screen / between major sections) */
    val welcomeSpacerLg: Dp = 48.dp
    /** Medium vertical spacer (between setup steps) */
    val welcomeSpacerMd: Dp = 16.dp
    /** Small vertical spacer (below title) */
    val welcomeSpacerSm: Dp = 8.dp
    /** Spacer below the settings button */
    val welcomeSpacerXl: Dp = 24.dp
    /** Corner radius for welcome screen cards (step rows, settings button) */
    val welcomeCardRadius: Dp = 12.dp
    /** Vertical padding for the settings button box */
    val welcomeButtonPadV: Dp = 16.dp
    /** Padding inside setup step cards */
    val welcomeCardPad: Dp = 16.dp
    /** Size of the step status indicator circle */
    val welcomeStepIndicatorSize: Dp = 32.dp
    /** Horizontal spacer between indicator and text in a step row */
    val welcomeStepTextSpacerW: Dp = 16.dp
    /** Spacing between title and optional badge inside a step row */
    val welcomeTagSpacing: Dp = 8.dp
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
    /** Background color for a completed setup step indicator circle */
    val setupCompleteGreen = Color(0xFF4CAF50)
    /** Text color on a completed step indicator (check mark on green bg) */
    val setupCompleteText = Color(0xFFFFFFFF)
}
