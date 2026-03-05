package dev.devkey.keyboard.ui.macro

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.devkey.keyboard.feature.macro.MacroStep
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Recording bar displayed during macro recording.
 *
 * Shows a pulsing red indicator, captured steps, and cancel/stop buttons.
 * Replaces the toolbar and suggestion bar during recording mode.
 *
 * @param capturedSteps The list of steps captured so far.
 * @param onCancel Callback to cancel recording.
 * @param onStop Callback to stop recording (parent handles naming dialog).
 */
@Composable
fun MacroRecordingBar(
    capturedSteps: List<MacroStep>,
    onCancel: () -> Unit,
    onStop: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recordingPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Auto-scroll to end when new steps are captured
    val listState = rememberLazyListState()
    LaunchedEffect(capturedSteps.size) {
        if (capturedSteps.isNotEmpty()) {
            listState.animateScrollToItem(capturedSteps.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DevKeyTheme.recordingBarHeight)
            .background(DevKeyTheme.kbBg)
    ) {
        // Red top border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(DevKeyTheme.macroRecordingRed)
                .align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Pulsing red circle + "Recording..."
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(pulseAlpha)
                        .clip(CircleShape)
                        .background(DevKeyTheme.macroRecordingRed)
                )
                Text(
                    text = "Recording...",
                    color = DevKeyTheme.macroRecordingRed,
                    fontSize = 12.sp
                )
            }

            // Center: Captured steps
            LazyRow(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(capturedSteps, key = { index, _ -> index }) { index, step ->
                    val stepText = buildString {
                        for (mod in step.modifiers) {
                            append(mod.replaceFirstChar { it.uppercase() })
                            append("+")
                        }
                        append(step.key.uppercase())
                    }
                    Text(
                        text = stepText,
                        color = DevKeyTheme.keyText,
                        fontSize = 11.sp
                    )
                    if (capturedSteps.isNotEmpty() && step != capturedSteps.lastOrNull()) {
                        Text(
                            text = "\u2192", // →
                            color = DevKeyTheme.timestampText,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }

            // Right: Cancel + Stop buttons
            TextButton(onClick = onCancel) {
                Text(
                    text = "Cancel",
                    color = DevKeyTheme.timestampText,
                    fontSize = 12.sp
                )
            }
            TextButton(onClick = onStop) {
                Text(
                    text = "Stop",
                    color = DevKeyTheme.macroRecordingRed,
                    fontSize = 12.sp
                )
            }
        }
    }
}
