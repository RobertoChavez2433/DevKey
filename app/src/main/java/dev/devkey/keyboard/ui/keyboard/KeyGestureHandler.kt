package dev.devkey.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import dev.devkey.keyboard.Keyboard
import dev.devkey.keyboard.core.KeyPressLogger
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.core.ModifierType
import dev.devkey.keyboard.debug.DevKeyLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Map a keycode to a [ModifierType], or null for non-modifier keys. */
internal fun getModifierType(code: Int): ModifierType? = when (code) {
    Keyboard.KEYCODE_SHIFT -> ModifierType.SHIFT
    KeyCodes.CTRL_LEFT -> ModifierType.CTRL
    KeyCodes.ALT_LEFT -> ModifierType.ALT
    else -> null
}

/**
 * Attaches a [pointerInput] modifier that routes gesture handling to one of three
 * branches: modifier-key tap, multi-char long-press popup, or normal press/repeat.
 */
internal fun Modifier.keyGestureHandler(
    key: KeyData,
    modifierState: ModifierStateManager,
    isModifierKey: Boolean,
    hasMultiPopup: Boolean,
    isPressed: MutableState<Boolean>,
    popupCodes: MutableState<List<Int>?>,
    popupActiveIndex: MutableIntState,
    keySize: MutableState<IntSize>,
    density: Density,
    view: View,
    coroutineScope: CoroutineScope,
    onKeyAction: (Int) -> Unit,
    onKeyPress: (Int) -> Unit,
    onKeyRelease: (Int) -> Unit
): Modifier = this.pointerInput(
    key.primaryCode, key.type, key.longPressCode, key.isRepeatable, hasMultiPopup
) {
    when {
        isModifierKey -> handleModifierGesture(key, modifierState, isPressed)
        hasMultiPopup -> handleMultiPopupGesture(
            key, isPressed, popupCodes, popupActiveIndex,
            keySize, density, view, coroutineScope, onKeyAction, onKeyPress, onKeyRelease
        )
        else -> handleNormalKeyGesture(
            key, isPressed, view, coroutineScope, onKeyAction, onKeyPress, onKeyRelease
        )
    }
}

private suspend fun PointerInputScope.handleModifierGesture(
    key: KeyData,
    modifierState: ModifierStateManager,
    isPressed: MutableState<Boolean>
) {
    detectTapGestures(
        onPress = {
            val modType = getModifierType(key.primaryCode)
            isPressed.value = true
            KeyPressLogger.logKeyDown(key.primaryLabel, key.primaryCode, "MODIFIER")
            if (modType != null) modifierState.onModifierDown(modType, 0)
            tryAwaitRelease()
            isPressed.value = false
            KeyPressLogger.logKeyUp(key.primaryLabel, key.primaryCode)
            if (modType != null) {
                modifierState.onModifierUp(modType, 0)
                modifierState.onModifierTap(modType)
                val currentState = when (modType) {
                    ModifierType.SHIFT -> modifierState.shiftState.value
                    ModifierType.CTRL -> modifierState.ctrlState.value
                    ModifierType.ALT -> modifierState.altState.value
                    ModifierType.META -> modifierState.metaState.value
                }
                KeyPressLogger.logModifierTransition(modType.name, "tap", currentState.name)
            }
        }
    )
}

private suspend fun PointerInputScope.handleNormalKeyGesture(
    key: KeyData,
    isPressed: MutableState<Boolean>,
    view: View,
    coroutineScope: CoroutineScope,
    onKeyAction: (Int) -> Unit,
    onKeyPress: (Int) -> Unit,
    onKeyRelease: (Int) -> Unit
) {
    detectTapGestures(
        onPress = {
            isPressed.value = true
            onKeyPress(key.primaryCode)
            KeyPressLogger.logKeyDown(key.primaryLabel, key.primaryCode, key.type.name)
            val job = coroutineScope.launch {
                delay(300L)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                KeyPressLogger.logLongPress(key.primaryLabel, key.primaryCode, key.longPressCode)
                if (key.longPressCode != null) {
                    DevKeyLogger.text("long_press_fired",
                        mapOf("label" to key.primaryLabel, "code" to key.primaryCode, "lp_code" to key.longPressCode))
                    onKeyAction(key.longPressCode)
                } else if (key.isRepeatable) {
                    var repeatCount = 0
                    while (isActive) {
                        repeatCount++
                        onKeyAction(key.primaryCode)
                        if (repeatCount % 10 == 0) {
                            KeyPressLogger.logRepeat(key.primaryLabel, key.primaryCode, repeatCount)
                        }
                        delay(50L)
                    }
                }
            }

            val released = tryAwaitRelease()
            job.cancel()
            isPressed.value = false
            if (released && !job.isCompleted) {
                KeyPressLogger.logKeyTap(key.primaryLabel, key.primaryCode)
                onKeyAction(key.primaryCode)
            }
            onKeyRelease(key.primaryCode)
            KeyPressLogger.logKeyUp(key.primaryLabel, key.primaryCode)
        }
    )
}
