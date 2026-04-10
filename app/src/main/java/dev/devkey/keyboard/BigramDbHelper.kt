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

/** Database version should increase if the database structure changes */
internal const val BIGRAM_DATABASE_VERSION = 1
internal const val BIGRAM_DATABASE_NAME = "userbigram_dict.db"

/** Name of the words table in the database */
internal const val MAIN_TABLE_NAME = "main"
internal const val MAIN_COLUMN_ID = android.provider.BaseColumns._ID
internal const val MAIN_COLUMN_WORD1 = "word1"
internal const val MAIN_COLUMN_WORD2 = "word2"
internal const val MAIN_COLUMN_LOCALE = "locale"

/** Name of the frequency table in the database */
internal const val FREQ_TABLE_NAME = "frequency"
internal const val FREQ_COLUMN_ID = android.provider.BaseColumns._ID
internal const val FREQ_COLUMN_PAIR_ID = "pair_id"
internal const val FREQ_COLUMN_FREQUENCY = "freq"

internal val sDictProjectionMap = HashMap<String, String>().apply {
    put(MAIN_COLUMN_ID, MAIN_COLUMN_ID)
    put(MAIN_COLUMN_WORD1, MAIN_COLUMN_WORD1)
    put(MAIN_COLUMN_WORD2, MAIN_COLUMN_WORD2)
    put(MAIN_COLUMN_LOCALE, MAIN_COLUMN_LOCALE)
    put(FREQ_COLUMN_ID, FREQ_COLUMN_ID)
    put(FREQ_COLUMN_PAIR_ID, FREQ_COLUMN_PAIR_ID)
    put(FREQ_COLUMN_FREQUENCY, FREQ_COLUMN_FREQUENCY)
}

/**
 * SQLiteOpenHelper subclass that owns the bigram database schema and migration logic.
 */
internal class BigramDbHelper(context: Context) :
    SQLiteOpenHelper(context, BIGRAM_DATABASE_NAME, null, BIGRAM_DATABASE_VERSION) {

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

    companion object {
        private const val TAG = "DevKey/BigramDbHelper"
    }
}

/** Prune any old data if the database is getting too big. */
internal fun checkPruneData(db: SQLiteDatabase, maxUserBigrams: Int, deleteUserBigrams: Int) {
    db.execSQL("PRAGMA foreign_keys = ON;")
    val c = db.query(
        FREQ_TABLE_NAME, arrayOf(FREQ_COLUMN_PAIR_ID),
        null, null, null, null, null
    )
    try {
        val totalRowCount = c.count
        if (totalRowCount > maxUserBigrams) {
            val numDeleteRows = (totalRowCount - maxUserBigrams) + deleteUserBigrams
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

internal fun getContentValues(word1: String, word2: String, locale: String): ContentValues {
    return ContentValues(3).apply {
        put(MAIN_COLUMN_WORD1, word1)
        put(MAIN_COLUMN_WORD2, word2)
        put(MAIN_COLUMN_LOCALE, locale)
    }
}

internal fun getFrequencyContentValues(pairId: Int, frequency: Int): ContentValues {
    return ContentValues(2).apply {
        put(FREQ_COLUMN_PAIR_ID, pairId)
        put(FREQ_COLUMN_FREQUENCY, frequency)
    }
}

/**
 * Query the database for bigram pairs matching [selection]/[selectionArgs].
 * Returns a [Cursor] over (word1, word2, frequency) columns.
 */
internal fun queryBigrams(helper: BigramDbHelper, selection: String, selectionArgs: Array<String>): Cursor {
    val qb = SQLiteQueryBuilder()
    // main INNER JOIN frequency ON (main._id=freq.pair_id)
    qb.tables = "$MAIN_TABLE_NAME INNER JOIN $FREQ_TABLE_NAME ON (" +
            "$MAIN_TABLE_NAME.$MAIN_COLUMN_ID=$FREQ_TABLE_NAME.$FREQ_COLUMN_PAIR_ID)"
    qb.projectionMap = sDictProjectionMap
    return qb.query(
        helper.readableDatabase,
        arrayOf(MAIN_COLUMN_WORD1, MAIN_COLUMN_WORD2, FREQ_COLUMN_FREQUENCY),
        selection, selectionArgs, null, null, null
    )
}

/**
 * Write a batch of [UserBigramDictionary.Bigram] entries to the database inside a single
 * transaction, then prune if needed. Updates [sUpdatingDB] flag via [onStart]/[onFinish]
 * callbacks so the caller can track write state without a shared mutable reference here.
 */
internal fun writeBigramBatch(
    helper: BigramDbHelper,
    map: HashSet<UserBigramDictionary.Bigram>,
    locale: String,
    maxUserBigrams: Int,
    deleteUserBigrams: Int
) {
    val db = helper.writableDatabase
    db.execSQL("PRAGMA foreign_keys = ON;")
    db.beginTransaction()
    try {
        for (bi in map) {
            val c = db.query(
                MAIN_TABLE_NAME, arrayOf(MAIN_COLUMN_ID),
                "$MAIN_COLUMN_WORD1=? AND $MAIN_COLUMN_WORD2=? AND $MAIN_COLUMN_LOCALE=?",
                arrayOf(bi.word1, bi.word2, locale), null, null, null
            )
            val pairId: Int
            if (c.moveToFirst()) {
                pairId = c.getInt(c.getColumnIndex(MAIN_COLUMN_ID))
                db.delete(FREQ_TABLE_NAME, "$FREQ_COLUMN_PAIR_ID=?", arrayOf(pairId.toString()))
            } else {
                pairId = db.insert(MAIN_TABLE_NAME, null, getContentValues(bi.word1, bi.word2, locale)).toInt()
            }
            c.close()
            db.insert(FREQ_TABLE_NAME, null, getFrequencyContentValues(pairId, bi.frequency))
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
    checkPruneData(db, maxUserBigrams, deleteUserBigrams)
}
