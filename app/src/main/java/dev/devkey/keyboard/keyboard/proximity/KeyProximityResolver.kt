/*
 * Copyright (C) 2008 The Android Open Source Project
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

package dev.devkey.keyboard.keyboard.proximity

import dev.devkey.keyboard.keyboard.model.Key

/**
 * Resolves which key is the intended target when a touch is near multiple keys,
 * using letter-frequency preference hints to bias toward likely next letters.
 */
class KeyProximityResolver(
    private val getNearestKeysSuper: (x: Int, y: Int) -> IntArray,
    private val getKeys: () -> List<Key>
) {

    companion object {
        private const val OVERLAP_PERCENTAGE_LOW_PROB = 0.70f
        private const val OVERLAP_PERCENTAGE_HIGH_PROB = 0.85f
    }

    private var mPrefLetterFrequencies: IntArray? = null
    private var mPrefLetter = 0
    private var mPrefLetterX = 0
    private var mPrefLetterY = 0
    private var mPrefDistance = 0

    fun setPreferredLetters(frequencies: IntArray?) {
        mPrefLetterFrequencies = frequencies
        mPrefLetter = 0
    }

    fun reset() {
        mPrefLetter = 0
        mPrefLetterX = 0
        mPrefLetterY = 0
        mPrefDistance = Integer.MAX_VALUE
    }

    /**
     * Resolves whether the given LatinKey is the intended target at (x, y), considering
     * letter-frequency preferences from the suggestion engine.
     *
     * [isInsideSuper] should delegate to `key.super.isInside(x, y)`.
     *
     * Returns null if there are no frequency preferences to apply (caller should
     * fall through to default isInsideSuper logic).
     */
    fun resolveIsInside(
        key: Key,
        adjustedX: Int,
        adjustedY: Int,
        isInsideSuper: (x: Int, y: Int) -> Boolean
    ): Boolean? {
        val pref = mPrefLetterFrequencies ?: return null
        val code = key.codes!![0]

        // New coordinate? Reset
        if (mPrefLetterX != adjustedX || mPrefLetterY != adjustedY) {
            mPrefLetter = 0
            mPrefDistance = Integer.MAX_VALUE
        }

        if (mPrefLetter > 0) {
            return mPrefLetter == code
        }

        val inside = isInsideSuper(adjustedX, adjustedY)
        val nearby = getNearestKeysSuper(adjustedX, adjustedY)
        val nearbyKeys = getKeys()

        if (inside) {
            if (inPrefList(code, pref)) {
                mPrefLetter = code
                mPrefLetterX = adjustedX
                mPrefLetterY = adjustedY
                for (i in nearby.indices) {
                    val k = nearbyKeys[nearby[i]]
                    if (k !== key && inPrefList(k.codes!![0], pref)) {
                        val dist = distanceFrom(k, adjustedX, adjustedY)
                        if (dist < (k.width * OVERLAP_PERCENTAGE_LOW_PROB).toInt() &&
                            pref[k.codes!![0]] > pref[mPrefLetter] * 3
                        ) {
                            mPrefLetter = k.codes!![0]
                            mPrefDistance = dist
                            break
                        }
                    }
                }
                return mPrefLetter == code
            }
        }

        // Get the surrounding keys and intersect with the preferred list
        for (i in nearby.indices) {
            val k = nearbyKeys[nearby[i]]
            if (inPrefList(k.codes!![0], pref)) {
                val dist = distanceFrom(k, adjustedX, adjustedY)
                if (dist < (k.width * OVERLAP_PERCENTAGE_HIGH_PROB).toInt()
                    && dist < mPrefDistance
                ) {
                    mPrefLetter = k.codes!![0]
                    mPrefLetterX = adjustedX
                    mPrefLetterY = adjustedY
                    mPrefDistance = dist
                }
            }
        }

        return if (mPrefLetter == 0) inside else mPrefLetter == code
    }

    private fun inPrefList(code: Int, pref: IntArray): Boolean {
        if (code < pref.size && code >= 0) return pref[code] > 0
        return false
    }

    private fun distanceFrom(k: Key, x: Int, y: Int): Int {
        if (y > k.y && y < k.y + k.height) {
            return Math.abs(k.x + k.width / 2 - x)
        }
        return Integer.MAX_VALUE
    }
}
