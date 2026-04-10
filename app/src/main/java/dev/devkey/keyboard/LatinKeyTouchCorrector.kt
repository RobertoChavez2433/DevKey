// Copyright (C) 2008 The Android Open Source Project. Licensed under the Apache License, Version 2.0.
package dev.devkey.keyboard

import dev.devkey.keyboard.ui.keyboard.KeyCodes

internal class LatinKeyTouchCorrector(
    private val spacebarVerticalCorrection: Int,
    private val proximityResolver: KeyProximityResolver,
    private val languageSwitcherProvider: () -> LanguageSwitcher?,
    private val localeDragControllerProvider: () -> LocaleDragController?
) {
    fun isInside(key: LatinKey, x: Int, y: Int): Boolean {
        var adjustedX = x; var adjustedY = y
        val code = key.codes!![0]
        if (code == Keyboard.KEYCODE_SHIFT || code == Keyboard.KEYCODE_DELETE) {
            adjustedY -= key.height / 10
            if (code == Keyboard.KEYCODE_SHIFT) adjustedX += if (key.x == 0) key.width / 6 else -key.width / 6
            if (code == Keyboard.KEYCODE_DELETE) adjustedX -= key.width / 6
        } else if (code == KeyCodes.ASCII_SPACE) {
            adjustedY += spacebarVerticalCorrection
            val switcher = languageSwitcherProvider()
            if (switcher != null && switcher.getLocaleCount() > 1) {
                val insideSpace = key.isInsideSuper(adjustedX, adjustedY)
                return localeDragControllerProvider()?.onTouchInSpace(adjustedX, adjustedY, insideSpace) ?: insideSpace
            }
        } else {
            val r = proximityResolver.resolveIsInside(key, adjustedX, adjustedY, key::isInsideSuper)
            if (r != null) return r
        }
        if (localeDragControllerProvider()?.currentlyInSpace == true) return false
        return key.isInsideSuper(adjustedX, adjustedY)
    }
}
