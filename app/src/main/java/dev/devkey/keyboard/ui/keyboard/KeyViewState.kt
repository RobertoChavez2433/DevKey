package dev.devkey.keyboard.ui.keyboard

import dev.devkey.keyboard.core.ModifierKeyState
import dev.devkey.keyboard.keyboard.model.Keyboard

internal data class ModifierVisualState(
    val isActive: Boolean,
    val isLocked: Boolean
)

internal data class KeyInteractionState(
    val isModifierKey: Boolean,
    val hasMultiPopup: Boolean,
    val ctrlShortcut: ShortcutInfo?
)

internal fun resolveModifierVisualState(
    key: KeyData,
    shiftState: ModifierKeyState,
    ctrlState: ModifierKeyState,
    altState: ModifierKeyState
): ModifierVisualState {
    val state = modifierStateForKey(key, shiftState, ctrlState, altState)
    return ModifierVisualState(
        isActive = state != null && state != ModifierKeyState.OFF,
        isLocked = state == ModifierKeyState.LOCKED
    )
}

internal fun resolveKeyInteractionState(
    key: KeyData,
    ctrlHeld: Boolean
): KeyInteractionState {
    val isModifierKey = isModifierLikeKey(key)
    return KeyInteractionState(
        isModifierKey = isModifierKey,
        hasMultiPopup = hasMultiLongPressPopup(key, isModifierKey),
        ctrlShortcut = resolveCtrlShortcut(ctrlHeld, key)
    )
}

internal fun isModifierLikeKey(key: KeyData): Boolean =
    key.type == KeyType.MODIFIER || isUtilityModifier(key)

internal fun hasMultiLongPressPopup(
    key: KeyData,
    isModifierKey: Boolean = isModifierLikeKey(key)
): Boolean = !isModifierKey && key.longPressCodes.orEmpty().size >= 2

internal fun resolveCtrlShortcut(
    ctrlHeld: Boolean,
    key: KeyData
): ShortcutInfo? {
    if (!ctrlHeld || key.type != KeyType.LETTER) return null
    return CtrlShortcutMap.getShortcut(key.primaryLabel.lowercase())
}

internal fun resolvePreviewLabel(
    key: KeyData,
    displayLabel: String,
    longPressFired: Boolean
): String {
    if (!longPressFired || key.longPressCode == null) return displayLabel
    return key.longPressLabel ?: key.longPressCode.toChar().toString()
}

private fun modifierStateForKey(
    key: KeyData,
    shiftState: ModifierKeyState,
    ctrlState: ModifierKeyState,
    altState: ModifierKeyState
): ModifierKeyState? = when {
    key.type == KeyType.MODIFIER && key.primaryCode == Keyboard.KEYCODE_SHIFT -> shiftState
    key.type == KeyType.MODIFIER && key.primaryCode == KeyCodes.CTRL_LEFT -> ctrlState
    key.type == KeyType.MODIFIER && key.primaryCode == KeyCodes.ALT_LEFT -> altState
    isUtilityModifier(key) && key.primaryCode == KeyCodes.CTRL_LEFT -> ctrlState
    isUtilityModifier(key) && key.primaryCode == KeyCodes.ALT_LEFT -> altState
    else -> null
}

private fun isUtilityModifier(key: KeyData): Boolean =
    key.type == KeyType.UTILITY &&
        (key.primaryCode == KeyCodes.CTRL_LEFT || key.primaryCode == KeyCodes.ALT_LEFT)
