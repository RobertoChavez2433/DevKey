/*
 * Copyright (C) 2008-2009 Google Inc.
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

import android.util.Log
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import java.util.HashSet
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Geometry, nearest-key grid, edge flags, and width rescaling for [Keyboard].
 */

internal fun Keyboard.computeNearestNeighbors() {
    mCellWidth = (getMinWidth() + mLayoutColumns - 1) / mLayoutColumns
    mCellHeight = (getHeight() + mLayoutRows - 1) / mLayoutRows
    mGridNeighbors = arrayOfNulls(mLayoutColumns * mLayoutRows)
    val indices = IntArray(mKeys.size)
    val gridWidth = mLayoutColumns * mCellWidth
    val gridHeight = mLayoutRows * mCellHeight
    var x = 0
    while (x < gridWidth) {
        var y = 0
        while (y < gridHeight) {
            var count = 0
            for (i in mKeys.indices) {
                val key = mKeys[i]
                val isSpace = key.codes != null && key.codes!!.isNotEmpty() &&
                        key.codes!![0] == KeyCodes.ASCII_SPACE
                if (key.squaredDistanceFrom(x, y) < mProximityThreshold ||
                        key.squaredDistanceFrom(x + mCellWidth - 1, y) < mProximityThreshold ||
                        key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1) < mProximityThreshold ||
                        key.squaredDistanceFrom(x, y + mCellHeight - 1) < mProximityThreshold ||
                        isSpace && !(x + mCellWidth - 1 < key.x || x > key.x + key.width ||
                                y + mCellHeight - 1 < key.y || y > key.y + key.height)) {
                    indices[count++] = i
                }
            }
            val cell = IntArray(count)
            indices.copyInto(cell, 0, 0, count)
            mGridNeighbors!![y / mCellHeight * mLayoutColumns + x / mCellWidth] = cell
            y += mCellHeight
        }
        x += mCellWidth
    }
}

internal fun Keyboard.setEdgeFlags() {
    if (mRowCount == 0) mRowCount = 1
    var row = 0
    var prevKey: Key? = null
    var rowFlags = 0
    for (key in mKeys) {
        var keyFlags = 0
        if (prevKey == null || key.x <= prevKey.x) {
            if (prevKey != null) {
                prevKey.edgeFlags = prevKey.edgeFlags or Keyboard.EDGE_RIGHT
            }
            rowFlags = 0
            if (row == 0) rowFlags = rowFlags or Keyboard.EDGE_TOP
            if (row == mRowCount - 1) rowFlags = rowFlags or Keyboard.EDGE_BOTTOM
            ++row
            keyFlags = keyFlags or Keyboard.EDGE_LEFT
        }
        key.edgeFlags = rowFlags or keyFlags
        prevKey = key
    }
    if (prevKey != null) prevKey.edgeFlags = prevKey.edgeFlags or Keyboard.EDGE_RIGHT
}

internal fun Keyboard.fixAltChars(locale: Locale?) {
    val effectiveLocale = locale ?: Locale.getDefault()
    val mainKeys = HashSet<Char>()
    for (key in mKeys) {
        if (key.label != null && !key.modifier && key.label!!.length == 1) {
            mainKeys.add(key.label!![0])
        }
    }

    for (key in mKeys) {
        if (key.popupCharacters == null) continue
        var popupLen = key.popupCharacters!!.length
        if (popupLen == 0) continue
        if (key.x >= mTotalWidth / 2) {
            key.popupReversed = true
        }

        val needUpcase = key.label != null && key.label!!.length == 1 && key.label!![0].isUpperCase()
        if (needUpcase) {
            key.popupCharacters = key.popupCharacters.toString().uppercase()
            popupLen = key.popupCharacters!!.length
        }

        val newPopup = StringBuilder(popupLen)
        for (i in 0 until popupLen) {
            val c = key.popupCharacters!![i]
            if (Character.isDigit(c.code) && mainKeys.contains(c)) continue
            if (key.edgeFlags and Keyboard.EDGE_TOP == 0 && Character.isDigit(c.code)) continue
            newPopup.append(c)
        }
        key.popupCharacters = newPopup.toString()
    }
}

internal fun Keyboard.setKeyboardWidthInternal(newWidth: Int) {
    Log.i(Keyboard.TAG, "setKeyboardWidth newWidth=$newWidth, mTotalWidth=$mTotalWidth")
    if (newWidth <= 0) return
    if (mTotalWidth <= newWidth) return
    val scale = newWidth.toFloat() / mDisplayWidth
    Log.i("PCKeyboard", "Rescaling keyboard: $mTotalWidth => $newWidth")
    for (key in mKeys) {
        key.x = (key.realX * scale).roundToInt()
    }
    mTotalWidth = newWidth
}
