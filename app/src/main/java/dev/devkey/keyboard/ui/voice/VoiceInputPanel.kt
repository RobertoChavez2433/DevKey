package dev.devkey.keyboard.ui.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import dev.devkey.keyboard.feature.voice.VoiceInputEngine
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

/**
 * Voice input panel shown when the keyboard is in voice mode.
 *
 * Replaces the keyboard area with a recording UI that includes:
 * - Status text ("Listening..." / "Processing...")
 * - Animated mic indicator
 * - Waveform visualization based on audio amplitude
 * - Cancel and Done buttons
 *
 * @param voiceState The current state of the voice input engine.
 * @param amplitude Current audio amplitude (0.0 to 1.0) for waveform visualization.
 * @param onStop Callback when the user taps "Done" to stop recording and transcribe.
 * @param onCancel Callback when the user cancels voice input.
 */
@Composable
fun VoiceInputPanel(
    voiceState: VoiceInputEngine.VoiceState,
    amplitude: Float,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    // Pulsing animation for mic icon during listening
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(DevKeyThemeDimensions.voicePanelHeight)
            .background(DevKeyThemeColors.voicePanelBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Status text
        Text(
            text = when (voiceState) {
                VoiceInputEngine.VoiceState.LISTENING -> "Listening..."
                VoiceInputEngine.VoiceState.PROCESSING -> "Processing..."
                VoiceInputEngine.VoiceState.ERROR -> "Error"
                else -> ""
            },
            color = DevKeyThemeColors.voiceStatusText,
            fontSize = DevKeyThemeTypography.voiceStatusSize,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(DevKeyThemeDimensions.voiceSpacerLg))

        // Mic icon
        Box(
            modifier = Modifier
                .size(DevKeyThemeDimensions.voiceMicSize)
                .let { mod ->
                    if (voiceState == VoiceInputEngine.VoiceState.LISTENING) {
                        mod.scale(pulseScale)
                    } else {
                        mod
                    }
                }
                .background(
                    color = if (voiceState == VoiceInputEngine.VoiceState.LISTENING) {
                        DevKeyThemeColors.voiceMicActive
                    } else {
                        DevKeyThemeColors.voiceMicInactive
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\uD83C\uDFA4", // microphone emoji
                fontSize = DevKeyThemeTypography.fontVoiceMic
            )
        }

        Spacer(modifier = Modifier.height(DevKeyThemeDimensions.voiceSpacerMd))

        // Waveform visualization (only visible when listening)
        if (voiceState == VoiceInputEngine.VoiceState.LISTENING) {
            Canvas(
                modifier = Modifier
                    .width(DevKeyThemeDimensions.voiceWaveformWidth)
                    .height(DevKeyThemeDimensions.voiceWaveformHeight)
            ) {
                val barCount = 7
                val barWidth = size.width / (barCount * 2)
                val centerY = size.height / 2

                for (i in 0 until barCount) {
                    // Create varying heights based on amplitude and position
                    val distFromCenter = kotlin.math.abs(i - barCount / 2).toFloat()
                    val heightFactor = (1f - distFromCenter / barCount) * amplitude
                    val barHeight = (size.height * 0.3f + size.height * 0.7f * heightFactor)
                        .coerceIn(4f, size.height)
                    val x = barWidth + i * barWidth * 2

                    drawLine(
                        color = DevKeyThemeColors.voiceWaveform,
                        start = Offset(x, centerY - barHeight / 2),
                        end = Offset(x, centerY + barHeight / 2),
                        strokeWidth = barWidth * 0.8f
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(DevKeyThemeDimensions.voiceSpacerMd))

        // Buttons / Progress
        when (voiceState) {
            VoiceInputEngine.VoiceState.PROCESSING -> {
                CircularProgressIndicator(
                    color = DevKeyThemeColors.voiceMicActive,
                    modifier = Modifier.size(DevKeyThemeDimensions.voiceProgressSize),
                    strokeWidth = DevKeyThemeDimensions.voiceProgressStrokeWidth
                )
            }
            VoiceInputEngine.VoiceState.LISTENING -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DevKeyThemeDimensions.voiceButtonSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text(
                            text = "Cancel",
                            color = DevKeyThemeColors.voiceMicInactive
                        )
                    }
                    TextButton(onClick = onStop) {
                        Text(
                            text = "Done",
                            color = DevKeyThemeColors.voiceMicActive,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            else -> {
                TextButton(onClick = onCancel) {
                    Text(
                        text = "Cancel",
                        color = DevKeyThemeColors.voiceMicInactive
                    )
                }
            }
        }
    }
}
