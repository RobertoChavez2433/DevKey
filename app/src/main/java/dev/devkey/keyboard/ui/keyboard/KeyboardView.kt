package dev.devkey.keyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Composable that renders the full keyboard grid.
 *
 * Lays out all key rows vertically with consistent spacing and padding.
 *
 * @param layout The keyboard layout data containing all rows.
 * @param modifierState The modifier state manager.
 * @param onKeyAction Callback when a key action fires.
 * @param onKeyPress Callback when a key is pressed.
 * @param onKeyRelease Callback when a key is released.
 */
@Composable
fun KeyboardView(
    layout: KeyboardLayoutData,
    modifierState: ModifierStateManager,
    onKeyAction: (Int) -> Unit,
    onKeyPress: (Int) -> Unit,
    onKeyRelease: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DevKeyTheme.keyboardBackground)
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (row in layout.rows) {
            KeyRow(
                row = row,
                modifierState = modifierState,
                onKeyAction = onKeyAction,
                onKeyPress = onKeyPress,
                onKeyRelease = onKeyRelease
            )
        }
    }
}
