package dev.devkey.keyboard.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

/**
 * Slim expand/collapse chevron row shown above the keyboard.
 *
 * When the toolbar is hidden (SwiftKey-parity default), this bar renders
 * a left-aligned down-chevron (`˅`) that the user can tap to reveal the
 * toolbar. When the toolbar is visible, the caller may optionally render
 * an up-chevron (`˄`) via [expanded] to collapse it back.
 *
 * WHY: Phase 3 parity task #30 — users need a way to toggle the toolbar
 *      on/off without diving into Settings. SwiftKey has exactly this
 *      control on the left edge of its keyboard.
 *
 * @param expanded When true, shows a collapse (up) chevron; otherwise expand (down).
 * @param onToggle Called when the user taps the chevron.
 */
@Composable
fun ToolbarChevronBar(
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DevKeyThemeDimensions.chevronRowHeight)
            .background(DevKeyThemeColors.kbBg)
            .toolbarInventory(
                id = "toolbar_chevron",
                action = "toggle_toolbar",
                isActive = expanded
            )
            .clickable {
                logToolbarAction("toolbar_chevron", "toggle_toolbar")
                onToggle()
            }
            .padding(horizontal = DevKeyThemeDimensions.chevronRowIconPad),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = if (expanded) "\u02C4" else "\u02C5",  // ˄ / ˅
            color = DevKeyThemeColors.keyHint,
            fontSize = DevKeyThemeTypography.fontKeyHint
        )
    }
}
