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

package dev.devkey.keyboard.legacy

import android.view.Gravity
import android.view.View

/**
 * Manages the floating preview popup that appears above a candidate word.
 *
 * Reads and writes shared state via [CandidateViewState]. The owning
 * [CandidateView] passes itself as [anchorView] so the popup can resolve its
 * screen position.
 */
internal class PreviewPopupManager(
    private val state: CandidateViewState,
    private val anchorView: View,
) {

    fun hidePreview() {
        state.touchX = CANDIDATE_OUT_OF_BOUNDS_X_COORD
        state.currentWordIndex = CANDIDATE_OUT_OF_BOUNDS_WORD_INDEX
        state.previewPopup.dismiss()
    }

    fun showPreview(wordIndex: Int, altText: String?) {
        val oldWordIndex = state.currentWordIndex
        state.currentWordIndex = wordIndex
        // Only update if the index changed or the override text changed
        if (oldWordIndex != state.currentWordIndex || altText != null) {
            if (wordIndex == CANDIDATE_OUT_OF_BOUNDS_WORD_INDEX) {
                hidePreview()
            } else {
                val word: CharSequence = altText ?: state.suggestions[wordIndex]
                state.previewText.text = word
                state.previewText.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val wordWidth = (state.paint.measureText(word, 0, word.length) + CANDIDATE_X_GAP * 2).toInt()
                val popupWidth = wordWidth + state.previewText.paddingLeft + state.previewText.paddingRight
                val popupHeight = state.previewText.measuredHeight
                state.popupPreviewX = state.wordX[wordIndex] - state.previewText.paddingLeft -
                        anchorView.scrollX + (state.wordWidth[wordIndex] - wordWidth) / 2
                state.popupPreviewY = -popupHeight
                val offsetInWindow = IntArray(2)
                anchorView.getLocationInWindow(offsetInWindow)
                if (state.previewPopup.isShowing) {
                    state.previewPopup.update(
                        state.popupPreviewX,
                        state.popupPreviewY + offsetInWindow[1],
                        popupWidth,
                        popupHeight
                    )
                } else {
                    state.previewPopup.width = popupWidth
                    state.previewPopup.height = popupHeight
                    state.previewPopup.showAtLocation(
                        anchorView, Gravity.NO_GRAVITY,
                        state.popupPreviewX,
                        state.popupPreviewY + offsetInWindow[1]
                    )
                }
                state.previewText.visibility = View.VISIBLE
            }
        }
    }
}
