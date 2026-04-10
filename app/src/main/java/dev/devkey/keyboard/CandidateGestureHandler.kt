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

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Handles touch and gesture events for the candidate suggestion strip.
 *
 * Reads and writes shared state via [CandidateViewState]. The owning
 * [CandidateView] creates a [GestureDetector] backed by [StripGestureListener]
 * and delegates both [View.onTouchEvent] (via [onTouchEvent]) and long-press
 * first-word logic (via [longPressFirstWord]) here.
 */
internal class CandidateGestureHandler(
    private val state: CandidateViewState,
    private val view: View,
    private val previewManager: PreviewPopupManager,
) {

    /**
     * GestureDetector.OnGestureListener implementation exposed so the owning
     * view can pass it directly to [GestureDetector].
     */
    inner class StripGestureListener(touchSlop: Int) : GestureDetector.SimpleOnGestureListener() {
        private val mTouchSlopSquare = touchSlop * touchSlop

        override fun onLongPress(me: MotionEvent) {
            if (state.suggestions.size > 0) {
                if (me.x + view.scrollX < state.wordWidth[0] && view.scrollX < 10) {
                    longPressFirstWord()
                }
            }
        }

        override fun onDown(e: MotionEvent): Boolean {
            state.scrolled = false
            return false
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (!state.scrolled) {
                if (e1 != null) {
                    val deltaX = (e2.x - e1.x).toInt()
                    val deltaY = (e2.y - e1.y).toInt()
                    val distance = deltaX * deltaX + deltaY * deltaY
                    if (distance < mTouchSlopSquare) {
                        return true
                    }
                }
                state.scrolled = true
            }

            val width = view.width
            state.scrolled = true
            var scrollX = view.scrollX
            scrollX += distanceX.toInt()
            if (scrollX < 0) {
                scrollX = 0
            }
            if (distanceX > 0 && scrollX + width > state.totalWidth) {
                scrollX -= distanceX.toInt()
            }
            state.targetScrollX = scrollX
            view.scrollTo(scrollX, view.scrollY)
            previewManager.hidePreview()
            view.invalidate()
            return true
        }
    }

    fun onTouchEvent(me: MotionEvent): Boolean {
        val action = me.action
        val x = me.x.toInt()
        val y = me.y.toInt()
        state.touchX = x

        when (action) {
            MotionEvent.ACTION_DOWN -> view.invalidate()
            MotionEvent.ACTION_MOVE -> {
                if (y <= 0) {
                    // Fling up — accept the currently highlighted suggestion
                    if (state.selectedString != null) {
                        state.service?.pickSuggestionManually(
                            state.selectedIndex,
                            state.selectedString!!
                        )
                        state.selectedString = null
                        state.selectedIndex = -1
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!state.scrolled) {
                    if (state.selectedString != null) {
                        if (state.showingAddToDictionary) {
                            longPressFirstWord()
                            view.clearCandidates()
                        } else {
                            state.service?.pickSuggestionManually(
                                state.selectedIndex,
                                state.selectedString!!
                            )
                        }
                    }
                }
                state.selectedString = null
                state.selectedIndex = -1
                view.requestLayout()
                previewManager.hidePreview()
                view.invalidate()
            }
        }
        return true
    }

    fun longPressFirstWord() {
        val word = state.suggestions[0]
        if (word.length < 2) return
        if (state.service?.addWordToDictionary(word.toString()) == true) {
            previewManager.showPreview(
                0,
                view.context.resources.getString(R.string.added_word, word)
            )
        }
    }
}

/**
 * Internal extension so [CandidateGestureHandler] can trigger a full clear
 * without creating a circular dependency on [CandidateView]'s public API.
 */
private fun View.clearCandidates() {
    (this as? CandidateView)?.clear()
}
