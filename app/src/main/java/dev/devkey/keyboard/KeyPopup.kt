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

/**
 * Popup keyboard content building and hint label logic for [Key].
 */

internal fun Key.getPopupKeyboardContent(isShiftCaps: Boolean, isShifted: Boolean, addExtra: Boolean): String {
    var mainChar = getPrimaryCode(false, false)
    var shiftChar = getPrimaryCode(false, true)
    var capsChar = getPrimaryCode(true, true)

    if (shiftChar == mainChar) shiftChar = 0
    if (capsChar == shiftChar || capsChar == mainChar) capsChar = 0

    val popupLen = popupCharacters?.length ?: 0
    val popup = StringBuilder(popupLen)
    for (i in 0 until popupLen) {
        var c = popupCharacters!![i]
        if (isShifted || isShiftCaps) {
            val upper = c.toString().uppercase(LatinIME.sKeyboardSettings.inputLocale)
            if (upper.length == 1) c = upper[0]
        }
        if (c.code == mainChar || c.code == shiftChar || c.code == capsChar) continue
        popup.append(c)
    }

    if (addExtra) {
        val extra = StringBuilder(3 + popup.length)
        val flags = LatinIME.sKeyboardSettings.popupKeyboardFlags
        if ((flags and Keyboard.POPUP_ADD_SELF) != 0) {
            if (isDistinctUppercase && isShiftCaps) {
                if (capsChar > 0) { extra.append(capsChar.toChar()); capsChar = 0 }
            } else if (isShifted) {
                if (shiftChar > 0) { extra.append(shiftChar.toChar()); shiftChar = 0 }
            } else {
                if (mainChar > 0) { extra.append(mainChar.toChar()); mainChar = 0 }
            }
        }
        if ((flags and Keyboard.POPUP_ADD_CASE) != 0) {
            if (isDistinctUppercase && isShiftCaps) {
                if (mainChar > 0) { extra.append(mainChar.toChar()); mainChar = 0 }
                if (shiftChar > 0) { extra.append(shiftChar.toChar()); shiftChar = 0 }
            } else if (isShifted) {
                if (mainChar > 0) { extra.append(mainChar.toChar()); mainChar = 0 }
                if (capsChar > 0) { extra.append(capsChar.toChar()); capsChar = 0 }
            } else {
                if (shiftChar > 0) { extra.append(shiftChar.toChar()); shiftChar = 0 }
                if (capsChar > 0) { extra.append(capsChar.toChar()); capsChar = 0 }
            }
        }
        if (!isSimpleUppercase && (flags and Keyboard.POPUP_ADD_SHIFT) != 0) {
            if (isShifted) {
                if (mainChar > 0) { extra.append(mainChar.toChar()); mainChar = 0 }
            } else {
                if (shiftChar > 0) { extra.append(shiftChar.toChar()); shiftChar = 0 }
            }
        }
        extra.append(popup)
        return extra.toString()
    }

    return popup.toString()
}

fun Key.getPopupKeyboard(context: Context, padding: Int): Keyboard? {
    if (popupCharacters == null) {
        if (popupResId != 0) {
            return Keyboard(context, keyboard.mDefaultHeight, popupResId)
        } else {
            if (modifier) return null
        }
    }

    if ((LatinIME.sKeyboardSettings.popupKeyboardFlags and Keyboard.POPUP_DISABLE) != 0) return null

    val popup = getPopupKeyboardContent(keyboard.isShiftCaps(), keyboard.isShifted(isSimpleUppercase), true)
    if (popup.isNotEmpty()) {
        val resId = if (popupResId == 0) R.xml.kbd_popup_template else popupResId
        return Keyboard(context, keyboard.mDefaultHeight, resId, popup, popupReversed, -1, padding)
    }
    return null
}

fun Key.getHintLabel(wantAscii: Boolean, wantAll: Boolean): String {
    if (hint == null) {
        hint = ""
        if (shiftLabel != null && !isSimpleUppercase) {
            val c = shiftLabel!![0]
            if (wantAll || wantAscii && Key.is7BitAscii(c)) {
                hint = c.toString()
            }
        }
    }
    return hint!!
}

fun Key.getAltHintLabel(wantAscii: Boolean, wantAll: Boolean): String {
    if (altHint == null) {
        altHint = ""
        val popup = getPopupKeyboardContent(false, false, false)
        if (popup.isNotEmpty()) {
            val c = popup[0]
            if (wantAll || wantAscii && Key.is7BitAscii(c)) {
                altHint = c.toString()
            }
        }
    }
    return altHint!!
}
