package dev.devkey.keyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.devkey.keyboard.Keyboard
import dev.devkey.keyboard.core.KeyPressLogger
import dev.devkey.keyboard.debug.DevKeyLogger
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
 *
 * When a key's longPressCodes has ≥2 entries, a Compose Popup opens on long-press
 * offering all candidates. The user drags to a candidate and releases to select it.
 * Releasing without dragging (or dragging back to origin) selects the default (index 0).
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
    val density = LocalDensity.current

    var isPressed by remember { mutableStateOf(false) }

    // Multi-char long-press popup state
    var popupCodes by remember { mutableStateOf<List<Int>?>(null) }
    var popupActiveIndex by remember { mutableIntStateOf(0) }

    // Track this key's size so the popup can be centered horizontally.
    var keySize by remember { mutableStateOf(IntSize.Zero) }

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

    // Display label — SwiftKey parity: letter keys render uppercase at rest and
    // lowercase only if some alternate composition ever requires it. Reference:
    // .claude/test-flows/swiftkey-reference/compact-dark-cropped.png.
    val displayLabel = when {
        key.type == KeyType.LETTER -> key.primaryLabel.uppercase()
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

    // Whether this key uses the multi-char popup on long-press
    val hasMultiPopup = !isModifierKey &&
            key.longPressCodes != null &&
            key.longPressCodes.size >= 2

    Box(
        modifier = modifier
            .testTag("key_${key.primaryCode}")
            .scale(scale)
            .clip(RoundedCornerShape(DevKeyTheme.keyRadius))
            .background(backgroundColor)
            .onGloballyPositioned { coords ->
                keySize = coords.size
            }
            .pointerInput(key.primaryCode, key.type, key.longPressCode, key.isRepeatable, hasMultiPopup) {
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
                } else if (hasMultiPopup) {
                    // Multi-char popup path: track pointer position across the gesture
                    // so we can highlight candidates as the user drags.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        onKeyPress(key.primaryCode)
                        KeyPressLogger.logKeyDown(key.primaryLabel, key.primaryCode, key.type.name)

                        var longPressTriggered = false

                        // Launch the long-press timer
                        val longPressJob = coroutineScope.launch {
                            delay(300L)
                            // Long-press fires: open the popup
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            KeyPressLogger.logLongPress(key.primaryLabel, key.primaryCode, key.longPressCode)
                            popupCodes = key.longPressCodes
                            popupActiveIndex = 0
                            longPressTriggered = true
                        }

                        // Poll for pointer move / up events
                        try {
                            while (true) {
                                val event: PointerEvent = awaitPointerEvent(PointerEventPass.Main)
                                val pointer = event.changes.firstOrNull() ?: break

                                if (!pointer.pressed) {
                                    // Finger lifted
                                    pointer.consume()
                                    break
                                }

                                // If popup is open, compute active index from drag distance.
                                // pointer.position is composable-local. The popup row is centered
                                // over the key, so its left edge in composable-local coords is:
                                //   popupLeftEdge = (keyWidth - totalPopupWidth) / 2
                                // candidate index = (fingerX - popupLeftEdge) / cellWidth
                                val codes = popupCodes
                                if (codes != null && codes.isNotEmpty()) {
                                    val cellWidthPx = with(density) {
                                        (DevKeyTheme.popupCellPadH * 2 + DevKeyTheme.popupCellGlyphWidth).toPx()
                                    }
                                    val totalPopupWidthPx = cellWidthPx * codes.size
                                    val popupLeftEdgePx = (keySize.width - totalPopupWidthPx) / 2f
                                    val fingerX = pointer.position.x
                                    val indexFromLeft = ((fingerX - popupLeftEdgePx) / cellWidthPx).toInt()
                                    popupActiveIndex = indexFromLeft.coerceIn(0, codes.size - 1)
                                }

                                pointer.consume()
                            }
                        } finally {
                            longPressJob.cancel()
                            isPressed = false
                        }

                        val codes = popupCodes
                        if (longPressTriggered && codes != null) {
                            // Dispatch selected candidate
                            val selectedCode = codes[popupActiveIndex]
                            DevKeyLogger.text(
                                "long_press_fired",
                                mapOf(
                                    "label" to key.primaryLabel,
                                    "code" to key.primaryCode,
                                    "lp_code" to selectedCode,
                                )
                            )
                            onKeyAction(selectedCode)
                            // Clear popup state
                            popupCodes = null
                            popupActiveIndex = 0
                        } else if (!longPressTriggered) {
                            // Short tap — dispatch primary code
                            KeyPressLogger.logKeyTap(key.primaryLabel, key.primaryCode)
                            onKeyAction(key.primaryCode)
                        }
                        onKeyRelease(key.primaryCode)
                        KeyPressLogger.logKeyUp(key.primaryLabel, key.primaryCode)
                    }
                } else {
                    // Non-modifier, non-popup keys: handle press, long-press, repeat
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
                                    DevKeyLogger.text(
                                        "long_press_fired",
                                        mapOf(
                                            "label" to key.primaryLabel,
                                            "code" to key.primaryCode,
                                            "lp_code" to key.longPressCode,
                                        )
                                    )
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

        // Center-top hint for long-press (respects hint mode preference, hidden in Ctrl Mode for letter/number keys).
        // WHY: SwiftKey renders the long-press hint glyph horizontally centered above the
        //      primary letter (not top-left, not top-right). Direct pixel comparison against
        //      .claude/test-flows/swiftkey-reference/compact-dark-cropped.png.
        if (showHints && key.longPressLabel != null && !(ctrlHeld && (key.type == KeyType.LETTER || key.type == KeyType.NUMBER))) {
            Text(
                text = key.longPressLabel,
                color = if (hintBright) DevKeyTheme.keyText.copy(alpha = 0.7f) else DevKeyTheme.keyHint,
                fontSize = DevKeyTheme.fontKeyHint,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = DevKeyTheme.hintLabelPadTop)
            )
        }

        // Locked indicator dot for modifier keys
        if (isModifierLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = DevKeyTheme.lockDotPadBottom)
                    .size(DevKeyTheme.lockDotSize)
                    .clip(CircleShape)
                    .background(DevKeyTheme.keyText)
            )
        }

        // Multi-char long-press popup
        val currentPopupCodes = popupCodes
        if (currentPopupCodes != null) {
            LongPressPopup(
                codes = currentPopupCodes,
                activeIndex = popupActiveIndex,
                keySize = keySize,
                density = density
            )
        }
    }
}

/**
 * Renders the multi-char long-press candidate popup anchored above the key.
 *
 * Displayed as a horizontal Row of candidate cells. The cell at [activeIndex]
 * is highlighted using [DevKeyTheme.popupSelectedBg]. All other cells use
 * [DevKeyTheme.popupBg].
 *
 * The popup is offset upward by its own estimated height plus a small gap
 * so it appears above the key surface.
 */
@Composable
private fun LongPressPopup(
    codes: List<Int>,
    activeIndex: Int,
    keySize: IntSize,
    density: Density
) {
    // Estimate cell width to compute the popup offset that centers it over the key.
    val cellWidthDp: Dp = DevKeyTheme.popupCellPadH * 2 + DevKeyTheme.popupCellGlyphWidth
    val popupHeightDp: Dp = DevKeyTheme.popupCellPadV * 2 + DevKeyTheme.popupCellGlyphHeight
    val gapDp: Dp = DevKeyTheme.popupAnchorGap

    val totalPopupWidthPx = with(density) { (cellWidthDp * codes.size).roundToPx() }
    val keyWidthPx = keySize.width
    // Center the popup horizontally over the key
    val xOffsetPx = (keyWidthPx - totalPopupWidthPx) / 2
    val yOffsetPx = with(density) { -(popupHeightDp + gapDp).roundToPx() }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(xOffsetPx, yOffsetPx),
        properties = PopupProperties(focusable = false)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(DevKeyTheme.popupRadius))
                .background(DevKeyTheme.popupBg)
        ) {
            codes.forEachIndexed { index, code ->
                val isSelected = index == activeIndex
                val bgColor = if (isSelected) DevKeyTheme.popupSelectedBg else Color.Transparent
                val textColor = if (isSelected) DevKeyTheme.popupSelectedText else DevKeyTheme.popupCandidateText
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(DevKeyTheme.popupRadius))
                        .background(bgColor)
                        .padding(
                            horizontal = DevKeyTheme.popupCellPadH,
                            vertical = DevKeyTheme.popupCellPadV
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val label = if (code > 0) code.toChar().toString() else "?"
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = DevKeyTheme.fontPopupCandidate
                    )
                }
            }
        }
    }
}

/**
 * Get the background and text colors for a key based on its type and keycode.
 */
private fun getKeyColors(key: KeyData): Pair<Color, Color> {
    // WHY: SwiftKey parity — compact-dark-findings.md §"Critical observation":
    //      Utility/modifier keys (shift, backspace, 123, emoji, mic, comma, period,
    //      enter) use the SAME fill color as letter keys — NOT a darker or distinct
    //      "special" shade. All key types collapse to (keyBg, keyText) except the
    //      active-state highlight which remains on modifiers via getActiveKeyColor.
    //      Previous teal tint on modifier keys diverged from the SwiftKey reference
    //      the rest of the theme is calibrated against.
    return DevKeyTheme.keyBg to DevKeyTheme.keyText
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
