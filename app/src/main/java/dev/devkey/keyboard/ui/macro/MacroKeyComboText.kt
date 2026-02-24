package dev.devkey.keyboard.ui.macro

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import dev.devkey.keyboard.feature.macro.MacroSerializer
import dev.devkey.keyboard.feature.macro.MacroStep

/**
 * Composable that renders a macro's steps as human-readable text.
 *
 * Format: "Ctrl+A -> Ctrl+C" (modifiers capitalized, joined with "+", steps joined with " -> ")
 *
 * @param steps The macro steps to display.
 * @param textColor The color for the text.
 * @param textSize The font size for the text.
 */
@Composable
fun MacroKeyComboText(
    steps: List<MacroStep>,
    textColor: Color,
    textSize: TextUnit
) {
    Text(
        text = formatSteps(steps),
        color = textColor,
        fontSize = textSize
    )
}

/**
 * Formats a list of macro steps into a human-readable string.
 *
 * @param steps The macro steps to format.
 * @return Formatted string like "Ctrl+A -> Ctrl+C"
 */
fun formatSteps(steps: List<MacroStep>): String {
    return steps.joinToString(" \u2192 ") { step ->
        val parts = mutableListOf<String>()
        for (mod in step.modifiers) {
            parts.add(mod.replaceFirstChar { it.uppercase() })
        }
        parts.add(step.key.uppercase())
        parts.joinToString("+")
    }
}

/**
 * Parses a JSON string of macro steps and formats them as human-readable text.
 *
 * @param json The JSON string representing macro steps.
 * @return Formatted string like "Ctrl+A -> Ctrl+C"
 */
fun formatFromJson(json: String): String {
    val steps = MacroSerializer.deserialize(json)
    return formatSteps(steps)
}
