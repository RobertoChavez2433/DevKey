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
    modifier: Modifier = Modifier,
    ctrlHeld: Boolean = false,
    showHints: Boolean = false,
    hintBright: Boolean = false
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Shared MutableState objects — read here for animation, mutated by the gesture handler.
    val isPressedState = remember { mutableStateOf(false) }
    val popupCodesState = remember { mutableStateOf<List<Int>?>(null) }
    val popupActiveIndexState = remember { mutableIntStateOf(0) }
    val keySizeState = remember { mutableStateOf(IntSize.Zero) }
    val longPressFiredState = remember { mutableStateOf(false) }

    val isPressed by isPressedState
    val popupCodes by popupCodesState
    val popupActiveIndex by popupActiveIndexState
    val longPressFired by longPressFiredState

    // Observe modifier states for visual feedback
    val shiftState by modifierState.shiftState.collectAsState()
    val ctrlState by modifierState.ctrlState.collectAsState()
    val altState by modifierState.altState.collectAsState()

    val (normalBg, normalText) = getKeyColors(key)
    val activeColor = getActiveKeyColor(key)

    val modifierVisual = resolveModifierVisualState(key, shiftState, ctrlState, altState)
    val interaction = resolveKeyInteractionState(key, ctrlHeld)

    val backgroundColor = animateKeyBackgroundColor(
        isPressed = isPressed,
        isModifierActive = modifierVisual.isActive,
        activeColor = activeColor,
        normalBg = normalBg,
        ctrlHeld = ctrlHeld,
        key = key,
        ctrlShortcut = interaction.ctrlShortcut
    )
    val scale = animateKeyScale(isPressed)

    val displayLabel = resolveDisplayLabel(key)
    val textSize = resolveTextSize(key)
    val ctrlTextColor = resolveCtrlTextColor(ctrlHeld, key, interaction.ctrlShortcut)
    val effectiveTextColor = ctrlTextColor ?: normalText

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
                isModifierKey = interaction.isModifierKey,
                hasMultiPopup = interaction.hasMultiPopup,
                isPressed = isPressedState,
                popupCodes = popupCodesState,
                popupActiveIndex = popupActiveIndexState,
                longPressFired = longPressFiredState,
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
            ctrlShortcut = interaction.ctrlShortcut
        )

        KeyHintLabel(
            key = key,
            showHints = showHints,
            hintBright = hintBright,
            ctrlHeld = ctrlHeld,
            boxScope = this
        )

        KeyLockDot(
            isModifierLocked = modifierVisual.isLocked,
            boxScope = this
        )

        KeyTransientOverlay(
            popupCodes = popupCodes,
            popupActiveIndex = popupActiveIndex,
            keySize = keySizeState.value,
            density = density,
            isPressed = isPressed,
            key = key,
            displayLabel = displayLabel,
            longPressFired = longPressFired
        )
    }
}
