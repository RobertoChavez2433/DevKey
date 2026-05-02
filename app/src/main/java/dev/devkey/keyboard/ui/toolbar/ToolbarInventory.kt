package dev.devkey.keyboard.ui.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import dev.devkey.keyboard.debug.DevKeyLogger
import dev.devkey.keyboard.debug.KeyMapGenerator

@Composable
internal fun Modifier.toolbarInventory(
    id: String,
    action: String,
    longAction: String? = null,
    isActive: Boolean = false,
    isEnabled: Boolean = true
): Modifier {
    val context = LocalContext.current
    val view = LocalView.current
    val shouldLog = remember(context) { KeyMapGenerator.isDebugBuild(context) }
    if (!shouldLog) return this

    return this.onGloballyPositioned { coordinates ->
        val viewLocation = IntArray(2)
        view.getLocationOnScreen(viewLocation)
        val rootBounds = coordinates.boundsInRoot()
        val bounds = Rect(
            left = viewLocation[0] + rootBounds.left,
            top = viewLocation[1] + rootBounds.top,
            right = viewLocation[0] + rootBounds.right,
            bottom = viewLocation[1] + rootBounds.bottom
        )
        if (bounds.width <= 0f || bounds.height <= 0f) return@onGloballyPositioned
        DevKeyLogger.ui(
            "toolbar_control_visible",
            toolbarInventoryPayload(
                id = id,
                action = action,
                longAction = longAction,
                isActive = isActive,
                isEnabled = isEnabled,
                bounds = bounds
            )
        )
    }
}

internal fun logToolbarAction(id: String, action: String) {
    DevKeyLogger.ui(
        "toolbar_action",
        mapOf(
            "id" to id,
            "action" to action
        )
    )
}

private fun toolbarInventoryPayload(
    id: String,
    action: String,
    longAction: String?,
    isActive: Boolean,
    isEnabled: Boolean,
    bounds: Rect
): Map<String, Any?> =
    mapOf(
        "id" to id,
        "action" to action,
        "long_action" to longAction,
        "active" to isActive,
        "enabled" to isEnabled,
        "x" to bounds.left.toInt(),
        "y" to bounds.top.toInt(),
        "width" to bounds.width.toInt(),
        "height" to bounds.height.toInt(),
        "center_x" to bounds.center.x.toInt(),
        "center_y" to bounds.center.y.toInt()
    )
