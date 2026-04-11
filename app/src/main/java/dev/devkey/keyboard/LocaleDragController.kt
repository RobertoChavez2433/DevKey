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
import dev.devkey.keyboard.keyboard.model.Key

import android.graphics.drawable.Drawable

/**
 * Manages the locale drag/swipe gesture on the space bar.
 * Tracks drag state and updates the sliding locale preview drawable.
 */
class LocaleDragController(
    private val mSpaceKey: Key,
    private val mSpacePreviewIcon: Drawable,
    private val mLanguageSwitcher: LanguageSwitcher,
    private val getSpacePreviewWidth: () -> Int,
    private val createSlidingLocaleDrawable: (background: Drawable, width: Int, height: Int) -> SlidingLocaleDrawable
) {

    companion object {
        private const val SPACEBAR_DRAG_THRESHOLD = 0.51f
    }

    var currentlyInSpace = false
        private set

    private var mSpaceDragStartX = 0
    var spaceDragLastDiff = 0
        private set

    private var mSlidingLocaleIcon: SlidingLocaleDrawable? = null

    fun updateLocaleDrag(diff: Int) {
        if (mSlidingLocaleIcon == null) {
            val width = getSpacePreviewWidth()
            val height = mSpacePreviewIcon.intrinsicHeight
            mSlidingLocaleIcon = createSlidingLocaleDrawable(mSpacePreviewIcon, width, height)
            mSlidingLocaleIcon!!.setBounds(0, 0, width, height)
            mSpaceKey.iconPreview = mSlidingLocaleIcon
        }
        mSlidingLocaleIcon!!.setDiff(diff)
        if (Math.abs(diff) == Integer.MAX_VALUE) {
            mSpaceKey.iconPreview = mSpacePreviewIcon
        } else {
            mSpaceKey.iconPreview = mSlidingLocaleIcon
        }
        mSpaceKey.iconPreview!!.invalidateSelf()
    }

    fun getLanguageChangeDirection(): Int {
        if (mLanguageSwitcher.getLocaleCount() < 2
            || Math.abs(spaceDragLastDiff) < getSpacePreviewWidth() * SPACEBAR_DRAG_THRESHOLD
        ) {
            return 0 // No change
        }
        return if (spaceDragLastDiff > 0) 1 else -1
    }

    /**
     * Called when touch enters the space key region. Returns true if now locked in space.
     */
    fun onTouchInSpace(adjustedX: Int, adjustedY: Int, isInsideSpace: Boolean): Boolean {
        if (currentlyInSpace) {
            val diff = adjustedX - mSpaceDragStartX
            if (Math.abs(diff - spaceDragLastDiff) > 0) {
                updateLocaleDrag(diff)
            }
            spaceDragLastDiff = diff
            return true
        } else {
            if (isInsideSpace) {
                currentlyInSpace = true
                mSpaceDragStartX = adjustedX
                updateLocaleDrag(0)
            }
            return isInsideSpace
        }
    }

    fun reset() {
        currentlyInSpace = false
        spaceDragLastDiff = 0
        updateLocaleDrag(Integer.MAX_VALUE)
    }
}
