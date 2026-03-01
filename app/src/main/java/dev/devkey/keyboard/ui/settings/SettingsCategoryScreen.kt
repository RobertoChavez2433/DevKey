package dev.devkey.keyboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Top-level settings screen showing category tiles.
 * Each tile navigates to a sub-screen with the actual settings for that category.
 */
@Composable
fun SettingsCategoryScreen(
    onNavigate: (SettingsNav) -> Unit
) {
    val categories = listOf(
        CategoryItem("Keyboard View", "Height, layout, hints, compact mode", SettingsNav.KEYBOARD_VIEW),
        CategoryItem("Key Behavior", "Caps lock, chording, Ctrl overrides", SettingsNav.KEY_BEHAVIOR),
        CategoryItem("Actions", "Swipe gestures, volume key bindings", SettingsNav.ACTIONS),
        CategoryItem("Feedback", "Haptics, sound, key popup", SettingsNav.FEEDBACK),
        CategoryItem("Prediction & Autocorrect", "Suggestion engine, quick fixes", SettingsNav.PREDICTION),
        CategoryItem("Macros", "Display mode, macro manager", SettingsNav.MACROS),
        CategoryItem("Voice Input", "Model selection, auto-stop timeout", SettingsNav.VOICE_INPUT),
        CategoryItem("Command Mode", "Terminal detection, pinned apps", SettingsNav.COMMAND_MODE),
        CategoryItem("Backup", "Export and import data", SettingsNav.BACKUP),
        CategoryItem("About", "Version info", SettingsNav.ABOUT)
    )

    Scaffold(
        containerColor = DevKeyTheme.keyboardBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DevKeyTheme.keyboardBackground)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Settings",
                        color = DevKeyTheme.keyText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(categories, key = { it.nav.name }) { category ->
                CategoryTile(
                    title = category.title,
                    description = category.description,
                    onClick = { onNavigate(category.nav) }
                )
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CategoryTile(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DevKeyTheme.keyFill)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = DevKeyTheme.keyText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = DevKeyTheme.settingsDescriptionColor,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "\u203A",
            color = DevKeyTheme.settingsDescriptionColor,
            fontSize = 24.sp
        )
    }
}

private data class CategoryItem(
    val title: String,
    val description: String,
    val nav: SettingsNav
)
