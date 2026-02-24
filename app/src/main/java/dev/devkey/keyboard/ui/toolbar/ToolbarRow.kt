package dev.devkey.keyboard.ui.toolbar

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Toolbar row displayed at the top of the keyboard.
 *
 * Contains icon buttons for clipboard, voice, symbols, macros, and overflow.
 * All buttons currently show a "Coming soon" toast.
 *
 * @param onClipboard Callback for clipboard button.
 * @param onVoice Callback for voice button.
 * @param onSymbols Callback for symbols button.
 * @param onMacros Callback for macros button.
 * @param onOverflow Callback for overflow button.
 */
@Composable
fun ToolbarRow(
    onClipboard: () -> Unit,
    onVoice: () -> Unit,
    onSymbols: () -> Unit,
    onMacros: () -> Unit,
    onOverflow: () -> Unit
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
            ToolbarButton(label = "\uD83D\uDCCB") { // clipboard emoji
                onClipboard()
                Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
            }
            ToolbarButton(label = "\uD83C\uDFA4") { // microphone emoji
                onVoice()
                Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
            }
            ToolbarButton(label = "123") {
                onSymbols()
                Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
            }
            ToolbarButton(label = "\u26A1") { // lightning bolt
                onMacros()
                Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
            }
            ToolbarButton(label = "\u00B7\u00B7\u00B7") { // middle dots (···)
                onOverflow()
                Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
            }
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
private fun ToolbarButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = DevKeyTheme.iconColor,
            fontSize = 16.sp
        )
    }
}
