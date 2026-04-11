package dev.devkey.keyboard.core

import android.content.Context
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.keyboard.switcher.KeyboardSwitcher
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.core.text.EditingUtil
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Configures the IME session when a new input field is focused.
 * Handles onStartInputView logic and re-correction checks.
 * Extracted from LatinIME to reduce god-class size.
 */
internal class InputViewSetup(
    private val state: ImeState,
    private val icProvider: InputConnectionProvider,
    private val candidateViewHost: CandidateViewHost,
    private val suggestionCoordinator: SuggestionCoordinator,
    private val preferenceObserver: PreferenceObserver,
    private val keyboardSettings: SettingsRepository,
    private val context: Context,
    private val resetPrediction: () -> Unit,
    private val loadSettings: () -> Unit,
    private val updateShiftKeyState: (EditorInfo?) -> Unit,
    private val isPredictionOn: () -> Boolean,
    private val toggleLanguage: (reset: Boolean, next: Boolean) -> Unit
) {

    var commandModeDetector: CommandModeDetector? = null

    fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
        keyboardSettings.editorPackageName = attribute.packageName
        keyboardSettings.editorFieldName = attribute.fieldName
        keyboardSettings.editorFieldId = attribute.fieldId
        keyboardSettings.editorInputType = attribute.inputType

        if (state.mRefreshKeyboardRequired) {
            state.mRefreshKeyboardRequired = false
            toggleLanguage(true, true)
        }

        state.mKeyboardSwitcher!!.makeKeyboards(false)
        TextEntryState.newSession(context)

        state.mPasswordText = false
        val variation = attribute.inputType and EditorInfo.TYPE_MASK_VARIATION
        if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
            || variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            || variation == 0xe0 /* EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD */
        ) {
            if ((attribute.inputType and EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
                state.mPasswordText = true
            }
        }

        state.mEnableVoiceButton = true // shouldShowVoiceButton always returns true
        val enableVoiceButton = state.mEnableVoiceButton && state.mEnableVoice

        // Detect command mode for the current app
        SessionDependencies.currentPackageName = attribute.packageName
        commandModeDetector?.let { detector ->
            val packageName = attribute.packageName
            state.serviceScope.launch(Dispatchers.IO) {
                try {
                    detector.detectSync(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Command mode detection failed", e)
                }
            }
        }

        state.mInputTypeNoAutoCorrect = false
        state.mPredictionOnForMode = false
        state.mCompletionOn = false
        state.mCompletions = null
        state.mModCtrl = false
        state.mModAlt = false
        state.mModMeta = false
        state.mModFn = false
        state.mEnteredText = null
        state.mSuggestionForceOn = false
        state.mSuggestionForceOff = false
        state.mKeyboardModeOverridePortrait = 0
        state.mKeyboardModeOverrideLandscape = 0
        keyboardSettings.useExtension = false

        when (attribute.inputType and EditorInfo.TYPE_MASK_CLASS) {
            EditorInfo.TYPE_CLASS_NUMBER,
            EditorInfo.TYPE_CLASS_DATETIME,
            EditorInfo.TYPE_CLASS_PHONE -> {
                state.mKeyboardSwitcher!!.setKeyboardMode(
                    KeyboardSwitcher.MODE_PHONE,
                    attribute.imeOptions, enableVoiceButton
                )
            }
            EditorInfo.TYPE_CLASS_TEXT -> {
                state.mKeyboardSwitcher!!.setKeyboardMode(
                    KeyboardSwitcher.MODE_TEXT,
                    attribute.imeOptions, enableVoiceButton
                )
                state.mPredictionOnForMode = true
                if (state.mPasswordText) {
                    state.mPredictionOnForMode = false
                }
                state.mAutoSpace = !(variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME
                    || !state.mLanguageSwitcher!!.allowAutoSpace())

                when (variation) {
                    EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> {
                        state.mPredictionOnForMode = false
                        state.mKeyboardSwitcher!!.setKeyboardMode(
                            KeyboardSwitcher.MODE_EMAIL,
                            attribute.imeOptions, enableVoiceButton
                        )
                    }
                    EditorInfo.TYPE_TEXT_VARIATION_URI -> {
                        state.mPredictionOnForMode = false
                        state.mKeyboardSwitcher!!.setKeyboardMode(
                            KeyboardSwitcher.MODE_URL,
                            attribute.imeOptions, enableVoiceButton
                        )
                    }
                    EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> {
                        state.mKeyboardSwitcher!!.setKeyboardMode(
                            KeyboardSwitcher.MODE_IM,
                            attribute.imeOptions, enableVoiceButton
                        )
                    }
                    EditorInfo.TYPE_TEXT_VARIATION_FILTER -> {
                        state.mPredictionOnForMode = false
                    }
                    EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> {
                        state.mKeyboardSwitcher!!.setKeyboardMode(
                            KeyboardSwitcher.MODE_WEB,
                            attribute.imeOptions, enableVoiceButton
                        )
                        if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
                            state.mInputTypeNoAutoCorrect = true
                        }
                    }
                }

                if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                    state.mPredictionOnForMode = false
                    state.mInputTypeNoAutoCorrect = true
                }
                if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0
                    && (attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0
                ) {
                    state.mInputTypeNoAutoCorrect = true
                }
                if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    state.mPredictionOnForMode = false
                    state.mCompletionOn = candidateViewHost.isInFullscreenMode()
                }
            }
            else -> {
                state.mKeyboardSwitcher!!.setKeyboardMode(
                    KeyboardSwitcher.MODE_TEXT,
                    attribute.imeOptions, enableVoiceButton
                )
            }
        }
        resetPrediction()
        loadSettings()
        updateShiftKeyState(attribute)

        state.mPredictionOnPref = state.mCorrectionMode > 0 || state.mShowSuggestions
        candidateViewHost.setCandidatesViewShownInternal(
            isPredictionOn() || state.mCompletionOn,
            needsInputViewShown = false
        )
        suggestionCoordinator.updateSuggestions()

        state.mHasDictionary = state.mSuggest!!.hasMainDictionary()
        preferenceObserver.updateCorrectionMode()

        checkReCorrectionOnStart()
    }

    fun checkReCorrectionOnStart() {
        if (state.mReCorrectionEnabled && isPredictionOn()) {
            val ic = icProvider.inputConnection ?: return
            val etr = ExtractedTextRequest().apply { token = 0 }
            val et = ic.getExtractedText(etr, 0) ?: return

            state.mLastSelectionStart = et.startOffset + et.selectionStart
            state.mLastSelectionEnd = et.startOffset + et.selectionEnd

            if (!et.text.isNullOrEmpty() && EditingUtil.isCursorTouchingWord(icProvider.inputConnection, state.mWordSeparators ?: "", state.mSuggestPuncList)) {
                suggestionCoordinator.postUpdateOldSuggestions()
            }
        }
    }

    companion object {
        private const val TAG = "DevKey/InputViewSetup"
    }
}
