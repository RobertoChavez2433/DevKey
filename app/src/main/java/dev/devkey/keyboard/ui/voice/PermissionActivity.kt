package dev.devkey.keyboard.ui.voice

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Transparent activity that requests the RECORD_AUDIO runtime permission.
 *
 * Since InputMethodService cannot request permissions directly (it has no
 * Activity context), this lightweight transparent activity is launched to
 * handle the permission flow.
 *
 * Usage: Launch this activity from the IME, and check [onPermissionResult]
 * for the result.
 */
class PermissionActivity : ComponentActivity() {

    companion object {
        /** Callback for permission result. Set before launching. */
        var onPermissionResult: ((Boolean) -> Unit)? = null

        /** Broadcast action for permission result. */
        const val ACTION_PERMISSION_RESULT = "dev.devkey.keyboard.VOICE_PERMISSION"
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            onPermissionResult?.invoke(isGranted)
            onPermissionResult = null
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch permission request immediately
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
