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
            sOpenHelper = DatabaseHelper(getContext())
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

    /**
     * Schedules a background coroutine to write any pending words to the database.
     */
    fun flushPendingWrites() {
        synchronized(mPendingWritesLock) {
            // Nothing pending? Return
            if (mPendingWrites.isEmpty()) return
            val pendingWrites = mPendingWrites
            val locale = mLocale
            // Create a new set for writing new entries into while the old one is written to db
            mPendingWrites = HashSet()
            dictScope.launch {
                withContext(Dispatchers.IO) {
                    updateDatabase(pendingWrites, locale)
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
        val cursor = query("$MAIN_COLUMN_LOCALE=?", arrayOf(mLocale))
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

    /**
     * Query the database
     */
    private fun query(selection: String, selectionArgs: Array<String>): Cursor {
        val qb = SQLiteQueryBuilder()

        // main INNER JOIN frequency ON (main._id=freq.pair_id)
        qb.tables = "$MAIN_TABLE_NAME INNER JOIN $FREQ_TABLE_NAME ON (" +
                "$MAIN_TABLE_NAME.$MAIN_COLUMN_ID=$FREQ_TABLE_NAME.$FREQ_COLUMN_PAIR_ID)"

        qb.projectionMap = sDictProjectionMap

        // Get the database and run the query
        val db = requireNotNull(sOpenHelper) { "DatabaseHelper not initialized" }.readableDatabase
        return qb.query(
            db,
            arrayOf(MAIN_COLUMN_WORD1, MAIN_COLUMN_WORD2, FREQ_COLUMN_FREQUENCY),
            selection, selectionArgs, null, null, null
        )
    }

    private fun updateDatabase(map: HashSet<Bigram>, locale: String) {
        sUpdatingDB = true
        try {
            val db = requireNotNull(sOpenHelper) { "DatabaseHelper not initialized" }.writableDatabase
            db.execSQL("PRAGMA foreign_keys = ON;")
            // Write all the entries to the db wrapped in a single transaction to avoid N+1 commits
            db.beginTransaction()
            try {
                for (bi in map) {
                    // find pair id
                    val c = db.query(
                        MAIN_TABLE_NAME, arrayOf(MAIN_COLUMN_ID),
                        "$MAIN_COLUMN_WORD1=? AND $MAIN_COLUMN_WORD2=? AND $MAIN_COLUMN_LOCALE=?",
                        arrayOf(bi.word1, bi.word2, locale), null, null, null
                    )

                    val pairId: Int
                    if (c.moveToFirst()) {
                        // existing pair
                        pairId = c.getInt(c.getColumnIndex(MAIN_COLUMN_ID))
                        db.delete(
                            FREQ_TABLE_NAME, "$FREQ_COLUMN_PAIR_ID=?",
                            arrayOf(pairId.toString())
                        )
                    } else {
                        // new pair
                        val pairIdLong = db.insert(
                            MAIN_TABLE_NAME, null,
                            getContentValues(bi.word1, bi.word2, locale)
                        )
                        pairId = pairIdLong.toInt()
                    }
                    c.close()

                    // insert new frequency
                    db.insert(FREQ_TABLE_NAME, null, getFrequencyContentValues(pairId, bi.frequency))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            checkPruneData(db)
        } finally {
            sUpdatingDB = false
        }
    }

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = ON;")
            db.execSQL(
                "CREATE TABLE $MAIN_TABLE_NAME (" +
                        "$MAIN_COLUMN_ID INTEGER PRIMARY KEY," +
                        "$MAIN_COLUMN_WORD1 TEXT," +
                        "$MAIN_COLUMN_WORD2 TEXT," +
                        "$MAIN_COLUMN_LOCALE TEXT" +
                        ");"
            )
            db.execSQL(
                "CREATE TABLE $FREQ_TABLE_NAME (" +
                        "$FREQ_COLUMN_ID INTEGER PRIMARY KEY," +
                        "$FREQ_COLUMN_PAIR_ID INTEGER," +
                        "$FREQ_COLUMN_FREQUENCY INTEGER," +
                        "FOREIGN KEY($FREQ_COLUMN_PAIR_ID) REFERENCES $MAIN_TABLE_NAME" +
                        "($MAIN_COLUMN_ID) ON DELETE CASCADE" +
                        ");"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            Log.w(
                TAG, "Upgrading database from version $oldVersion to " +
                        "$newVersion, which will destroy all old data"
            )
            db.execSQL("DROP TABLE IF EXISTS $MAIN_TABLE_NAME")
            db.execSQL("DROP TABLE IF EXISTS $FREQ_TABLE_NAME")
            onCreate(db)
        }
    }

    companion object {
        private const val TAG = "DevKey/UserBigramDictionary"

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

        /**
         * Database version should increase if the database structure changes
         */
        private const val DATABASE_VERSION = 1

        private const val DATABASE_NAME = "userbigram_dict.db"

        /** Name of the words table in the database */
        private const val MAIN_TABLE_NAME = "main"
        private const val MAIN_COLUMN_ID = android.provider.BaseColumns._ID
        private const val MAIN_COLUMN_WORD1 = "word1"
        private const val MAIN_COLUMN_WORD2 = "word2"
        private const val MAIN_COLUMN_LOCALE = "locale"

        /** Name of the frequency table in the database */
        private const val FREQ_TABLE_NAME = "frequency"
        private const val FREQ_COLUMN_ID = android.provider.BaseColumns._ID
        private const val FREQ_COLUMN_PAIR_ID = "pair_id"
        private const val FREQ_COLUMN_FREQUENCY = "freq"

        private val sDictProjectionMap = HashMap<String, String>().apply {
            put(MAIN_COLUMN_ID, MAIN_COLUMN_ID)
            put(MAIN_COLUMN_WORD1, MAIN_COLUMN_WORD1)
            put(MAIN_COLUMN_WORD2, MAIN_COLUMN_WORD2)
            put(MAIN_COLUMN_LOCALE, MAIN_COLUMN_LOCALE)
            put(FREQ_COLUMN_ID, FREQ_COLUMN_ID)
            put(FREQ_COLUMN_PAIR_ID, FREQ_COLUMN_PAIR_ID)
            put(FREQ_COLUMN_FREQUENCY, FREQ_COLUMN_FREQUENCY)
        }

        private var sOpenHelper: DatabaseHelper? = null

        @Volatile
        private var sUpdatingDB = false

        /** Prune any old data if the database is getting too big. */
        private fun checkPruneData(db: SQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = ON;")
            val c = db.query(
                FREQ_TABLE_NAME, arrayOf(FREQ_COLUMN_PAIR_ID),
                null, null, null, null, null
            )
            try {
                val totalRowCount = c.count
                // prune out old data if we have too much data
                if (totalRowCount > sMaxUserBigrams) {
                    val numDeleteRows = (totalRowCount - sMaxUserBigrams) + sDeleteUserBigrams
                    val pairIdColumnId = c.getColumnIndex(FREQ_COLUMN_PAIR_ID)
                    c.moveToFirst()
                    var count = 0
                    while (count < numDeleteRows && !c.isAfterLast) {
                        val pairId = c.getString(pairIdColumnId)
                        // Deleting from MAIN table will delete the frequencies
                        // due to FOREIGN KEY .. ON DELETE CASCADE
                        db.delete(
                            MAIN_TABLE_NAME, "$MAIN_COLUMN_ID=?",
                            arrayOf(pairId)
                        )
                        c.moveToNext()
                        count++
                    }
                }
            } finally {
                c.close()
            }
        }

        private fun getContentValues(word1: String, word2: String, locale: String): ContentValues {
            return ContentValues(3).apply {
                put(MAIN_COLUMN_WORD1, word1)
                put(MAIN_COLUMN_WORD2, word2)
                put(MAIN_COLUMN_LOCALE, locale)
            }
        }

        private fun getFrequencyContentValues(pairId: Int, frequency: Int): ContentValues {
            return ContentValues(2).apply {
                put(FREQ_COLUMN_PAIR_ID, pairId)
                put(FREQ_COLUMN_FREQUENCY, frequency)
            }
        }
    }
}
