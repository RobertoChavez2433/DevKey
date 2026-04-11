package dev.devkey.keyboard.core

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import dev.devkey.keyboard.R

/**
 * Manages the legacy CandidateView container lifecycle.
 * Extracted from LatinIME to reduce god-class size.
 */
internal class CandidateViewManager(
    private val state: ImeState,
    private val layoutInflater: LayoutInflater,
    private val setCandidatesView: (View) -> Unit,
    private val setNextSuggestions: () -> Unit,
    private val isPredictionOn: () -> Boolean,
    private val commitTyped: () -> Unit,
    private val onEvaluateInputViewShown: () -> Boolean,
    private val isInputViewShown: () -> Boolean,
    private val superSetCandidatesViewShown: (Boolean) -> Unit
) {
    var container: LinearLayout? = null
        private set

    fun createCandidatesView(): View? {
        if (container == null) {
            container = layoutInflater.inflate(R.layout.candidates, null) as LinearLayout
            state.mCandidateView = container!!.findViewById(R.id.candidates)
            state.mCandidateView!!.setPadding(0, 0, 0, 0)
            setCandidatesView(container!!)
        }
        return container
    }

    fun removeContainer() {
        if (container != null) {
            container!!.removeAllViews()
            val parent = container!!.parent
            if (parent is ViewGroup) {
                parent.removeView(container)
            }
            container = null
            state.mCandidateView = null
        }
        state.resetPrediction()
    }

    fun setCandidatesViewShownInternal(shown: Boolean, needsInputViewShown: Boolean) {
        val visible = shown
            && onEvaluateInputViewShown()
            && isPredictionOn()
            && (if (needsInputViewShown) isInputViewShown() else true)
        if (visible) {
            if (container == null) {
                createCandidatesView()
                setNextSuggestions()
            }
        } else {
            if (container != null) {
                removeContainer()
                commitTyped()
            }
        }
        superSetCandidatesViewShown(visible)
    }

    fun onFinishCandidatesView() {
        if (container != null) {
            removeContainer()
        }
    }
}
