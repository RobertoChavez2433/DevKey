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
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat

class CandidateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var mService: LatinIME? = null
    private val mSuggestions = ArrayList<CharSequence>()
    private var mShowingCompletions = false
    private var mSelectedString: CharSequence? = null
    private var mSelectedIndex = -1
    private var mTouchX = OUT_OF_BOUNDS_X_COORD
    private val mSelectionHighlight: Drawable?
    private var mTypedWordValid = false

    private var mHaveMinimalSuggestion = false

    private var mBgPadding: Rect? = null

    private val mPreviewText: TextView
    private val mPreviewPopup: PopupWindow
    private var mCurrentWordIndex = OUT_OF_BOUNDS_WORD_INDEX
    private val mDivider: Drawable?

    private val mWordWidth = IntArray(MAX_SUGGESTIONS)
    private val mWordX = IntArray(MAX_SUGGESTIONS)
    private var mPopupPreviewX = 0
    private var mPopupPreviewY = 0

    private val mColorNormal: Int
    private val mColorRecommended: Int
    private val mColorOther: Int
    private val mPaint: Paint
    private val mDescent: Int
    private var mScrolled = false
    private var mShowingAddToDictionary = false
    private val mAddToDictionaryHint: CharSequence

    private var mTargetScrollX = 0

    private val mMinTouchableWidth: Int

    private var mTotalWidth = 0

    private val mGestureDetector: GestureDetector

    init {
        mSelectionHighlight = ContextCompat.getDrawable(
            context, R.drawable.list_selector_background_pressed
        )

        val inflate = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val res = context.resources
        mPreviewPopup = PopupWindow(context)
        mPreviewText = inflate.inflate(R.layout.candidate_preview, null) as TextView
        mPreviewPopup.setWindowLayoutMode(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        mPreviewPopup.contentView = mPreviewText
        mPreviewPopup.setBackgroundDrawable(null)
        mPreviewPopup.animationStyle = android.R.style.Animation_Dialog
        // Enable clipping for Android P+
        mPreviewPopup.isClippingEnabled = true
        mColorNormal = ContextCompat.getColor(context, R.color.candidate_normal)
        mColorRecommended = ContextCompat.getColor(context, R.color.candidate_recommended)
        mColorOther = ContextCompat.getColor(context, R.color.candidate_other)
        mDivider = ContextCompat.getDrawable(context, R.drawable.keyboard_suggest_strip_divider)
        mAddToDictionaryHint = res.getString(R.string.hint_add_to_dictionary)

        mPaint = Paint()
        mPaint.color = mColorNormal
        mPaint.isAntiAlias = true
        mPaint.textSize = mPreviewText.textSize * LatinIME.sKeyboardSettings.candidateScalePref
        mPaint.strokeWidth = 0f
        mPaint.textAlign = Paint.Align.CENTER
        mDescent = mPaint.descent().toInt()
        mMinTouchableWidth = res.getDimension(R.dimen.candidate_min_touchable_width).toInt()

        mGestureDetector = GestureDetector(context, CandidateStripGestureListener(mMinTouchableWidth))
        setWillNotDraw(false)
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false
        scrollTo(0, scrollY)
    }

    private inner class CandidateStripGestureListener(
        touchSlop: Int
    ) : GestureDetector.SimpleOnGestureListener() {
        private val mTouchSlopSquare = touchSlop * touchSlop

        override fun onLongPress(me: MotionEvent) {
            if (mSuggestions.size > 0) {
                if (me.x + scrollX < mWordWidth[0] && scrollX < 10) {
                    longPressFirstWord()
                }
            }
        }

        override fun onDown(e: MotionEvent): Boolean {
            mScrolled = false
            return false
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (!mScrolled) {
                if (e1 != null) {
                    val deltaX = (e2.x - e1.x).toInt()
                    val deltaY = (e2.y - e1.y).toInt()
                    val distance = deltaX * deltaX + deltaY * deltaY
                    if (distance < mTouchSlopSquare) {
                        return true
                    }
                }
                mScrolled = true
            }

            val width = getWidth()
            mScrolled = true
            var scrollX = getScrollX()
            scrollX += distanceX.toInt()
            if (scrollX < 0) {
                scrollX = 0
            }
            if (distanceX > 0 && scrollX + width > mTotalWidth) {
                scrollX -= distanceX.toInt()
            }
            mTargetScrollX = scrollX
            scrollTo(scrollX, scrollY)
            hidePreview()
            invalidate()
            return true
        }
    }

    /**
     * A connection back to the service to communicate with the text field
     */
    fun setService(listener: LatinIME) {
        mService = listener
    }

    override fun computeHorizontalScrollRange(): Int = mTotalWidth

    /**
     * If the canvas is null, then only touch calculations are performed to pick the target
     * candidate.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSuggestions(canvas)
    }

    /**
     * Draws suggestions and computes word widths/positions.
     * If canvas is null, only touch calculations and width computation are performed.
     */
    private fun drawSuggestions(canvas: Canvas?) {
        mTotalWidth = 0

        val height = getHeight()
        if (mBgPadding == null) {
            mBgPadding = Rect(0, 0, 0, 0)
            background?.getPadding(mBgPadding!!)
            mDivider?.setBounds(0, 0, mDivider.intrinsicWidth, mDivider.intrinsicHeight)
        }

        val count = mSuggestions.size
        val bgPadding = mBgPadding!!
        val paint = mPaint
        val touchX = mTouchX
        val scrollX = getScrollX()
        val scrolled = mScrolled
        val typedWordValid = mTypedWordValid
        val y = (height + mPaint.textSize.toInt() - mDescent) / 2

        var existsAutoCompletion = false

        var x = 0
        for (i in 0 until count) {
            val suggestion = mSuggestions[i] ?: continue
            val wordLength = suggestion.length

            paint.color = mColorNormal
            if (mHaveMinimalSuggestion &&
                ((i == 1 && !typedWordValid) || (i == 0 && typedWordValid))
            ) {
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.color = mColorRecommended
                existsAutoCompletion = true
            } else if (i != 0 || (wordLength == 1 && count > 1)) {
                // HACK: even if i == 0, we use mColorOther when this suggestion's length is 1 and
                // there are multiple suggestions, such as the default punctuation list.
                paint.color = mColorOther
            }
            var wordWidth = mWordWidth[i]
            if (wordWidth == 0) {
                val textWidth = paint.measureText(suggestion, 0, wordLength)
                wordWidth = maxOf(mMinTouchableWidth, (textWidth + X_GAP * 2).toInt())
                mWordWidth[i] = wordWidth
            }

            mWordX[i] = x

            if (touchX != OUT_OF_BOUNDS_X_COORD && !scrolled &&
                touchX + scrollX >= x && touchX + scrollX < x + wordWidth
            ) {
                if (canvas != null && !mShowingAddToDictionary) {
                    canvas.translate(x.toFloat(), 0f)
                    mSelectionHighlight?.setBounds(0, bgPadding.top, wordWidth, height)
                    mSelectionHighlight?.draw(canvas)
                    canvas.translate(-x.toFloat(), 0f)
                }
                mSelectedString = suggestion
                mSelectedIndex = i
            }

            if (canvas != null) {
                canvas.drawText(suggestion, 0, wordLength, (x + wordWidth / 2).toFloat(), y.toFloat(), paint)
                paint.color = mColorOther
                canvas.translate((x + wordWidth).toFloat(), 0f)
                // Draw a divider unless it's after the hint
                if (!(mShowingAddToDictionary && i == 1)) {
                    mDivider?.draw(canvas)
                }
                canvas.translate(-(x + wordWidth).toFloat(), 0f)
            }
            paint.typeface = Typeface.DEFAULT
            x += wordWidth
        }
        if (!isInEditMode) {
            mService?.onAutoCompletionStateChanged(existsAutoCompletion)
        }
        mTotalWidth = x
        if (mTargetScrollX != scrollX) {
            scrollToTarget()
        }
    }

    private fun scrollToTarget() {
        var scrollX = getScrollX()
        if (mTargetScrollX > scrollX) {
            scrollX += SCROLL_PIXELS
            if (scrollX >= mTargetScrollX) {
                scrollX = mTargetScrollX
                scrollTo(scrollX, scrollY)
                requestLayout()
            } else {
                scrollTo(scrollX, scrollY)
            }
        } else {
            scrollX -= SCROLL_PIXELS
            if (scrollX <= mTargetScrollX) {
                scrollX = mTargetScrollX
                scrollTo(scrollX, scrollY)
                requestLayout()
            } else {
                scrollTo(scrollX, scrollY)
            }
        }
        invalidate()
    }

    fun setSuggestions(
        suggestions: List<CharSequence>?,
        completions: Boolean,
        typedWordValid: Boolean,
        haveMinimalSuggestion: Boolean
    ) {
        clear()
        if (suggestions != null) {
            var insertCount = minOf(suggestions.size, MAX_SUGGESTIONS)
            for (suggestion in suggestions) {
                mSuggestions.add(suggestion)
                if (--insertCount == 0) break
            }
        }
        mShowingCompletions = completions
        mTypedWordValid = typedWordValid
        scrollTo(0, scrollY)
        mTargetScrollX = 0
        mHaveMinimalSuggestion = haveMinimalSuggestion
        // Compute the total width
        drawSuggestions(null)
        invalidate()
        requestLayout()
    }

    fun isShowingAddToDictionaryHint(): Boolean = mShowingAddToDictionary

    fun showAddToDictionaryHint(word: CharSequence) {
        val suggestions = ArrayList<CharSequence>()
        suggestions.add(word)
        suggestions.add(mAddToDictionaryHint)
        setSuggestions(suggestions, false, false, false)
        mShowingAddToDictionary = true
    }

    fun dismissAddToDictionaryHint(): Boolean {
        if (!mShowingAddToDictionary) return false
        clear()
        return true
    }

    fun getSuggestions(): List<CharSequence> = mSuggestions

    fun clear() {
        mSuggestions.clear()
        mTouchX = OUT_OF_BOUNDS_X_COORD
        mSelectedString = null
        mSelectedIndex = -1
        mShowingAddToDictionary = false
        invalidate()
        mWordWidth.fill(0)
        mWordX.fill(0)
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        if (mGestureDetector.onTouchEvent(me)) {
            return true
        }

        val action = me.action
        val x = me.x.toInt()
        val y = me.y.toInt()
        mTouchX = x

        when (action) {
            MotionEvent.ACTION_DOWN -> invalidate()
            MotionEvent.ACTION_MOVE -> {
                if (y <= 0) {
                    // Fling up!?
                    if (mSelectedString != null) {
                        if (!mShowingCompletions) {
                            // This "acceptedSuggestion" will not be counted as a word because
                            // it will be counted in pickSuggestion instead.
                        }
                        mService?.pickSuggestionManually(mSelectedIndex, mSelectedString!!)
                        mSelectedString = null
                        mSelectedIndex = -1
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!mScrolled) {
                    if (mSelectedString != null) {
                        if (mShowingAddToDictionary) {
                            longPressFirstWord()
                            clear()
                        } else {
                            if (!mShowingCompletions) {
                                // placeholder for TextEntryState tracking
                            }
                            mService?.pickSuggestionManually(mSelectedIndex, mSelectedString!!)
                        }
                    }
                }
                mSelectedString = null
                mSelectedIndex = -1
                requestLayout()
                hidePreview()
                invalidate()
            }
        }
        return true
    }

    private fun hidePreview() {
        mTouchX = OUT_OF_BOUNDS_X_COORD
        mCurrentWordIndex = OUT_OF_BOUNDS_WORD_INDEX
        mPreviewPopup.dismiss()
    }

    private fun showPreview(wordIndex: Int, altText: String?) {
        val oldWordIndex = mCurrentWordIndex
        mCurrentWordIndex = wordIndex
        // If index changed or changing text
        if (oldWordIndex != mCurrentWordIndex || altText != null) {
            if (wordIndex == OUT_OF_BOUNDS_WORD_INDEX) {
                hidePreview()
            } else {
                val word: CharSequence = altText ?: mSuggestions[wordIndex]
                mPreviewText.text = word
                mPreviewText.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                )
                val wordWidth = (mPaint.measureText(word, 0, word.length) + X_GAP * 2).toInt()
                val popupWidth = wordWidth + mPreviewText.paddingLeft + mPreviewText.paddingRight
                val popupHeight = mPreviewText.measuredHeight
                mPopupPreviewX = mWordX[wordIndex] - mPreviewText.paddingLeft - scrollX +
                        (mWordWidth[wordIndex] - wordWidth) / 2
                mPopupPreviewY = -popupHeight
                val offsetInWindow = IntArray(2)
                getLocationInWindow(offsetInWindow)
                if (mPreviewPopup.isShowing) {
                    mPreviewPopup.update(
                        mPopupPreviewX, mPopupPreviewY + offsetInWindow[1],
                        popupWidth, popupHeight
                    )
                } else {
                    mPreviewPopup.width = popupWidth
                    mPreviewPopup.height = popupHeight
                    mPreviewPopup.showAtLocation(
                        this, Gravity.NO_GRAVITY, mPopupPreviewX,
                        mPopupPreviewY + offsetInWindow[1]
                    )
                }
                mPreviewText.visibility = VISIBLE
            }
        }
    }

    private fun longPressFirstWord() {
        val word = mSuggestions[0]
        if (word.length < 2) return
        if (mService?.addWordToDictionary(word.toString()) == true) {
            showPreview(0, context.resources.getString(R.string.added_word, word))
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hidePreview()
    }

    companion object {
        private const val OUT_OF_BOUNDS_WORD_INDEX = -1
        private const val OUT_OF_BOUNDS_X_COORD = -1
        private const val MAX_SUGGESTIONS = 32
        private const val SCROLL_PIXELS = 20
        private const val X_GAP = 10
    }
}
