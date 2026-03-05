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

/**
 * Stores the currently composing word with information such as adjacent key codes.
 */
class WordComposer {

    /** The list of unicode values for each keystroke (including surrounding keys). */
    private val mCodes: MutableList<IntArray>

    /** The word chosen from the candidate list, until it is committed. */
    private var mPreferredWord: String? = null

    private val mTypedWord: StringBuilder

    private var mCapsCount: Int = 0

    private var mAutoCapitalized: Boolean = false

    /** Whether the user chose to capitalize the first char of the word. */
    private var mIsFirstCharCapitalized: Boolean = false

    constructor() {
        mCodes = mutableListOf()
        mTypedWord = StringBuilder(20)
    }

    constructor(copy: WordComposer) {
        mCodes = mutableListOf(*copy.mCodes.toTypedArray())
        mPreferredWord = copy.mPreferredWord
        mTypedWord = StringBuilder(copy.mTypedWord)
        mCapsCount = copy.mCapsCount
        mAutoCapitalized = copy.mAutoCapitalized
        mIsFirstCharCapitalized = copy.mIsFirstCharCapitalized
    }

    /** Clear out the keys registered so far. */
    fun reset() {
        mCodes.clear()
        mIsFirstCharCapitalized = false
        mPreferredWord = null
        mTypedWord.setLength(0)
        mCapsCount = 0
    }

    /** Number of keystrokes in the composing word. */
    fun size(): Int = mCodes.size

    /**
     * Returns the codes at a particular position in the word.
     * @param index the position in the word
     * @return the unicode for the pressed and surrounding keys
     */
    fun getCodesAt(index: Int): IntArray = mCodes[index]

    /**
     * Add a new keystroke, with codes[0] containing the pressed key's unicode
     * and the rest of the array containing unicode for adjacent keys,
     * sorted by reducing probability/proximity.
     */
    fun add(primaryCode: Int, codes: IntArray) {
        mTypedWord.append(primaryCode.toChar())
        correctPrimaryJuxtapos(primaryCode, codes)
        correctCodesCase(codes)
        mCodes.add(codes)
        if (primaryCode.toChar().isUpperCase()) mCapsCount++
    }

    /**
     * Swaps the first and second values in the codes array if the primary code
     * is not the first value but the second.
     */
    private fun correctPrimaryJuxtapos(primaryCode: Int, codes: IntArray) {
        if (codes.size < 2) return
        if (codes[0] > 0 && codes[1] > 0 && codes[0] != primaryCode && codes[1] == primaryCode) {
            codes[1] = codes[0]
            codes[0] = primaryCode
        }
    }

    /** Prediction expects the keyCodes to be lowercase. */
    private fun correctCodesCase(codes: IntArray) {
        for (i in codes.indices) {
            val code = codes[i]
            if (code > 0) codes[i] = code.toChar().lowercaseChar().code
        }
    }

    /** Delete the last keystroke as a result of hitting backspace. */
    fun deleteLast() {
        val codesSize = mCodes.size
        if (codesSize > 0) {
            mCodes.removeAt(codesSize - 1)
            val lastPos = mTypedWord.length - 1
            val last = mTypedWord[lastPos]
            mTypedWord.deleteCharAt(lastPos)
            if (last.isUpperCase()) mCapsCount--
        }
    }

    /**
     * Returns the word as it was typed, without any correction applied.
     * @return the word that was typed so far, or null if empty
     */
    fun getTypedWord(): CharSequence? {
        return if (mCodes.isEmpty()) null else mTypedWord
    }

    fun setFirstCharCapitalized(capitalized: Boolean) {
        mIsFirstCharCapitalized = capitalized
    }

    /** Whether or not the user typed a capital letter as the first letter in the word. */
    fun isFirstCharCapitalized(): Boolean = mIsFirstCharCapitalized

    /** Whether or not all of the user typed chars are upper case. */
    fun isAllUpperCase(): Boolean = mCapsCount > 0 && mCapsCount == size()

    /**
     * Stores the user's selected word, before it is actually committed to the text field.
     */
    fun setPreferredWord(preferred: String?) {
        mPreferredWord = preferred
    }

    /**
     * Return the word chosen by the user, or the typed word if no other word was chosen.
     */
    fun getPreferredWord(): CharSequence? = mPreferredWord ?: getTypedWord()

    /** Returns true if more than one character is upper case. */
    fun isMostlyCaps(): Boolean = mCapsCount > 1

    /**
     * Saves the reason why the word is capitalized - whether it was automatic or
     * due to the user hitting shift in the middle of a sentence.
     */
    fun setAutoCapitalized(auto: Boolean) {
        mAutoCapitalized = auto
    }

    /** Returns whether the word was automatically capitalized. */
    fun isAutoCapitalized(): Boolean = mAutoCapitalized
}
