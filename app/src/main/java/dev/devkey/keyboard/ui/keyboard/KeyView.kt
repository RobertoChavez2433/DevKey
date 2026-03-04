package dev.devkey.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.devkey.keyboard.Keyboard
import dev.devkey.keyboard.core.KeyPressLogger
import dev.devkey.keyboard.core.ModifierKeyState
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.core.ModifierType
import dev.devkey.keyboard.ui.theme.DevKeyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Composable for a single keyboard key.
 *
 * Handles rendering, press animation, long-press, key repeat, modifier behavior,
 * and theme-based coloring per KeyType.
 */
@Composable
fun KeyView(
    key: KeyData,
    modifierState: ModifierStateManager,
    onKeyAction: (Int) -> Unit,
    onKeyPress: (Int) -> Unit,
    onKeyRelease: (Int) -> Unit,
    ctrlHeld: Boolean = false,
    showHints: Boolean = false,
    hintBright: Boolean = false,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    // Observe modifier states for visual feedback
    val shiftState by modifierState.shiftState.collectAsState()
    val ctrlState by modifierState.ctrlState.collectAsState()
    val altState by modifierState.altState.collectAsState()

    // Determine base and active colors for this key
    val (normalBg, normalText) = getKeyColors(key)
    val activeColor = getActiveKeyColor(key)

    // Determine if this modifier key is active
    val isModifierActive = when {
        key.type == KeyType.MODIFIER && key.primaryCode == Keyboard.KEYCODE_SHIFT ->
            shiftState != ModifierKeyState.OFF
        key.type == KeyType.MODIFIER && key.primaryCode == KeyCodes.CTRL_LEFT ->
            ctrlState != ModifierKeyState.OFF
        key.type == KeyType.MODIFIER && key.primaryCode == KeyCodes.ALT_LEFT ->
            altState != ModifierKeyState.OFF
        // UTILITY type Ctrl/Alt also need active state
        key.type == KeyType.UTILITY && key.primaryCode == KeyCodes.CTRL_LEFT ->
            ctrlState != ModifierKeyState.OFF
        key.type == KeyType.UTILITY && key.primaryCode == KeyCodes.ALT_LEFT ->
            altState != ModifierKeyState.OFF
        else -> false
    }

    val isModifierLocked = when {
        key.type == KeyType.MODIFIER && key.primaryCode == Keyboard.KEYCODE_SHIFT ->
            shiftState == ModifierKeyState.LOCKED
        key.type == KeyType.MODIFIER && key.primaryCode == KeyCodes.CTRL_LEFT ->
            ctrlState == ModifierKeyState.LOCKED
        key.type == KeyType.MODIFIER && key.primaryCode == KeyCodes.ALT_LEFT ->
            altState == ModifierKeyState.LOCKED
        key.type == KeyType.UTILITY && key.primaryCode == KeyCodes.CTRL_LEFT ->
            ctrlState == ModifierKeyState.LOCKED
        key.type == KeyType.UTILITY && key.primaryCode == KeyCodes.ALT_LEFT ->
            altState == ModifierKeyState.LOCKED
        else -> false
    }

    // Ctrl Mode shortcut lookup for letter keys
    val ctrlShortcut = if (ctrlHeld && key.type == KeyType.LETTER) {
        CtrlShortcutMap.getShortcut(key.primaryLabel.lowercase())
    } else null

    // Background color with animation
    val targetColor = when {
        isPressed -> DevKeyTheme.keyPressed
        isModifierActive -> activeColor
        ctrlHeld && key.type == KeyType.LETTER && ctrlShortcut != null -> {
            when (ctrlShortcut.highlight) {
                HighlightType.RED -> DevKeyTheme.ctrlModeRedBg
                HighlightType.NORMAL -> DevKeyTheme.ctrlModeShortcutBg
            }
        }
        else -> normalBg
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(
            durationMillis = if (isPressed) DevKeyTheme.pressDownMs else DevKeyTheme.pressUpMs
        ),
        label = "keyBgColor"
    )

    // Press scale animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) DevKeyTheme.pressScale else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) DevKeyTheme.pressDownMs else DevKeyTheme.pressUpMs
        ),
        label = "keyScale"
    )

    // Ctrl Mode text color overrides
    val ctrlTextColor = when {
        ctrlHeld && key.type == KeyType.LETTER && ctrlShortcut != null -> DevKeyTheme.keyText
        ctrlHeld && key.type == KeyType.LETTER -> DevKeyTheme.ctrlModeDimmed
        ctrlHeld && key.type == KeyType.NUMBER -> DevKeyTheme.ctrlModeFullDim
        else -> null // use default
    }

    // Display label (uppercase if shift is active and this is a letter key)
    val displayLabel = when {
        key.type == KeyType.LETTER && shiftState != ModifierKeyState.OFF ->
            key.primaryLabel.uppercase()
        key.type == KeyType.SPACEBAR -> ""
        else -> key.primaryLabel
    }

    // Determine text size based on key type
    val textSize = when (key.type) {
        KeyType.UTILITY -> DevKeyTheme.fontKeyUtility
        KeyType.SPECIAL -> DevKeyTheme.fontKeySpecial
        KeyType.TOGGLE -> DevKeyTheme.fontKeySpecial
        else -> DevKeyTheme.fontKey
    }

    // Determine if this key is a modifier-like key (needs tap-only handling)
    val isModifierKey = key.type == KeyType.MODIFIER ||
            (key.type == KeyType.UTILITY && (key.primaryCode == KeyCodes.CTRL_LEFT || key.primaryCode == KeyCodes.ALT_LEFT))

    Box(
        modifier = modifier
            .testTag("key_${key.primaryCode}")
            .scale(scale)
            .clip(RoundedCornerShape(DevKeyTheme.keyRadius))
            .background(backgroundColor)
            .pointerInput(key.primaryCode, key.type, key.longPressCode, key.isRepeatable) {
                if (isModifierKey) {
                    // Modifier keys use simple tap detection
                    detectTapGestures(
                        onPress = {
                            val modType = getModifierType(key.primaryCode)
                            isPressed = true
                            KeyPressLogger.logKeyDown(key.primaryLabel, key.primaryCode, "MODIFIER")
                            if (modType != null) {
                                modifierState.onModifierDown(modType, 0)
                            }
                            tryAwaitRelease()
                            isPressed = false
                            KeyPressLogger.logKeyUp(key.primaryLabel, key.primaryCode)
                            if (modType != null) {
                                modifierState.onModifierUp(modType, 0)
                                modifierState.onModifierTap(modType)
                                val currentState = when (modType) {
                                    ModifierType.SHIFT -> modifierState.shiftState.value
                                    ModifierType.CTRL -> modifierState.ctrlState.value
                                    ModifierType.ALT -> modifierState.altState.value
                                    ModifierType.META -> modifierState.metaState.value
                                }
                                KeyPressLogger.logModifierTransition(modType.name, "tap", currentState.name)
                            }
                        }
                    )
                } else {
                    // Non-modifier keys: handle press, long-press, repeat
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            onKeyPress(key.primaryCode)
                            KeyPressLogger.logKeyDown(key.primaryLabel, key.primaryCode, key.type.name)

                            // Start long-press / repeat timer
                            val job = coroutineScope.launch {
                                delay(300L)
                                // Long press triggered
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.LONG_PRESS
                                )
                                KeyPressLogger.logLongPress(key.primaryLabel, key.primaryCode, key.longPressCode)
                                if (key.longPressCode != null) {
                                    onKeyAction(key.longPressCode)
                                } else if (key.isRepeatable) {
                                    var repeatCount = 0
                                    while (isActive) {
                                        repeatCount++
                                        onKeyAction(key.primaryCode)
                                        if (repeatCount % 10 == 0) {
                                            KeyPressLogger.logRepeat(key.primaryLabel, key.primaryCode, repeatCount)
                                        }
                                        delay(50L)
                                    }
                                }
                            }

                            val released = tryAwaitRelease()
                            job.cancel()
                            isPressed = false

                            if (released && !job.isCompleted) {
                                // Short tap - job didn't complete (no long press)
                                KeyPressLogger.logKeyTap(key.primaryLabel, key.primaryCode)
                                onKeyAction(key.primaryCode)
                            }
                            onKeyRelease(key.primaryCode)
                            KeyPressLogger.logKeyUp(key.primaryLabel, key.primaryCode)
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (ctrlHeld && key.type == KeyType.LETTER && ctrlShortcut != null) {
            // Ctrl Mode: letter with shortcut label below
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = displayLabel,
                    color = ctrlTextColor ?: normalText,
                    fontSize = textSize
                )
                Text(
                    text = ctrlShortcut.label,
                    color = DevKeyTheme.keyText,
                    fontSize = DevKeyTheme.ctrlModeShortcutLabelSize
                )
            }
        } else {
            // Normal rendering: center label
            val effectiveTextColor = ctrlTextColor ?: normalText
            Text(
                text = displayLabel,
                color = effectiveTextColor,
                fontSize = textSize
            )
        }

        // Top-right hint for long-press (respects hint mode preference, hidden in Ctrl Mode for letter/number keys)
        if (showHints && key.longPressLabel != null && !(ctrlHeld && (key.type == KeyType.LETTER || key.type == KeyType.NUMBER))) {
            Text(
                text = key.longPressLabel,
                color = if (hintBright) DevKeyTheme.keyText.copy(alpha = 0.7f) else DevKeyTheme.keyHint,
                fontSize = DevKeyTheme.fontKeyHint,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 3.dp, top = 2.dp)
            )
        }

        // Locked indicator dot for modifier keys
        if (isModifierLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(DevKeyTheme.keyText)
            )
        }
    }
}

/**
 * Get the background and text colors for a key based on its type and keycode.
 */
private fun getKeyColors(key: KeyData): Pair<Color, Color> {
    return when (key.type) {
        KeyType.LETTER -> DevKeyTheme.keyBg to DevKeyTheme.keyText
        KeyType.NUMBER -> DevKeyTheme.keyBg to DevKeyTheme.keyText
        KeyType.SPACEBAR -> DevKeyTheme.keyBg to DevKeyTheme.keyText
        KeyType.SPECIAL -> DevKeyTheme.keyBgSpecial to DevKeyTheme.keyTextSpecial
        KeyType.MODIFIER -> DevKeyTheme.modBgShift to DevKeyTheme.modTextShift
        KeyType.ACTION -> {
            if (key.primaryCode == KeyCodes.ENTER) {
                DevKeyTheme.modBgEnter to DevKeyTheme.modTextEnter
            } else {
                DevKeyTheme.modBgAction to DevKeyTheme.modTextAction
            }
        }
        KeyType.TOGGLE -> DevKeyTheme.modBgToggle to DevKeyTheme.modTextToggle
        KeyType.UTILITY -> {
            // Navigation arrows use nav colors, system modifiers use sysmod
            when (key.primaryCode) {
                KeyCodes.CTRL_LEFT, KeyCodes.ALT_LEFT, KeyCodes.TAB ->
                    DevKeyTheme.modBgSysmod to DevKeyTheme.modTextSysmod
                else -> DevKeyTheme.modBgNav to DevKeyTheme.modTextNav
            }
        }
        KeyType.ARROW -> DevKeyTheme.modBgNav to DevKeyTheme.modTextNav
    }
}

/**
 * Get the active (modifier engaged) background color.
 */
private fun getActiveKeyColor(key: KeyData): Color = when (key.primaryCode) {
    Keyboard.KEYCODE_SHIFT -> DevKeyTheme.modBgShiftActive
    KeyCodes.CTRL_LEFT -> DevKeyTheme.modBgCtrlActive
    KeyCodes.ALT_LEFT -> DevKeyTheme.modBgAltActive
    else -> DevKeyTheme.keyBg
}

/**
 * Map a keycode to a ModifierType.
 */
private fun getModifierType(code: Int): ModifierType? = when (code) {
    Keyboard.KEYCODE_SHIFT -> ModifierType.SHIFT
    KeyCodes.CTRL_LEFT -> ModifierType.CTRL
    KeyCodes.ALT_LEFT -> ModifierType.ALT
    else -> null
}
