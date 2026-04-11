/*
 * Narrow interface for candidate-view management and IME service bridge methods.
 * Collaborators depend on this instead of the concrete LatinIME class.
 */
package dev.devkey.keyboard.core

interface CandidateViewHost {
    fun setCandidatesViewShown(shown: Boolean)
    fun setCandidatesViewShownInternal(shown: Boolean, needsInputViewShown: Boolean)
    fun isKeyboardVisible(): Boolean
    fun isInFullscreenMode(): Boolean
    fun requestHideSelf(flags: Int)
}
