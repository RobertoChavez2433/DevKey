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

package dev.devkey.keyboard.suggestion.renderer

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Typeface
import android.view.View
import dev.devkey.keyboard.legacy.CANDIDATE_OUT_OF_BOUNDS_X_COORD
import dev.devkey.keyboard.legacy.CANDIDATE_SCROLL_PIXELS
import dev.devkey.keyboard.legacy.CANDIDATE_X_GAP
import dev.devkey.keyboard.legacy.CandidateViewState

/**
 * Handles all drawing logic for the candidate suggestion strip.
 *
 * Reads and writes shared state via [CandidateViewState]. The owning
 * [CandidateView] calls [drawSuggestions] from [View.onDraw] and also with a
 * null canvas when only width/position calculations are needed (e.g. after
 * [CandidateView.setSuggestions]).
 */
internal class SuggestionRenderer(
    private val state: CandidateViewState,
    private val view: View,
) {

    /**
     * Draws suggestions onto [canvas] and computes word widths/positions.
     * Pass null for [canvas] to perform only the touch-target and width
     * calculations without any drawing.
     */
    fun drawSuggestions(canvas: Canvas?) {
        state.totalWidth = 0

        val height = view.height
        if (state.bgPadding == null) {
            state.bgPadding = Rect(0, 0, 0, 0)
            view.background?.getPadding(state.bgPadding!!)
            state.divider?.setBounds(
                0, 0,
                state.divider.intrinsicWidth,
                state.divider.intrinsicHeight
            )
        }

        val count = state.suggestions.size
        val bgPadding = state.bgPadding!!
        val paint = state.paint
        val touchX = state.touchX
        val scrollX = view.scrollX
        val scrolled = state.scrolled
        val typedWordValid = state.typedWordValid
        val y = (height + state.paint.textSize.toInt() - state.descent) / 2

        var existsAutoCompletion = false

        var x = 0
        for (i in 0 until count) {
            val suggestion = state.suggestions[i] ?: continue
            val wordLength = suggestion.length

            paint.color = state.colorNormal
            if (state.haveMinimalSuggestion &&
                ((i == 1 && !typedWordValid) || (i == 0 && typedWordValid))
            ) {
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.color = state.colorRecommended
                existsAutoCompletion = true
            } else if (i != 0 || (wordLength == 1 && count > 1)) {
                // HACK: even if i == 0, use colorOther when this suggestion's length is 1 and
                // there are multiple suggestions, such as the default punctuation list.
                paint.color = state.colorOther
            }
            var wordWidth = state.wordWidth[i]
            if (wordWidth == 0) {
                val textWidth = paint.measureText(suggestion, 0, wordLength)
                wordWidth = maxOf(state.minTouchableWidth, (textWidth + CANDIDATE_X_GAP * 2).toInt())
                state.wordWidth[i] = wordWidth
            }

            state.wordX[i] = x

            if (touchX != CANDIDATE_OUT_OF_BOUNDS_X_COORD && !scrolled &&
                touchX + scrollX >= x && touchX + scrollX < x + wordWidth
            ) {
                if (canvas != null && !state.showingAddToDictionary) {
                    canvas.translate(x.toFloat(), 0f)
                    state.selectionHighlight?.setBounds(0, bgPadding.top, wordWidth, height)
                    state.selectionHighlight?.draw(canvas)
                    canvas.translate(-x.toFloat(), 0f)
                }
                state.selectedString = suggestion
                state.selectedIndex = i
            }

            if (canvas != null) {
                canvas.drawText(
                    suggestion, 0, wordLength,
                    (x + wordWidth / 2).toFloat(), y.toFloat(),
                    paint
                )
                paint.color = state.colorOther
                canvas.translate((x + wordWidth).toFloat(), 0f)
                // Draw a divider unless it's after the hint
                if (!(state.showingAddToDictionary && i == 1)) {
                    state.divider?.draw(canvas)
                }
                canvas.translate(-(x + wordWidth).toFloat(), 0f)
            }
            paint.typeface = Typeface.DEFAULT
            x += wordWidth
        }
        if (!view.isInEditMode) {
            state.service?.onAutoCompletionStateChanged(existsAutoCompletion)
        }
        state.totalWidth = x
        if (state.targetScrollX != scrollX) {
            scrollToTarget()
        }
    }

    private fun scrollToTarget() {
        var scrollX = view.scrollX
        if (state.targetScrollX > scrollX) {
            scrollX += CANDIDATE_SCROLL_PIXELS
            if (scrollX >= state.targetScrollX) {
                scrollX = state.targetScrollX
                view.scrollTo(scrollX, view.scrollY)
                view.requestLayout()
            } else {
                view.scrollTo(scrollX, view.scrollY)
            }
        } else {
            scrollX -= CANDIDATE_SCROLL_PIXELS
            if (scrollX <= state.targetScrollX) {
                scrollX = state.targetScrollX
                view.scrollTo(scrollX, view.scrollY)
                view.requestLayout()
            } else {
                view.scrollTo(scrollX, view.scrollY)
            }
        }
        view.invalidate()
    }
}
