package dev.devkey.keyboard.ui.suggestion

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Suggestion bar displayed above the keyboard rows.
 *
 * Shows up to 3 word suggestions separated by vertical dividers.
 * Can be collapsed/expanded with the arrow toggle.
 *
 * @param suggestions List of suggestion strings to display (up to 3).
 * @param onSuggestionClick Callback when a suggestion is tapped.
 * @param onCollapseToggle Callback to toggle collapsed state.
 * @param isCollapsed Whether the bar is currently collapsed.
 */
@Composable
fun SuggestionBar(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onCollapseToggle: () -> Unit,
    isCollapsed: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .height(if (isCollapsed) 0.dp else DevKeyTheme.suggestionBarHeight)
            .background(DevKeyTheme.keyboardBackground),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isCollapsed) {
            // Collapse toggle arrow
            Box(
                modifier = Modifier
                    .clickable { onCollapseToggle() }
                    .padding(horizontal = 8.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u25C0", // ◀
                    color = DevKeyTheme.suggestionText,
                    fontSize = DevKeyTheme.suggestionTextSize
                )
            }

            // Suggestion slots
            val displaySuggestions = suggestions.take(3)
            displaySuggestions.forEachIndexed { index, suggestion ->
                if (index > 0) {
                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(DevKeyTheme.dividerColor)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSuggestionClick(suggestion) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = suggestion,
                        color = DevKeyTheme.suggestionText,
                        fontSize = DevKeyTheme.suggestionTextSize
                    )
                }
            }
        }
    }
}
