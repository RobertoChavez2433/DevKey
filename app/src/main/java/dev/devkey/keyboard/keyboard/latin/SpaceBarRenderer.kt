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
import dev.devkey.keyboard.LanguageSwitcher
import dev.devkey.keyboard.R
import dev.devkey.keyboard.keyboard.model.Key

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.util.Locale

/**
 * Handles rendering of the space bar including language indicator and auto-completion LED.
 */
class SpaceBarRenderer(
    private val mRes: Resources,
    private val mSpaceKey: Key,
    private val mSpaceIcon: Drawable,
    private val mSpaceAutoCompletionIndicator: Drawable,
    private val mButtonArrowLeftIcon: Drawable,
    private val mButtonArrowRightIcon: Drawable,
    private val getTextSizeFromTheme: (style: Int, defValue: Int) -> Int
) {

    companion object {
        private const val OPACITY_FULLY_OPAQUE = 255
        private const val SPACE_LED_LENGTH_PERCENT = 80
        private const val SPACEBAR_LANGUAGE_BASELINE = 0.6f
        private const val MINIMUM_SCALE_OF_LANGUAGE_NAME = 0.8f

        fun getTextWidth(paint: Paint, text: String, textSize: Float, bounds: Rect): Int {
            paint.textSize = textSize
            paint.getTextBounds(text, 0, text.length, bounds)
            return bounds.width()
        }

        fun layoutSpaceBar(
            paint: Paint, locale: Locale, lArrow: Drawable,
            rArrow: Drawable, width: Int, height: Int, origTextSize: Float,
            allowVariableTextSize: Boolean
        ): String {
            val arrowWidth = lArrow.intrinsicWidth.toFloat()
            val arrowHeight = lArrow.intrinsicHeight.toFloat()
            val maxTextWidth = width - (arrowWidth + arrowWidth)
            val bounds = Rect()

            var language = LanguageSwitcher.toTitleCase(locale.getDisplayLanguage(locale))
            var textWidth = getTextWidth(paint, language, origTextSize, bounds)
            var textSize = origTextSize * Math.min(maxTextWidth / textWidth, 1.0f)

            val useShortName: Boolean
            if (allowVariableTextSize) {
                textWidth = getTextWidth(paint, language, textSize, bounds)
                useShortName = textSize / origTextSize < MINIMUM_SCALE_OF_LANGUAGE_NAME
                        || textWidth > maxTextWidth
            } else {
                useShortName = textWidth > maxTextWidth
                textSize = origTextSize
            }
            if (useShortName) {
                language = LanguageSwitcher.toTitleCase(locale.language)
                textWidth = getTextWidth(paint, language, origTextSize, bounds)
                textSize = origTextSize * Math.min(maxTextWidth / textWidth, 1.0f)
            }
            paint.textSize = textSize

            val baseline = height * SPACEBAR_LANGUAGE_BASELINE
            val top = (baseline - arrowHeight).toInt()
            val remains = (width - textWidth) / 2f
            lArrow.setBounds((remains - arrowWidth).toInt(), top, remains.toInt(), baseline.toInt())
            rArrow.setBounds(
                (remains + textWidth).toInt(), top,
                (remains + textWidth + arrowWidth).toInt(), baseline.toInt()
            )

            return language
        }
    }

    fun drawSpaceBar(
        opacity: Int, isAutoCompletion: Boolean,
        locale: Locale?, languageSwitcher: LanguageSwitcher?
    ): Bitmap {
        val width = mSpaceKey.width
        val height = mSpaceIcon.intrinsicHeight
        val buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(buffer)
        @Suppress("DEPRECATION")
        canvas.drawColor(mRes.getColor(R.color.latinkeyboard_transparent), PorterDuff.Mode.CLEAR)
        if (locale != null && languageSwitcher != null) {
            val paint = Paint()
            paint.alpha = opacity
            paint.isAntiAlias = true
            paint.textAlign = Paint.Align.CENTER

            val inputLocale = languageSwitcher.getInputLocale()!!
            val language = layoutSpaceBar(
                paint, inputLocale,
                mButtonArrowLeftIcon, mButtonArrowRightIcon, width, height,
                getTextSizeFromTheme(android.R.style.TextAppearance_Small, 14).toFloat(),
                allowVariableTextSize = true
            )

            @Suppress("DEPRECATION")
            val shadowColor = mRes.getColor(R.color.latinkeyboard_bar_language_shadow_white)
            val baseline = height * SPACEBAR_LANGUAGE_BASELINE
            val descent = paint.descent()
            paint.color = shadowColor
            canvas.drawText(language, width / 2f, baseline - descent - 1, paint)
            @Suppress("DEPRECATION")
            paint.color = mRes.getColor(R.color.latinkeyboard_dim_color_white)
            canvas.drawText(language, width / 2f, baseline - descent, paint)

            if (languageSwitcher.getLocaleCount() > 1) {
                mButtonArrowLeftIcon.draw(canvas)
                mButtonArrowRightIcon.draw(canvas)
            }
        }

        if (isAutoCompletion) {
            val iconWidth = width * SPACE_LED_LENGTH_PERCENT / 100
            val iconHeight = mSpaceAutoCompletionIndicator.intrinsicHeight
            val x = (width - iconWidth) / 2
            val y = height - iconHeight
            mSpaceAutoCompletionIndicator.setBounds(x, y, x + iconWidth, y + iconHeight)
            mSpaceAutoCompletionIndicator.draw(canvas)
        } else {
            val iconWidth = mSpaceIcon.intrinsicWidth
            val iconHeight = mSpaceIcon.intrinsicHeight
            val x = (width - iconWidth) / 2
            val y = height - iconHeight
            mSpaceIcon.setBounds(x, y, x + iconWidth, y + iconHeight)
            mSpaceIcon.draw(canvas)
        }
        return buffer
    }

    @Suppress("DEPRECATION")
    fun updateSpaceBarForLocale(isAutoCompletion: Boolean, locale: Locale?, languageSwitcher: LanguageSwitcher?) {
        mSpaceKey.icon = when {
            locale != null -> BitmapDrawable(mRes, drawSpaceBar(OPACITY_FULLY_OPAQUE, isAutoCompletion, locale, languageSwitcher))
            isAutoCompletion -> BitmapDrawable(mRes, drawSpaceBar(OPACITY_FULLY_OPAQUE, isAutoCompletion, null, null))
            else -> mRes.getDrawable(R.drawable.sym_keyboard_space)
        }
    }

    fun drawSynthesizedSettingsHintImage(
        width: Int, height: Int, mainIcon: Drawable?, hintIcon: Drawable?,
        setDefaultBoundsCallback: (Drawable) -> Unit
    ): Bitmap? {
        if (mainIcon == null || hintIcon == null) return null
        val hintIconPadding = Rect(0, 0, 0, 0)
        hintIcon.getPadding(hintIconPadding)
        val buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(buffer)
        @Suppress("DEPRECATION")
        canvas.drawColor(mRes.getColor(R.color.latinkeyboard_transparent), PorterDuff.Mode.CLEAR)

        val drawableX = (width + hintIconPadding.left - hintIconPadding.right
                - mainIcon.intrinsicWidth) / 2
        val drawableY = (height + hintIconPadding.top - hintIconPadding.bottom
                - mainIcon.intrinsicHeight) / 2
        setDefaultBoundsCallback(mainIcon)
        canvas.translate(drawableX.toFloat(), drawableY.toFloat()); mainIcon.draw(canvas)
        canvas.translate(-drawableX.toFloat(), -drawableY.toFloat())
        hintIcon.setBounds(0, 0, width, height)
        hintIcon.draw(canvas)
        return buffer
    }
}
