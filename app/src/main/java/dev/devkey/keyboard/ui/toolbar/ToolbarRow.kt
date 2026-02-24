package dev.devkey.keyboard.ui.toolbar

import android.widget.Toast
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.devkey.keyboard.ui.keyboard.KeyboardMode
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Toolbar row displayed at the top of the keyboard.
 *
 * Contains icon buttons for clipboard, voice, symbols, macros, and overflow.
 * Clipboard, symbols, and macros buttons have real actions. Voice and overflow
 * show a "Coming soon" toast. The macros button supports long-press to open the grid.
 *
 * @param onClipboard Callback for clipboard button.
 * @param onVoice Callback for voice button.
 * @param onSymbols Callback for symbols button.
 * @param onMacros Callback for macros button (tap).
 * @param onMacrosLongPress Callback for macros button (long-press).
 * @param onOverflow Callback for overflow button.
 * @param activeMode The current keyboard mode for visual active state.
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
    activeMode: KeyboardMode = KeyboardMode.Normal
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DevKeyTheme.toolbarHeight)
                .background(DevKeyTheme.keyboardBackground),
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
                isActive = false,
                onClick = {
                    onVoice()
                    Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
                }
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
                    fontSize = 16.sp
                )
            }
            ToolbarButton(
                label = "\u00B7\u00B7\u00B7", // middle dots (···)
                isActive = false,
                onClick = {
                    onOverflow()
                    Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 1px bottom border divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
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
            fontSize = 16.sp
        )
    }
}
