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
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Toolbar row displayed at the top of the keyboard.
 *
 * Contains icon buttons for clipboard, voice, symbols, macros, and overflow.
 * All buttons have real actions. The macros button supports long-press to open the grid.
 * When command mode is active, a "CMD" badge is shown.
 *
 * @param onClipboard Callback for clipboard button.
 * @param onVoice Callback for voice button.
 * @param onSymbols Callback for symbols button.
 * @param onMacros Callback for macros button (tap).
 * @param onMacrosLongPress Callback for macros button (long-press).
 * @param onOverflow Callback for overflow button.
 * @param activeMode The current keyboard mode for visual active state.
 * @param isCommandMode Whether command mode is currently active.
 * @param onCommandModeToggle Callback to toggle command mode manually.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolbarRow(
    onClipboard: () -> Unit,
    onVoice: () -> Unit,
    onSymbols: () -> Unit,
    onMacros: () -> Unit,
    onMacrosLongPress: () -> Unit = {},
    onOverflow: () -> Unit,
    activeMode: KeyboardMode = KeyboardMode.Normal,
    isCommandMode: Boolean = false,
    onCommandModeToggle: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DevKeyTheme.toolbarHeight)
                .background(DevKeyTheme.kbBg),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarButton(
                label = "\uD83D\uDCCB", // clipboard emoji
                isActive = activeMode is KeyboardMode.Clipboard,
                onClick = { onClipboard() }
            )
            ToolbarButton(
                label = "\uD83C\uDFA4", // microphone emoji
                isActive = activeMode is KeyboardMode.Voice,
                onClick = { onVoice() }
            )
            ToolbarButton(
                label = "123",
                isActive = activeMode is KeyboardMode.Symbols,
                onClick = { onSymbols() }
            )
            // Macros button with long-press support
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .combinedClickable(
                        onClick = { onMacros() },
                        onLongClick = { onMacrosLongPress() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val macroActive = activeMode is KeyboardMode.MacroChips ||
                        activeMode is KeyboardMode.MacroGrid ||
                        activeMode is KeyboardMode.MacroRecording
                Text(
                    text = "\u26A1", // lightning bolt
                    color = if (macroActive) DevKeyTheme.keyText else DevKeyTheme.iconColor,
                    fontSize = DevKeyTheme.fontToolbarIcon
                )
            }
            // Overflow button — toggles command mode for now (proper menu in Session 5)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable {
                        onCommandModeToggle()
                        onOverflow()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\u00B7\u00B7\u00B7", // middle dots (···)
                        color = DevKeyTheme.iconColor,
                        fontSize = DevKeyTheme.fontToolbarIcon
                    )
                    // CMD badge — shown when command mode is active
                    if (isCommandMode) {
                        Box(
                            modifier = Modifier
                                .padding(start = DevKeyTheme.cmdBadgeStartPad)
                                .height(DevKeyTheme.cmdBadgeHeight)
                                .background(
                                    color = DevKeyTheme.cmdBadgeBg,
                                    shape = RoundedCornerShape(DevKeyTheme.cmdBadgeRadius)
                                )
                                .padding(horizontal = DevKeyTheme.cmdBadgePadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CMD",
                                color = DevKeyTheme.cmdBadgeText,
                                fontSize = DevKeyTheme.cmdBadgeTextSize
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
                .height(DevKeyTheme.dividerThickness)
                .background(DevKeyTheme.dividerColor)
        )
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isActive) DevKeyTheme.keyText else DevKeyTheme.iconColor,
            fontSize = DevKeyTheme.fontToolbarIcon
        )
    }
}
