package dev.devkey.keyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Key preview popup shown when a key is pressed.
 *
 * Displays a magnified label above the pressed key's position.
 *
 * @param label The label to display in the popup.
 * @param anchorBounds The bounds of the pressed key (used for positioning).
 * @param visible Whether the popup should be shown.
 */
@Composable
fun KeyPreviewPopup(
    label: String,
    anchorBounds: IntRect,
    visible: Boolean
) {
    if (!visible) return

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(
            x = anchorBounds.left + (anchorBounds.width - 48) / 2,
            y = anchorBounds.top - 60
        ),
        properties = PopupProperties(
            clippingEnabled = false
        )
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DevKeyTheme.keyPressed),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = DevKeyTheme.keyText,
                fontSize = 24.sp
            )
        }
    }
}
