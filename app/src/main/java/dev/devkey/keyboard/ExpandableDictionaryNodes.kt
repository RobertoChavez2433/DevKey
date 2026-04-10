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

package dev.devkey.keyboard

import java.util.LinkedList

/**
 * Trie node types for [ExpandableDictionary]. Extracted to keep the shell under 200 lines.
 * These were formerly inner classes of [ExpandableDictionary].
 */

class Node {
    var code: Char = '\u0000'
    var frequency: Int = 0
    var terminal: Boolean = false
    var parent: Node? = null
    var children: NodeArray? = null
    var ngrams: LinkedList<NextWord>? = null
}

class NodeArray {
    var data: Array<Node?> = arrayOfNulls(NODE_ARRAY_INCREMENT)
    var length: Int = 0

    fun add(n: Node) {
        if (length + 1 > data.size) {
            val tempData = arrayOfNulls<Node>(length + NODE_ARRAY_INCREMENT)
            if (length > 0) {
                data.copyInto(tempData, 0, 0, length)
            }
            data = tempData
        }
        data[length++] = n
    }
}

private const val NODE_ARRAY_INCREMENT = 2

class NextWord(
    val word: Node,
    var frequency: Int
) {
    var nextWord: NextWord? = null
}
