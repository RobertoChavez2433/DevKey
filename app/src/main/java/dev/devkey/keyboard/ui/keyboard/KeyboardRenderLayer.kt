package dev.devkey.keyboard.ui.keyboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import dev.devkey.keyboard.core.KeyboardModeManager
import dev.devkey.keyboard.core.ModifierKeyState
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.feature.macro.MacroEngine

/**
 * Renders the ctrl-mode banner and the keyboard grid.
 *
 * Hidden entirely when [keyboardMode] is [KeyboardMode.Voice].
 *
 * @param onKeyAction Called when a key is fully actioned (tapped/released).
 * @param onKeyPress Called when a key is pressed down.
 * @param onKeyRelease Called when a key press is released.
 * @param currentInputConnection Provider for the current InputConnection (for smart backspace/esc).
 */
@Composable
fun KeyboardRenderLayer(
    keyboardMode: KeyboardMode,
    modeManager: KeyboardModeManager,
    modifierState: ModifierStateManager,
    ctrlState: ModifierKeyState,
    macroEngine: MacroEngine,
    layoutMode: LayoutMode,
    keyboardHeightPercent: Float,
    showHints: Boolean,
    hintBright: Boolean,
    showNumberRow: Boolean,
    bridge: dev.devkey.keyboard.core.KeyboardActionBridge,
    currentInputConnection: (() -> android.view.inputmethod.InputConnection?)? = null
) {
    // 3. Ctrl Mode banner (independent of KeyboardMode)
    AnimatedVisibility(
        visible = ctrlState == ModifierKeyState.HELD,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it })
    ) {
        CtrlModeBanner()
    }

    // 4. Keyboard grid (hidden during voice input)
    if (keyboardMode !is KeyboardMode.Voice) {
        val activeLayoutMode = when (keyboardMode) {
            is KeyboardMode.Symbols -> LayoutMode.FULL  // Symbols uses its own layout data
            else -> layoutMode
        }
        val activeLayout = when (keyboardMode) {
            is KeyboardMode.Symbols -> SymbolsLayout.layout
            else -> QwertyLayout.getLayout(layoutMode, includeNumberRow = showNumberRow)
        }

        KeyboardView(
            layout = activeLayout,
            layoutMode = activeLayoutMode,
            modifierState = modifierState,
            ctrlHeld = ctrlState == ModifierKeyState.HELD,
            heightPercent = keyboardHeightPercent,
            showHints = showHints,
            hintBright = hintBright,
            onKeyAction = { code ->
                // Capture for macro recording
                if (macroEngine.isRecording) {
                    val modifiers = buildList {
                        if (modifierState.isCtrlActive()) add("ctrl")
                        if (modifierState.isAltActive()) add("alt")
                        if (modifierState.isShiftActive()) add("shift")
                    }
                    val key = if (code in 32..126) code.toChar().toString() else ""
                    if (key.isNotEmpty()) {
                        macroEngine.captureKey(key, code, modifiers)
                    }
                }
                // Handle special keycodes
                when (code) {
                    SymbolsLayout.KEYCODE_ALPHA -> {
                        // ABC key from symbols layout
                        modeManager.setMode(KeyboardMode.Normal)
                    }
                    KeyCodes.SYMBOLS -> {
                        // 123 key — toggle symbols mode
                        modeManager.toggleMode(KeyboardMode.Symbols)
                    }
                    KeyCodes.EMOJI -> {
                        // Emoji key — show system emoji picker
                        // TODO: implement InputMethodService.switchToNextInputMethod()
                        // For now, no-op (emoji picker requires IME service access)
                    }
                    KeyCodes.SMART_BACK_ESC -> {
                        // Smart backspace/esc — resolve using InputConnection
                        val ic = currentInputConnection?.invoke()
                        val resolvedCode = bridge.resolveSmartBackEsc(ic)
                        bridge.onKey(resolvedCode, modifierState)
                    }
                    else -> {
                        bridge.onKey(code, modifierState)
                    }
                }
            },
            onKeyPress = { code ->
                // Don't forward codes handled locally by Compose UI to avoid
                // LatinIME legacy mode-switching interference
                if (!isComposeLocalCode(code)) bridge.onKeyPress(code)
            },
            onKeyRelease = { code ->
                if (!isComposeLocalCode(code)) bridge.onKeyRelease(code)
            }
        )
    }
}
