package dev.devkey.keyboard.ui.welcome

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.devkey.keyboard.InputLanguageSelection
import dev.devkey.keyboard.ui.settings.DevKeySettingsActivity
import dev.devkey.keyboard.ui.theme.DevKeyTheme

/**
 * Modern Compose-based welcome/setup screen for DevKey.
 * Replaces the legacy setup wizard.
 *
 * Shows 3 setup steps with live completion status:
 * 1. Enable DevKey in system settings
 * 2. Set DevKey as default keyboard
 * 3. Configure languages (optional)
 */
class DevKeyWelcomeActivity : ComponentActivity() {

    private var isEnabled by mutableStateOf(false)
    private var isDefault by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Observe lifecycle to refresh status on resume
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        })

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                WelcomeScreen(
                    isEnabled = isEnabled,
                    isDefault = isDefault,
                    onEnableClick = { openInputMethodSettings() },
                    onSetDefaultClick = { showInputMethodPicker() },
                    onLanguagesClick = { openLanguageSelection() },
                    onSettingsClick = { openSettings() }
                )
            }
        }
    }

    private fun refreshStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        // Check if DevKey is enabled
        isEnabled = try {
            imm.enabledInputMethodList.any {
                it.packageName == packageName
            }
        } catch (e: Exception) {
            false
        }

        // Check if DevKey is the default keyboard
        isDefault = try {
            val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            defaultIme?.startsWith("$packageName/") == true
        } catch (e: Exception) {
            // Permission denied on some API levels
            false
        }
    }

    private fun openInputMethodSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun showInputMethodPicker() {
        if (Build.VERSION.SDK_INT >= 33) {
            // showInputMethodPicker() silently no-ops from non-IME activities on API 33+
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            Toast.makeText(this, "Select DevKey from the list", Toast.LENGTH_LONG).show()
        } else {
            val imm = getSystemService(InputMethodManager::class.java)
            imm.showInputMethodPicker()
        }
    }

    private fun openLanguageSelection() {
        startActivity(Intent(this, InputLanguageSelection::class.java))
    }

    private fun openSettings() {
        startActivity(Intent(this, DevKeySettingsActivity::class.java))
    }
}

@Composable
private fun WelcomeScreen(
    isEnabled: Boolean,
    isDefault: Boolean,
    onEnableClick: () -> Unit,
    onSetDefaultClick: () -> Unit,
    onLanguagesClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevKeyTheme.keyboardBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Title
        Text(
            text = "DevKey",
            color = DevKeyTheme.keyText,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Power-user keyboard for Android",
            color = DevKeyTheme.settingsDescriptionColor,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Setup steps
        SetupStep(
            stepNumber = 1,
            title = "Enable DevKey",
            description = "Turn on DevKey in your device's keyboard settings",
            isComplete = isEnabled,
            onClick = onEnableClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        SetupStep(
            stepNumber = 2,
            title = "Set as Default",
            description = "Choose DevKey as your active keyboard",
            isComplete = isDefault,
            onClick = onSetDefaultClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        SetupStep(
            stepNumber = 3,
            title = "Languages (Optional)",
            description = "Configure additional input languages",
            isComplete = false,
            isOptional = true,
            onClick = onLanguagesClick
        )

        Spacer(modifier = Modifier.weight(1f))

        // Settings button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DevKeyTheme.keyFill)
                .clickable { onSettingsClick() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Settings",
                color = DevKeyTheme.keyText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SetupStep(
    stepNumber: Int,
    title: String,
    description: String,
    isComplete: Boolean,
    isOptional: Boolean = false,
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
        // Status indicator
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isComplete) Color(0xFF4CAF50) else DevKeyTheme.keyboardBackground
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isComplete) "\u2713" else stepNumber.toString(),
                color = if (isComplete) Color.White else DevKeyTheme.settingsDescriptionColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = DevKeyTheme.keyText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                if (isOptional) {
                    Text(
                        text = "Optional",
                        color = DevKeyTheme.settingsDescriptionColor,
                        fontSize = 11.sp
                    )
                }
            }
            Text(
                text = description,
                color = DevKeyTheme.settingsDescriptionColor,
                fontSize = 13.sp
            )
        }

        // Arrow indicator
        Text(
            text = "\u203A",
            color = DevKeyTheme.settingsDescriptionColor,
            fontSize = 24.sp
        )
    }
}
