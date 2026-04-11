// Copyright (C) 2008 The Android Open Source Project. Licensed under the Apache License, Version 2.0.
package dev.devkey.keyboard.keyboard.latin
import dev.devkey.keyboard.KeyboardSwitcher
import dev.devkey.keyboard.LanguageSwitcher
import dev.devkey.keyboard.LocaleDragController
import dev.devkey.keyboard.R
import dev.devkey.keyboard.keyboard.model.Key
import dev.devkey.keyboard.keyboard.model.Keyboard
import dev.devkey.keyboard.keyboard.model.KeyboardModes
import dev.devkey.keyboard.keyboard.model.Row
import dev.devkey.keyboard.keyboard.proximity.KeyProximityResolver

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import dev.devkey.keyboard.ui.keyboard.KeyCodes
import java.util.Locale

class LatinKeyboard : Keyboard {

    companion object {
        private const val SPACEBAR_POPUP_MIN_RATIO = 0.4f
        private const val SPACEBAR_POPUP_MAX_RATIO = 0.4f
        private var sSpacebarVerticalCorrection = 0

        fun hasPuncOrSmileysPopup(key: Key): Boolean =
            key.popupResId == R.xml.popup_punctuation || key.popupResId == R.xml.popup_smileys
    }

    private val mIcons: LatinKeyboardIcons
    private var mOldShiftIcon: Drawable? = null
    // mShiftKey inherited from Keyboard as internal var
    private var mEnterKey: Key? = null
    private var mF1Key: Key? = null
    private var mSpaceKey: Key? = null
    private var m123Key: Key? = null
    private val mSpaceKeyIndexArray: IntArray
    private var mLocale: Locale? = null
    private var mLanguageSwitcher: LanguageSwitcher? = null
    private val mRes: Resources
    private val mContext: Context
    private var mMode: Int
    private var mHasVoiceButton = false
    private var mVoiceEnabled = false
    private val mIsAlphaKeyboard: Boolean
    private val mIsAlphaFullKeyboard: Boolean
    private val mIsFnFullKeyboard: Boolean
    private var m123Label: CharSequence? = null
    private val mVerticalGap: Int
    private var mExtensionKeyboard: LatinKeyboard? = null
    private var mSpaceBarRenderer: SpaceBarRenderer? = null
    private var mLocaleDragController: LocaleDragController? = null
    private var mF1KeyManager: F1KeyManager? = null
    private val mProximityResolver: KeyProximityResolver
    private val mTouchCorrector: LatinKeyTouchCorrector

    constructor(context: Context, xmlLayoutResId: Int) : this(context, xmlLayoutResId, 0, 0f)

    @Suppress("DEPRECATION")
    constructor(context: Context, xmlLayoutResId: Int, mode: Int, kbHeightPercent: Float)
            : super(context, 0, xmlLayoutResId, mode, kbHeightPercent) {
        val res = context.resources
        mContext = context; mMode = mode; mRes = res
        mIcons = LatinKeyboardIcons(res)
        sSpacebarVerticalCorrection = res.getDimensionPixelOffset(R.dimen.spacebar_vertical_correction)
        mIsAlphaKeyboard = xmlLayoutResId == R.xml.kbd_qwerty
        mIsAlphaFullKeyboard = xmlLayoutResId == R.xml.kbd_full
        mIsFnFullKeyboard = xmlLayoutResId == R.xml.kbd_full_fn || xmlLayoutResId == R.xml.kbd_compact_fn
        mSpaceKeyIndexArray = intArrayOf(getKeys().indexOfFirst { it.codes?.get(0) == KeyCodes.ASCII_SPACE })
        mVerticalGap = super.getVerticalGap()
        mProximityResolver = KeyProximityResolver(
            getNearestKeysSuper = { x, y ->
                super.getNearestKeys(
                    Math.max(0, Math.min(x, getMinWidth() - 1)),
                    Math.max(0, Math.min(y, getHeight() - 1))
                )
            },
            getKeys = { getKeys() }
        )
        mTouchCorrector = LatinKeyTouchCorrector(
            sSpacebarVerticalCorrection, mProximityResolver,
            { mLanguageSwitcher }, { getLocaleDragController() }
        )
    }

    override fun createKeyFromXml(res: Resources, parent: Row, x: Int, y: Int, parser: XmlResourceParser): Key {
        val key = LatinKey(res, parent, x, y, parser)
        key.verticalGap = mVerticalGap
        key.isInsideCallback = ::isInside
        if (key.codes == null) return key
        when (key.codes!![0]) {
            KeyCodes.ENTER -> mEnterKey = key
            KeyCodes.KEYCODE_F1 -> mF1Key = key
            KeyCodes.ASCII_SPACE -> mSpaceKey = key
            KEYCODE_MODE_CHANGE -> { m123Key = key; m123Label = key.label }
        }
        return key
    }

    fun setImeOptions(res: Resources, mode: Int, options: Int) { mMode = mode; mEnterKey?.let { EnterKeyAppearance.apply(it, res, options) } }

    fun enableShiftLock() { val i = getShiftKeyIndex(); if (i >= 0) { mShiftKey = getKeys()[i]; mOldShiftIcon = mShiftKey!!.icon } }

    override fun setShiftState(shiftState: Int): Boolean = if (mShiftKey != null) {
        mShiftKey!!.on = shiftState == SHIFT_ON || shiftState == SHIFT_LOCKED
        mShiftKey!!.locked = shiftState == SHIFT_LOCKED || shiftState == SHIFT_CAPS_LOCKED
        mShiftKey!!.icon = if (shiftState == SHIFT_OFF || shiftState == SHIFT_ON || shiftState == SHIFT_LOCKED) mOldShiftIcon else mIcons.shiftLocked
        super.setShiftState(shiftState, false)
    } else super.setShiftState(shiftState, true)

    fun isAlphaKeyboard(): Boolean = mIsAlphaKeyboard
    fun setExtension(extKeyboard: LatinKeyboard?) { mExtensionKeyboard = extKeyboard }
    fun getExtension(): LatinKeyboard? = mExtensionKeyboard

    fun updateSymbolIcons(isAutoCompletion: Boolean) {
        updateDynamicKeys()
        getSpaceBarRenderer()?.updateSpaceBarForLocale(isAutoCompletion, mLocale, mLanguageSwitcher)
    }

    fun setVoiceMode(hasVoiceButton: Boolean, hasVoice: Boolean) {
        mHasVoiceButton = hasVoiceButton; mVoiceEnabled = hasVoice; updateDynamicKeys()
    }

    private fun updateDynamicKeys() {
        if (m123Key != null && mIsAlphaKeyboard) {
            if (mVoiceEnabled && !mHasVoiceButton) {
                m123Key!!.icon = mIcons.mic123; m123Key!!.iconPreview = mIcons.mic123Preview; m123Key!!.label = null
            } else {
                m123Key!!.icon = null; m123Key!!.iconPreview = null; m123Key!!.label = m123Label
            }
        }
        if (mF1Key != null) getF1KeyManager()?.updateF1Key(
            mIsAlphaKeyboard, mIsAlphaFullKeyboard, mIsFnFullKeyboard, mMode, mVoiceEnabled, mHasVoiceButton
        )
    }

    fun isF1Key(key: Key): Boolean = getF1KeyManager()?.isF1Key(key) ?: false
    fun onAutoCompletionStateChanged(isAutoCompletion: Boolean): Key? {
        getSpaceBarRenderer()?.updateSpaceBarForLocale(isAutoCompletion, mLocale, mLanguageSwitcher)
        return mSpaceKey
    }
    fun isLanguageSwitchEnabled(): Boolean = mLocale != null

    fun setLanguageSwitcher(switcher: LanguageSwitcher?, isAutoCompletion: Boolean) {
        mLanguageSwitcher = switcher
        var locale = if (mLanguageSwitcher!!.getLocaleCount() > 0) mLanguageSwitcher!!.getInputLocale() else null
        if (locale != null && mLanguageSwitcher!!.getLocaleCount() == 1
            && mLanguageSwitcher!!.getSystemLocale()?.language.equals(locale.language, ignoreCase = true))
            locale = null
        mLocale = locale
        updateSymbolIcons(isAutoCompletion)
    }

    fun isCurrentlyInSpace(): Boolean = getLocaleDragController()?.currentlyInSpace ?: false
    fun setPreferredLetters(frequencies: IntArray?) = mProximityResolver.setPreferredLetters(frequencies)
    fun keyReleased() { mProximityResolver.reset(); getLocaleDragController()?.reset() }
    fun getLanguageChangeDirection(): Int =
        if (mSpaceKey == null) 0 else getLocaleDragController()?.getLanguageChangeDirection() ?: 0

    fun isInside(key: LatinKey, x: Int, y: Int): Boolean = mTouchCorrector.isInside(key, x, y)

    override fun getNearestKeys(x: Int, y: Int): IntArray =
        if (getLocaleDragController()?.currentlyInSpace == true) mSpaceKeyIndexArray
        else super.getNearestKeys(
            Math.max(0, Math.min(x, getMinWidth() - 1)),
            Math.max(0, Math.min(y, getHeight() - 1))
        )

    private fun getTextSizeFromTheme(style: Int, defValue: Int): Int =
        mContext.theme.obtainStyledAttributes(style, intArrayOf(android.R.attr.textSize)).let { a ->
            try { val r = a.getResourceId(0, 0); if (r >= a.length()) defValue else a.getDimensionPixelSize(r, defValue) }
            finally { a.recycle() }
        }

    private fun getSpaceBarRenderer(): SpaceBarRenderer? {
        val spaceKey = mSpaceKey ?: return null
        if (mSpaceBarRenderer == null) mSpaceBarRenderer = SpaceBarRenderer(
            mRes, spaceKey, mIcons.space, mIcons.spaceAutoCompletion,
            mIcons.arrowLeft, mIcons.arrowRight, ::getTextSizeFromTheme
        )
        return mSpaceBarRenderer
    }

    private fun getLocaleDragController(): LocaleDragController? {
        val spaceKey = mSpaceKey ?: return null
        val switcher = mLanguageSwitcher ?: return null
        if (mLocaleDragController == null) mLocaleDragController = LocaleDragController(
            spaceKey, mIcons.spacePreview, switcher, {
                Math.min(Math.max(mSpaceKey!!.width, (getMinWidth() * SPACEBAR_POPUP_MIN_RATIO).toInt()),
                    (getScreenHeight() * SPACEBAR_POPUP_MAX_RATIO).toInt())
            }
        ) { bg, w, h ->
            SlidingLocaleDrawable(bg, w, h, mRes, mContext, switcher,
                getTextSizeFromTheme(android.R.style.TextAppearance_Medium, 18).toFloat(), ::setDefaultBounds)
        }
        return mLocaleDragController
    }

    private fun getF1KeyManager(): F1KeyManager? {
        val f1Key = mF1Key ?: return null
        if (mF1KeyManager == null) mF1KeyManager = F1KeyManager(
            mRes, f1Key, mIcons.mic, mIcons.micPreview, mIcons.settings, mIcons.settingsPreview, mIcons.hint
        ) { w, h, main, hint ->
            getSpaceBarRenderer()?.drawSynthesizedSettingsHintImage(w, h, main, hint, ::setDefaultBounds)
        }
        return mF1KeyManager
    }
}
