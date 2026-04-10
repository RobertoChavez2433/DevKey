package dev.devkey.keyboard.core

/**
 * States a modifier key can be in.
 */
enum class ModifierKeyState {
    OFF,
    ONE_SHOT,
    LOCKED,
    HELD,
    /** Modifier is held while another key is being pressed (chording). */
    CHORDING
}

/**
 * Types of modifier keys tracked by the state machine.
 */
enum class ModifierType {
    SHIFT,
    CTRL,
    ALT,
    META
}
