package dev.devkey.keyboard.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.devkey.keyboard.core.ModifierStateManager

/**
 * Composable for a single row of keyboard keys.
 *
 * Lays out keys horizontally with weighted widths and consistent spacing.
 *
 * @param row The row data containing the list of keys.
 * @param modifierState The modifier state manager.
 * @param onKeyAction Callback when a key action fires.
 * @param onKeyPress Callback when a key is pressed.
 * @param onKeyRelease Callback when a key is released.
 */
@Composable
fun KeyRow(
    row: KeyRowData,
    modifierState: ModifierStateManager,
    onKeyAction: (Int) -> Unit,
    onKeyPress: (Int) -> Unit,
    onKeyRelease: (Int) -> Unit,
    ctrlHeld: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(row.rowHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (key in row.keys) {
            KeyView(
                key = key,
                modifierState = modifierState,
                onKeyAction = onKeyAction,
                onKeyPress = onKeyPress,
                onKeyRelease = onKeyRelease,
                ctrlHeld = ctrlHeld,
                modifier = Modifier.weight(key.weight)
            )
        }
    }
}
