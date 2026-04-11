package dev.devkey.keyboard.core

import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.keyboard.model.Keyboard

internal class SwipeActionHandler(
    private val handleClose: () -> Unit,
    private val launchSettings: () -> Unit,
    private val toggleSuggestions: () -> Unit,
    private val toggleLanguagePrev: () -> Unit,
    private val toggleLanguageNext: () -> Unit,
    private val cycleFullMode: () -> Unit,
    private val toggleExtension: () -> Unit,
    private val adjustHeightUp: () -> Unit,
    private val adjustHeightDown: () -> Unit
) {
    var swipeUpAction: String? = null
    var swipeDownAction: String? = null
    var swipeLeftAction: String? = null
    var swipeRightAction: String? = null
    var volUpAction: String? = null
    var volDownAction: String? = null

    fun doSwipeAction(action: String?): Boolean {
        if (action.isNullOrEmpty() || action == "none") return false
        when (action) {
            "close" -> handleClose()
            "settings" -> launchSettings()
            "suggestions" -> toggleSuggestions()
            "lang_prev" -> toggleLanguagePrev()
            "lang_next" -> toggleLanguageNext()
            "full_mode" -> cycleFullMode()
            "extension" -> toggleExtension()
            "height_up" -> adjustHeightUp()
            "height_down" -> adjustHeightDown()
            else -> Log.i(TAG, "Unsupported swipe action config: $action")
        }
        return true
    }

    fun swipeRight(): Boolean = doSwipeAction(swipeRightAction)
    fun swipeLeft(): Boolean = doSwipeAction(swipeLeftAction)
    fun swipeDown(): Boolean = doSwipeAction(swipeDownAction)
    fun swipeUp(): Boolean = doSwipeAction(swipeUpAction)

    /**
     * Handle hardware key-up events for d-pad (shift-modified) and volume keys.
     * @return true if the event was consumed, false to pass to super.
     */
    fun handleHardwareKeyUp(
        keyCode: Int, event: KeyEvent, keyboardVisible: Boolean,
        shiftState: Int, ic: InputConnection?
    ): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (keyboardVisible && shiftState == Keyboard.SHIFT_ON) {
                    val modifiedEvent = KeyEvent(
                        event.downTime, event.eventTime, event.action, event.keyCode,
                        event.repeatCount, event.deviceId, event.scanCode,
                        KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_ON
                    )
                    ic?.sendKeyEvent(modifiedEvent)
                    return true
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (volUpAction != "none" && keyboardVisible) return doSwipeAction(volUpAction)
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (volDownAction != "none" && keyboardVisible) return doSwipeAction(volDownAction)
            }
        }
        return false
    }

    companion object {
        private const val TAG = "DevKey/SwipeAction"
    }
}
