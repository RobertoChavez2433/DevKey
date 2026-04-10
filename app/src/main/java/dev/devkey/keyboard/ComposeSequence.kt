/*
 * Copyright (C) 2011 Darren Salt
 *
 * Licensed under the Apache License, Version 2.0 (the "Licence"); you may
 * not use this file except in compliance with the Licence. You may obtain
 * a copy of the Licence at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * Licence for the specific language governing permissions and limitations
 * under the Licence.
 */

package dev.devkey.keyboard

import android.util.Log
import android.view.inputmethod.EditorInfo

/**
 * Compose key sequence handler.
 *
 * @param onComposedText Callback to send composed text to the IME.
 * @param updateShiftState Callback to refresh shift key state after composing.
 * @param editorInfoProvider Provides the current [EditorInfo] for shift state updates.
 */
open class ComposeSequence(
    protected val onComposedText: (CharSequence) -> Unit,
    private val updateShiftState: (EditorInfo?) -> Unit,
    private val editorInfoProvider: () -> EditorInfo?
) {

    protected var composeBuffer: StringBuilder = StringBuilder(10)

    init {
        clear()
    }

    fun clear() {
        composeBuffer.setLength(0)
    }

    fun hasBufferedKeys(): Boolean = composeBuffer.isNotEmpty()

    fun bufferKey(code: Char) {
        composeBuffer.append(code)
    }

    /**
     * Returns the composed string if sequence is complete,
     * empty string if sequence is unrecognised,
     * or null if the sequence is valid but incomplete.
     */
    fun executeToString(code: Int): String? {
        var mutableCode = code
        val ks = KeyboardSwitcher.getInstance()
        if (ks.getShiftState() == Keyboard.SHIFT_LOCKED
            && ks.isAlphabetMode()
            && Character.isLowerCase(mutableCode)
        ) {
            mutableCode = mutableCode.toChar().uppercaseChar().code
        }
        bufferKey(mutableCode.toChar())
        updateShiftState(editorInfoProvider())

        val composed = get(composeBuffer.toString())
        if (composed != null) {
            return composed
        } else if (!isValid(composeBuffer.toString())) {
            return ""
        }
        return null
    }

    open fun execute(code: Int): Boolean {
        val composed = executeToString(code)
        if (composed != null) {
            clear()
            onComposedText(composed)
            return false
        }
        return true
    }

    fun execute(sequence: CharSequence): Boolean {
        var result = true
        for (i in sequence.indices) {
            result = execute(sequence[i].code)
        }
        return result
    }

    companion object {
        private const val TAG = "DevKey/ComposeSequence"

        private val mMap: MutableMap<String, String> = HashMap(1024)
        private val mPrefixes: MutableSet<String> = HashSet(2048)

        internal fun getMap(): Map<String, String> = mMap
        internal fun getPrefixes(): Set<String> = mPrefixes

        fun get(key: String?): String? {
            if (key.isNullOrEmpty()) return null
            return mMap[key]
        }

        private fun isValid(partialKey: String?): Boolean {
            if (partialKey.isNullOrEmpty()) return false
            return mPrefixes.contains(partialKey)
        }

        fun format(seq: String): String {
            val output = StringBuilder()
            var quoted = false
            for (i in seq.indices) {
                val c = seq[i]
                val keyName = ComposeKeyAliases.keyNames[c]
                if (keyName != null) {
                    output.append(if (quoted) "\" " else " ")
                    output.append(keyName)
                    quoted = false
                } else {
                    if (!quoted) {
                        output.append(if (output.isNotEmpty()) " \"" else "\"")
                    }
                    if (c < ' ' || c == '"' || c == '\\') {
                        output.append("\\")
                        output.append(if (c < ' ') (c.code + 64).toChar() else c)
                    } else {
                        output.append(c)
                    }
                    quoted = true
                }
            }
            if (quoted) output.append('"')
            return output.toString()
        }

        internal fun put(key: String, value: String) {
            var found = false
            if (key.isEmpty() || value.isEmpty()) return
            if (mMap.containsKey(key)) {
                Log.w(TAG, "compose sequence is a duplicate: ${format(key)}")
            } else if (mPrefixes.contains(key)) {
                Log.w(TAG, "compose sequence is a subset: ${format(key)}")
            }
            mMap[key] = value
            for (i in 1 until key.length) {
                val substr = key.substring(0, i)
                found = found or mMap.containsKey(substr)
                mPrefixes.add(substr)
            }
            if (found) {
                Log.w(TAG, "compose sequence is a superset: ${format(key)}")
            }
        }
    }
}
