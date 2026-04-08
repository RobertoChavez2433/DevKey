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
import android.util.Log
import org.pocketworkstation.pckeyboard.BinaryDictionary as JniBridge
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels


/**
 * Implements a static, compacted, binary dictionary of standard words.
 */
class BinaryDictionary : Dictionary, java.io.Closeable {

    private var mDicTypeId: Int
    private var mNativeDict: Long = 0
    private var mDictLength: Int = 0
    private val mInputCodes = IntArray(MAX_WORD_LENGTH * MAX_ALTERNATIVES)
    private val mOutputChars = CharArray(MAX_WORD_LENGTH * MAX_WORDS)
    private val mOutputCharsBigrams = CharArray(MAX_WORD_LENGTH * MAX_BIGRAMS)
    private val mFrequencies = IntArray(MAX_WORDS)
    private val mFrequenciesBigrams = IntArray(MAX_BIGRAMS)
    // Reusable buffer for isValidWord to avoid per-call CharArray allocation
    private val mWordBuffer = CharArray(MAX_WORD_LENGTH)
    // Keep a reference to the native dict direct buffer in Java to avoid
    // unexpected deallocation of the direct buffer.
    private var mNativeDictDirectBuffer: ByteBuffer? = null

    /**
     * Create a dictionary from a raw resource file
     * @param context application context for reading resources
     * @param resId the resource containing the raw binary dictionary
     */
    constructor(context: Context, resId: IntArray?, dicTypeId: Int) {
        if (resId != null && resId.isNotEmpty() && resId[0] != 0) {
            loadDictionary(context, resId)
        }
        mDicTypeId = dicTypeId
    }

    /**
     * Create a dictionary from input streams
     * @param context application context for reading resources
     * @param streams the resource streams containing the raw binary dictionary
     */
    constructor(context: Context, streams: Array<InputStream>?, dicTypeId: Int) {
        if (streams != null && streams.isNotEmpty()) {
            loadDictionary(streams)
        }
        mDicTypeId = dicTypeId
    }

    /**
     * Create a dictionary from a byte buffer. This is used for testing.
     * @param context application context for reading resources
     * @param byteBuffer a ByteBuffer containing the binary dictionary
     */
    constructor(context: Context, byteBuffer: ByteBuffer?, dicTypeId: Int) {
        if (byteBuffer != null) {
            mNativeDictDirectBuffer = if (byteBuffer.isDirect) {
                byteBuffer
            } else {
                ByteBuffer.allocateDirect(byteBuffer.capacity()).also {
                    byteBuffer.rewind()
                    it.put(byteBuffer)
                }
            }
            mDictLength = byteBuffer.capacity()
            mNativeDict = openNative(
                mNativeDictDirectBuffer!!,
                TYPED_LETTER_MULTIPLIER, FULL_WORD_FREQ_MULTIPLIER, mDictLength
            )
        }
        mDicTypeId = dicTypeId
    }

    // Native methods are delegated to the JNI bridge class at the old package path
    // because the C++ RegisterNatives binds to org.pocketworkstation.pckeyboard.BinaryDictionary
    private fun openNative(
        bb: ByteBuffer, typedLetterMultiplier: Int,
        fullWordMultiplier: Int, dictSize: Int
    ): Long {
        return JniBridge.openNative(
            bb, typedLetterMultiplier, fullWordMultiplier, dictSize
        )
    }

    private fun closeNative(dict: Long) {
        JniBridge.closeNative(dict)
    }

    private fun isValidWordNative(nativeData: Long, word: CharArray, wordLength: Int): Boolean {
        return JniBridge.isValidWordNative(
            nativeData, word, wordLength
        )
    }

    private fun getSuggestionsNative(
        dict: Long, inputCodes: IntArray, codesSize: Int,
        outputChars: CharArray, frequencies: IntArray, maxWordLength: Int, maxWords: Int,
        maxAlternatives: Int, skipPos: Int, nextLettersFrequencies: IntArray?, nextLettersSize: Int
    ): Int {
        return JniBridge.getSuggestionsNative(
            dict, inputCodes, codesSize, outputChars, frequencies,
            maxWordLength, maxWords, maxAlternatives, skipPos,
            nextLettersFrequencies, nextLettersSize
        )
    }

    private fun getBigramsNative(
        dict: Long, prevWord: CharArray, prevWordLength: Int,
        inputCodes: IntArray, inputCodesLength: Int, outputChars: CharArray, frequencies: IntArray,
        maxWordLength: Int, maxBigrams: Int, maxAlternatives: Int
    ): Int {
        return JniBridge.getBigramsNative(
            dict, prevWord, prevWordLength, inputCodes, inputCodesLength,
            outputChars, frequencies, maxWordLength, maxBigrams, maxAlternatives
        )
    }

    private fun loadDictionary(streams: Array<InputStream>) {
        try {
            // merging separated dictionary into one if dictionary is separated
            var total = 0
            for (stream in streams) {
                total += stream.available()
            }

            mNativeDictDirectBuffer =
                ByteBuffer.allocateDirect(total).order(ByteOrder.nativeOrder())
            var got = 0
            for (stream in streams) {
                got += Channels.newChannel(stream).read(mNativeDictDirectBuffer)
            }
            if (got != total) {
                Log.e(TAG, "Read $got bytes, expected $total")
            } else {
                mNativeDict = openNative(
                    mNativeDictDirectBuffer!!,
                    TYPED_LETTER_MULTIPLIER, FULL_WORD_FREQ_MULTIPLIER, total
                )
                mDictLength = total
            }
            if (mDictLength > 10000) Log.i("DevKey", "Loaded dictionary, len=$mDictLength")
        } catch (e: IOException) {
            Log.w(TAG, "No available memory for binary dictionary")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Failed to load native dictionary", e)
        } finally {
            try {
                for (stream in streams) {
                    stream.close()
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to close input stream")
            }
        }
    }

    private fun loadDictionary(context: Context, resId: IntArray) {
        val streams = Array(resId.size) { i ->
            context.resources.openRawResource(resId[i])
        }
        loadDictionary(streams)
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

        val codesSize = composer.size()
        mInputCodes.fill(-1)
        if (codesSize > 0) {
            // WHY: composer.getCodesAt(0) throws IndexOutOfBoundsException when
            // the composer is empty (next-word prediction path in Suggest
            // .getNextWordSuggestions passes a zero-size composer). The native
            // side (see dictionary.cpp Dictionary::searchForTerminalNode) is
            // patched to skip checkFirstCharacter when mInputLength == 0, so
            // passing codesSize=0 with mInputCodes left as -1 fill is safe.
            val alternatives = composer.getCodesAt(0)
            alternatives.copyInto(mInputCodes, 0, 0, minOf(alternatives.size, MAX_ALTERNATIVES))
        }

        val count = getBigramsNative(
            mNativeDict, chars, chars.size, mInputCodes, codesSize,
            mOutputCharsBigrams, mFrequenciesBigrams, MAX_WORD_LENGTH, MAX_BIGRAMS,
            MAX_ALTERNATIVES
        )

        for (j in 0 until count) {
            if (mFrequenciesBigrams[j] < 1) break
            val start = j * MAX_WORD_LENGTH
            var len = 0
            while (mOutputCharsBigrams[start + len] != 0.toChar()) {
                len++
            }
            if (len > 0) {
                callback.addWord(
                    mOutputCharsBigrams, start, len, mFrequenciesBigrams[j],
                    mDicTypeId, DataType.BIGRAM
                )
            }
        }
    }

    override fun getWords(
        composer: WordComposer,
        callback: WordCallback,
        nextLettersFrequencies: IntArray?
    ) {
        val codesSize = composer.size()
        // Won't deal with really long words.
        if (codesSize > MAX_WORD_LENGTH - 1) return

        mInputCodes.fill(-1)
        for (i in 0 until codesSize) {
            val alternatives = composer.getCodesAt(i)
            alternatives.copyInto(mInputCodes, i * MAX_ALTERNATIVES, 0, minOf(alternatives.size, MAX_ALTERNATIVES))
        }
        mOutputChars.fill(0.toChar())
        mFrequencies.fill(0)

        if (mNativeDict == 0L) return

        var count = getSuggestionsNative(
            mNativeDict, mInputCodes, codesSize,
            mOutputChars, mFrequencies,
            MAX_WORD_LENGTH, MAX_WORDS, MAX_ALTERNATIVES, -1,
            nextLettersFrequencies,
            nextLettersFrequencies?.size ?: 0
        )

        // If there aren't sufficient suggestions, search for words by allowing wild cards at
        // the different character positions. This feature is not ready for prime-time as we need
        // to figure out the best ranking for such words compared to proximity corrections and
        // completions.
        if (ENABLE_MISSED_CHARACTERS && count < 5) {
            for (skip in 0 until codesSize) {
                val tempCount = getSuggestionsNative(
                    mNativeDict, mInputCodes, codesSize,
                    mOutputChars, mFrequencies,
                    MAX_WORD_LENGTH, MAX_WORDS, MAX_ALTERNATIVES, skip,
                    null, 0
                )
                count = maxOf(count, tempCount)
                if (tempCount > 0) break
            }
        }

        for (j in 0 until count) {
            if (mFrequencies[j] < 1) break
            val start = j * MAX_WORD_LENGTH
            var len = 0
            while (mOutputChars[start + len] != 0.toChar()) {
                len++
            }
            if (len > 0) {
                callback.addWord(
                    mOutputChars, start, len, mFrequencies[j], mDicTypeId,
                    DataType.UNIGRAM
                )
            }
        }
    }

    override fun isValidWord(word: CharSequence): Boolean {
        if (mNativeDict == 0L) return false
        val len = minOf(word.length, MAX_WORD_LENGTH)
        for (i in 0 until len) mWordBuffer[i] = word[i]
        return isValidWordNative(mNativeDict, mWordBuffer, len)
    }

    fun getSize(): Int = mDictLength

    @Synchronized
    override fun close() {
        if (mNativeDict != 0L) {
            closeNative(mNativeDict)
            mNativeDict = 0
        }
    }

    companion object {
        /**
         * There is difference between what java and native code can handle.
         * This value should only be used in BinaryDictionary.
         * It is necessary to keep it at this value because some languages e.g. German have
         * really long words.
         */
        val MAX_WORD_LENGTH = 48

        private const val TAG = "DevKey/BinaryDictionary"
        private const val MAX_ALTERNATIVES = 16
        private const val MAX_WORDS = 18
        private const val MAX_BIGRAMS = 60

        private const val TYPED_LETTER_MULTIPLIER = 2
        private const val ENABLE_MISSED_CHARACTERS = true

        // JNI bridge: the native library registers methods on the old package path class.
        // Trigger loading of the bridge class which loads the native library.
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
