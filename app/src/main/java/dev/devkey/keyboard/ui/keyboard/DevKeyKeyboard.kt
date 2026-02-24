package dev.devkey.keyboard.ui.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.LatinKeyboardBaseView
import dev.devkey.keyboard.core.KeyboardActionBridge
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.ui.suggestion.SuggestionBar
import dev.devkey.keyboard.ui.toolbar.ToolbarRow

/**
 * Root composable for the DevKey keyboard.
 *
 * Assembles the toolbar, suggestion bar, and keyboard view into a single
 * keyboard UI, wired to the legacy IME action listener.
 *
 * @param actionListener The IME action listener to bridge key events to.
 */
@Composable
fun DevKeyKeyboard(actionListener: LatinKeyboardBaseView.OnKeyboardActionListener) {
    val modifierState = remember { ModifierStateManager() }
    val bridge = remember { KeyboardActionBridge(actionListener) }

    var isSuggestionCollapsed by remember { mutableStateOf(false) }
    val defaultSuggestions = remember { listOf("the", "I", "and") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Toolbar
        ToolbarRow(
            onClipboard = {},
            onVoice = {},
            onSymbols = {},
            onMacros = {},
            onOverflow = {}
        )

        // Suggestion bar
        SuggestionBar(
            suggestions = defaultSuggestions,
            onSuggestionClick = { suggestion -> bridge.onText(suggestion + " ") },
            onCollapseToggle = { isSuggestionCollapsed = !isSuggestionCollapsed },
            isCollapsed = isSuggestionCollapsed
        )

        // Keyboard view
        KeyboardView(
            layout = QwertyLayout.layout,
            modifierState = modifierState,
            onKeyAction = { code -> bridge.onKey(code, modifierState) },
            onKeyPress = { code -> bridge.onKeyPress(code) },
            onKeyRelease = { code -> bridge.onKeyRelease(code) }
        )
    }
}
