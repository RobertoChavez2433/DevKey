/* Copyright (C) 2008 The Android Open Source Project. Apache License 2.0 */
package dev.devkey.keyboard

import android.content.BroadcastReceiver
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.core.*
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.dictionary.loader.PluginManager
import dev.devkey.keyboard.feature.clipboard.DevKeyClipboardManager
import dev.devkey.keyboard.feature.command.CommandModeDetector
import dev.devkey.keyboard.suggestion.word.WordComposer
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import java.io.FileDescriptor
import java.io.PrintWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Wiring shell for the DevKey input method.
 * State lives in [ImeState], collaborators in [ImeCollaborators],
 * lifecycle logic in [ImeLifecycleDelegate], init in [ImeInitializer].
 */
class LatinIME : InputMethodService(),
    KeyboardActionListener, InputConnectionProvider, CandidateViewHost {

    internal val state = ImeState()
    internal val col = ImeCollaborators()

    internal var clipboardManager: DevKeyClipboardManager? = null
    internal var commandModeDetector: CommandModeDetector? = null
    internal var mSystemLocale: String? = null
    internal var mPluginManager: PluginManager? = null
    internal var keyMapDumpReceiver: BroadcastReceiver? = null
    internal var mDebugReceivers: dev.devkey.keyboard.debug.DebugReceiverManager? = null
    private lateinit var lifecycle: ImeLifecycleDelegate
    private var settingsChangeRegistration: AutoCloseable? = null

    override val inputConnection: InputConnection? get() = currentInputConnection
    override val editorInfo: EditorInfo? get() = currentInputEditorInfo
    override fun isKeyboardVisible(): Boolean = isInputViewShown
    override fun isInFullscreenMode(): Boolean = isFullscreenMode

    // Bridge methods for collaborators that need super/protected access
    internal fun superSetCandidatesViewShown(shown: Boolean) = super.setCandidatesViewShown(shown)
    internal fun setCandidatesViewBridge(v: View) = setCandidatesView(v)
    internal fun onEvaluateInputViewShownBridge(): Boolean = onEvaluateInputViewShown()

    override fun onCreate() {
        Log.i(TAG, "onCreate(), os.version=${System.getProperty("os.version")}")
        super.onCreate()
        sInstance = this
        sKeyboardSettings = SettingsRepository.from(this)
        lifecycle = ImeLifecycleDelegate(this, state, col)
        ImeInitializer(this, state, col, sKeyboardSettings).run { val lang = initialize(); initPhase2(); lifecycle.finishCreate(lang) }
        settingsChangeRegistration = sKeyboardSettings.registerChangeCallback { key ->
            col.preferenceObserver.onPreferenceChanged(sKeyboardSettings, key)
        }
    }

    override fun onDestroy() {
        settingsChangeRegistration?.close()
        settingsChangeRegistration = null
        lifecycle.onDestroy()
        sInstance = null
        super.onDestroy()
    }
    override fun onConfigurationChanged(conf: Configuration) {
        lifecycle.onConfigurationChanged(conf)
        state.mConfigurationChanging = true
        super.onConfigurationChanged(conf)
        state.mConfigurationChanging = false
    }
    override fun onCreateInputView(): View = lifecycle.onCreateInputView()
    override fun onCreateCandidatesView(): View? = lifecycle.onCreateCandidatesView()
    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) = col.inputViewSetup.onStartInputView(attribute, restarting)
    override fun onFinishInput() { super.onFinishInput(); lifecycle.onFinishInput() }
    override fun onFinishInputView(finishingInput: Boolean) { super.onFinishInputView(finishingInput); lifecycle.onFinishInputView() }
    override fun onUpdateSelection(oSS: Int, oSE: Int, nSS: Int, nSE: Int, cS: Int, cE: Int) {
        super.onUpdateSelection(oSS, oSE, nSS, nSE, cS, cE)
        col.suggestionCoordinator.onSelectionChanged(oSS, nSS, nSE, cS, cE)
    }
    override fun onExtractedTextClicked() { if (!lifecycle.suppressExtractedClick()) super.onExtractedTextClicked() }
    override fun onExtractedCursorMovement(dx: Int, dy: Int) { if (!lifecycle.suppressExtractedClick()) super.onExtractedCursorMovement(dx, dy) }
    override fun hideWindow() { lifecycle.hideWindow(); super.hideWindow() }
    override fun onDisplayCompletions(completions: Array<CompletionInfo>?) = lifecycle.onDisplayCompletions(completions)
    override fun setCandidatesViewShownInternal(shown: Boolean, needsInputViewShown: Boolean) =
        col.candidateViewManager.setCandidatesViewShownInternal(shown, needsInputViewShown)
    override fun onFinishCandidatesView(finishingInput: Boolean) {
        super.onFinishCandidatesView(finishingInput)
        col.candidateViewManager.onFinishCandidatesView()
    }
    override fun onEvaluateInputViewShown(): Boolean = state.mForceKeyboardOn || super.onEvaluateInputViewShown()
    override fun setCandidatesViewShown(shown: Boolean) = setCandidatesViewShownInternal(shown, needsInputViewShown = true)
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!isFullscreenMode) outInsets.contentTopInsets = outInsets.visibleTopInsets
    }
    override fun onEvaluateFullscreenMode(): Boolean = lifecycle.onEvaluateFullscreenMode(super.onEvaluateFullscreenMode())
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = lifecycle.onKeyDown(keyCode, event) ?: super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = lifecycle.onKeyUp(keyCode, event) ?: super.onKeyUp(keyCode, event)

    override fun onKey(primaryCode: Int, keyCodes: IntArray?, x: Int, y: Int) = col.inputDispatcher.onKey(primaryCode, keyCodes, x, y)
    override fun onText(text: CharSequence) = col.inputDispatcher.onText(text)
    fun onCancel() = col.inputDispatcher.onCancel()
    override fun onPress(primaryCode: Int) = col.modifierHandler.onPress(primaryCode)
    override fun onRelease(primaryCode: Int) = col.modifierHandler.onRelease(primaryCode)

    fun getCurrentWord(): WordComposer = state.mWord

    fun pickSuggestionManually(index: Int, suggestion: CharSequence) = col.suggestionPicker.pickSuggestionManually(index, suggestion)
    fun addWordToDictionary(word: String): Boolean {
        val learningEngine = SessionDependencies.learningEngine ?: return false
        state.serviceScope.launch(Dispatchers.IO) {
            learningEngine.addCustomWord(word)
        }
        col.suggestionCoordinator.postUpdateSuggestions()
        return true
    }
    fun changeKeyboardMode() = col.modifierHandler.changeKeyboardMode()
    fun onAutoCompletionStateChanged(isAutoCompletion: Boolean) = state.mKeyboardSwitcher!!.onAutoCompletionStateChanged(isAutoCompletion)
    internal fun handleClose() {
        col.inputHandlers.commitTyped(currentInputConnection, true)
        requestHideSelf(0)
        dev.devkey.keyboard.core.input.TextEntryState.endSession()
    }
    override fun dump(fd: FileDescriptor, fout: PrintWriter, args: Array<out String>?) { super.dump(fd, fout, args); lifecycle.dump(fd, fout) }

    companion object {
        private const val TAG = "DevKey/LatinIME"
        @JvmField val PREF_SELECTED_LANGUAGES = dev.devkey.keyboard.PREF_SELECTED_LANGUAGES
        @JvmField val PREF_INPUT_LANGUAGE = dev.devkey.keyboard.PREF_INPUT_LANGUAGE
        lateinit var sKeyboardSettings: SettingsRepository; internal set
        var sInstance: LatinIME? = null; private set
    }
}
