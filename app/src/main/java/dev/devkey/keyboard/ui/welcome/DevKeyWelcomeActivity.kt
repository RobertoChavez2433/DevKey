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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.devkey.keyboard.InputLanguageSelection
import dev.devkey.keyboard.ui.settings.DevKeySettingsActivity

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
