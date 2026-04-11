/*
 * Copyright (C) 2024 DevKey contributors
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

package dev.devkey.keyboard.suggestion.engine
import dev.devkey.keyboard.*
import dev.devkey.keyboard.suggestion.word.WordComposer

/**
 * Interface to break circular dependency between dictionary classes and LatinIME.
 * LatinIME implements this interface and passes it to AutoDictionary/UserBigramDictionary
 * instead of a direct LatinIME reference.
 */
interface WordPromotionDelegate {
    fun getCurrentWord(): WordComposer
    fun promoteToUserDictionary(word: String, frequency: Int)
}
