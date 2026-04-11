package dev.devkey.keyboard.core

/**
 * Processes compose-sequence and dead-accent key input.
 * Extracted from ImeState to keep business logic out of the state holder.
 */
internal fun processComposeKey(state: ImeState, primaryCode: Int): Boolean {
    if (state.mDeadAccentBuffer.hasBufferedKeys()) {
        state.mDeadAccentBuffer.execute(primaryCode)
        state.mDeadAccentBuffer.clear()
        return true
    }
    if (state.mComposeMode) {
        state.mComposeMode = state.mComposeBuffer.execute(primaryCode)
        return true
    }
    return false
}
