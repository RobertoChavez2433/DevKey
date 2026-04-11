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

package dev.devkey.keyboard.keyboard.xml

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.util.Log
import android.util.Xml
import dev.devkey.keyboard.R
import dev.devkey.keyboard.keyboard.switcher.setEdgeFlags
import dev.devkey.keyboard.keyboard.model.Key
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.keyboard.model.Row
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import kotlin.math.roundToInt

private const val TAG_KEYBOARD = "Keyboard"
private const val TAG_ROW = "Row"
private const val TAG_KEY = "Key"

/** Populates a popup keyboard (created from template) with individual character keys. */
internal fun Keyboard.populateFromCharacters(
    characters: CharSequence, reversed: Boolean, columns: Int, horizontalPadding: Int
) {
    var x = 0; var y = 0; var column = 0
    mTotalWidth = 0

    val row = Row(this).also {
        it.defaultHeight = mDefaultHeight
        it.defaultWidth = mDefaultWidth
        it.defaultHorizontalGap = mDefaultHorizontalGap
        it.verticalGap = mDefaultVerticalGap
    }

    val maxColumns = if (columns == -1) Integer.MAX_VALUE else columns
    mLayoutRows = 1

    val start = if (reversed) characters.length - 1 else 0
    val end = if (reversed) -1 else characters.length
    val step = if (reversed) -1 else 1

    var i = start
    while (i != end) {
        val c = characters[i]
        if (column >= maxColumns || x + mDefaultWidth + horizontalPadding > mDisplayWidth) {
            x = 0
            y += mDefaultVerticalGap + mDefaultHeight.toInt()
            column = 0
            ++mLayoutRows
        }
        val key = Key(row)
        key.x = x
        key.realX = x.toFloat()
        key.y = y
        key.label = c.toString()
        key.codes = key.getFromString(key.label!!)
        column++
        x += key.width + key.gap
        mKeys.add(key)
        if (x > mTotalWidth) mTotalWidth = x
        i += step
    }
    mTotalHeight = y + mDefaultHeight
    mLayoutColumns = if (columns == -1) column else maxColumns
    setEdgeFlags()
}

/**
 * Loads keyboard XML resource and populates the key/row lists on [Keyboard].
 */
internal fun Keyboard.loadKeyboard(context: Context, parser: XmlResourceParser) {
    var inKey = false
    var inRow = false
    var x = 0f
    var y = 0
    var key: Key? = null
    var currentRow: Row? = null
    val res = context.resources
    var skipRow = false
    mRowCount = 0

    try {
        var event: Int
        var prevKey: Key? = null
        while (parser.next().also { event = it } != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.START_TAG) {
                val tag = parser.name
                when {
                    TAG_ROW == tag -> {
                        inRow = true
                        x = 0f
                        currentRow = createRowFromXml(res, parser)
                        skipRow = currentRow.mode != 0 && currentRow.mode != mKeyboardMode
                        if (currentRow.extension) {
                            if (mUseExtension) {
                                ++mExtensionRowCount
                            } else {
                                skipRow = true
                            }
                        }
                        if (skipRow) {
                            skipToEndOfRow(parser)
                            inRow = false
                        }
                    }
                    TAG_KEY == tag -> {
                        inKey = true
                        key = createKeyFromXml(res, currentRow!!, x.roundToInt(), y, parser)
                        key.realX = x
                        if (key.codes == null) {
                            if (prevKey != null) {
                                prevKey.width += key.width
                            }
                        } else {
                            mKeys.add(key)
                            prevKey = key
                            when {
                                key.codes!![0] == Keyboard.KEYCODE_SHIFT -> {
                                    if (mShiftKeyIndex == -1) {
                                        mShiftKey = key
                                        mShiftKeyIndex = mKeys.size - 1
                                    }
                                    mModifierKeys.add(key)
                                }
                                key.codes!![0] == Keyboard.KEYCODE_ALT_SYM -> mModifierKeys.add(key)
                                key.codes!![0] == KeyCodes.CTRL_LEFT -> mCtrlKey = key
                                key.codes!![0] == KeyCodes.ALT_LEFT -> mAltKey = key
                                key.codes!![0] == KeyCodes.KEYCODE_META_LEFT -> mMetaKey = key
                            }
                        }
                    }
                    TAG_KEYBOARD == tag -> parseKeyboardAttributes(res, parser)
                }
            } else if (event == XmlResourceParser.END_TAG) {
                when {
                    inKey -> {
                        inKey = false
                        x += key!!.realGap + key.realWidth
                        if (x > mTotalWidth) {
                            mTotalWidth = x.roundToInt()
                        }
                    }
                    inRow -> {
                        inRow = false
                        y += currentRow!!.verticalGap
                        y += currentRow.defaultHeight
                        mRowCount++
                    }
                    else -> { /* no-op */ }
                }
            }
        }
    } catch (e: Exception) {
        Log.e(Keyboard.TAG, "Parse error:$e")
        e.printStackTrace()
    }
    mTotalHeight = y - mDefaultVerticalGap
}

internal fun Keyboard.skipToEndOfRow(parser: XmlResourceParser) {
    var event: Int
    while (parser.next().also { event = it } != XmlResourceParser.END_DOCUMENT) {
        if (event == XmlResourceParser.END_TAG && parser.name == TAG_ROW) {
            break
        }
    }
}

internal fun Keyboard.parseKeyboardAttributes(res: Resources, parser: XmlResourceParser) {
    val a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard)

    mDefaultWidth = Keyboard.getDimensionOrFraction(a, R.styleable.Keyboard_keyWidth, mDisplayWidth, (mDisplayWidth / 10).toFloat())
    mDefaultHeight = Keyboard.getDimensionOrFraction(a, R.styleable.Keyboard_keyHeight, mDisplayHeight, mDefaultHeight.toFloat()).roundToInt()
    mDefaultHorizontalGap = Keyboard.getDimensionOrFraction(a, R.styleable.Keyboard_horizontalGap, mDisplayWidth, 0f)
    mDefaultVerticalGap = Keyboard.getDimensionOrFraction(a, R.styleable.Keyboard_verticalGap, mDisplayHeight, 0f).roundToInt()
    mHorizontalPad = Keyboard.getDimensionOrFraction(a, R.styleable.Keyboard_horizontalPad, mDisplayWidth, res.getDimension(R.dimen.key_horizontal_pad))
    mVerticalPad = Keyboard.getDimensionOrFraction(a, R.styleable.Keyboard_verticalPad, mDisplayHeight, res.getDimension(R.dimen.key_vertical_pad))
    mLayoutRows = a.getInteger(R.styleable.Keyboard_layoutRows, Keyboard.DEFAULT_LAYOUT_ROWS)
    mLayoutColumns = a.getInteger(R.styleable.Keyboard_layoutColumns, Keyboard.DEFAULT_LAYOUT_COLUMNS)
    if (mDefaultHeight == 0 && mKeyboardHeight > 0 && mLayoutRows > 0) {
        mDefaultHeight = mKeyboardHeight / mLayoutRows
    }
    mProximityThreshold = (mDefaultWidth * Keyboard.SEARCH_DISTANCE).toInt()
    mProximityThreshold *= mProximityThreshold
    a.recycle()
}
