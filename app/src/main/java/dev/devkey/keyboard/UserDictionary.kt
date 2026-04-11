/*
 * Copyright (C) 2008 The Android Open Source Project
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

package dev.devkey.keyboard
import dev.devkey.keyboard.suggestion.engine.Suggest
import dev.devkey.keyboard.suggestion.word.WordComposer

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.provider.UserDictionary.Words
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserDictionary(
    context: Context,
    private val mLocale: String
) : ExpandableDictionary(context, Suggest.DIC_USER) {

    private var mObserver: ContentObserver? = null
    private val dictScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        val cres = context.contentResolver
        mObserver = object : ContentObserver(null) {
            override fun onChange(self: Boolean) {
                setRequiresReload(true)
            }
        }
        cres.registerContentObserver(Words.CONTENT_URI, true, mObserver!!)
        loadDictionary()
    }

    @Synchronized
    override fun close() {
        if (mObserver != null) {
            getContext().contentResolver.unregisterContentObserver(mObserver!!)
            mObserver = null
        }
        dictScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.close()
    }

    override fun loadDictionaryAsync() {
        val cursor = getContext().contentResolver
            .query(
                Words.CONTENT_URI, PROJECTION, "(locale IS NULL) or (locale=?)",
                arrayOf(mLocale), null
            )
        addWords(cursor)
    }

    /**
     * Adds a word to the dictionary and makes it persistent.
     * @param word the word to add. If the word is capitalized, then the dictionary will
     * recognize it as a capitalized word when searched.
     * @param frequency the frequency of occurrence of the word. A frequency of 255 is considered
     * the highest.
     */
    @Synchronized
    override fun addWord(word: String, frequency: Int) {
        // Force load the dictionary here synchronously
        if (getRequiresReload()) loadDictionaryAsync()
        // Safeguard against adding long words. Can cause stack overflow.
        if (word.length >= getMaxWordLength()) return

        super.addWord(word, frequency)

        // Update the user dictionary provider
        val values = ContentValues(5).apply {
            put(Words.WORD, word)
            put(Words.FREQUENCY, frequency)
            put(Words.LOCALE, mLocale)
            put(Words.APP_ID, 0)
        }

        val contentResolver = getContext().contentResolver
        // TODO: defer reload to coroutine — addWord currently triggers loadDictionaryAsync on the
        //  calling thread via getRequiresReload(). This is a known legacy issue.
        dictScope.launch {
            withContext(Dispatchers.IO) {
                contentResolver.insert(Words.CONTENT_URI, values)
            }
        }

        // In case the above does a synchronous callback of the change observer
        setRequiresReload(false)
    }

    @Synchronized
    override fun getWords(
        composer: WordComposer,
        callback: WordCallback,
        nextLettersFrequencies: IntArray?
    ) {
        super.getWords(composer, callback, nextLettersFrequencies)
    }

    @Synchronized
    override fun isValidWord(word: CharSequence): Boolean {
        return super.isValidWord(word)
    }

    private fun addWords(cursor: Cursor?) {
        if (cursor == null) {
            Log.w(TAG, "Unexpected null cursor in addWords()")
            return
        }
        clearDictionary()

        val maxWordLength = getMaxWordLength()
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast) {
                val word = cursor.getString(INDEX_WORD)
                val frequency = cursor.getInt(INDEX_FREQUENCY)
                // Safeguard against adding really long words. Stack may overflow due
                // to recursion
                if (word.length < maxWordLength) {
                    super.addWord(word, frequency)
                }
                cursor.moveToNext()
            }
        }
        cursor.close()
    }

    companion object {
        private val PROJECTION = arrayOf(
            Words._ID,
            Words.WORD,
            Words.FREQUENCY
        )

        private const val INDEX_WORD = 1
        private const val INDEX_FREQUENCY = 2

        private const val TAG = "DevKey/UserDictionary"
    }
}
