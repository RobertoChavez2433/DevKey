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

package dev.devkey.keyboard.keyboard.latin
import dev.devkey.keyboard.language.LanguageSwitcher
import dev.devkey.keyboard.R

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.view.ViewConfiguration

/**
 * Animation to be displayed on the spacebar preview popup when switching
 * languages by swiping the spacebar. It draws the current, previous and
 * next languages and moves them by the delta of touch movement on the spacebar.
 */
class SlidingLocaleDrawable(
    private val mBackground: Drawable,
    private val mWidth: Int,
    private val mHeight: Int,
    private val mRes: Resources,
    private val mContext: Context,
    private val mLanguageSwitcher: LanguageSwitcher,
    textSize: Float,
    private val setDefaultBoundsCallback: (Drawable) -> Unit
) : Drawable() {

    companion object {
        // Height in space key the language name will be drawn (proportional to space key height)
        private const val SPACEBAR_LANGUAGE_BASELINE = 0.6f
        private const val OPACITY_FULLY_OPAQUE = 255
    }

    private val mTextPaint: TextPaint
    private val mMiddleX: Int
    private val mLeftDrawable: Drawable
    private val mRightDrawable: Drawable
    private val mThreshold: Int
    private var mDiff = 0
    private var mHitThreshold = false
    private var mCurrentLanguage: String? = null
    private var mNextLanguage: String? = null
    private var mPrevLanguage: String? = null

    init {
        setDefaultBoundsCallback(mBackground)
        mTextPaint = TextPaint()
        mTextPaint.textSize = textSize
        @Suppress("DEPRECATION")
        mTextPaint.color = mRes.getColor(R.color.latinkeyboard_transparent)
        mTextPaint.textAlign = Paint.Align.CENTER
        mTextPaint.alpha = OPACITY_FULLY_OPAQUE
        mTextPaint.isAntiAlias = true
        mMiddleX = (mWidth - mBackground.intrinsicWidth) / 2
        @Suppress("DEPRECATION")
        mLeftDrawable = mRes.getDrawable(R.drawable.sym_keyboard_feedback_language_arrows_left)
        @Suppress("DEPRECATION")
        mRightDrawable = mRes.getDrawable(R.drawable.sym_keyboard_feedback_language_arrows_right)
        mThreshold = ViewConfiguration.get(mContext).scaledTouchSlop
    }

    fun setDiff(diff: Int) {
        if (diff == Integer.MAX_VALUE) {
            mHitThreshold = false
            mCurrentLanguage = null
            return
        }
        mDiff = diff.coerceIn(-mWidth, mWidth)
        if (Math.abs(mDiff) > mThreshold) mHitThreshold = true
        invalidateSelf()
    }

    private fun getLanguageName(locale: java.util.Locale): String {
        return LanguageSwitcher.toTitleCase(locale.getDisplayLanguage(locale))
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        if (mHitThreshold) {
            val paint: Paint = mTextPaint
            val width = mWidth
            val height = mHeight
            val diff = mDiff
            val lArrow = mLeftDrawable
            val rArrow = mRightDrawable
            canvas.clipRect(0, 0, width, height)
            if (mCurrentLanguage == null) {
                mCurrentLanguage = getLanguageName(mLanguageSwitcher.getInputLocale()!!)
                mNextLanguage = getLanguageName(mLanguageSwitcher.getNextInputLocale()!!)
                mPrevLanguage = getLanguageName(mLanguageSwitcher.getPrevInputLocale()!!)
            }
            // Draw language text with shadow
            val baseline = mHeight * SPACEBAR_LANGUAGE_BASELINE - paint.descent()
            @Suppress("DEPRECATION")
            paint.color = mRes.getColor(R.color.latinkeyboard_feedback_language_text)
            canvas.drawText(mCurrentLanguage!!, width / 2f + diff, baseline, paint)
            canvas.drawText(mNextLanguage!!, diff - width / 2f, baseline, paint)
            canvas.drawText(mPrevLanguage!!, diff + width + width / 2f, baseline, paint)

            setDefaultBoundsCallback(lArrow)
            rArrow.setBounds(
                width - rArrow.intrinsicWidth, 0, width,
                rArrow.intrinsicHeight
            )
            lArrow.draw(canvas)
            rArrow.draw(canvas)
        }
        @Suppress("SENSELESS_COMPARISON")
        if (mBackground != null) {
            canvas.translate(mMiddleX.toFloat(), 0f)
            mBackground.draw(canvas)
        }
        canvas.restore()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setAlpha(alpha: Int) {
        // Ignore
    }

    override fun setColorFilter(cf: ColorFilter?) {
        // Ignore
    }

    override fun getIntrinsicWidth(): Int = mWidth

    override fun getIntrinsicHeight(): Int = mHeight
}
