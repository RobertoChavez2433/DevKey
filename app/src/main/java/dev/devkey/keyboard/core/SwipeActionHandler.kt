package dev.devkey.keyboard.core

import android.util.Log

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

    companion object {
        private const val TAG = "DevKey/SwipeAction"
    }
}
