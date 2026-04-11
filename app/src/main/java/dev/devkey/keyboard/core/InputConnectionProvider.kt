/*
 * Narrow interface for InputMethodService connection methods.
 * Collaborators depend on this instead of the concrete LatinIME class.
 */
package dev.devkey.keyboard.core

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

interface InputConnectionProvider {
    val inputConnection: InputConnection?
    val editorInfo: EditorInfo?
    fun sendDownUpKeyEvents(keyEventCode: Int)
    fun sendKeyChar(c: Char)
}
