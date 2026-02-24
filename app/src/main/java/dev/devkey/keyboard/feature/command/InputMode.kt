package dev.devkey.keyboard.feature.command

/**
 * Represents the current input mode of the keyboard.
 *
 * NORMAL: Standard text input with autocorrect and word predictions.
 * COMMAND: Terminal/command mode with command-specific suggestions.
 */
enum class InputMode {
    NORMAL,
    COMMAND
}
