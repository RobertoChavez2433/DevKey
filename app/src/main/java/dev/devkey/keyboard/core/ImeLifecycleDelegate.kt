package dev.devkey.keyboard.core

import android.content.res.Configuration
import android.util.Log
import android.util.PrintWriterPrinter
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.core.input.TextEntryState
import dev.devkey.keyboard.ui.keyboard.ComposeKeyboardViewFactory
import kotlinx.coroutines.cancel
import java.io.FileDescriptor
import java.io.PrintWriter

/**
 * Handles all non-trivial lifecycle callback bodies on behalf of [LatinIME].
 *
 * Each method mirrors one framework override and contains the logic that was
 * previously inline in LatinIME.  LatinIME's overrides become one-line
 * delegates: `override fun onFoo() = lifecycle.onFoo()`.
 */
internal class ImeLifecycleDelegate(
    private val ime: LatinIME,
    private val state: ImeState,
    private val col: ImeCollaborators
) {

    /** Called at the end of onCreate after all collaborators are wired. */
    fun finishCreate(inputLanguage: String) {
        col.candidateViewManager = CandidateViewManager(
            state, ime.layoutInflater,
            setCandidatesView = { v -> ime.setCandidatesViewBridge(v) },
            setNextSuggestions = { col.suggestionCoordinator.setNextSuggestions() },
            isPredictionOn = { state.isPredictionOn(col.suggestionCoordinator.isPredictionWanted()) },
            commitTyped = { col.inputHandlers.commitTyped(ime.currentInputConnection, true) },
            onEvaluateInputViewShown = { ime.onEvaluateInputViewShownBridge() },
            isInputViewShown = { ime.isInputViewShown },
            superSetCandidatesViewShown = { shown -> ime.superSetCandidatesViewShown(shown) }
        )
        try { col.dictionaryManager.initSuggest(inputLanguage) } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during dictionary init for $inputLanguage", e)
        }
    }

    fun onDestroy() {
        state.serviceScope.cancel()
        ime.clipboardManager?.stopListening()
        ime.keyMapDumpReceiver?.let { ime.unregisterReceiver(it) }
        ime.mDebugReceivers?.unregisterAll()
        state.mUserDictionary?.close()
        ime.unregisterReceiver(col.feedbackManager.ringerModeReceiver)
        ime.unregisterReceiver(ime.mPluginManager)
        col.notificationController.destroy()
    }

    fun onConfigurationChanged(conf: Configuration) {
        Log.i(TAG, "onConfigurationChanged()")
        val sysLoc = conf.locales[0].toString()
        if (sysLoc != ime.mSystemLocale) {
            ime.mSystemLocale = sysLoc
            if (state.mLanguageSwitcher != null) {
                state.mLanguageSwitcher!!.loadLocales(LatinIME.sKeyboardSettings)
                state.mLanguageSwitcher!!.setSystemLocale(conf.locales[0])
                col.dictionaryManager.toggleLanguage(reset = true, next = true)
            } else {
                state.reloadKeyboards(LatinIME.sKeyboardSettings)
            }
        }
        if (conf.orientation != state.mOrientation) {
            col.inputHandlers.commitTyped(ime.currentInputConnection, true)
            ime.currentInputConnection?.finishComposingText()
            state.mOrientation = conf.orientation
            state.reloadKeyboards(LatinIME.sKeyboardSettings)
            col.candidateViewManager.removeContainer()
        }
        state.mConfigurationChanging = true
    }

    fun onCreateInputView(): View {
        ime.setCandidatesViewShown(false)
        return ComposeKeyboardViewFactory.create(ime, ime, ime.window.window!!.decorView)
    }

    fun onCreateCandidatesView(): View? {
        val view = col.candidateViewManager.createCandidatesView()
        state.mCandidateView?.setService(ime)
        return view
    }

    fun onFinishInput() {
        ime.onAutoCompletionStateChanged(false)
        state.mAutoDictionary?.flushPendingWrites()
        state.mUserBigramDictionary?.flushPendingWrites()
    }

    fun onFinishInputView() {
        state.suggestionsJob?.cancel()
        state.oldSuggestionsJob?.cancel()
    }

    fun hideWindow() {
        ime.onAutoCompletionStateChanged(false)
        col.optionsMenuHandler.dismissDialog()
        state.mWordHistory.clear()
        TextEntryState.endSession()
    }

    fun onDisplayCompletions(completions: Array<CompletionInfo>?) =
        col.suggestionCoordinator.onDisplayCompletions(completions) {
            ime.setCandidatesViewShown(true)
        }

    /** Returns true when re-correction is active and the click/movement should be suppressed. */
    fun suppressExtractedClick(): Boolean =
        state.mReCorrectionEnabled && state.isPredictionOn(col.suggestionCoordinator.isPredictionWanted())

    fun onEvaluateFullscreenMode(superResult: Boolean): Boolean {
        val dm = ime.resources.displayMetrics
        val displayHeight = dm.heightPixels.toFloat()
        val dimen = ime.resources.getDimension(dev.devkey.keyboard.R.dimen.max_height_for_fullscreen)
        return if (displayHeight > dimen || state.mFullscreenOverride) false else superResult
    }

    fun onKeyDown(keyCode: Int, _event: KeyEvent): Boolean? {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> { /* Compose keyboard handles back key internally */ }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (col.swipeHandler.volUpAction != "none" && ime.isKeyboardVisible()) return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (col.swipeHandler.volDownAction != "none" && ime.isKeyboardVisible()) return true
            }
        }
        return null // caller should delegate to super
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean? {
        val handled = col.swipeHandler.handleHardwareKeyUp(
            keyCode, event, ime.isKeyboardVisible(),
            state.mKeyboardSwitcher!!.getShiftState(),
            ime.currentInputConnection
        )
        return if (handled) true else null
    }

    fun dump(_fd: FileDescriptor, fout: PrintWriter) {
        state.dump(PrintWriterPrinter(fout), col.feedbackManager)
    }

    companion object {
        private const val TAG = "DevKey/LatinIME"
    }
}
