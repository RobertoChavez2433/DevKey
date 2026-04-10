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

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.content.res.XmlResourceParser
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import kotlin.math.roundToInt

/**
 * Loads an XML description of a keyboard and stores the attributes of the keys. A keyboard
 * consists of rows of keys.
 */
open class Keyboard {

    companion object {
        const val TAG = "Keyboard"

        const val DEAD_KEY_PLACEHOLDER = '\u25cc' // dotted small circle
        @JvmField val DEAD_KEY_PLACEHOLDER_STRING: String = DEAD_KEY_PLACEHOLDER.toString()

        const val EDGE_LEFT = 0x01
        const val EDGE_RIGHT = 0x02
        const val EDGE_TOP = 0x04
        const val EDGE_BOTTOM = 0x08

        const val KEYCODE_SHIFT = -1
        const val KEYCODE_MODE_CHANGE = -2
        const val KEYCODE_CANCEL = -3
        const val KEYCODE_DONE = -4
        const val KEYCODE_DELETE = -5
        const val KEYCODE_ALT_SYM = -6

        const val DEFAULT_LAYOUT_ROWS = 4
        const val DEFAULT_LAYOUT_COLUMNS = 10

        const val POPUP_ADD_SHIFT = 1
        const val POPUP_ADD_CASE = 2
        const val POPUP_ADD_SELF = 4
        const val POPUP_DISABLE = 256
        const val POPUP_AUTOREPEAT = 512

        const val SHIFT_OFF = 0
        const val SHIFT_ON = 1
        const val SHIFT_LOCKED = 2
        const val SHIFT_CAPS = 3
        const val SHIFT_CAPS_LOCKED = 4

        internal val SEARCH_DISTANCE = 1.8f

        fun getDimensionOrFraction(a: TypedArray, index: Int, base: Int, defValue: Float): Float {
            val value = a.peekValue(index) ?: return defValue
            return when (value.type) {
                TypedValue.TYPE_DIMENSION -> a.getDimensionPixelOffset(index, defValue.roundToInt()).toFloat()
                TypedValue.TYPE_FRACTION -> a.getFraction(index, base, base, defValue)
                else -> defValue
            }
        }
    }

    internal var mDefaultHorizontalGap = 0f
    internal var mHorizontalPad = 0f
    internal var mVerticalPad = 0f
    internal var mDefaultWidth = 0f
    var mDefaultHeight = 0; internal set
    internal var mDefaultVerticalGap = 0
    private var mShiftState = SHIFT_OFF
    internal var mShiftKey: Key? = null
    internal var mAltKey: Key? = null
    internal var mCtrlKey: Key? = null
    internal var mMetaKey: Key? = null
    internal var mShiftKeyIndex = -1
    internal var mTotalHeight = 0
    internal var mTotalWidth = 0
    internal var mKeys: MutableList<Key> = ArrayList()
    internal var mModifierKeys: MutableList<Key> = ArrayList()
    internal var mDisplayWidth = 0
    internal var mDisplayHeight = 0
    internal var mKeyboardHeight = 0
    internal var mKeyboardMode = 0
    internal var mUseExtension = false
    @JvmField var mLayoutRows = 0
    @JvmField var mLayoutColumns = 0
    @JvmField var mRowCount = 1
    @JvmField var mExtensionRowCount = 0
    internal var mCellWidth = 0
    internal var mCellHeight = 0
    internal var mGridNeighbors: Array<IntArray?>? = null
    internal var mProximityThreshold = 0

    constructor(context: Context, defaultHeight: Int, xmlLayoutResId: Int) :
            this(context, defaultHeight, xmlLayoutResId, 0)

    constructor(context: Context, defaultHeight: Int, xmlLayoutResId: Int, modeId: Int) :
            this(context, defaultHeight, xmlLayoutResId, modeId, 0f)

    constructor(context: Context, defaultHeight: Int, xmlLayoutResId: Int, modeId: Int, kbHeightPercent: Float) {
        val dm: DisplayMetrics = context.resources.displayMetrics
        mDisplayWidth = dm.widthPixels
        mDisplayHeight = dm.heightPixels
        Log.v(TAG, "keyboard's display metrics:$dm, mDisplayWidth=$mDisplayWidth")
        mDefaultHorizontalGap = 0f
        mDefaultWidth = mDisplayWidth / 10f
        mDefaultVerticalGap = 0
        mDefaultHeight = defaultHeight
        mKeyboardHeight = (mDisplayHeight * kbHeightPercent / 100).roundToInt()
        mKeys = ArrayList()
        mModifierKeys = ArrayList()
        mKeyboardMode = modeId
        mUseExtension = LatinIME.sKeyboardSettings.useExtension
        loadKeyboard(context, context.resources.getXml(xmlLayoutResId))
        setEdgeFlags()
        fixAltChars(LatinIME.sKeyboardSettings.inputLocale)
    }

    /** Creates a popup keyboard from a template, populated with [characters]. */
    internal constructor(
        context: Context, defaultHeight: Int, layoutTemplateResId: Int,
        characters: CharSequence, reversed: Boolean, columns: Int, horizontalPadding: Int
    ) : this(context, defaultHeight, layoutTemplateResId) {
        populateFromCharacters(characters, reversed, columns, horizontalPadding)
    }

    fun getKeys(): List<Key> = mKeys
    fun getModifierKeys(): List<Key> = mModifierKeys

    protected fun getHorizontalGap(): Int = mDefaultHorizontalGap.roundToInt()
    protected fun setHorizontalGap(gap: Int) { mDefaultHorizontalGap = gap.toFloat() }
    protected fun getVerticalGap(): Int = mDefaultVerticalGap
    protected fun setVerticalGap(gap: Int) { mDefaultVerticalGap = gap }
    protected fun getKeyHeight(): Int = mDefaultHeight
    protected fun setKeyHeight(height: Int) { mDefaultHeight = height }
    protected fun getKeyWidth(): Int = mDefaultWidth.roundToInt()
    protected fun setKeyWidth(width: Int) { mDefaultWidth = width.toFloat() }

    fun getHeight(): Int = mTotalHeight
    fun getScreenHeight(): Int = mDisplayHeight
    fun getMinWidth(): Int = mTotalWidth

    open fun setShiftState(shiftState: Int): Boolean = setShiftState(shiftState, true)

    open fun setShiftState(shiftState: Int, updateKey: Boolean): Boolean {
        if (updateKey && mShiftKey != null) mShiftKey!!.on = shiftState != SHIFT_OFF
        if (mShiftState != shiftState) { mShiftState = shiftState; return true }
        return false
    }

    fun setCtrlIndicator(active: Boolean): Key? { mCtrlKey?.on = active; return mCtrlKey }
    fun setAltIndicator(active: Boolean): Key? { mAltKey?.on = active; return mAltKey }
    fun setMetaIndicator(active: Boolean): Key? { mMetaKey?.on = active; return mMetaKey }

    fun isShiftCaps(): Boolean = mShiftState == SHIFT_CAPS || mShiftState == SHIFT_CAPS_LOCKED

    fun isShifted(applyCaps: Boolean): Boolean =
        if (applyCaps) mShiftState != SHIFT_OFF else mShiftState == SHIFT_ON || mShiftState == SHIFT_LOCKED

    fun getShiftState(): Int = mShiftState
    fun getShiftKeyIndex(): Int = mShiftKeyIndex

    open fun getNearestKeys(x: Int, y: Int): IntArray {
        if (mGridNeighbors == null) computeNearestNeighbors()
        if (x >= 0 && x < getMinWidth() && y >= 0 && y < getHeight()) {
            val index = y / mCellHeight * mLayoutColumns + x / mCellWidth
            if (index < mLayoutRows * mLayoutColumns) return mGridNeighbors!![index] ?: IntArray(0)
        }
        return IntArray(0)
    }

    fun setKeyboardWidth(newWidth: Int) = setKeyboardWidthInternal(newWidth)

    internal open fun createRowFromXml(res: Resources, parser: XmlResourceParser): Row =
        Row(res, this, parser)

    internal open fun createKeyFromXml(res: Resources, parent: Row, x: Int, y: Int, parser: XmlResourceParser): Key =
        Key(res, parent, x, y, parser)

    override fun toString(): String =
        "Keyboard(${mLayoutColumns}x${mLayoutRows} keys=${mKeys.size} rowCount=$mRowCount mode=$mKeyboardMode size=${mTotalWidth}x${mTotalHeight})"
}
