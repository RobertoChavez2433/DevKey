package dev.devkey.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.devkey.keyboard.Keyboard
import dev.devkey.keyboard.core.ModifierKeyState
import dev.devkey.keyboard.core.ModifierStateManager
import dev.devkey.keyboard.core.ModifierType
import dev.devkey.keyboard.ui.theme.DevKeyTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Keycodes matching LatinKeyboardView (package-private in Java)
private const val KEYCODE_CTRL_LEFT = -113
private const val KEYCODE_ALT_LEFT = -57

/**
 * Composable for a single keyboard key.
 *
 * Handles rendering, press animation, long-press, key repeat, and modifier behavior.
 */
@Composable
fun KeyView(
    key: KeyData,
    modifierState: ModifierStateManager,
    onKeyAction: (Int) -> Unit,
    onKeyPress: (Int) -> Unit,
    onKeyRelease: (Int) -> Unit,
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
    val normalColor = getKeyColor(key)
    val pressedColor = DevKeyTheme.keyPressed
    val activeColor = getActiveKeyColor(key)

    // Determine if this modifier key is active
    val isModifierActive = when {
        key.type == KeyType.MODIFIER && key.primaryCode == Keyboard.KEYCODE_SHIFT ->
            shiftState != ModifierKeyState.OFF
        key.type == KeyType.MODIFIER && key.primaryCode == KEYCODE_CTRL_LEFT ->
            ctrlState != ModifierKeyState.OFF
        key.type == KeyType.MODIFIER && key.primaryCode == KEYCODE_ALT_LEFT ->
            altState != ModifierKeyState.OFF
        else -> false
    }

    val isModifierLocked = when {
        key.type == KeyType.MODIFIER && key.primaryCode == Keyboard.KEYCODE_SHIFT ->
            shiftState == ModifierKeyState.LOCKED
        key.type == KeyType.MODIFIER && key.primaryCode == KEYCODE_CTRL_LEFT ->
            ctrlState == ModifierKeyState.LOCKED
        key.type == KeyType.MODIFIER && key.primaryCode == KEYCODE_ALT_LEFT ->
            altState == ModifierKeyState.LOCKED
        else -> false
    }

    // Background color with animation
    val targetColor = when {
        isPressed -> pressedColor
        isModifierActive -> activeColor
        else -> normalColor
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 100),
        label = "keyBgColor"
    )

    // Display label (uppercase if shift is active and this is a letter key)
    val displayLabel = when {
        key.type == KeyType.LETTER && shiftState != ModifierKeyState.OFF ->
            key.primaryLabel.uppercase()
        key.type == KeyType.SPACEBAR -> ""
        else -> key.primaryLabel
    }

    // Track repeat job for repeatable keys
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DevKeyTheme.keyCornerRadius))
            .background(backgroundColor)
            .pointerInput(key.primaryCode, key.type) {
                if (key.type == KeyType.MODIFIER) {
                    // Modifier keys use simple tap detection
                    detectTapGestures(
                        onPress = {
                            val modType = getModifierType(key.primaryCode)
                            isPressed = true
                            if (modType != null) {
                                modifierState.onModifierDown(modType, 0)
                            }
                            tryAwaitRelease()
                            isPressed = false
                            if (modType != null) {
                                modifierState.onModifierUp(modType, 0)
                                modifierState.onModifierTap(modType)
                            }
                        }
                    )
                } else {
                    // Non-modifier keys: handle press, long-press, repeat
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            onKeyPress(key.primaryCode)

                            // Start long-press / repeat timer
                            val job = coroutineScope.launch {
                                delay(300L)
                                // Long press triggered
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.LONG_PRESS
                                )
                                if (key.longPressCode != null) {
                                    onKeyAction(key.longPressCode)
                                } else if (key.isRepeatable) {
                                    while (isActive) {
                                        onKeyAction(key.primaryCode)
                                        delay(50L)
                                    }
                                }
                            }
                            repeatJob = job

                            val released = tryAwaitRelease()
                            job.cancel()
                            repeatJob = null
                            isPressed = false

                            if (released && !job.isCompleted) {
                                // Short tap — job didn't complete (no long press)
                                onKeyAction(key.primaryCode)
                            }
                            onKeyRelease(key.primaryCode)
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Center label
        Text(
            text = displayLabel,
            color = if (key.type == KeyType.SPACEBAR) DevKeyTheme.spaceKeyText else DevKeyTheme.keyText,
            fontSize = DevKeyTheme.keyLabelSize
        )

        // Top-right hint for long-press
        if (key.longPressLabel != null) {
            Text(
                text = key.longPressLabel,
                color = DevKeyTheme.keyHint,
                fontSize = DevKeyTheme.keyHintSize,
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
 * Get the background color for a key based on its type.
 */
private fun getKeyColor(key: KeyData): Color = when (key.type) {
    KeyType.LETTER -> DevKeyTheme.keyFill
    KeyType.NUMBER -> DevKeyTheme.keyFill
    KeyType.MODIFIER -> when (key.primaryCode) {
        Keyboard.KEYCODE_SHIFT -> DevKeyTheme.keyFill
        KEYCODE_CTRL_LEFT -> DevKeyTheme.ctrlKeyFill
        KEYCODE_ALT_LEFT -> DevKeyTheme.altKeyFill
        else -> DevKeyTheme.keyFill
    }
    KeyType.ACTION -> when {
        key.primaryLabel == "Del" -> DevKeyTheme.backspaceKeyFill
        key.primaryLabel == "Enter" -> DevKeyTheme.enterKeyFill
        else -> DevKeyTheme.keyFill
    }
    KeyType.ARROW -> DevKeyTheme.arrowKeyFill
    KeyType.SPECIAL -> when {
        key.primaryLabel == "Esc" -> DevKeyTheme.escKeyFill
        key.primaryLabel == "Tab" -> DevKeyTheme.tabKeyFill
        else -> DevKeyTheme.keyFill
    }
    KeyType.SPACEBAR -> DevKeyTheme.spaceKeyFill
}

/**
 * Get the active (modifier engaged) background color.
 */
private fun getActiveKeyColor(key: KeyData): Color = when (key.primaryCode) {
    Keyboard.KEYCODE_SHIFT -> DevKeyTheme.shiftActiveKeyFill
    KEYCODE_CTRL_LEFT -> DevKeyTheme.ctrlActiveKeyFill
    KEYCODE_ALT_LEFT -> DevKeyTheme.altActiveKeyFill
    else -> DevKeyTheme.keyFill
}

/**
 * Map a keycode to a ModifierType.
 */
private fun getModifierType(code: Int): ModifierType? = when (code) {
    Keyboard.KEYCODE_SHIFT -> ModifierType.SHIFT
    KEYCODE_CTRL_LEFT -> ModifierType.CTRL
    KEYCODE_ALT_LEFT -> ModifierType.ALT
    else -> null
}
