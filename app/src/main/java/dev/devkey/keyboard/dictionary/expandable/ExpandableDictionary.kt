/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.devkey.keyboard.dictionary.expandable
import dev.devkey.keyboard.dictionary.base.*
import dev.devkey.keyboard.dictionary.bigram.BigramTraversal
import dev.devkey.keyboard.dictionary.trie.*
import dev.devkey.keyboard.suggestion.word.WordComposer

import android.content.Context
import dev.devkey.keyboard.compose.BASE_CHARS_TABLE
import dev.devkey.keyboard.compose.lowercaseByBaseChars
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Base class for an in-memory dictionary that can grow dynamically and can
 * be searched for suggestions and valid words.
 */
open class ExpandableDictionary(
    private val mContext: Context,
    private val mDicTypeId: Int
) : Dictionary() {

    private var mWordBuilder = CharArray(MAX_WORD_LENGTH)
    private var mMaxDepth = 0
    private var mInputLength = 0
    private var mNextLettersFrequencies: IntArray? = null

    private var mRequiresReload = false
    private var mUpdatingDictionary = false

    // Use this lock before touching mUpdatingDictionary & mRequiresReload
    private val mUpdatingLock = Any()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val bigramTraversal = BigramTraversal(mDicTypeId)

    private var mRoots: NodeArray = NodeArray()

    private var mCodes: Array<IntArray?> = arrayOfNulls(MAX_WORD_LENGTH)

    init {
        clearDictionary()
    }

    fun loadDictionary() {
        synchronized(mUpdatingLock) {
            startDictionaryLoadingTaskLocked()
        }
    }

    fun startDictionaryLoadingTaskLocked() {
        if (!mUpdatingDictionary) {
            mUpdatingDictionary = true
            mRequiresReload = false
            scope.launch(Dispatchers.IO) {
                loadDictionaryAsync()
                synchronized(mUpdatingLock) {
                    mUpdatingDictionary = false
                }
            }
        }
    }

    fun setRequiresReload(reload: Boolean) {
        synchronized(mUpdatingLock) {
            mRequiresReload = reload
        }
    }

    fun getRequiresReload(): Boolean = mRequiresReload

    /** Override to load your dictionary here, on a background thread. */
    open fun loadDictionaryAsync() {
    }

    fun getContext(): Context = mContext

    fun getMaxWordLength(): Int = MAX_WORD_LENGTH

    open fun addWord(word: String, frequency: Int) {
        TrieEditor.addWordRec(mRoots, word, 0, frequency, null)
    }

    override fun getWords(
        composer: WordComposer,
        callback: WordCallback,
        nextLettersFrequencies: IntArray?
    ) {
        synchronized(mUpdatingLock) {
            if (mRequiresReload) startDictionaryLoadingTaskLocked()
            if (mUpdatingDictionary) return
        }

        mInputLength = composer.size()
        mNextLettersFrequencies = nextLettersFrequencies
        if (mCodes.size < mInputLength) mCodes = arrayOfNulls(mInputLength)
        for (i in 0 until mInputLength) {
            mCodes[i] = composer.getCodesAt(i)
        }
        mMaxDepth = mInputLength * 3
        getWordsRec(mRoots, composer, mWordBuilder, 0, false, 1, 0, -1, callback)
        for (i in 0 until mInputLength) {
            getWordsRec(mRoots, composer, mWordBuilder, 0, false, 1, 0, i, callback)
        }
    }

    override fun isValidWord(word: CharSequence): Boolean {
        synchronized(mUpdatingLock) {
            if (mRequiresReload) startDictionaryLoadingTaskLocked()
            if (mUpdatingDictionary) return false
        }
        return getWordFrequency(word) > -1
    }

    /**
     * Returns the word's frequency or -1 if not found.
     */
    fun getWordFrequency(word: CharSequence): Int =
        TrieEditor.searchNode(mRoots, word, 0, word.length)?.frequency ?: -1

    /** Delegates trie word search to [TrieWalkState]. */
    protected open fun getWordsRec(
        roots: NodeArray, codes: WordComposer, word: CharArray,
        depth: Int, completion: Boolean, snr: Int, inputIndex: Int, skipPos: Int, callback: WordCallback
    ) = TrieWalkState(mInputLength, mMaxDepth, mNextLettersFrequencies, mCodes, mDicTypeId, word)
        .walkRec(roots, codes, word, depth, completion, snr, inputIndex, skipPos, callback)

    protected fun setBigram(word1: String, word2: String, frequency: Int): Int =
        TrieEditor.addOrSetBigram(mRoots, word1, word2, frequency, false)

    protected fun addBigram(word1: String, word2: String, frequency: Int): Int =
        TrieEditor.addOrSetBigram(mRoots, word1, word2, frequency, true)

    // @VisibleForTesting
    fun reloadDictionaryIfRequired(): Boolean {
        synchronized(mUpdatingLock) {
            if (mRequiresReload) startDictionaryLoadingTaskLocked()
            return mUpdatingDictionary
        }
    }

    override fun getBigrams(
        composer: WordComposer,
        previousWord: CharSequence,
        callback: WordCallback,
        nextLettersFrequencies: IntArray?
    ) {
        if (!reloadDictionaryIfRequired()) {
            bigramTraversal.runReverseLookUp(mRoots, previousWord, callback)
        }
    }

    /**
     * Used only for testing purposes.
     * This function will wait for loading from database to be done.
     *
     * TODO: Legacy busy-wait; replace with CompletableDeferred in a future cleanup pass.
     */
    fun waitForDictionaryLoading() {
        while (mUpdatingDictionary) {
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
            }
        }
    }

    protected fun clearDictionary() {
        mRoots = NodeArray()
    }

    override fun close() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.close()
    }

    companion object {
        /** Max word length; Java overflows the stack beyond 32. */
        const val MAX_WORD_LENGTH = 32

        /** Delegates to [lowercaseByBaseChars] from BaseCharsTable.kt. */
        fun toLowerCase(c: Char): Char = lowercaseByBaseChars(c)

        /** Data lives in [BASE_CHARS_TABLE] (BaseCharsTable.kt). */
        val BASE_CHARS: CharArray get() = BASE_CHARS_TABLE
    }
}
