package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

/**
 * Reusable sub-screen header with back navigation.
 */
@Composable
internal fun SubScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DevKeyThemeColors.kbBg)
            .padding(horizontal = DevKeyThemeDimensions.settingsRowPadH, vertical = DevKeyThemeDimensions.settingsRowPadVLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\u2190",
            color = DevKeyThemeColors.settingsCategoryColor,
            fontSize = DevKeyThemeTypography.fontSettingsScreenTitle,
            modifier = Modifier
                .clickable { onBack() }
                .padding(end = DevKeyThemeDimensions.settingsBackArrowPadEnd)
        )
        Text(
            text = title,
            color = DevKeyThemeColors.keyText,
            fontSize = DevKeyThemeTypography.fontSettingsScreenTitle,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Reusable sub-screen wrapper that provides the standard Scaffold + Column + SubScreenHeader
 * + LazyColumn structure used by all settings sub-screens.
 */
@Composable
internal fun SettingsSubScreen(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Scaffold(containerColor = DevKeyThemeColors.kbBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DevKeyThemeColors.kbBg)
        ) {
            SubScreenHeader(title = title, onBack = onBack)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DevKeyThemeColors.kbBg),
                content = content
            )
        }
    }
}
