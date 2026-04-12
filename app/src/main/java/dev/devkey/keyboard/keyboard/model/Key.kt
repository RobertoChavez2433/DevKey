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

package dev.devkey.keyboard.keyboard.model

import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import dev.devkey.keyboard.keyboard.xml.parseKeyXml
import kotlin.math.roundToInt

/**
 * Class for describing the position and characteristics of a single key in the keyboard.
 */
open class Key {
    /** All the key codes (unicode or custom code) that this key could generate */
    @JvmField var codes: IntArray? = null

    @JvmField var label: CharSequence? = null
    @JvmField var shiftLabel: CharSequence? = null
    @JvmField var capsLabel: CharSequence? = null

    @JvmField var icon: Drawable? = null
    @JvmField var iconPreview: Drawable? = null
    @JvmField var width = 0
    @JvmField var realWidth = 0f
    @JvmField var height = 0
    @JvmField var gap = 0
    @JvmField var realGap = 0f
    @JvmField var sticky = false
    @JvmField var x = 0
    @JvmField var realX = 0f
    @JvmField var y = 0
    @JvmField var pressed = false
    @JvmField var on = false
    @JvmField var locked = false
    @JvmField var text: CharSequence? = null
    @JvmField var popupCharacters: CharSequence? = null
    @JvmField var popupReversed = false
    @JvmField var isCursor = false
    @JvmField var hint: String? = null
    @JvmField var altHint: String? = null

    @JvmField var edgeFlags = 0
    @JvmField var modifier = false
    val keyboard: Keyboard
    @JvmField var popupResId = 0
    @JvmField var repeatable = false
    internal var isSimpleUppercase = false
    internal var isDistinctUppercase = false

    companion object {
        private val sc = android.R.attr.state_checkable; private val sp = android.R.attr.state_pressed
        private val sa = android.R.attr.state_active; private val sk = android.R.attr.state_checked
        internal val KEY_STATE_NORMAL_ON = intArrayOf(sc, sk)
        internal val KEY_STATE_PRESSED_ON = intArrayOf(sp, sc, sk)
        internal val KEY_STATE_NORMAL_LOCK = intArrayOf(sa, sc, sk)
        internal val KEY_STATE_PRESSED_LOCK = intArrayOf(sa, sp, sc, sk)
        internal val KEY_STATE_NORMAL_OFF = intArrayOf(sc)
        internal val KEY_STATE_PRESSED_OFF = intArrayOf(sp, sc)
        internal val KEY_STATE_NORMAL = intArrayOf()
        internal val KEY_STATE_PRESSED = intArrayOf(sp)
        internal fun is7BitAscii(c: Char) = c.code in 32..126 && c !in 'A'..'Z' && c !in 'a'..'z'
    }

    /** Create an empty key with no attributes. */
    constructor(parent: Row) {
        keyboard = parent.parent
        height = parent.defaultHeight
        width = parent.defaultWidth.roundToInt()
        realWidth = parent.defaultWidth
        gap = parent.defaultHorizontalGap.roundToInt()
        realGap = parent.defaultHorizontalGap
    }

    /** Create a key with the given top-left coordinate and extract its attributes from the XML parser. */
    constructor(res: Resources, parent: Row, x: Int, y: Int, parser: XmlResourceParser) : this(parent) {
        parseKeyXml(res, parent, x, y, parser)
    }

    fun isDistinctCaps(): Boolean = isDistinctUppercase && keyboard.isShiftCaps()

    fun isShifted(): Boolean = keyboard.isShifted(isSimpleUppercase)

    fun getPrimaryCode(isShiftCaps: Boolean, isShifted: Boolean): Int {
        if (isDistinctUppercase && isShiftCaps) {
            return capsLabel!!.get(0).code
        }
        return if (isShifted && shiftLabel != null) {
            if (shiftLabel!!.get(0) == Keyboard.DEAD_KEY_PLACEHOLDER && shiftLabel!!.length >= 2) {
                shiftLabel!!.get(1).code
            } else {
                shiftLabel!!.get(0).code
            }
        } else {
            codes!![0]
        }
    }

    fun getPrimaryCode(): Int = getPrimaryCode(keyboard.isShiftCaps(), keyboard.isShifted(isSimpleUppercase))

    fun isDeadKey(): Boolean {
        if (codes == null || codes!!.isEmpty()) return false
        return Character.getType(codes!![0]) == Character.NON_SPACING_MARK.toInt()
    }

    fun getFromString(str: CharSequence): IntArray {
        return if (str.length > 1) {
            if (str[0] == Keyboard.DEAD_KEY_PLACEHOLDER && str.length >= 2) {
                intArrayOf(str[1].code)
            } else {
                text = str
                intArrayOf(0)
            }
        } else {
            intArrayOf(str[0].code)
        }
    }

    fun getCaseLabel(): String? {
        if (isDistinctUppercase && keyboard.isShiftCaps()) {
            return capsLabel.toString()
        }
        val isShifted = keyboard.isShifted(isSimpleUppercase)
        return if (isShifted && shiftLabel != null) {
            shiftLabel.toString()
        } else {
            label?.toString()
        }
    }

    fun parseCSV(value: String): IntArray {
        val tokens = value.split(",")
        return tokens.mapNotNull { it.trim().toIntOrNull().also { v ->
            if (v == null && it.trim().isNotEmpty()) android.util.Log.e(Keyboard.TAG, "Error parsing keycodes $value")
        } }.toIntArray()
    }

    fun onPressed() { pressed = true }
    fun onReleased(_inside: Boolean) { pressed = false }

    open fun isInside(x: Int, y: Int): Boolean {
        val leftEdge = edgeFlags and Keyboard.EDGE_LEFT > 0
        val rightEdge = edgeFlags and Keyboard.EDGE_RIGHT > 0
        val topEdge = edgeFlags and Keyboard.EDGE_TOP > 0
        val bottomEdge = edgeFlags and Keyboard.EDGE_BOTTOM > 0
        return (x >= this.x || leftEdge && x <= this.x + this.width) &&
                (x < this.x + this.width || rightEdge && x >= this.x) &&
                (y >= this.y || topEdge && y <= this.y + this.height) &&
                (y < this.y + this.height || bottomEdge && y >= this.y)
    }

    open fun squaredDistanceFrom(x: Int, y: Int): Int {
        val xDist = this.x + width / 2 - x
        val yDist = this.y + height / 2 - y
        return xDist * xDist + yDist * yDist
    }

    open fun getCurrentDrawableState(): IntArray {
        return when {
            locked -> if (pressed) KEY_STATE_PRESSED_LOCK else KEY_STATE_NORMAL_LOCK
            on -> if (pressed) KEY_STATE_PRESSED_ON else KEY_STATE_NORMAL_ON
            sticky -> if (pressed) KEY_STATE_PRESSED_OFF else KEY_STATE_NORMAL_OFF
            pressed -> KEY_STATE_PRESSED
            else -> KEY_STATE_NORMAL
        }
    }

    override fun toString(): String {
        val code = if (codes != null && codes!!.isNotEmpty()) codes!![0] else 0
        val edges = (
            (if (edgeFlags and Keyboard.EDGE_LEFT != 0) "L" else "-") +
            (if (edgeFlags and Keyboard.EDGE_RIGHT != 0) "R" else "-") +
            (if (edgeFlags and Keyboard.EDGE_TOP != 0) "T" else "-") +
            (if (edgeFlags and Keyboard.EDGE_BOTTOM != 0) "B" else "-"))
        return "KeyDebugFIXME(label=$label" +
            (if (shiftLabel != null) " shift=$shiftLabel" else "") +
            (if (capsLabel != null) " caps=$capsLabel" else "") +
            (if (text != null) " text=$text" else "") +
            " code=$code" +
            (if (code <= 0 || Character.isWhitespace(code)) "" else ":'${code.toChar()}'") +
            " x=$x..${x + width} y=$y..${y + height}" +
            " edgeFlags=$edges" +
            (if (popupCharacters != null) " pop=$popupCharacters" else "") +
            " res=$popupResId" +
            ")"
    }
}
