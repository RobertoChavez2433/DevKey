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

package dev.devkey.keyboard.core.input

import android.content.Context
import androidx.annotation.MainThread

/**
 * Tracks text entry state transitions for suggestion/correction behavior.
 */
@MainThread
object TextEntryState {

    private var sBackspaceCount = 0
    private var sAutoSuggestCount = 0
    private var sAutoSuggestUndoneCount = 0
    private var sManualSuggestCount = 0
    private var sWordNotInDictionaryCount = 0
    private var sSessionCount = 0
    private var sTypedChars = 0
    private var sActualChars = 0

    enum class State {
        UNKNOWN,
        START,
        IN_WORD,
        ACCEPTED_DEFAULT,
        PICKED_SUGGESTION,
        PUNCTUATION_AFTER_WORD,
        PUNCTUATION_AFTER_ACCEPTED,
        SPACE_AFTER_ACCEPTED,
        SPACE_AFTER_PICKED,
        UNDO_COMMIT,
        CORRECTING,
        PICKED_CORRECTION
    }

    private var sState = State.UNKNOWN

    fun newSession(context: Context) {
        sSessionCount++
        sAutoSuggestCount = 0
        sBackspaceCount = 0
        sAutoSuggestUndoneCount = 0
        sManualSuggestCount = 0
        sWordNotInDictionaryCount = 0
        sTypedChars = 0
        sActualChars = 0
        sState = State.START
    }

    fun endSession() {
        // No-op: file-based logging infrastructure was removed
    }

    fun acceptedDefault(typedWord: CharSequence?, actualWord: CharSequence) {
        if (typedWord == null) return
        if (typedWord != actualWord) {
            sAutoSuggestCount++
        }
        sTypedChars += typedWord.length
        sActualChars += actualWord.length
        sState = State.ACCEPTED_DEFAULT
    }

    // State.ACCEPTED_DEFAULT will be changed to other sub-states
    // (see "case ACCEPTED_DEFAULT" in typedCharacter() below),
    // and should be restored back to State.ACCEPTED_DEFAULT after processing for each sub-state.
    fun backToAcceptedDefault(typedWord: CharSequence?) {
        if (typedWord == null) return
        when (sState) {
            State.SPACE_AFTER_ACCEPTED,
            State.PUNCTUATION_AFTER_ACCEPTED,
            State.IN_WORD -> sState = State.ACCEPTED_DEFAULT
            else -> {}
        }
    }

    fun manualTyped(typedWord: CharSequence?) {
        sState = State.START
    }

    fun acceptedTyped(typedWord: CharSequence?) {
        sWordNotInDictionaryCount++
        sState = State.PICKED_SUGGESTION
    }

    fun acceptedSuggestion(typedWord: CharSequence, actualWord: CharSequence) {
        sManualSuggestCount++
        val oldState = sState
        if (typedWord == actualWord) {
            acceptedTyped(typedWord)
        }
        sState = if (oldState == State.CORRECTING || oldState == State.PICKED_CORRECTION) {
            State.PICKED_CORRECTION
        } else {
            State.PICKED_SUGGESTION
        }
    }

    fun typedCharacter(c: Char, isSeparator: Boolean) {
        val isSpace = c == ' '
        when (sState) {
            State.IN_WORD -> {
                if (isSpace || isSeparator) {
                    sState = State.START
                }
            }
            State.ACCEPTED_DEFAULT,
            State.SPACE_AFTER_PICKED -> {
                sState = when {
                    isSpace -> State.SPACE_AFTER_ACCEPTED
                    isSeparator -> State.PUNCTUATION_AFTER_ACCEPTED
                    else -> State.IN_WORD
                }
            }
            State.PICKED_SUGGESTION,
            State.PICKED_CORRECTION -> {
                sState = when {
                    isSpace -> State.SPACE_AFTER_PICKED
                    isSeparator -> State.PUNCTUATION_AFTER_ACCEPTED
                    else -> State.IN_WORD
                }
            }
            State.START,
            State.UNKNOWN,
            State.SPACE_AFTER_ACCEPTED,
            State.PUNCTUATION_AFTER_ACCEPTED,
            State.PUNCTUATION_AFTER_WORD -> {
                sState = if (!isSpace && !isSeparator) {
                    State.IN_WORD
                } else {
                    State.START
                }
            }
            State.UNDO_COMMIT -> {
                sState = if (isSpace || isSeparator) {
                    State.ACCEPTED_DEFAULT
                } else {
                    State.IN_WORD
                }
            }
            State.CORRECTING -> {
                sState = State.START
            }
        }
    }

    fun backspace() {
        if (sState == State.ACCEPTED_DEFAULT) {
            sState = State.UNDO_COMMIT
            sAutoSuggestUndoneCount++
        } else if (sState == State.UNDO_COMMIT) {
            sState = State.IN_WORD
        }
        sBackspaceCount++
    }

    fun reset() {
        sState = State.START
    }

    fun getState(): State {
        return sState
    }

    fun isCorrecting(): Boolean {
        return sState == State.CORRECTING || sState == State.PICKED_CORRECTION
    }
}
