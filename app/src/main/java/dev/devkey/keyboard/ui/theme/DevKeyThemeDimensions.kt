package dev.devkey.keyboard.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dimension tokens (Dp) for the DevKey keyboard theme.
 *
 * HARD RULE: every consumer of a dimension value in ui/ MUST reference a named token
 * declared here. No inline literals anywhere in ui/ except inside theme files.
 */
object DevKeyThemeDimensions {

    // -- Keyboard spacing/shape --
    val keyGap: Dp = 3.dp
    val keyRadius: Dp = 7.dp
    val kbPadH: Dp = 5.dp
    val kbPadV: Dp = 5.dp

    // -- KeyView hint/lock padding --
    val hintLabelPadEnd: Dp = 3.dp
    val hintLabelPadTop: Dp = 2.dp
    val lockDotPadBottom: Dp = 4.dp
    val lockDotSize: Dp = 4.dp

    // -- KeyboardView row height fallbacks --
    val keyAreaMinHeight: Dp = 48.dp
    val rowHeightFallback: Dp = 40.dp

    // -- Suggestion bar / toolbar --
    val suggestionBarHeight: Dp = 40.dp
    val toolbarHeight: Dp = 36.dp
    val chevronRowHeight: Dp = 14.dp
    val chevronRowIconPad: Dp = 6.dp
    val suggestionBarPadH: Dp = 8.dp
    val dividerThickness: Dp = 1.dp
    val collapsedHeight: Dp = 0.dp

    // -- Ctrl mode / command badge --
    val ctrlModeBannerHeight: Dp = 24.dp
    val cmdBadgeHeight: Dp = 20.dp
    val cmdBadgePadding: Dp = 4.dp
    val cmdBadgeRadius: Dp = 4.dp
    val cmdBadgeStartPad: Dp = 4.dp

    // -- Popup candidate cells --
    val popupRadius: Dp = 8.dp
    val popupCellPadH: Dp = 10.dp
    val popupCellPadV: Dp = 8.dp
    val popupCellGlyphWidth: Dp = 24.dp
    val popupCellGlyphHeight: Dp = 28.dp
    val popupAnchorGap: Dp = 4.dp

    // -- Macro UI --
    val macroGridCellHeight: Dp = 56.dp
    val macroGridMaxHeight: Dp = 168.dp
    val recordingBarHeight: Dp = 76.dp
    val chipRadius: Dp = 16.dp
    val chipPadH: Dp = 8.dp
    val chipPadV: Dp = 4.dp
    val chipStripSpacing: Dp = 8.dp
    val chipInnerSpacing: Dp = 4.dp
    val macroRecordingTopBorderH: Dp = 2.dp
    val macroRecordingIndicatorSpacing: Dp = 6.dp
    val macroRecordingDotSize: Dp = 8.dp
    val macroRecordingBarPadH: Dp = 8.dp
    val macroRecordingBarPadV: Dp = 4.dp
    val macroStepSpacing: Dp = 4.dp
    val macroStepArrowPadH: Dp = 2.dp
    val macroGridPadH: Dp = 4.dp
    val macroCellRadius: Dp = 8.dp
    val macroCellPad: Dp = 4.dp
    val macroGridSpacing: Dp = 4.dp

    // -- Voice panel --
    val voicePanelHeight: Dp = 200.dp
    val voiceMicSize: Dp = 64.dp
    val voiceSpacerLg: Dp = 16.dp
    val voiceSpacerMd: Dp = 12.dp
    val voiceWaveformWidth: Dp = 120.dp
    val voiceWaveformHeight: Dp = 32.dp
    val voiceProgressSize: Dp = 24.dp
    val voiceProgressStrokeWidth: Dp = 2.dp
    val voiceButtonSpacing: Dp = 32.dp

    // -- Clipboard panel --
    val clipboardPanelMaxHeight: Dp = 200.dp
    val clipboardEntryHeight: Dp = 48.dp
    val clipboardPadH: Dp = 8.dp
    val clipboardEntryPadV: Dp = 4.dp
    val clipboardSearchRadius: Dp = 8.dp
    val clipboardEntrySpacing: Dp = 4.dp

    // -- Settings UI --
    val settingsCategoryPadTop: Dp = 16.dp
    val settingsCategoryPadBottom: Dp = 4.dp
    val settingsCategoryPadH: Dp = 16.dp
    val settingsCategorySpacerH: Dp = 4.dp
    val settingsRowPadH: Dp = 16.dp
    val settingsRowPadVLg: Dp = 12.dp
    val settingsRowPadVSm: Dp = 8.dp
    val settingsTrailingSpacerW: Dp = 8.dp
    val settingsCategoryScreenPadH: Dp = 16.dp
    val settingsTileSpacing: Dp = 8.dp
    val settingsHeaderPadTop: Dp = 24.dp
    val settingsHeaderPadBottom: Dp = 16.dp
    val settingsTilePad: Dp = 16.dp
    val settingsTileRadius: Dp = 12.dp
    val settingsTileTrailingSpacerW: Dp = 8.dp
    val settingsBackArrowPadEnd: Dp = 16.dp
    val settingsListBottomSpacerH: Dp = 16.dp

    // -- Manager screens (Command / Macro) --
    val managerBarPadH: Dp = 4.dp
    val managerBarPadV: Dp = 8.dp
    val managerRowPadH: Dp = 16.dp
    val managerInfoPadV: Dp = 8.dp
    val managerSectionPadTop: Dp = 12.dp
    val managerSectionPadBottom: Dp = 4.dp
    val managerFullPad: Dp = 32.dp
    val managerItemSubtitleSpacerH: Dp = 2.dp
    val managerDividerPadTop: Dp = 8.dp
    val managerRowPadVSm: Dp = 4.dp
    val managerDialogButtonSpacerW: Dp = 8.dp
    val managerDialogFieldSpacerH: Dp = 16.dp
    val managerDialogLabelSpacerH: Dp = 8.dp

    // -- Welcome screen --
    val welcomeScreenPad: Dp = 32.dp
    val welcomeSpacerLg: Dp = 48.dp
    val welcomeSpacerMd: Dp = 16.dp
    val welcomeSpacerSm: Dp = 8.dp
    val welcomeSpacerXl: Dp = 24.dp
    val welcomeCardRadius: Dp = 12.dp
    val welcomeButtonPadV: Dp = 16.dp
    val welcomeCardPad: Dp = 16.dp
    val welcomeStepIndicatorSize: Dp = 32.dp
    val welcomeStepTextSpacerW: Dp = 16.dp
    val welcomeTagSpacing: Dp = 8.dp
}
