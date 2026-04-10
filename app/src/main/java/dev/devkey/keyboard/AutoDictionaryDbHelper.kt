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

// DB schema constants shared between AutoDictionary and this helper.
internal const val AUTODICT_TABLE_NAME = "words"
internal const val AUTODICT_COLUMN_ID = android.provider.BaseColumns._ID
internal const val AUTODICT_COLUMN_WORD = "word"
internal const val AUTODICT_COLUMN_FREQUENCY = "freq"
internal const val AUTODICT_COLUMN_LOCALE = "locale"
internal const val AUTODICT_DEFAULT_SORT_ORDER = "$AUTODICT_COLUMN_FREQUENCY DESC"

private const val DATABASE_NAME = "auto_dict.db"
private const val DATABASE_VERSION = 1

private val sAutoDictProjectionMap = HashMap<String, String>().apply {
    put(AUTODICT_COLUMN_ID, AUTODICT_COLUMN_ID)
    put(AUTODICT_COLUMN_WORD, AUTODICT_COLUMN_WORD)
    put(AUTODICT_COLUMN_FREQUENCY, AUTODICT_COLUMN_FREQUENCY)
    put(AUTODICT_COLUMN_LOCALE, AUTODICT_COLUMN_LOCALE)
}

/**
 * SQLite open/create/upgrade helper for the auto-dictionary database.
 * Owns the singleton connection and exposes query and write helpers used
 * by [AutoDictionary].
 */
internal class AutoDictionaryDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $AUTODICT_TABLE_NAME (" +
                    "$AUTODICT_COLUMN_ID INTEGER PRIMARY KEY," +
                    "$AUTODICT_COLUMN_WORD TEXT," +
                    "$AUTODICT_COLUMN_FREQUENCY INTEGER," +
                    "$AUTODICT_COLUMN_LOCALE TEXT" +
                    ");"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(
            "AutoDictionary",
            "Upgrading database from version $oldVersion to $newVersion, " +
                    "which will destroy all old data"
        )
        db.execSQL("DROP TABLE IF EXISTS $AUTODICT_TABLE_NAME")
        onCreate(db)
    }

    fun query(selection: String, selectionArgs: Array<String>): Cursor {
        val qb = SQLiteQueryBuilder()
        qb.tables = AUTODICT_TABLE_NAME
        qb.projectionMap = sAutoDictProjectionMap
        return qb.query(
            readableDatabase, null, selection, selectionArgs,
            null, null, AUTODICT_DEFAULT_SORT_ORDER
        )
    }

    fun updateDb(map: HashMap<String, Int?>, locale: String) {
        val db = writableDatabase
        for ((word, freq) in map) {
            db.delete(
                AUTODICT_TABLE_NAME,
                "$AUTODICT_COLUMN_WORD=? AND $AUTODICT_COLUMN_LOCALE=?",
                arrayOf(word, locale)
            )
            if (freq != null) {
                db.insert(AUTODICT_TABLE_NAME, null, contentValues(word, freq, locale))
            }
        }
    }

    companion object {
        @Volatile private var instance: AutoDictionaryDbHelper? = null

        fun getInstance(context: Context): AutoDictionaryDbHelper =
            instance ?: synchronized(this) {
                instance ?: AutoDictionaryDbHelper(context.applicationContext).also {
                    instance = it
                }
            }

        private fun contentValues(word: String, frequency: Int, locale: String): ContentValues =
            ContentValues(4).apply {
                put(AUTODICT_COLUMN_WORD, word)
                put(AUTODICT_COLUMN_FREQUENCY, frequency)
                put(AUTODICT_COLUMN_LOCALE, locale)
            }
    }
}
