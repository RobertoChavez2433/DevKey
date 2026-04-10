package dev.devkey.keyboard.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.ui.theme.DevKeyTheme
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions

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
 * @param rowHeight Explicit row height from parent's weight calculation.
 * @param modifier Optional modifier for the row.
 */
@Composable
fun KeyRow(
    row: KeyRowData,
    modifierState: ModifierStateManager,
    onKeyAction: (Int) -> Unit,
    onKeyPress: (Int) -> Unit,
    onKeyRelease: (Int) -> Unit,
    ctrlHeld: Boolean = false,
    showHints: Boolean = false,
    hintBright: Boolean = false,
    rowHeight: Dp = DevKeyThemeDimensions.keyAreaMinHeight,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight),
        horizontalArrangement = Arrangement.spacedBy(DevKeyThemeDimensions.keyGap)
    ) {
        for (key in row.keys) {
            KeyView(
                key = key,
                modifierState = modifierState,
                onKeyAction = onKeyAction,
                onKeyPress = onKeyPress,
                onKeyRelease = onKeyRelease,
                ctrlHeld = ctrlHeld,
                showHints = showHints,
                hintBright = hintBright,
                modifier = Modifier.weight(key.weight).fillMaxHeight()
            )
        }
    }
}
