package dev.devkey.keyboard.ui.suggestion

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.font.FontWeight
import dev.devkey.keyboard.feature.prediction.PredictionResult
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Suggestion bar displayed above the keyboard rows.
 *
 * Shows up to 3 word suggestions separated by vertical dividers.
 * Autocorrect suggestions are rendered bold. Long-press on a suggestion
 * triggers the [onSuggestionLongPress] callback (used for "Add to dictionary").
 *
 * @param predictions List of prediction results to display (up to 3).
 * @param onSuggestionClick Callback when a suggestion is tapped.
 * @param onSuggestionLongPress Callback when a suggestion is long-pressed.
 * @param onCollapseToggle Callback to toggle collapsed state.
 * @param isCollapsed Whether the bar is currently collapsed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SuggestionBar(
    predictions: List<PredictionResult>,
    onSuggestionClick: (PredictionResult) -> Unit,
    onSuggestionLongPress: (PredictionResult) -> Unit = {},
    onCollapseToggle: () -> Unit,
    isCollapsed: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .height(if (isCollapsed) DevKeyTheme.collapsedHeight else DevKeyTheme.suggestionBarHeight)
            .background(DevKeyTheme.kbBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isCollapsed) {
            // Collapse toggle arrow
            Box(
                modifier = Modifier
                    .clickable { onCollapseToggle() }
                    .padding(horizontal = DevKeyTheme.suggestionBarPadH)
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
            val displayPredictions = predictions.take(3)
            displayPredictions.forEachIndexed { index, prediction ->
                if (index > 0) {
                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .width(DevKeyTheme.dividerThickness)
                            .fillMaxHeight()
                            .background(DevKeyTheme.dividerColor)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .combinedClickable(
                            onClick = { onSuggestionClick(prediction) },
                            onLongClick = { onSuggestionLongPress(prediction) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = prediction.word,
                        color = DevKeyTheme.suggestionText,
                        fontSize = DevKeyTheme.suggestionTextSize,
                        fontWeight = if (prediction.isAutocorrect) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
