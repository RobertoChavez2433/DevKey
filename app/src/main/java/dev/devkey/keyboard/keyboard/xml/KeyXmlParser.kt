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

import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.util.TypedValue
import android.util.Xml
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.R
import dev.devkey.keyboard.keyboard.model.Key
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.keyboard.model.Row
import kotlin.math.roundToInt

/**
 * XML attribute parsing for [Key]. Called from the XML constructor.
 */
internal fun Key.parseKeyXml(
    res: Resources,
    parent: Row,
    x: Int,
    y: Int,
    parser: XmlResourceParser
) {
    this.x = x
    this.y = y

    var a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard)
    realWidth = Keyboard.getDimensionOrFraction(a, R.styleable.Keyboard_keyWidth, keyboard.mDisplayWidth, parent.defaultWidth)
    var realHeight = Keyboard.getDimensionOrFraction(a, R.styleable.Keyboard_keyHeight, keyboard.mDisplayHeight, parent.defaultHeight.toFloat())
    realHeight -= parent.parent.mVerticalPad
    height = realHeight.roundToInt()
    this.y += (parent.parent.mVerticalPad / 2).toInt()
    realGap = Keyboard.getDimensionOrFraction(a, R.styleable.Keyboard_horizontalGap, keyboard.mDisplayWidth, parent.defaultHorizontalGap)
    realGap += parent.parent.mHorizontalPad
    realWidth -= parent.parent.mHorizontalPad
    width = realWidth.roundToInt()
    gap = realGap.roundToInt()
    a.recycle()

    a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard_Key)
    this.realX = this.x + realGap - parent.parent.mHorizontalPad / 2
    this.x = this.realX.roundToInt()

    val codesValue = TypedValue()
    a.getValue(R.styleable.Keyboard_Key_codes, codesValue)
    codes = when {
        codesValue.type == TypedValue.TYPE_INT_DEC || codesValue.type == TypedValue.TYPE_INT_HEX ->
            intArrayOf(codesValue.data)
        codesValue.type == TypedValue.TYPE_STRING ->
            this.parseCSV(codesValue.string.toString())
        else -> null
    }

    iconPreview = a.getDrawable(R.styleable.Keyboard_Key_iconPreview)
    iconPreview?.setBounds(0, 0, iconPreview!!.intrinsicWidth, iconPreview!!.intrinsicHeight)

    popupCharacters = a.getText(R.styleable.Keyboard_Key_popupCharacters)
    popupResId = a.getResourceId(R.styleable.Keyboard_Key_popupKeyboard, 0)
    repeatable = a.getBoolean(R.styleable.Keyboard_Key_isRepeatable, false)
    modifier = a.getBoolean(R.styleable.Keyboard_Key_isModifier, false)
    sticky = a.getBoolean(R.styleable.Keyboard_Key_isSticky, false)
    isCursor = a.getBoolean(R.styleable.Keyboard_Key_isCursor, false)

    icon = a.getDrawable(R.styleable.Keyboard_Key_keyIcon)
    icon?.setBounds(0, 0, icon!!.intrinsicWidth, icon!!.intrinsicHeight)

    label = a.getText(R.styleable.Keyboard_Key_keyLabel)
    shiftLabel = a.getText(R.styleable.Keyboard_Key_shiftLabel)
    if (shiftLabel != null && shiftLabel!!.length == 0) shiftLabel = null
    capsLabel = a.getText(R.styleable.Keyboard_Key_capsLabel)
    if (capsLabel != null && capsLabel!!.length == 0) capsLabel = null
    text = a.getText(R.styleable.Keyboard_Key_keyOutputText)

    if (codes == null && !label.isNullOrEmpty()) {
        codes = getFromString(label!!)
        if (codes != null && codes!!.size == 1) {
            val locale = LatinIME.sKeyboardSettings.inputLocale
            val upperLabel = label.toString().uppercase(locale)
            if (shiftLabel == null) {
                if (upperLabel != label.toString() && upperLabel.length == 1) {
                    shiftLabel = upperLabel
                    isSimpleUppercase = true
                }
            } else {
                if (capsLabel != null) {
                    isDistinctUppercase = true
                } else if (upperLabel == shiftLabel.toString()) {
                    isSimpleUppercase = true
                } else if (upperLabel.length == 1) {
                    capsLabel = upperLabel
                    isDistinctUppercase = true
                }
            }
        }
        if ((LatinIME.sKeyboardSettings.popupKeyboardFlags and Keyboard.POPUP_DISABLE) != 0) {
            popupCharacters = null
            popupResId = 0
        }
        if ((LatinIME.sKeyboardSettings.popupKeyboardFlags and Keyboard.POPUP_AUTOREPEAT) != 0) {
            repeatable = true
        }
    }
    a.recycle()
}
