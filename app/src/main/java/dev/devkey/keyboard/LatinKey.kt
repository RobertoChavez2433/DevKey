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

package dev.devkey.keyboard

import android.content.res.Resources
import android.content.res.XmlResourceParser

/**
 * Custom Key subclass for LatinKeyboard. Delegates isInside to the owning keyboard for
 * space-bar drag lock and proximity correction. verticalGap is set after construction.
 */
class LatinKey(
    res: Resources, parent: Row, x: Int, y: Int, parser: XmlResourceParser
) : Key(res, parent, x, y, parser) {

    /** Set by LatinKeyboard after construction; used for squaredDistanceFrom. */
    var verticalGap: Int = 0
    /** Set by LatinKeyboard after construction; overrides isInside routing. */
    var isInsideCallback: ((LatinKey, Int, Int) -> Boolean)? = null

    private val KEY_STATE_FUNCTIONAL_NORMAL = intArrayOf(android.R.attr.state_single)
    private val KEY_STATE_FUNCTIONAL_PRESSED = intArrayOf(android.R.attr.state_single, android.R.attr.state_pressed)

    private fun isFunctionalKey(): Boolean = !sticky && modifier

    override fun isInside(x: Int, y: Int): Boolean =
        isInsideCallback?.invoke(this, x, y) ?: super.isInside(x, y)

    fun isInsideSuper(x: Int, y: Int): Boolean = super.isInside(x, y)

    override fun getCurrentDrawableState(): IntArray =
        if (isFunctionalKey()) { if (pressed) KEY_STATE_FUNCTIONAL_PRESSED else KEY_STATE_FUNCTIONAL_NORMAL }
        else super.getCurrentDrawableState()

    override fun squaredDistanceFrom(x: Int, y: Int): Int {
        val xd = this.x + width / 2 - x
        val yd = this.y + (height + verticalGap) / 2 - y
        return xd * xd + yd * yd
    }
}
