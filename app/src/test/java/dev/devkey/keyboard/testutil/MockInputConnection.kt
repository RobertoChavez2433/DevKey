package dev.devkey.keyboard.testutil

import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo

/**
 * Test double for [InputConnection] that tracks call metadata without storing
 * actual text content (privacy requirement).
 *
 * Tracks:
 * - [commitTextCount] and [commitTextLengths] — count and lengths only, never content.
 * - [sendKeyEventCalls] — recorded [KeyEvent] instances.
 * - [batchEditDepth] — nesting counter for begin/end batch edit pairs.
 *
 * Configurable return values for [getTextBeforeCursor] and [getTextAfterCursor]
 * via constructor parameters.
 */
open class MockInputConnection(
    private val textBeforeCursor: CharSequence = "",
    private val textAfterCursor: CharSequence = "",
) : InputConnection {

    var commitTextCount: Int = 0
        private set
    val commitTextLengths: MutableList<Int> = mutableListOf()

    val sendKeyEventCalls: MutableList<KeyEvent> = mutableListOf()

    var batchEditDepth: Int = 0
        private set

    var deleteSurroundingTextCount: Int = 0
        private set

    // --- InputConnection implementation ---

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
        val len = n.coerceAtMost(textBeforeCursor.length)
        return textBeforeCursor.subSequence(textBeforeCursor.length - len, textBeforeCursor.length)
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
        val len = n.coerceAtMost(textAfterCursor.length)
        return textAfterCursor.subSequence(0, len)
    }

    override fun getSelectedText(flags: Int): CharSequence? = null

    override fun getCursorCapsMode(reqModes: Int): Int = 0

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        deleteSurroundingTextCount++
        return true
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
        true

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true

    override fun setComposingRegion(start: Int, end: Int): Boolean = true

    override fun finishComposingText(): Boolean = true

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        commitTextCount++
        commitTextLengths.add(text?.length ?: 0)
        return true
    }

    override fun commitCompletion(text: CompletionInfo?): Boolean = true

    override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = true

    override fun setSelection(start: Int, end: Int): Boolean = true

    override fun performEditorAction(editorAction: Int): Boolean = true

    override fun performContextMenuAction(id: Int): Boolean = true

    override fun beginBatchEdit(): Boolean {
        batchEditDepth++
        return true
    }

    override fun endBatchEdit(): Boolean {
        if (batchEditDepth > 0) batchEditDepth--
        return true
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        if (event != null) sendKeyEventCalls.add(event)
        return true
    }

    override fun clearMetaKeyStates(states: Int): Boolean = true

    override fun reportFullscreenMode(enabled: Boolean): Boolean = true

    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = true

    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = true

    override fun getHandler(): Handler? = null

    override fun closeConnection() {}

    override fun commitContent(
        inputContentInfo: InputContentInfo,
        flags: Int,
        opts: Bundle?,
    ): Boolean = true
}
