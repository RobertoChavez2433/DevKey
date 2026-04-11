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

package dev.devkey.keyboard.dictionary.user
import dev.devkey.keyboard.dictionary.expandable.ExpandableDictionary
import dev.devkey.keyboard.suggestion.engine.WordPromotionDelegate

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stores new words temporarily until they are promoted to the user dictionary
 * for longevity. Words in the auto dictionary are used to determine if it's ok
 * to accept a word that's not in the main or user dictionary. Using a new word
 * repeatedly will promote it to the user dictionary.
 */
class AutoDictionary(
    context: Context,
    private val mDelegate: WordPromotionDelegate,
    private val mLocale: String,
    dicTypeId: Int
) : ExpandableDictionary(context, dicTypeId) {

    private var mPendingWrites = HashMap<String, Int?>()
    private val mPendingWritesLock = Any()
    private val dictScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dbHelper = AutoDictionaryDbHelper.getInstance(context)

    init {
        if (mLocale.length > 1) {
            loadDictionary()
        }
    }

    override fun isValidWord(word: CharSequence): Boolean {
        val frequency = getWordFrequency(word)
        return frequency >= VALIDITY_THRESHOLD
    }

    override fun close() {
        flushPendingWrites()
        dictScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        // Don't close the database as locale changes will require it to be reopened anyway.
        // The database is written to frequently so it must stay alive for the process lifetime.
        super.close()
    }

    override fun loadDictionaryAsync() {
        val cursor = dbHelper.query("$AUTODICT_COLUMN_LOCALE=?", arrayOf(mLocale))
        try {
            if (cursor.moveToFirst()) {
                val wordIndex = cursor.getColumnIndex(AUTODICT_COLUMN_WORD)
                val frequencyIndex = cursor.getColumnIndex(AUTODICT_COLUMN_FREQUENCY)
                while (!cursor.isAfterLast) {
                    val word = cursor.getString(wordIndex)
                    val frequency = cursor.getInt(frequencyIndex)
                    // Safeguard against adding really long words — stack may overflow on deep lookup.
                    if (word.length < getMaxWordLength()) {
                        super.addWord(word, frequency)
                    }
                    cursor.moveToNext()
                }
            }
        } finally {
            cursor.close()
        }
    }

    override fun addWord(word: String, frequency: Int) {
        var mutableWord = word
        val length = mutableWord.length
        // Don't add very short or very long words.
        if (length < 2 || length > getMaxWordLength()) return
        if (mDelegate.getCurrentWord().isAutoCapitalized()) {
            // Remove caps before adding
            mutableWord = mutableWord[0].lowercaseChar() + mutableWord.substring(1)
        }
        var freq = getWordFrequency(mutableWord)
        freq = if (freq < 0) frequency else freq + frequency
        super.addWord(mutableWord, freq)

        if (freq >= PROMOTION_THRESHOLD) {
            mDelegate.promoteToUserDictionary(mutableWord, FREQUENCY_FOR_AUTO_ADD)
            freq = 0
        }

        synchronized(mPendingWritesLock) {
            // Write a null frequency if the word is to be deleted from the db.
            mPendingWrites[mutableWord] = if (freq == 0) null else freq
        }
    }

    /**
     * Schedules a background coroutine to write any pending words to the database.
     */
    fun flushPendingWrites() {
        synchronized(mPendingWritesLock) {
            if (mPendingWrites.isEmpty()) return
            val pendingWrites = mPendingWrites
            val locale = mLocale
            mPendingWrites = HashMap()
            dictScope.launch {
                withContext(Dispatchers.IO) {
                    dbHelper.updateDb(pendingWrites, locale)
                }
            }
        }
    }

    companion object {
        // Weight added to a user picking a new word from the suggestion strip
        val FREQUENCY_FOR_PICKED = 3
        // Weight added to a user typing a new word that doesn't get corrected (or is reverted)
        val FREQUENCY_FOR_TYPED = 1
        // A word that is frequently typed and gets promoted to the user dictionary, uses this
        // frequency.
        val FREQUENCY_FOR_AUTO_ADD = 250
        // If the user touches a typed word 2 times or more, it will become valid.
        private val VALIDITY_THRESHOLD = 2 * FREQUENCY_FOR_PICKED
        // If the user touches a typed word 4 times or more, it will be added to the user dict.
        private val PROMOTION_THRESHOLD = 4 * FREQUENCY_FOR_PICKED
    }
}
