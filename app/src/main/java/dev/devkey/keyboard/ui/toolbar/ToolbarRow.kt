package dev.devkey.keyboard.ui.toolbar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.ui.keyboard.KeyboardMode
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

/**
 * Toolbar row displayed at the top of the keyboard.
 *
 * Contains icon buttons for clipboard, voice, symbols, macros, and overflow.
 * All buttons have real actions. The macros button supports long-press to open the grid.
 * When command mode is active, a "CMD" badge is shown.
 *
 * @param onClipboard Callback for clipboard button.
 * @param onSymbols Callback for symbols button.
 * @param onMacros Callback for macros button (tap).
 * @param onOverflow Callback for overflow button.
 * @param onVoice Optional callback for voice button. Voice is hidden when null.
 * @param onMacrosLongPress Callback for macros button (long-press).
 * @param activeMode The current keyboard mode for visual active state.
 * @param isCommandMode Whether command mode is currently active.
 * @param onCommandModeToggle Callback to toggle command mode manually.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolbarRow(
    onClipboard: () -> Unit,
    onSymbols: () -> Unit,
    onMacros: () -> Unit,
    onOverflow: () -> Unit,
    onVoice: (() -> Unit)? = null,
    onMacrosLongPress: () -> Unit = {},
    activeMode: KeyboardMode = KeyboardMode.Normal,
    isCommandMode: Boolean = false,
    onCommandModeToggle: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DevKeyThemeDimensions.toolbarHeight)
                .background(DevKeyThemeColors.kbBg),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarButton(
                id = "clipboard",
                label = "\uD83D\uDCCB", // clipboard emoji
                action = "toggle_clipboard",
                isActive = activeMode is KeyboardMode.Clipboard,
                onClick = onClipboard
            )
            if (onVoice != null) {
                ToolbarButton(
                    id = "voice",
                    label = "\uD83C\uDFA4", // microphone emoji
                    action = "toggle_voice",
                    isActive = activeMode is KeyboardMode.Voice,
                    onClick = onVoice
                )
            }
            ToolbarButton(
                id = "symbols",
                label = "123",
                action = "toggle_symbols",
                isActive = activeMode is KeyboardMode.Symbols,
                onClick = onSymbols
            )
            // Macros button with long-press support
            val macroActive = activeMode is KeyboardMode.MacroChips ||
                    activeMode is KeyboardMode.MacroGrid ||
                    activeMode is KeyboardMode.MacroRecording
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(
                        toolbarInventoryModifier(
                            id = "macros",
                            action = "toggle_macro_chips",
                            longAction = "toggle_macro_grid",
                            isActive = macroActive
                        )
                    )
                    .combinedClickable(
                        onClick = {
                            logToolbarAction("macros", "toggle_macro_chips")
                            onMacros()
                        },
                        onLongClick = {
                            logToolbarAction("macros", "toggle_macro_grid")
                            onMacrosLongPress()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u26A1", // lightning bolt
                    color = if (macroActive) DevKeyThemeColors.keyText else DevKeyThemeColors.iconColor,
                    fontSize = DevKeyThemeTypography.fontToolbarIcon
                )
            }
            // Overflow button — toggles command mode for now (proper menu in Session 5)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(
                        toolbarInventoryModifier(
                            id = "overflow",
                            action = "toggle_command_mode",
                            isActive = isCommandMode
                        )
                    )
                    .clickable {
                        logToolbarAction("overflow", "toggle_command_mode")
                        onCommandModeToggle()
                        onOverflow()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\u00B7\u00B7\u00B7", // middle dots (···)
                        color = DevKeyThemeColors.iconColor,
                        fontSize = DevKeyThemeTypography.fontToolbarIcon
                    )
                    // CMD badge — shown when command mode is active
                    if (isCommandMode) {
                        Box(
                            modifier = Modifier
                                .padding(start = DevKeyThemeDimensions.cmdBadgeStartPad)
                                .height(DevKeyThemeDimensions.cmdBadgeHeight)
                                .background(
                                    color = DevKeyThemeColors.cmdBadgeBg,
                                    shape = RoundedCornerShape(DevKeyThemeDimensions.cmdBadgeRadius)
                                )
                                .padding(horizontal = DevKeyThemeDimensions.cmdBadgePadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CMD",
                                color = DevKeyThemeColors.cmdBadgeText,
                                fontSize = DevKeyThemeTypography.cmdBadgeTextSize
                            )
                        }
                    }
                }
            }
        }

        // 1px bottom border divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DevKeyThemeDimensions.dividerThickness)
                .background(DevKeyThemeColors.dividerColor)
        )
    }
}

@Composable
private fun ToolbarButton(
    id: String,
    label: String,
    action: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .then(toolbarInventoryModifier(id = id, action = action, isActive = isActive))
            .clickable {
                logToolbarAction(id, action)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isActive) DevKeyThemeColors.keyText else DevKeyThemeColors.iconColor,
            fontSize = DevKeyThemeTypography.fontToolbarIcon
        )
    }
}
