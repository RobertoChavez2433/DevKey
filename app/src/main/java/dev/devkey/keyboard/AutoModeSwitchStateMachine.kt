/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package dev.devkey.keyboard

import dev.devkey.keyboard.ui.keyboard.KeyCodes

/**
 * State machine that tracks auto-switching between alphabet and symbol keyboard
 * modes based on user input events (key presses, mode-change key, multitouch).
 *
 * The caller supplies two lambdas:
 *  - [changeKeyboardMode]: invoked when the machine decides to snap back to the
 *    previous mode.
 *  - [getPointerCount]: returns the current number of active touch pointers so
 *    the machine can distinguish momentary vs. chording gestures.
 */
internal class AutoModeSwitchStateMachine(
    private val changeKeyboardMode: () -> Unit,
    private val getPointerCount: () -> Int
) {
    companion object {
        const val STATE_ALPHA = 0
        const val STATE_SYMBOL_BEGIN = 1
        const val STATE_SYMBOL = 2
        /** Used only on distinct multi-touch panel devices. */
        const val STATE_MOMENTARY = 3
        const val STATE_CHORDING = 4
    }

    var state: Int = STATE_ALPHA
        private set

    fun reset() {
        state = STATE_ALPHA
    }

    fun setMomentary() {
        state = STATE_MOMENTARY
    }

    fun isInMomentaryState(): Boolean = state == STATE_MOMENTARY

    fun isInChordingState(): Boolean = state == STATE_CHORDING

    /**
     * Called after [toggleSymbols] to advance the state from the toggle direction.
     *
     * @param isSymbols whether the keyboard is now showing symbols.
     * @param preferSymbols whether symbols mode was explicitly requested.
     */
    fun onToggleSymbols(isSymbols: Boolean, preferSymbols: Boolean) {
        state = if (isSymbols && !preferSymbols) STATE_SYMBOL_BEGIN else STATE_ALPHA
    }

    /**
     * Called on [onCancelInput] to snap back when in momentary state.
     */
    fun onCancelInput() {
        if (state == STATE_MOMENTARY && getPointerCount() == 1) {
            changeKeyboardMode()
        }
    }

    /**
     * Updates state machine to figure out when to automatically snap back to
     * the previous mode.  Must be called for every key event.
     *
     * @param key the key code of the pressed key.
     * @param isSymbols whether the keyboard is currently in symbols mode.
     */
    fun onKey(key: Int, isSymbols: Boolean) {
        when (state) {
            STATE_MOMENTARY ->
                // Only distinct multi touch devices can be in this state.
                // On non-distinct multi touch devices, mode change key is handled
                // by onKey, not by onPress and onRelease.
                // So, on such devices, mAutoModeSwitchState starts from
                // STATE_SYMBOL_BEGIN, or STATE_ALPHA, not from STATE_MOMENTARY.
                if (key == Keyboard.KEYCODE_MODE_CHANGE) {
                    // Detected only the mode change key has been pressed, and then released.
                    state = if (isSymbols) STATE_SYMBOL_BEGIN else STATE_ALPHA
                } else if (getPointerCount() == 1) {
                    // Snap back to the previous keyboard mode if the user pressed
                    // the mode change key and slid to other key, then released the finger.
                    // If the user cancels the sliding input, snapping back to the
                    // previous keyboard mode is handled by onCancelInput.
                    changeKeyboardMode()
                } else {
                    // Chording input is being started. The keyboard mode will be
                    // snapped back to the previous mode in onReleaseSymbol
                    // when the mode change key is released.
                    state = STATE_CHORDING
                }
            STATE_SYMBOL_BEGIN ->
                if (key != KeyCodes.ASCII_SPACE && key != KeyCodes.ENTER && key >= 0) {
                    state = STATE_SYMBOL
                }
            STATE_SYMBOL ->
                // Snap back to alpha keyboard mode if user types one or more
                // non-space/enter characters followed by a space/enter.
                if (key == KeyCodes.ENTER || key == KeyCodes.ASCII_SPACE) {
                    changeKeyboardMode()
                }
        }
    }
}
