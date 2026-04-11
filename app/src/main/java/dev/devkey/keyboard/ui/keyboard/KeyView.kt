package dev.devkey.keyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.core.ModifierKeyState
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions

/**
 * Composable for a single keyboard key.
 *
 * Handles rendering, press animation, long-press, key repeat, modifier behavior,
 * and theme-based coloring per KeyType.
 *
 * When a key's longPressCodes has ≥2 entries, a Compose Popup opens on long-press
 * offering all candidates. The user drags to a candidate and releases to select it.
 * Releasing without dragging (or dragging back to origin) selects the default (index 0).
 *
 * Rendering is delegated to:
 *   - [KeyBackground]    — color + scale animation helpers
 *   - [KeyGlyph]         — label, hint, and lock dot composables
 *   - [KeyPopupPreview]  — multi-char long-press candidate popup
 *   - [KeyGestureHandler] — all pointer-input / gesture handling
 */
@Composable
fun KeyView(
    key: KeyData,
    modifierState: ModifierStateManager,
    onKeyAction: (Int) -> Unit,
    onKeyPress: (Int) -> Unit,
    onKeyRelease: (Int) -> Unit,
    ctrlHeld: Boolean = false,
    showHints: Boolean = false,
    hintBright: Boolean = false,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Shared MutableState objects — read here for animation, mutated by the gesture handler.
    val isPressedState = remember { mutableStateOf(false) }
    val popupCodesState = remember { mutableStateOf<List<Int>?>(null) }
    val popupActiveIndexState = remember { mutableIntStateOf(0) }
    val keySizeState = remember { mutableStateOf(IntSize.Zero) }

    val isPressed by isPressedState
    val popupCodes by popupCodesState
    val popupActiveIndex by popupActiveIndexState

    // Observe modifier states for visual feedback
    val shiftState by modifierState.shiftState.collectAsState()
    val ctrlState by modifierState.ctrlState.collectAsState()
    val altState by modifierState.altState.collectAsState()

    val (normalBg, normalText) = getKeyColors(key)
    val activeColor = getActiveKeyColor(key)

    // Determine if this modifier key is active
    val isModifierActive = when {
        key.type == KeyType.MODIFIER && key.primaryCode == Keyboard.KEYCODE_SHIFT ->
            shiftState != ModifierKeyState.OFF
        key.type == KeyType.MODIFIER && key.primaryCode == KeyCodes.CTRL_LEFT ->
            ctrlState != ModifierKeyState.OFF
        key.type == KeyType.MODIFIER && key.primaryCode == KeyCodes.ALT_LEFT ->
            altState != ModifierKeyState.OFF
        // UTILITY type Ctrl/Alt also need active state
        key.type == KeyType.UTILITY && key.primaryCode == KeyCodes.CTRL_LEFT ->
            ctrlState != ModifierKeyState.OFF
        key.type == KeyType.UTILITY && key.primaryCode == KeyCodes.ALT_LEFT ->
            altState != ModifierKeyState.OFF
        else -> false
    }

    val isModifierLocked = when {
        key.type == KeyType.MODIFIER && key.primaryCode == Keyboard.KEYCODE_SHIFT ->
            shiftState == ModifierKeyState.LOCKED
        key.type == KeyType.MODIFIER && key.primaryCode == KeyCodes.CTRL_LEFT ->
            ctrlState == ModifierKeyState.LOCKED
        key.type == KeyType.MODIFIER && key.primaryCode == KeyCodes.ALT_LEFT ->
            altState == ModifierKeyState.LOCKED
        key.type == KeyType.UTILITY && key.primaryCode == KeyCodes.CTRL_LEFT ->
            ctrlState == ModifierKeyState.LOCKED
        key.type == KeyType.UTILITY && key.primaryCode == KeyCodes.ALT_LEFT ->
            altState == ModifierKeyState.LOCKED
        else -> false
    }

    // Ctrl Mode shortcut lookup for letter keys
    val ctrlShortcut = if (ctrlHeld && key.type == KeyType.LETTER) {
        CtrlShortcutMap.getShortcut(key.primaryLabel.lowercase())
    } else null

    val backgroundColor = animateKeyBackgroundColor(
        isPressed = isPressed,
        isModifierActive = isModifierActive,
        activeColor = activeColor,
        normalBg = normalBg,
        ctrlHeld = ctrlHeld,
        key = key,
        ctrlShortcut = ctrlShortcut
    )
    val scale = animateKeyScale(isPressed)

    val displayLabel = resolveDisplayLabel(key)
    val textSize = resolveTextSize(key)
    val ctrlTextColor = resolveCtrlTextColor(ctrlHeld, key, ctrlShortcut)
    val effectiveTextColor = ctrlTextColor ?: normalText

    // Determine if this key is a modifier-like key (needs tap-only handling)
    val isModifierKey = key.type == KeyType.MODIFIER ||
            (key.type == KeyType.UTILITY &&
                    (key.primaryCode == KeyCodes.CTRL_LEFT || key.primaryCode == KeyCodes.ALT_LEFT))

    // Whether this key uses the multi-char popup on long-press
    val hasMultiPopup = !isModifierKey &&
            key.longPressCodes != null &&
            key.longPressCodes.size >= 2

    Box(
        modifier = modifier
            .testTag("key_${key.primaryCode}")
            .scale(scale)
            .clip(RoundedCornerShape(DevKeyThemeDimensions.keyRadius))
            .background(backgroundColor)
            .onGloballyPositioned { coords ->
                keySizeState.value = coords.size
            }
            .keyGestureHandler(
                key = key,
                modifierState = modifierState,
                isModifierKey = isModifierKey,
                hasMultiPopup = hasMultiPopup,
                isPressed = isPressedState,
                popupCodes = popupCodesState,
                popupActiveIndex = popupActiveIndexState,
                keySize = keySizeState,
                density = density,
                view = view,
                coroutineScope = coroutineScope,
                onKeyAction = onKeyAction,
                onKeyPress = onKeyPress,
                onKeyRelease = onKeyRelease
            ),
        contentAlignment = Alignment.Center
    ) {
        KeyLabel(
            displayLabel = displayLabel,
            textSize = textSize,
            textColor = effectiveTextColor,
            ctrlHeld = ctrlHeld,
            key = key,
            ctrlShortcut = ctrlShortcut
        )

        KeyHintLabel(
            key = key,
            showHints = showHints,
            hintBright = hintBright,
            ctrlHeld = ctrlHeld,
            boxScope = this
        )

        KeyLockDot(
            isModifierLocked = isModifierLocked,
            boxScope = this
        )

        val currentPopupCodes = popupCodes
        if (currentPopupCodes != null) {
            LongPressPopup(
                codes = currentPopupCodes,
                activeIndex = popupActiveIndex,
                keySize = keySizeState.value,
                density = density
            )
        } else if (isPressed && key.type == KeyType.LETTER && displayLabel.length <= 2) {
            // Single-character press preview for letter keys
            KeyPressPreview(
                label = displayLabel,
                keySize = keySizeState.value,
                density = density
            )
        }
    }
}
