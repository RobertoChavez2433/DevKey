/*
 * Copyright (C) 2010 Google Inc.
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
import dev.devkey.keyboard.suggestion.engine.WordPromotionDelegate

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stores all the pairs user types in databases. Prune the database if the size
 * gets too big. Unlike AutoDictionary, it even stores the pairs that are already
 * in the dictionary.
 */
class UserBigramDictionary(
    context: Context,
    private val mDelegate: WordPromotionDelegate?,
    private val mLocale: String,
    dicTypeId: Int
) : ExpandableDictionary(context, dicTypeId) {

    private var mPendingWrites = HashSet<Bigram>()
    private val mPendingWritesLock = Any()
    private val dictScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Regular class (not data class) because equals/hashCode intentionally exclude frequency —
    // a data class would auto-generate equality that includes all constructor properties.
    class Bigram(
        val word1: String,
        val word2: String,
        val frequency: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (other !is Bigram) return false
            return word1 == other.word1 && word2 == other.word2
        }

        override fun hashCode(): Int {
            return 31 * word1.hashCode() + word2.hashCode()
        }
    }

    init {
        if (sOpenHelper == null) {
            sOpenHelper = BigramDbHelper(getContext())
        }
        if (mLocale.length > 1) {
            loadDictionary()
        }
    }

    fun setDatabaseMax(maxUserBigram: Int) {
        require(maxUserBigram > 0) { "maxUserBigram must be positive, was $maxUserBigram" }
        sMaxUserBigrams = maxUserBigram
    }

    fun setDatabaseDelete(deleteUserBigram: Int) {
        require(deleteUserBigram > 0) { "deleteUserBigram must be positive, was $deleteUserBigram" }
        sDeleteUserBigrams = deleteUserBigram
    }

    override fun close() {
        flushPendingWrites()
        dictScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        // Don't close the database as locale changes will require it to be reopened anyway
        // Also, the database is written to somewhat frequently, so it needs to be kept alive
        // throughout the life of the process.
        super.close()
    }

    /**
     * Pair will be added to the userbigram database.
     */
    fun addBigrams(word1: String, word2: String): Int {
        var mutableWord2 = word2
        // remove caps
        if (mDelegate != null && mDelegate.getCurrentWord().isAutoCapitalized()) {
            mutableWord2 = mutableWord2[0].lowercaseChar() + mutableWord2.substring(1)
        }

        var freq = super.addBigram(word1, mutableWord2, FREQUENCY_FOR_TYPED)
        if (freq > FREQUENCY_MAX) freq = FREQUENCY_MAX
        synchronized(mPendingWritesLock) {
            if (freq == FREQUENCY_FOR_TYPED || mPendingWrites.isEmpty()) {
                mPendingWrites.add(Bigram(word1, mutableWord2, freq))
            } else {
                val bi = Bigram(word1, mutableWord2, freq)
                mPendingWrites.remove(bi)
                mPendingWrites.add(bi)
            }
        }
        return freq
    }

    /** Schedules a background coroutine to write any pending words to the database. */
    fun flushPendingWrites() {
        synchronized(mPendingWritesLock) {
            if (mPendingWrites.isEmpty()) return
            val pendingWrites = mPendingWrites
            val locale = mLocale
            // Create a new set for writing new entries into while the old one is written to db
            mPendingWrites = HashSet()
            dictScope.launch {
                withContext(Dispatchers.IO) {
                    sUpdatingDB = true
                    try {
                        val helper = requireNotNull(sOpenHelper) { "DatabaseHelper not initialized" }
                        writeBigramBatch(helper, pendingWrites, locale, sMaxUserBigrams, sDeleteUserBigrams)
                    } finally {
                        sUpdatingDB = false
                    }
                }
            }
        }
    }

    /** Used for testing purpose */
    fun waitUntilUpdateDBDone() {
        synchronized(mPendingWritesLock) {
            while (sUpdatingDB) {
                try {
                    Thread.sleep(100)
                } catch (_: InterruptedException) {
                }
            }
        }
    }

    override fun loadDictionaryAsync() {
        // Load the words that correspond to the current input locale
        val helper = requireNotNull(sOpenHelper) { "DatabaseHelper not initialized" }
        val cursor = queryBigrams(helper, "$MAIN_COLUMN_LOCALE=?", arrayOf(mLocale))
        try {
            if (cursor.moveToFirst()) {
                val word1Index = cursor.getColumnIndex(MAIN_COLUMN_WORD1)
                val word2Index = cursor.getColumnIndex(MAIN_COLUMN_WORD2)
                val frequencyIndex = cursor.getColumnIndex(FREQ_COLUMN_FREQUENCY)
                while (!cursor.isAfterLast) {
                    val word1 = cursor.getString(word1Index)
                    val word2 = cursor.getString(word2Index)
                    val frequency = cursor.getInt(frequencyIndex)
                    // Safeguard against adding really long words. Stack may overflow due
                    // to recursive lookup
                    if (word1.length < MAX_WORD_LENGTH && word2.length < MAX_WORD_LENGTH) {
                        super.setBigram(word1, word2, frequency)
                    }
                    cursor.moveToNext()
                }
            }
        } finally {
            cursor.close()
        }
    }

    companion object {
        /** Any pair being typed or picked */
        private const val FREQUENCY_FOR_TYPED = 2

        /** Maximum frequency for all pairs */
        private const val FREQUENCY_MAX = 127

        /**
         * If this pair is typed 6 times, it would be suggested.
         * Should be smaller than ContactsDictionary.FREQUENCY_FOR_CONTACTS_BIGRAM
         */
        val SUGGEST_THRESHOLD = 6 * FREQUENCY_FOR_TYPED

        /** Maximum number of pairs. Pruning will start when databases goes above this number. */
        private var sMaxUserBigrams = 10000

        /**
         * When it hits maximum bigram pair, it will delete until you are left with
         * only (sMaxUserBigrams - sDeleteUserBigrams) pairs.
         * Do not keep this number small to avoid deleting too often.
         */
        private var sDeleteUserBigrams = 1000

        private var sOpenHelper: BigramDbHelper? = null

        @Volatile
        private var sUpdatingDB = false
    }
}
