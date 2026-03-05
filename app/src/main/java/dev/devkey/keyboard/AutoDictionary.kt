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

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteQueryBuilder
import android.util.Log
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

    init {
        if (sOpenHelper == null) {
            sOpenHelper = DatabaseHelper(getContext())
        }
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
        // Don't close the database as locale changes will require it to be reopened anyway
        // Also, the database is written to somewhat frequently, so it needs to be kept alive
        // throughout the life of the process.
        super.close()
    }

    override fun loadDictionaryAsync() {
        // Load the words that correspond to the current input locale
        val cursor = query("$COLUMN_LOCALE=?", arrayOf(mLocale))
        try {
            if (cursor.moveToFirst()) {
                val wordIndex = cursor.getColumnIndex(COLUMN_WORD)
                val frequencyIndex = cursor.getColumnIndex(COLUMN_FREQUENCY)
                while (!cursor.isAfterLast) {
                    val word = cursor.getString(wordIndex)
                    val frequency = cursor.getInt(frequencyIndex)
                    // Safeguard against adding really long words. Stack may overflow due
                    // to recursive lookup
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
            // Write a null frequency if it is to be deleted from the db
            mPendingWrites[mutableWord] = if (freq == 0) null else freq
        }
    }

    /**
     * Schedules a background coroutine to write any pending words to the database.
     */
    fun flushPendingWrites() {
        synchronized(mPendingWritesLock) {
            // Nothing pending? Return
            if (mPendingWrites.isEmpty()) return
            val pendingWrites = mPendingWrites
            val locale = mLocale
            // Create a new map for writing new entries into while the old one is written to db
            mPendingWrites = HashMap()
            dictScope.launch {
                withContext(Dispatchers.IO) {
                    updateDatabase(pendingWrites, locale)
                }
            }
        }
    }

    private fun updateDatabase(map: HashMap<String, Int?>, locale: String) {
        val db = requireNotNull(sOpenHelper) { "DatabaseHelper not initialized" }.writableDatabase
        val entries = map.entries
        for ((key, freq) in entries) {
            db.delete(
                AUTODICT_TABLE_NAME, "$COLUMN_WORD=? AND $COLUMN_LOCALE=?",
                arrayOf(key, locale)
            )
            if (freq != null) {
                db.insert(
                    AUTODICT_TABLE_NAME, null,
                    getContentValues(key, freq, locale)
                )
            }
        }
    }

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $AUTODICT_TABLE_NAME (" +
                        "$COLUMN_ID INTEGER PRIMARY KEY," +
                        "$COLUMN_WORD TEXT," +
                        "$COLUMN_FREQUENCY INTEGER," +
                        "$COLUMN_LOCALE TEXT" +
                        ");"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            Log.w(
                "AutoDictionary", "Upgrading database from version $oldVersion to " +
                        "$newVersion, which will destroy all old data"
            )
            db.execSQL("DROP TABLE IF EXISTS $AUTODICT_TABLE_NAME")
            onCreate(db)
        }
    }

    private fun query(selection: String, selectionArgs: Array<String>): Cursor {
        val qb = SQLiteQueryBuilder()
        qb.tables = AUTODICT_TABLE_NAME
        qb.projectionMap = sDictProjectionMap

        // Get the database and run the query
        val db = requireNotNull(sOpenHelper) { "DatabaseHelper not initialized" }.readableDatabase
        return qb.query(db, null, selection, selectionArgs, null, null, DEFAULT_SORT_ORDER)
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

        private const val DATABASE_NAME = "auto_dict.db"
        private const val DATABASE_VERSION = 1

        // These are the columns in the dictionary
        private const val COLUMN_ID = android.provider.BaseColumns._ID
        private const val COLUMN_WORD = "word"
        private const val COLUMN_FREQUENCY = "freq"
        private const val COLUMN_LOCALE = "locale"

        /** Sort by descending order of frequency. */
        const val DEFAULT_SORT_ORDER = "$COLUMN_FREQUENCY DESC"

        /** Name of the words table in the auto_dict.db */
        private const val AUTODICT_TABLE_NAME = "words"

        private val sDictProjectionMap = HashMap<String, String>().apply {
            put(COLUMN_ID, COLUMN_ID)
            put(COLUMN_WORD, COLUMN_WORD)
            put(COLUMN_FREQUENCY, COLUMN_FREQUENCY)
            put(COLUMN_LOCALE, COLUMN_LOCALE)
        }

        private var sOpenHelper: DatabaseHelper? = null

        private fun getContentValues(word: String, frequency: Int, locale: String): ContentValues {
            return ContentValues(4).apply {
                put(COLUMN_WORD, word)
                put(COLUMN_FREQUENCY, frequency)
                put(COLUMN_LOCALE, locale)
            }
        }
    }
}
