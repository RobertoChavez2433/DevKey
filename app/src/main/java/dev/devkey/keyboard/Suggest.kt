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

import android.content.Context
import android.text.AutoText
import android.text.TextUtils
import android.util.Log
import android.view.View
import java.nio.ByteBuffer
import java.util.Arrays

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of
 * characters. This includes corrections and completions.
 */
class Suggest : Dictionary.WordCallback {

    private var mMainDict: BinaryDictionary

    private var mUserDictionary: Dictionary? = null
    private var mAutoDictionary: Dictionary? = null
    private var mContactsDictionary: Dictionary? = null
    private var mUserBigramDictionary: Dictionary? = null

    private var mPrefMaxSuggestions = 12

    private var mAutoTextEnabled = false

    private var mPriorities = IntArray(mPrefMaxSuggestions)
    private var mBigramPriorities = IntArray(PREF_MAX_BIGRAMS)

    // Handle predictive correction for only the first 1280 characters for performance reasons
    // If we support scripts that need latin characters beyond that, we should probably use some
    // kind of a sparse array or language specific list with a mapping lookup table.
    // 1280 is the size of the BASE_CHARS array in ExpandableDictionary, which is a basic set of
    // latin characters.
    private val mNextLettersFrequencies = IntArray(1280)
    private val mSuggestions = ArrayList<CharSequence>()
    private val mBigramSuggestions = ArrayList<CharSequence>()
    val bigramSuggestions: List<CharSequence> get() = mBigramSuggestions
    private val mStringPool = ArrayList<CharSequence>()
    private var mHaveCorrection = false
    private var mOriginalWord: CharSequence? = null
    private var mLowerOriginalWord: String = ""

    // TODO: Remove these member variables by passing more context to addWord() callback method
    private var mIsFirstCharCapitalized = false
    private var mIsAllUpperCase = false

    private var mCorrectionMode = CORRECTION_BASIC

    constructor(context: Context, dictionaryResId: IntArray) {
        mMainDict = BinaryDictionary(context, dictionaryResId, DIC_MAIN)
        if (!hasMainDictionary()) {
            val locale = context.resources.configuration.locales[0]
            val plug = PluginManager.getDictionary(context, locale.language)
            if (plug != null) {
                mMainDict.close()
                mMainDict = plug
            }
        }
        initPool()
    }

    constructor(context: Context, byteBuffer: ByteBuffer) {
        mMainDict = BinaryDictionary(context, byteBuffer, DIC_MAIN)
        initPool()
    }

    private fun initPool() {
        for (i in 0 until mPrefMaxSuggestions) {
            val sb = StringBuilder(getApproxMaxWordLength())
            mStringPool.add(sb)
        }
    }

    fun setAutoTextEnabled(enabled: Boolean) {
        mAutoTextEnabled = enabled
    }

    fun getCorrectionMode(): Int = mCorrectionMode

    fun setCorrectionMode(mode: Int) {
        mCorrectionMode = mode
    }

    fun hasMainDictionary(): Boolean {
        return mMainDict.getSize() > LARGE_DICTIONARY_THRESHOLD
    }

    fun getApproxMaxWordLength(): Int = APPROX_MAX_WORD_LENGTH

    /**
     * Sets an optional user dictionary resource to be loaded. The user dictionary is consulted
     * before the main dictionary, if set.
     */
    fun setUserDictionary(userDictionary: Dictionary?) {
        mUserDictionary = userDictionary
    }

    /**
     * Sets an optional contacts dictionary resource to be loaded.
     */
    fun setContactsDictionary(userDictionary: Dictionary?) {
        mContactsDictionary = userDictionary
    }

    fun setAutoDictionary(autoDictionary: Dictionary?) {
        mAutoDictionary = autoDictionary
    }

    fun setUserBigramDictionary(userBigramDictionary: Dictionary?) {
        mUserBigramDictionary = userBigramDictionary
    }

    /**
     * Number of suggestions to generate from the input key sequence. This has
     * to be a number between 1 and 100 (inclusive).
     * @param maxSuggestions
     * @throws IllegalArgumentException if the number is out of range
     */
    fun setMaxSuggestions(maxSuggestions: Int) {
        require(maxSuggestions in 1..100) { "maxSuggestions must be between 1 and 100" }
        mPrefMaxSuggestions = maxSuggestions
        mPriorities = IntArray(mPrefMaxSuggestions)
        mBigramPriorities = IntArray(PREF_MAX_BIGRAMS)
        collectGarbage(mSuggestions, mPrefMaxSuggestions)
        while (mStringPool.size < mPrefMaxSuggestions) {
            val sb = StringBuilder(getApproxMaxWordLength())
            mStringPool.add(sb)
        }
    }

    private fun haveSufficientCommonality(original: String, suggestion: CharSequence): Boolean {
        val originalLength = original.length
        val suggestionLength = suggestion.length
        val minLength = minOf(originalLength, suggestionLength)
        if (minLength <= 2) return true
        var matching = 0
        var lessMatching = 0 // Count matches if we skip one character
        for (i in 0 until minLength) {
            val origChar = ExpandableDictionary.toLowerCase(original[i])
            if (origChar == ExpandableDictionary.toLowerCase(suggestion[i])) {
                matching++
                lessMatching++
            } else if (i + 1 < suggestionLength &&
                origChar == ExpandableDictionary.toLowerCase(suggestion[i + 1])
            ) {
                lessMatching++
            }
        }
        matching = maxOf(matching, lessMatching)

        return if (minLength <= 4) {
            matching >= 2
        } else {
            matching > minLength / 2
        }
    }

    /**
     * Returns a list of words that match the list of character codes passed in.
     * This list will be overwritten the next time this function is called.
     * @param view a view for retrieving the context for AutoText
     * @param wordComposer contains what is currently being typed
     * @param prevWordForBigram previous word (used only for bigram)
     * @return list of suggestions.
     */
    fun getSuggestions(
        view: View?,
        wordComposer: WordComposer,
        includeTypedWordIfValid: Boolean,
        prevWordForBigram: CharSequence?
    ): List<CharSequence> {
        mHaveCorrection = false
        mIsFirstCharCapitalized = wordComposer.isFirstCharCapitalized()
        mIsAllUpperCase = wordComposer.isAllUpperCase()
        collectGarbage(mSuggestions, mPrefMaxSuggestions)
        Arrays.fill(mPriorities, 0)
        Arrays.fill(mNextLettersFrequencies, 0)

        // Save a lowercase version of the original word
        mOriginalWord = wordComposer.getTypedWord()
        if (mOriginalWord != null) {
            val originalWordString = mOriginalWord.toString()
            mOriginalWord = originalWordString
            mLowerOriginalWord = originalWordString.lowercase()
        } else {
            mLowerOriginalWord = ""
        }

        var mutablePrevWordForBigram = prevWordForBigram

        if (wordComposer.size() == 1 && (mCorrectionMode == CORRECTION_FULL_BIGRAM ||
                    mCorrectionMode == CORRECTION_BASIC)
        ) {
            // At first character typed, search only the bigrams
            Arrays.fill(mBigramPriorities, 0)
            collectGarbage(mBigramSuggestions, PREF_MAX_BIGRAMS)

            if (!TextUtils.isEmpty(mutablePrevWordForBigram)) {
                val lowerPrevWord: CharSequence = mutablePrevWordForBigram.toString().lowercase()
                if (mMainDict.isValidWord(lowerPrevWord)) {
                    mutablePrevWordForBigram = lowerPrevWord
                }
                if (mUserBigramDictionary != null) {
                    mUserBigramDictionary!!.getBigrams(
                        wordComposer, mutablePrevWordForBigram!!, this,
                        mNextLettersFrequencies
                    )
                }
                if (mContactsDictionary != null) {
                    mContactsDictionary!!.getBigrams(
                        wordComposer, mutablePrevWordForBigram!!, this,
                        mNextLettersFrequencies
                    )
                }
                mMainDict.getBigrams(
                    wordComposer, mutablePrevWordForBigram!!, this,
                    mNextLettersFrequencies
                )
                val currentChar = wordComposer.getTypedWord()!![0]
                val currentCharUpper = Character.toUpperCase(currentChar)
                var count = 0
                val bigramSuggestionSize = mBigramSuggestions.size
                for (i in 0 until bigramSuggestionSize) {
                    if (mBigramSuggestions[i][0] == currentChar ||
                        mBigramSuggestions[i][0] == currentCharUpper
                    ) {
                        val poolSize = mStringPool.size
                        val sb = if (poolSize > 0) {
                            mStringPool.removeAt(poolSize - 1) as StringBuilder
                        } else {
                            StringBuilder(getApproxMaxWordLength())
                        }
                        sb.setLength(0)
                        sb.append(mBigramSuggestions[i])
                        mSuggestions.add(count++, sb)
                        if (count > mPrefMaxSuggestions) break
                    }
                }
            }
        } else if (wordComposer.size() > 1) {
            // At second character typed, search the unigrams (scores being affected by bigrams)
            if (mUserDictionary != null || mContactsDictionary != null) {
                mUserDictionary?.getWords(wordComposer, this, mNextLettersFrequencies)
                mContactsDictionary?.getWords(wordComposer, this, mNextLettersFrequencies)

                if (mSuggestions.size > 0 && isValidWord(mOriginalWord!!) &&
                    (mCorrectionMode == CORRECTION_FULL ||
                            mCorrectionMode == CORRECTION_FULL_BIGRAM)
                ) {
                    mHaveCorrection = true
                }
            }
            mMainDict.getWords(wordComposer, this, mNextLettersFrequencies)
            if ((mCorrectionMode == CORRECTION_FULL || mCorrectionMode == CORRECTION_FULL_BIGRAM) &&
                mSuggestions.size > 0
            ) {
                mHaveCorrection = true
            }
        }
        if (mOriginalWord != null) {
            mSuggestions.add(0, mOriginalWord.toString())
        }

        // Check if the first suggestion has a minimum number of characters in common
        if (wordComposer.size() > 1 && mSuggestions.size > 1 &&
            (mCorrectionMode == CORRECTION_FULL || mCorrectionMode == CORRECTION_FULL_BIGRAM)
        ) {
            if (!haveSufficientCommonality(mLowerOriginalWord, mSuggestions[1])) {
                mHaveCorrection = false
            }
        }
        if (mAutoTextEnabled) {
            var i = 0
            var max = 6
            // Don't autotext the suggestions from the dictionaries
            if (mCorrectionMode == CORRECTION_BASIC) max = 1
            while (i < mSuggestions.size && i < max) {
                val suggestedWord = mSuggestions[i].toString().lowercase()
                val autoText = AutoText.get(suggestedWord, 0, suggestedWord.length, view)
                // Is there an AutoText correction?
                var canAdd = autoText != null
                // Is that correction already the current prediction (or original word)?
                canAdd = canAdd && !TextUtils.equals(autoText, mSuggestions[i])
                // Is that correction already the next predicted word?
                if (canAdd && i + 1 < mSuggestions.size && mCorrectionMode != CORRECTION_BASIC) {
                    canAdd = canAdd && !TextUtils.equals(autoText, mSuggestions[i + 1])
                }
                if (canAdd) {
                    mHaveCorrection = true
                    mSuggestions.add(i + 1, autoText!!)
                    i++
                }
                i++
            }
        }
        removeDupes()
        return mSuggestions
    }

    fun getNextLettersFrequencies(): IntArray = mNextLettersFrequencies

    private fun removeDupes() {
        val suggestions = mSuggestions
        if (suggestions.size < 2) return
        var i = 1
        // Don't cache suggestions.size(), since we may be removing items
        while (i < suggestions.size) {
            val cur = suggestions[i]
            // Compare each candidate with each previous candidate
            for (j in 0 until i) {
                val previous = suggestions[j]
                if (TextUtils.equals(cur, previous)) {
                    removeFromSuggestions(i)
                    i--
                    break
                }
            }
            i++
        }
    }

    private fun removeFromSuggestions(index: Int) {
        val garbage = mSuggestions.removeAt(index)
        if (garbage is StringBuilder) {
            mStringPool.add(garbage)
        }
    }

    fun hasMinimalCorrection(): Boolean = mHaveCorrection

    private fun compareCaseInsensitive(
        lowerOriginalWord: String,
        word: CharArray, offset: Int, length: Int
    ): Boolean {
        val originalLength = lowerOriginalWord.length
        if (originalLength == length && Character.isUpperCase(word[offset])) {
            for (i in 0 until originalLength) {
                if (lowerOriginalWord[i] != Character.toLowerCase(word[offset + i])) {
                    return false
                }
            }
            return true
        }
        return false
    }

    override fun addWord(
        word: CharArray, wordOffset: Int, wordLength: Int, freq: Int,
        dicTypeId: Int, dataType: Dictionary.DataType
    ): Boolean {
        var frequency = freq
        val suggestions: ArrayList<CharSequence>
        val priorities: IntArray
        val prefMaxSuggestions: Int
        if (dataType == Dictionary.DataType.BIGRAM) {
            suggestions = mBigramSuggestions
            priorities = mBigramPriorities
            prefMaxSuggestions = PREF_MAX_BIGRAMS
        } else {
            suggestions = mSuggestions
            priorities = mPriorities
            prefMaxSuggestions = mPrefMaxSuggestions
        }

        var pos = 0

        // Check if it's the same word, only caps are different
        if (compareCaseInsensitive(mLowerOriginalWord, word, wordOffset, wordLength)) {
            pos = 0
        } else {
            if (dataType == Dictionary.DataType.UNIGRAM) {
                // Check if the word was already added before (by bigram data)
                val bigramSuggestion = searchBigramSuggestion(word, wordOffset, wordLength)
                if (bigramSuggestion >= 0) {
                    // turn freq from bigram into multiplier specified above
                    val multiplier = (mBigramPriorities[bigramSuggestion].toDouble() /
                            MAXIMUM_BIGRAM_FREQUENCY) *
                            (BIGRAM_MULTIPLIER_MAX - BIGRAM_MULTIPLIER_MIN) +
                            BIGRAM_MULTIPLIER_MIN
                    frequency = Math.round(frequency * multiplier).toInt()
                }
            }

            // Check the last one's priority and bail
            if (priorities[prefMaxSuggestions - 1] >= frequency) return true
            while (pos < prefMaxSuggestions) {
                if (priorities[pos] < frequency ||
                    (priorities[pos] == frequency && wordLength < suggestions[pos].length)
                ) {
                    break
                }
                pos++
            }
        }
        if (pos >= prefMaxSuggestions) {
            return true
        }

        System.arraycopy(priorities, pos, priorities, pos + 1, prefMaxSuggestions - pos - 1)
        priorities[pos] = frequency
        val poolSize = mStringPool.size
        val sb = if (poolSize > 0) {
            mStringPool.removeAt(poolSize - 1) as StringBuilder
        } else {
            StringBuilder(getApproxMaxWordLength())
        }
        sb.setLength(0)
        if (mIsAllUpperCase) {
            sb.append(String(word, wordOffset, wordLength).uppercase())
        } else if (mIsFirstCharCapitalized) {
            sb.append(Character.toUpperCase(word[wordOffset]))
            if (wordLength > 1) {
                sb.append(word, wordOffset + 1, wordLength - 1)
            }
        } else {
            sb.append(word, wordOffset, wordLength)
        }
        suggestions.add(pos, sb)
        if (suggestions.size > prefMaxSuggestions) {
            val garbage = suggestions.removeAt(prefMaxSuggestions)
            if (garbage is StringBuilder) {
                mStringPool.add(garbage)
            }
        }
        return true
    }

    private fun searchBigramSuggestion(word: CharArray, offset: Int, length: Int): Int {
        // TODO This is almost O(n^2). Might need fix.
        // search whether the word appeared in bigram data
        val bigramSuggestSize = mBigramSuggestions.size
        for (i in 0 until bigramSuggestSize) {
            if (mBigramSuggestions[i].length == length) {
                var chk = true
                for (j in 0 until length) {
                    if (mBigramSuggestions[i][j] != word[offset + j]) {
                        chk = false
                        break
                    }
                }
                if (chk) return i
            }
        }
        return -1
    }

    fun isValidWord(word: CharSequence): Boolean {
        if (word.isEmpty()) {
            return false
        }
        return mMainDict.isValidWord(word) ||
                (mUserDictionary != null && mUserDictionary!!.isValidWord(word)) ||
                (mAutoDictionary != null && mAutoDictionary!!.isValidWord(word)) ||
                (mContactsDictionary != null && mContactsDictionary!!.isValidWord(word))
    }

    private fun collectGarbage(suggestions: ArrayList<CharSequence>, prefMaxSuggestions: Int) {
        var poolSize = mStringPool.size
        var garbageSize = suggestions.size
        while (poolSize < prefMaxSuggestions && garbageSize > 0) {
            val garbage = suggestions[garbageSize - 1]
            if (garbage is StringBuilder) {
                mStringPool.add(garbage)
                poolSize++
            }
            garbageSize--
        }
        if (poolSize == prefMaxSuggestions + 1) {
            Log.w("Suggest", "String pool got too big: $poolSize")
        }
        suggestions.clear()
    }

    fun close() {
        mMainDict.close()
        mUserDictionary?.close()
        mAutoDictionary?.close()
        mContactsDictionary?.close()
        mUserBigramDictionary?.close()
    }

    companion object {
        private const val TAG = "DevKey"

        const val APPROX_MAX_WORD_LENGTH = 32

        const val CORRECTION_NONE = 0
        const val CORRECTION_BASIC = 1
        const val CORRECTION_FULL = 2
        const val CORRECTION_FULL_BIGRAM = 3

        /**
         * Words that appear in both bigram and unigram data gets multiplier ranging from
         * BIGRAM_MULTIPLIER_MIN to BIGRAM_MULTIPLIER_MAX depending on the frequency score from
         * bigram data.
         */
        const val BIGRAM_MULTIPLIER_MIN = 1.2
        const val BIGRAM_MULTIPLIER_MAX = 1.5

        /**
         * Maximum possible bigram frequency. Will depend on how many bits are being used in data
         * structure. Maximum bigram frequency will get the BIGRAM_MULTIPLIER_MAX as the multiplier.
         */
        const val MAXIMUM_BIGRAM_FREQUENCY = 127

        const val DIC_USER_TYPED = 0
        const val DIC_MAIN = 1
        const val DIC_USER = 2
        const val DIC_AUTO = 3
        const val DIC_CONTACTS = 4
        // If you add a type of dictionary, increment DIC_TYPE_LAST_ID
        const val DIC_TYPE_LAST_ID = 4

        const val LARGE_DICTIONARY_THRESHOLD = 200 * 1000

        private const val PREF_MAX_BIGRAMS = 60
    }
}
