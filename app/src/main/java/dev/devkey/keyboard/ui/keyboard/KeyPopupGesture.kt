package dev.devkey.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import dev.devkey.keyboard.core.KeyPressLogger
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal suspend fun PointerInputScope.handleMultiPopupGesture(
    key: KeyData,
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
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        isPressed.value = true
        onKeyPress(key.primaryCode)
        KeyPressLogger.logKeyDown(key.primaryLabel, key.primaryCode, key.type.name)
        var longPressTriggered = false
        val longPressJob = coroutineScope.launch {
            delay(300L)
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            KeyPressLogger.logLongPress(key.primaryLabel, key.primaryCode, key.longPressCode)
            popupCodes.value = key.longPressCodes
            popupActiveIndex.intValue = 0
            longPressTriggered = true
        }

        try {
            while (true) {
                val event: PointerEvent = awaitPointerEvent(PointerEventPass.Main)
                val pointer = event.changes.firstOrNull() ?: break
                if (!pointer.pressed) { pointer.consume(); break }
                val codes = popupCodes.value
                if (codes != null && codes.isNotEmpty()) {
                    val cellPx = with(density) {
                        (DevKeyThemeDimensions.popupCellPadH * 2 + DevKeyThemeDimensions.popupCellGlyphWidth).toPx()
                    }
                    val leftEdgePx = (keySize.value.width - cellPx * codes.size) / 2f
                    val idx = ((pointer.position.x - leftEdgePx) / cellPx).toInt()
                    popupActiveIndex.intValue = idx.coerceIn(0, codes.size - 1)
                }
                pointer.consume()
            }
        } finally {
            longPressJob.cancel()
            isPressed.value = false
        }
        val codes = popupCodes.value
        if (longPressTriggered && codes != null) {
            val selectedCode = codes[popupActiveIndex.intValue]
            DevKeyLogger.text("long_press_fired",
                mapOf("label" to key.primaryLabel, "code" to key.primaryCode, "lp_code" to selectedCode))
            onKeyAction(selectedCode)
            popupCodes.value = null
            popupActiveIndex.intValue = 0
        } else if (!longPressTriggered) {
            KeyPressLogger.logKeyTap(key.primaryLabel, key.primaryCode)
            onKeyAction(key.primaryCode)
        }
        onKeyRelease(key.primaryCode)
        KeyPressLogger.logKeyUp(key.primaryLabel, key.primaryCode)
    }
}
