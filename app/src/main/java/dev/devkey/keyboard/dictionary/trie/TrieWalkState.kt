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
import dev.devkey.keyboard.dictionary.base.Dictionary
import dev.devkey.keyboard.dictionary.expandable.*
import dev.devkey.keyboard.suggestion.word.WordComposer

import dev.devkey.keyboard.dictionary.base.Dictionary.DataType
import dev.devkey.keyboard.dictionary.base.Dictionary.WordCallback

private const val QUOTE = '\''
private const val INCLUDE_TYPED_WORD_IF_VALID = false
private const val FULL_WORD_FREQ_MULTIPLIER = 2

/**
 * Holds per-query traversal state for a trie word search and contains the
 * recursive walk logic formerly inlined in ExpandableDictionary.getWordsRec.
 */
internal class TrieWalkState(
    val inputLength: Int,
    val maxDepth: Int,
    val nextLettersFrequencies: IntArray?,
    val codes: Array<IntArray?>,
    val dicTypeId: Int,
    val wordBuilder: CharArray
) {
    /**
     * Recursively traverse the trie for words that match the input.
     * Mirrors the logic of ExpandableDictionary.getWordsRec.
     */
    fun walkRec(
        roots: NodeArray,
        composer: WordComposer,
        word: CharArray,
        depth: Int,
        completion: Boolean,
        snr: Int,
        inputIndex: Int,
        skipPos: Int,
        callback: WordCallback
    ) {
        var isCompletion = completion
        val count = roots.length
        val codeSize = inputLength
        if (depth > maxDepth) return

        var currentChars: IntArray? = null
        if (codeSize <= inputIndex) {
            isCompletion = true
        } else {
            currentChars = codes[inputIndex]
        }

        for (i in 0 until count) {
            val node = roots.data[i]!!
            val c = node.code
            val lowerC = ExpandableDictionary.toLowerCase(c)
            val terminal = node.terminal
            val children = node.children
            val freq = node.frequency

            if (isCompletion) {
                word[depth] = c
                if (terminal) {
                    if (!callback.addWord(word, 0, depth + 1, freq * snr, dicTypeId, DataType.UNIGRAM)) {
                        return
                    }
                    if (nextLettersFrequencies != null && depth >= inputIndex && skipPos < 0
                        && nextLettersFrequencies.size > word[inputIndex].code
                    ) {
                        nextLettersFrequencies[word[inputIndex].code]++
                    }
                }
                if (children != null) {
                    walkRec(children, composer, word, depth + 1, isCompletion, snr, inputIndex, skipPos, callback)
                }
            } else if ((c == QUOTE && currentChars!![0].toChar() != QUOTE) || depth == skipPos) {
                word[depth] = c
                if (children != null) {
                    walkRec(children, composer, word, depth + 1, isCompletion, snr, inputIndex, skipPos, callback)
                }
            } else {
                val alternativesSize = if (skipPos >= 0) 1 else currentChars!!.size
                for (j in 0 until alternativesSize) {
                    val addedAttenuation = if (j > 0) 1 else 2
                    val currentChar = currentChars!![j]
                    if (currentChar == -1) break
                    if (currentChar == lowerC.code || currentChar == c.code) {
                        word[depth] = c
                        if (codeSize == inputIndex + 1) {
                            if (terminal) {
                                if (INCLUDE_TYPED_WORD_IF_VALID || !same(word, depth + 1, composer.getTypedWord()!!)) {
                                    var finalFreq = freq * snr * addedAttenuation
                                    if (skipPos < 0) finalFreq *= FULL_WORD_FREQ_MULTIPLIER
                                    callback.addWord(word, 0, depth + 1, finalFreq, dicTypeId, DataType.UNIGRAM)
                                }
                            }
                            if (children != null) {
                                walkRec(
                                    children, composer, word, depth + 1,
                                    true, snr * addedAttenuation, inputIndex + 1, skipPos, callback
                                )
                            }
                        } else if (children != null) {
                            walkRec(
                                children, composer, word, depth + 1,
                                false, snr * addedAttenuation, inputIndex + 1, skipPos, callback
                            )
                        }
                    }
                }
            }
        }
    }

    private fun same(word: CharArray, length: Int, typedWord: CharSequence): Boolean {
        if (length != typedWord.length) return false
        for (i in 0 until length) {
            if (word[i] != typedWord[i]) return false
        }
        return true
    }
}
