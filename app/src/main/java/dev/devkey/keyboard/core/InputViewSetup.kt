package dev.devkey.keyboard.core

import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import dev.devkey.keyboard.keyboard.switcher.KeyboardSwitcher
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.TextEntryState
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Configures the IME session when a new input field is focused.
 * Handles onStartInputView logic and re-correction checks.
 * Extracted from LatinIME to reduce god-class size.
 */
internal class InputViewSetup(private val ime: LatinIME) {

    fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
        LatinIME.sKeyboardSettings.editorPackageName = attribute.packageName
        LatinIME.sKeyboardSettings.editorFieldName = attribute.fieldName
        LatinIME.sKeyboardSettings.editorFieldId = attribute.fieldId
        LatinIME.sKeyboardSettings.editorInputType = attribute.inputType

        if (ime.mRefreshKeyboardRequired) {
            ime.mRefreshKeyboardRequired = false
            ime.toggleLanguage(reset = true, next = true)
        }

        ime.mKeyboardSwitcher!!.makeKeyboards(false)
        TextEntryState.newSession(ime)

        ime.mPasswordText = false
        val variation = attribute.inputType and EditorInfo.TYPE_MASK_VARIATION
        if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
            || variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            || variation == 0xe0 /* EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD */
        ) {
            if ((attribute.inputType and EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
                ime.mPasswordText = true
            }
        }

        ime.mEnableVoiceButton = true // shouldShowVoiceButton always returns true
        val enableVoiceButton = ime.mEnableVoiceButton && ime.mEnableVoice

        // Detect command mode for the current app
        SessionDependencies.currentPackageName = attribute.packageName
        ime.commandModeDetector?.let { detector ->
            val packageName = attribute.packageName
            ime.serviceScope.launch(Dispatchers.IO) {
                try {
                    detector.detectSync(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Command mode detection failed", e)
                }
            }
        }

        ime.mInputTypeNoAutoCorrect = false
        ime.mPredictionOnForMode = false
        ime.mCompletionOn = false
        ime.mCompletions = null
        ime.mModCtrl = false
        ime.mModAlt = false
        ime.mModMeta = false
        ime.mModFn = false
        ime.mEnteredText = null
        ime.mSuggestionForceOn = false
        ime.mSuggestionForceOff = false
        ime.mKeyboardModeOverridePortrait = 0
        ime.mKeyboardModeOverrideLandscape = 0
        LatinIME.sKeyboardSettings.useExtension = false

        when (attribute.inputType and EditorInfo.TYPE_MASK_CLASS) {
            EditorInfo.TYPE_CLASS_NUMBER,
            EditorInfo.TYPE_CLASS_DATETIME,
            EditorInfo.TYPE_CLASS_PHONE -> {
                ime.mKeyboardSwitcher!!.setKeyboardMode(
                    KeyboardSwitcher.MODE_PHONE,
                    attribute.imeOptions, enableVoiceButton
                )
            }
            EditorInfo.TYPE_CLASS_TEXT -> {
                ime.mKeyboardSwitcher!!.setKeyboardMode(
                    KeyboardSwitcher.MODE_TEXT,
                    attribute.imeOptions, enableVoiceButton
                )
                ime.mPredictionOnForMode = true
                if (ime.mPasswordText) {
                    ime.mPredictionOnForMode = false
                }
                ime.mAutoSpace = !(variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME
                    || !ime.mLanguageSwitcher!!.allowAutoSpace())

                when (variation) {
                    EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> {
                        ime.mPredictionOnForMode = false
                        ime.mKeyboardSwitcher!!.setKeyboardMode(
                            KeyboardSwitcher.MODE_EMAIL,
                            attribute.imeOptions, enableVoiceButton
                        )
                    }
                    EditorInfo.TYPE_TEXT_VARIATION_URI -> {
                        ime.mPredictionOnForMode = false
                        ime.mKeyboardSwitcher!!.setKeyboardMode(
                            KeyboardSwitcher.MODE_URL,
                            attribute.imeOptions, enableVoiceButton
                        )
                    }
                    EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> {
                        ime.mKeyboardSwitcher!!.setKeyboardMode(
                            KeyboardSwitcher.MODE_IM,
                            attribute.imeOptions, enableVoiceButton
                        )
                    }
                    EditorInfo.TYPE_TEXT_VARIATION_FILTER -> {
                        ime.mPredictionOnForMode = false
                    }
                    EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> {
                        ime.mKeyboardSwitcher!!.setKeyboardMode(
                            KeyboardSwitcher.MODE_WEB,
                            attribute.imeOptions, enableVoiceButton
                        )
                        if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
                            ime.mInputTypeNoAutoCorrect = true
                        }
                    }
                }

                if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                    ime.mPredictionOnForMode = false
                    ime.mInputTypeNoAutoCorrect = true
                }
                if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0
                    && (attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0
                ) {
                    ime.mInputTypeNoAutoCorrect = true
                }
                if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    ime.mPredictionOnForMode = false
                    ime.mCompletionOn = ime.isFullscreenMode
                }
            }
            else -> {
                ime.mKeyboardSwitcher!!.setKeyboardMode(
                    KeyboardSwitcher.MODE_TEXT,
                    attribute.imeOptions, enableVoiceButton
                )
            }
        }
        ime.resetPrediction()
        ime.loadSettings()
        ime.updateShiftKeyState(attribute)

        ime.mPredictionOnPref = ime.mCorrectionMode > 0 || ime.mShowSuggestions
        ime.setCandidatesViewShownInternal(
            ime.isPredictionOn() || ime.mCompletionOn,
            needsInputViewShown = false
        )
        ime.mSuggestionCoordinator.updateSuggestions()

        ime.mHasDictionary = ime.mSuggest!!.hasMainDictionary()
        ime.mPreferenceObserver.updateCorrectionMode()

        checkReCorrectionOnStart()
    }

    fun checkReCorrectionOnStart() {
        if (ime.mReCorrectionEnabled && ime.isPredictionOn()) {
            val ic = ime.currentInputConnection ?: return
            val etr = ExtractedTextRequest().apply { token = 0 }
            val et = ic.getExtractedText(etr, 0) ?: return

            ime.mLastSelectionStart = et.startOffset + et.selectionStart
            ime.mLastSelectionEnd = et.startOffset + et.selectionEnd

            if (!et.text.isNullOrEmpty() && ime.isCursorTouchingWord()) {
                ime.mSuggestionCoordinator.postUpdateOldSuggestions()
            }
        }
    }

    companion object {
        private const val TAG = "DevKey/InputViewSetup"
    }
}
