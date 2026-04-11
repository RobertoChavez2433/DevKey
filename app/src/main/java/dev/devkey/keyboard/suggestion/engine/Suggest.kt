// Copyright (C) 2008 The Android Open Source Project. Licensed under the Apache License, Version 2.0.
package dev.devkey.keyboard.suggestion.engine
import dev.devkey.keyboard.*
import dev.devkey.keyboard.dictionary.base.*
import dev.devkey.keyboard.dictionary.loader.PluginManager
import dev.devkey.keyboard.suggestion.word.WordComposer

import android.content.Context
import android.view.View
import java.nio.ByteBuffer

class Suggest {

    private var mMainDict: BinaryDictionary

    private val mDicts = DictionaryRefs()
    private val mState: SuggestState
    private val mInserter: SuggestionInserter
    private val mNextWordSuggester: NextWordSuggester
    private val mPipeline: SuggestionPipeline

    private var mAutoTextEnabled = false
    private var mCorrectionMode = CORRECTION_BASIC

    val bigramSuggestions: List<CharSequence> get() = mState.bigramSuggestions

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
        mState = SuggestState(PREF_MAX_SUGGESTIONS_DEFAULT)
        mInserter = SuggestionInserter(mState)
        mNextWordSuggester = NextWordSuggester(mState, mInserter, mMainDict, mDicts)
        mPipeline = SuggestionPipeline(
            mState, mInserter, mNextWordSuggester, mMainDict, mDicts,
            { mCorrectionMode }, { mAutoTextEnabled }
        )
        initPool()
    }

    constructor(context: Context, byteBuffer: ByteBuffer) {
        mMainDict = BinaryDictionary(context, byteBuffer, DIC_MAIN)
        mState = SuggestState(PREF_MAX_SUGGESTIONS_DEFAULT)
        mInserter = SuggestionInserter(mState)
        mNextWordSuggester = NextWordSuggester(mState, mInserter, mMainDict, mDicts)
        mPipeline = SuggestionPipeline(
            mState, mInserter, mNextWordSuggester, mMainDict, mDicts,
            { mCorrectionMode }, { mAutoTextEnabled }
        )
        initPool()
    }

    private fun initPool() {
        for (i in 0 until mState.prefMaxSuggestions) {
            val sb = StringBuilder(APPROX_MAX_WORD_LENGTH)
            mState.stringPool.add(sb)
        }
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    fun setAutoTextEnabled(enabled: Boolean) {
        mAutoTextEnabled = enabled
    }

    fun getCorrectionMode(): Int = mCorrectionMode

    fun setCorrectionMode(mode: Int) {
        mCorrectionMode = mode
    }

    fun hasMainDictionary(): Boolean = mMainDict.getSize() > LARGE_DICTIONARY_THRESHOLD

    fun getApproxMaxWordLength(): Int = APPROX_MAX_WORD_LENGTH

    /**
     * Sets an optional user dictionary resource to be loaded. The user dictionary is consulted
     * before the main dictionary, if set.
     */
    fun setUserDictionary(userDictionary: Dictionary?) {
        mDicts.userDictionary = userDictionary
    }

    /** Sets an optional contacts dictionary resource to be loaded. */
    fun setContactsDictionary(userDictionary: Dictionary?) {
        mDicts.contactsDictionary = userDictionary
    }

    fun setAutoDictionary(autoDictionary: Dictionary?) {
        mDicts.autoDictionary = autoDictionary
    }

    fun setUserBigramDictionary(userBigramDictionary: Dictionary?) {
        mDicts.userBigramDictionary = userBigramDictionary
    }

    /**
     * Number of suggestions to generate from the input key sequence. This has
     * to be a number between 1 and 100 (inclusive).
     * @throws IllegalArgumentException if the number is out of range
     */
    fun setMaxSuggestions(maxSuggestions: Int) {
        require(maxSuggestions in 1..100) { "maxSuggestions must be between 1 and 100" }
        mState.prefMaxSuggestions = maxSuggestions
        mState.resizePriorities()
        mInserter.collectGarbage(mState.suggestions, mState.prefMaxSuggestions)
        while (mState.stringPool.size < mState.prefMaxSuggestions) {
            val sb = StringBuilder(APPROX_MAX_WORD_LENGTH)
            mState.stringPool.add(sb)
        }
    }

    // -------------------------------------------------------------------------
    // Public API — delegates to the pipeline
    // -------------------------------------------------------------------------

    /**
     * Returns a list of words that match the list of character codes passed in.
     * This list will be overwritten the next time this function is called.
     *
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
    ): List<CharSequence> = mPipeline.getSuggestions(
        view, wordComposer, includeTypedWordIfValid, prevWordForBigram
    )

    fun getNextLettersFrequencies(): IntArray = mState.nextLettersFrequencies

    fun hasMinimalCorrection(): Boolean = mState.haveCorrection

    /**
     * Bigram-only suggestions for the "next word after space" case.
     * FROM SPEC: §4.3 "predictive next-word after space".
     */
    fun getNextWordSuggestions(prevWord: CharSequence?): List<CharSequence> =
        mNextWordSuggester.getNextWordSuggestions(prevWord)

    fun isValidWord(word: CharSequence): Boolean {
        if (word.isEmpty()) return false
        return mMainDict.isValidWord(word) ||
                (mDicts.userDictionary != null && mDicts.userDictionary!!.isValidWord(word)) ||
                (mDicts.autoDictionary != null && mDicts.autoDictionary!!.isValidWord(word)) ||
                (mDicts.contactsDictionary != null && mDicts.contactsDictionary!!.isValidWord(word))
    }

    fun close() {
        mMainDict.close()
        mDicts.userDictionary?.close()
        mDicts.autoDictionary?.close()
        mDicts.contactsDictionary?.close()
        mDicts.userBigramDictionary?.close()
    }

    companion object {
        const val APPROX_MAX_WORD_LENGTH = 32

        const val CORRECTION_NONE = 0
        const val CORRECTION_BASIC = 1
        const val CORRECTION_FULL = 2
        const val CORRECTION_FULL_BIGRAM = 3

        /**
         * Words that appear in both bigram and unigram data get a multiplier ranging from
         * BIGRAM_MULTIPLIER_MIN to BIGRAM_MULTIPLIER_MAX depending on the frequency score from
         * bigram data.
         */
        const val BIGRAM_MULTIPLIER_MIN = 1.2
        const val BIGRAM_MULTIPLIER_MAX = 1.5

        /**
         * Maximum possible bigram frequency. Will depend on how many bits are being used in data
         * structure. Maximum bigram frequency will get BIGRAM_MULTIPLIER_MAX as the multiplier.
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

        private const val PREF_MAX_SUGGESTIONS_DEFAULT = 12
    }
}
