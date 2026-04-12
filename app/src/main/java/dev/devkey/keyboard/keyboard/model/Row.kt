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
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.R
import android.content.res.XmlResourceParser
import android.util.Xml
import kotlin.math.roundToInt

/**
 * Container for keys in the keyboard. All keys in a row are at the same Y-coordinate.
 * @attr ref android.R.styleable#Keyboard_keyWidth
 * @attr ref android.R.styleable#Keyboard_keyHeight
 * @attr ref android.R.styleable#Keyboard_horizontalGap
 * @attr ref android.R.styleable#Keyboard_verticalGap
 * @attr ref android.R.styleable#Keyboard_Row_keyboardMode
 */
class Row {
    @JvmField var defaultWidth = 0f
    @JvmField var defaultHeight = 0
    @JvmField var defaultHorizontalGap = 0f
    @JvmField var verticalGap = 0
    @JvmField var mode = 0
    @JvmField var extension = false

    val parent: Keyboard

    constructor(parent: Keyboard) {
        this.parent = parent
    }

    constructor(res: Resources, parent: Keyboard, parser: XmlResourceParser) {
        this.parent = parent
        var a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard)
        defaultWidth = Keyboard.getDimensionOrFraction(
            a, R.styleable.Keyboard_keyWidth, parent.mDisplayWidth, parent.mDefaultWidth
        )
        defaultHeight = Keyboard.getDimensionOrFraction(
            a, R.styleable.Keyboard_keyHeight, parent.mDisplayHeight,
            parent.mDefaultHeight.toFloat()
        ).roundToInt()
        defaultHorizontalGap = Keyboard.getDimensionOrFraction(
            a, R.styleable.Keyboard_horizontalGap, parent.mDisplayWidth,
            parent.mDefaultHorizontalGap
        )
        verticalGap = Keyboard.getDimensionOrFraction(
            a, R.styleable.Keyboard_verticalGap, parent.mDisplayHeight,
            parent.mDefaultVerticalGap.toFloat()
        ).roundToInt()
        a.recycle()
        a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard_Row)
        mode = a.getResourceId(R.styleable.Keyboard_Row_keyboardMode, 0)
        extension = a.getBoolean(R.styleable.Keyboard_Row_extension, false)

        if (parent.mLayoutRows >= 5 || extension) {
            val isTop = extension || parent.mRowCount - parent.mExtensionRowCount <= 0
            val topScale = LatinIME.sKeyboardSettings.topRowScale
            val scale = if (isTop) topScale
            else 1.0f + (1.0f - topScale) / (parent.mLayoutRows - 1)
            defaultHeight = (defaultHeight * scale).roundToInt()
        }
        a.recycle()
    }
}
