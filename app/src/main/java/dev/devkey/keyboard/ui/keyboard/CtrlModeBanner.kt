package dev.devkey.keyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

/**
 * Ctrl Mode banner displayed when the Ctrl key is held down.
 *
 * Shows a semi-transparent blue/purple banner with instructional text.
 * The AnimatedVisibility wrapper is applied in DevKeyKeyboard.kt, not here.
 */
@Composable
fun CtrlModeBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DevKeyThemeDimensions.ctrlModeBannerHeight)
            .background(DevKeyThemeColors.ctrlModeBannerBg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "CTRL MODE \u2014 tap a key for shortcut",
            color = DevKeyThemeColors.keyText,
            fontSize = DevKeyThemeTypography.fontCtrlModeBanner
        )
    }
}
