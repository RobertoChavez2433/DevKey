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

package dev.devkey.keyboard.dictionary.bigram
import dev.devkey.keyboard.dictionary.base.Dictionary
import dev.devkey.keyboard.dictionary.expandable.*
import dev.devkey.keyboard.dictionary.trie.TrieEditor

import dev.devkey.keyboard.dictionary.base.Dictionary.DataType
import java.util.LinkedList

/**
 * Bigram traversal helpers extracted from ExpandableDictionary.
 * Handles reverse-lookup from a previous word's node to produce bigram suggestions.
 */
internal class BigramTraversal(private val dicTypeId: Int) {

    private val sb = StringBuilder(ExpandableDictionary.MAX_WORD_LENGTH)

    /**
     * Locate the node for [previousWord] in the trie and, if it has ngrams,
     * fire bigram suggestions through [callback].
     */
    fun runReverseLookUp(
        roots: NodeArray,
        previousWord: CharSequence,
        callback: Dictionary.WordCallback
    ) {
        val prevWord = TrieEditor.searchNode(roots, previousWord, 0, previousWord.length)
        if (prevWord != null && prevWord.ngrams != null) {
            reverseLookUp(prevWord.ngrams!!, callback)
        }
    }

    /**
     * Retrieve full words from terminal nodes and deliver them through [callback].
     * Filters results below [UserBigramDictionary.SUGGEST_THRESHOLD].
     */
    private fun reverseLookUp(
        terminalNodes: LinkedList<NextWord>,
        callback: Dictionary.WordCallback
    ) {
        for (nextWord in terminalNodes) {
            var node: Node? = nextWord.word
            val freq = nextWord.frequency
            if (freq >= UserBigramDictionary.SUGGEST_THRESHOLD) {
                sb.setLength(0)
                while (node != null) {
                    sb.insert(0, node.code)
                    node = node.parent
                }
                val chars = CharArray(sb.length)
                sb.getChars(0, sb.length, chars, 0)
                callback.addWord(chars, 0, sb.length, freq, dicTypeId, DataType.BIGRAM)
            }
        }
    }

}
