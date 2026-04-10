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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import dev.devkey.keyboard.debug.DevKeyLogger

/**
 * Horizontal strip that displays word suggestions from the IME engine.
 *
 * Drawing logic lives in [SuggestionRenderer], touch/gesture handling in
 * [CandidateGestureHandler], and popup preview management in
 * [PreviewPopupManager]. All three share mutable state via [CandidateViewState].
 */
class CandidateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val state: CandidateViewState
    private val renderer: SuggestionRenderer
    private val previewManager: PreviewPopupManager
    private val gestureHandler: CandidateGestureHandler
    private val gestureDetector: GestureDetector

    init {
        val res = context.resources
        val inflate = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        val previewPopup = PopupWindow(context).apply {
            setWindowLayoutMode(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(null)
            animationStyle = android.R.style.Animation_Dialog
            isClippingEnabled = true
        }
        val previewText = inflate.inflate(R.layout.candidate_preview, null) as TextView
        previewPopup.contentView = previewText

        val paint = Paint().apply {
            isAntiAlias = true
            textSize = previewText.textSize * LatinIME.sKeyboardSettings.candidateScalePref
            strokeWidth = 0f
            textAlign = Paint.Align.CENTER
        }
        val colorNormal = ContextCompat.getColor(context, R.color.candidate_normal)
        paint.color = colorNormal

        state = CandidateViewState(
            paint = paint,
            descent = paint.descent().toInt(),
            minTouchableWidth = res.getDimension(R.dimen.candidate_min_touchable_width).toInt(),
            colorNormal = colorNormal,
            colorRecommended = ContextCompat.getColor(context, R.color.candidate_recommended),
            colorOther = ContextCompat.getColor(context, R.color.candidate_other),
            selectionHighlight = ContextCompat.getDrawable(
                context, R.drawable.list_selector_background_pressed
            ),
            divider = ContextCompat.getDrawable(context, R.drawable.keyboard_suggest_strip_divider),
            addToDictionaryHint = res.getString(R.string.hint_add_to_dictionary),
            previewPopup = previewPopup,
            previewText = previewText,
        )

        renderer = SuggestionRenderer(state, this)
        previewManager = PreviewPopupManager(state, this)
        gestureHandler = CandidateGestureHandler(state, this, previewManager)
        gestureDetector = GestureDetector(context, gestureHandler.StripGestureListener(state.minTouchableWidth))

        setWillNotDraw(false)
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false
        scrollTo(0, scrollY)
    }

    /** Connects this view to the IME service for suggestion callbacks. */
    fun setService(listener: LatinIME) {
        state.service = listener
    }

    override fun computeHorizontalScrollRange(): Int = state.totalWidth

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.drawSuggestions(canvas)
    }

    fun setSuggestions(
        suggestions: List<CharSequence>?,
        completions: Boolean,
        typedWordValid: Boolean,
        haveMinimalSuggestion: Boolean
    ) {
        clear()
        if (suggestions != null) {
            var insertCount = minOf(suggestions.size, CANDIDATE_MAX_SUGGESTIONS)
            for (suggestion in suggestions) {
                state.suggestions.add(suggestion)
                if (--insertCount == 0) break
            }
        }
        state.showingCompletions = completions
        state.typedWordValid = typedWordValid
        scrollTo(0, scrollY)
        state.targetScrollX = 0
        state.haveMinimalSuggestion = haveMinimalSuggestion
        // Compute total width without drawing
        renderer.drawSuggestions(null)
        invalidate()
        requestLayout()
        // WHY: Phase 2.3 next-word test asserts that the candidate strip actually rendered,
        //      not just that the prediction logic fired. This guards against regressions where
        //      setNextSuggestions runs but the UI strip never reflects the result.
        // IMPORTANT: PRIVACY — only the count is logged, never the suggestion strings themselves.
        DevKeyLogger.ui(
            "candidate_strip_rendered",
            mapOf("suggestion_count" to state.suggestions.size)
        )
    }

    fun isShowingAddToDictionaryHint(): Boolean = state.showingAddToDictionary

    fun showAddToDictionaryHint(word: CharSequence) {
        val suggestions = ArrayList<CharSequence>()
        suggestions.add(word)
        suggestions.add(state.addToDictionaryHint)
        setSuggestions(suggestions, false, false, false)
        state.showingAddToDictionary = true
    }

    fun dismissAddToDictionaryHint(): Boolean {
        if (!state.showingAddToDictionary) return false
        clear()
        return true
    }

    fun getSuggestions(): List<CharSequence> = state.suggestions

    fun clear() {
        state.suggestions.clear()
        state.touchX = CANDIDATE_OUT_OF_BOUNDS_X_COORD
        state.selectedString = null
        state.selectedIndex = -1
        state.showingAddToDictionary = false
        invalidate()
        state.wordWidth.fill(0)
        state.wordX.fill(0)
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(me)) {
            return true
        }
        return gestureHandler.onTouchEvent(me)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        previewManager.hidePreview()
    }
}
