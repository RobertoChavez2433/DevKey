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

package dev.devkey.keyboard.dictionary.base
import dev.devkey.keyboard.dictionary.loader.DictionaryLoader
import dev.devkey.keyboard.suggestion.word.WordComposer

import android.content.Context
import android.util.Log
import org.pocketworkstation.pckeyboard.BinaryDictionary as JniBridge
import java.io.InputStream
import java.nio.ByteBuffer

private const val TAG = "DevKey/BinaryDictionary"
private const val MAX_ALTERNATIVES = 16
private const val MAX_WORDS = 18
private const val MAX_BIGRAMS = 60
private const val TYPED_LETTER_MULTIPLIER = 2
private const val ENABLE_MISSED_CHARACTERS = true

/** Static, compacted binary dictionary of standard words backed by a JNI native engine. */
class BinaryDictionary : Dictionary, java.io.Closeable {

    private var mDicTypeId: Int
    private var mNativeDict: Long = 0
    private var mDictLength: Int = 0
    private val mInputCodes = IntArray(MAX_WORD_LENGTH * MAX_ALTERNATIVES)
    private val mOutputChars = CharArray(MAX_WORD_LENGTH * MAX_WORDS)
    private val mOutputCharsBigrams = CharArray(MAX_WORD_LENGTH * MAX_BIGRAMS)
    private val mFrequencies = IntArray(MAX_WORDS)
    private val mFrequenciesBigrams = IntArray(MAX_BIGRAMS)
    // Reusable buffer for isValidWord to avoid per-call CharArray allocation.
    private val mWordBuffer = CharArray(MAX_WORD_LENGTH)
    // Hold a Java reference to prevent unexpected GC of the direct buffer used by native code.
    private var mNativeDictDirectBuffer: ByteBuffer? = null

    /** Create a dictionary from raw resource IDs. */
    constructor(context: Context, resId: IntArray?, dicTypeId: Int) {
        if (resId != null && resId.isNotEmpty() && resId[0] != 0) {
            applyLoadResult(DictionaryLoader.load(context, resId))
        }
        mDicTypeId = dicTypeId
    }

    /** Create a dictionary from pre-opened input streams. */
    constructor(context: Context, streams: Array<InputStream>?, dicTypeId: Int) {
        if (streams != null && streams.isNotEmpty()) {
            applyLoadResult(DictionaryLoader.load(streams))
        }
        mDicTypeId = dicTypeId
    }

    /** Create a dictionary from a byte buffer (used in tests). */
    constructor(context: Context, byteBuffer: ByteBuffer?, dicTypeId: Int) {
        if (byteBuffer != null) {
            mNativeDictDirectBuffer = if (byteBuffer.isDirect) byteBuffer
            else ByteBuffer.allocateDirect(byteBuffer.capacity()).also {
                byteBuffer.rewind(); it.put(byteBuffer)
            }
            mDictLength = byteBuffer.capacity()
            mNativeDict = JniBridge.openNative(
                mNativeDictDirectBuffer!!, TYPED_LETTER_MULTIPLIER, FULL_WORD_FREQ_MULTIPLIER, mDictLength
            )
        }
        mDicTypeId = dicTypeId
    }

    // All native calls delegate to JniBridge (C++ RegisterNatives bound to org.pocketworkstation.pckeyboard.BinaryDictionary).

    private fun applyLoadResult(result: DictionaryLoader.LoadResult?) {
        if (result == null) return
        try {
            mNativeDictDirectBuffer = result.buffer
            mNativeDict = JniBridge.openNative(
                result.buffer, TYPED_LETTER_MULTIPLIER, FULL_WORD_FREQ_MULTIPLIER, result.length
            )
            mDictLength = result.length
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Failed to open native dictionary", e)
        }
    }

    override fun getBigrams(
        composer: WordComposer,
        previousWord: CharSequence,
        callback: WordCallback,
        nextLettersFrequencies: IntArray?
    ) {
        val chars = previousWord.toString().toCharArray()
        mOutputCharsBigrams.fill(0.toChar())
        mFrequenciesBigrams.fill(0)
        if (mNativeDict == 0L) return
        val codesSize = composer.size()
        mInputCodes.fill(-1)
        if (codesSize > 0) {
            // WHY: composer.getCodesAt(0) throws IndexOutOfBoundsException when the composer is
            // empty (next-word prediction path passes a zero-size composer). The native side
            // (dictionary.cpp ::searchForTerminalNode) skips checkFirstCharacter when
            // mInputLength == 0, so passing codesSize=0 with mInputCodes as -1 fill is safe.
            val alternatives = composer.getCodesAt(0)
            alternatives.copyInto(mInputCodes, 0, 0, minOf(alternatives.size, MAX_ALTERNATIVES))
        }
        val count = JniBridge.getBigramsNative(
            mNativeDict, chars, chars.size, mInputCodes, codesSize,
            mOutputCharsBigrams, mFrequenciesBigrams, MAX_WORD_LENGTH, MAX_BIGRAMS, MAX_ALTERNATIVES
        )
        dispatchResults(count, mOutputCharsBigrams, mFrequenciesBigrams, DataType.BIGRAM, callback)
    }

    override fun getWords(
        composer: WordComposer,
        callback: WordCallback,
        nextLettersFrequencies: IntArray?
    ) {
        val codesSize = composer.size()
        if (codesSize > MAX_WORD_LENGTH - 1) return  // won't handle really long words
        mInputCodes.fill(-1)
        for (i in 0 until codesSize) {
            val alternatives = composer.getCodesAt(i)
            alternatives.copyInto(mInputCodes, i * MAX_ALTERNATIVES, 0, minOf(alternatives.size, MAX_ALTERNATIVES))
        }
        mOutputChars.fill(0.toChar())
        mFrequencies.fill(0)
        if (mNativeDict == 0L) return

        var count = JniBridge.getSuggestionsNative(
            mNativeDict, mInputCodes, codesSize, mOutputChars, mFrequencies,
            MAX_WORD_LENGTH, MAX_WORDS, MAX_ALTERNATIVES, -1,
            nextLettersFrequencies, nextLettersFrequencies?.size ?: 0
        )

        // Missed-character wildcard fallback (not yet production-ranked).
        if (ENABLE_MISSED_CHARACTERS && count < 5) {
            for (skip in 0 until codesSize) {
                val n = JniBridge.getSuggestionsNative(
                    mNativeDict, mInputCodes, codesSize, mOutputChars, mFrequencies,
                    MAX_WORD_LENGTH, MAX_WORDS, MAX_ALTERNATIVES, skip, null, 0
                )
                count = maxOf(count, n)
                if (n > 0) break
            }
        }
        dispatchResults(count, mOutputChars, mFrequencies, DataType.UNIGRAM, callback)
    }

    /** Iterates a native output buffer and fires [callback] for each non-empty result. */
    private fun dispatchResults(
        count: Int, chars: CharArray, freqs: IntArray,
        type: DataType, callback: WordCallback
    ) {
        for (j in 0 until count) {
            if (freqs[j] < 1) break
            val start = j * MAX_WORD_LENGTH
            var len = 0
            while (chars[start + len] != 0.toChar()) len++
            if (len > 0) callback.addWord(chars, start, len, freqs[j], mDicTypeId, type)
        }
    }

    override fun isValidWord(word: CharSequence): Boolean {
        if (mNativeDict == 0L) return false
        val len = minOf(word.length, MAX_WORD_LENGTH)
        for (i in 0 until len) mWordBuffer[i] = word[i]
        return JniBridge.isValidWordNative(mNativeDict, mWordBuffer, len)
    }

    fun getSize(): Int = mDictLength

    @Synchronized
    override fun close() {
        if (mNativeDict != 0L) {
            JniBridge.closeNative(mNativeDict)
            mNativeDict = 0
        }
    }

    companion object {
        /** Max word length for both Java and native; 48 to support long German compounds. */
        val MAX_WORD_LENGTH = 48
        // JNI bridge: trigger loading of the bridge class, which loads the native library.
        init {
            try {
                Class.forName("org.pocketworkstation.pckeyboard.BinaryDictionary")
                Log.i(TAG, "loaded jni_pckeyboard via JNI bridge")
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "Could not load JNI bridge class", e)
            }
        }
    }
}
