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

package dev.devkey.keyboard.dictionary.trie
import dev.devkey.keyboard.dictionary.expandable.*

import java.util.LinkedList

/**
 * Trie mutation helpers extracted from [ExpandableDictionary]:
 * word insertion ([addWordRec]) and bigram bookkeeping ([addOrSetBigram] / [searchWord]).
 */
internal object TrieEditor {

    fun addWordRec(
        children: NodeArray,
        word: String,
        depth: Int,
        frequency: Int,
        parentNode: Node?
    ) {
        val wordLength = word.length
        val c = word[depth]
        val childrenLength = children.length
        var childNode: Node? = null
        var found = false
        for (i in 0 until childrenLength) {
            childNode = children.data[i]
            if (childNode!!.code == c) {
                found = true
                break
            }
        }
        if (!found) {
            childNode = Node()
            childNode.code = c
            childNode.parent = parentNode
            children.add(childNode)
        }
        if (wordLength == depth + 1) {
            childNode!!.terminal = true
            childNode.frequency = maxOf(frequency, childNode.frequency)
            if (childNode.frequency > 255) childNode.frequency = 255
            return
        }
        if (childNode!!.children == null) {
            childNode.children = NodeArray()
        }
        addWordRec(childNode.children!!, word, depth + 1, frequency, childNode)
    }

    /**
     * Adds bigrams to the in-memory trie structure.
     * @param addFrequency if true, adds to current frequency; otherwise replaces it
     * @return the final frequency
     */
    fun addOrSetBigram(
        roots: NodeArray,
        word1: String,
        word2: String,
        frequency: Int,
        addFrequency: Boolean
    ): Int {
        val firstWord = searchWord(roots, word1, 0, null)
        val secondWord = searchWord(roots, word2, 0, null)
        var bigram = firstWord.ngrams
        if (bigram == null || bigram.size == 0) {
            firstWord.ngrams = LinkedList()
            bigram = firstWord.ngrams
        } else {
            for (nw in bigram) {
                if (nw.word === secondWord) {
                    if (addFrequency) nw.frequency += frequency else nw.frequency = frequency
                    return nw.frequency
                }
            }
        }
        val nw = NextWord(secondWord, frequency)
        firstWord.ngrams!!.add(nw)
        return frequency
    }

    /**
     * Search for the terminal node of [word] (exact match, read-only).
     * @return the terminal node if the word exists, null otherwise.
     */
    fun searchNode(
        children: NodeArray,
        word: CharSequence,
        offset: Int,
        length: Int
    ): Node? {
        val count = children.length
        val currentChar = word[offset]
        for (j in 0 until count) {
            val node = children.data[j]!!
            if (node.code == currentChar) {
                if (offset == length - 1) {
                    if (node.terminal) return node
                } else {
                    if (node.children != null) {
                        val returnNode = searchNode(node.children!!, word, offset + 1, length)
                        if (returnNode != null) return returnNode
                    }
                }
            }
        }
        return null
    }

    /**
     * Searches for the word and adds it if it does not exist.
     * @return the terminal node of the word.
     */
    fun searchWord(
        children: NodeArray,
        word: String,
        depth: Int,
        parentNode: Node?
    ): Node {
        val wordLength = word.length
        val c = word[depth]
        val childrenLength = children.length
        var childNode: Node? = null
        var found = false
        for (i in 0 until childrenLength) {
            childNode = children.data[i]
            if (childNode!!.code == c) {
                found = true
                break
            }
        }
        if (!found) {
            childNode = Node()
            childNode.code = c
            childNode.parent = parentNode
            children.add(childNode)
        }
        if (wordLength == depth + 1) {
            childNode!!.terminal = true
            return childNode
        }
        if (childNode!!.children == null) {
            childNode.children = NodeArray()
        }
        return searchWord(childNode.children!!, word, depth + 1, childNode)
    }
}
