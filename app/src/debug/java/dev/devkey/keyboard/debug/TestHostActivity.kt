package dev.devkey.keyboard.debug

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Debug-only test host activity with a single EditText that the Phase 3
 * E2E harness uses as a reliable focus target.
 *
 * WHY: Prior Phase 3 gate used the Chrome URL bar as the focus host, which was
 *      unreliable — Chrome can navigate away on typed input, tab to another
 *      activity, or lose focus to OS affordances. This activity is owned by
 *      the DevKey build, so the harness can always bring it to the front with:
 *
 *          adb shell am start -n dev.devkey.keyboard/.debug.TestHostActivity
 *
 *      It automatically requests the soft keyboard on start, renders a single
 *      tall EditText, and swallows navigation events. The EditText ID is
 *      stable across builds so ADB UIAutomator queries can locate it.
 *
 * FROM SPEC: Phase 3 gate requires a deterministic focus host so test_smoke,
 *            test_rapid, test_modifiers, test_next_word, etc. produce events
 *            regardless of prior test state.
 *
 * IMPORTANT: DEBUG BUILD ONLY — this activity is declared in
 *            `app/src/debug/AndroidManifest.xml` and is not compiled into
 *            release APKs.
 */
class TestHostActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force the soft keyboard to open as soon as this activity is focused.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        @SuppressLint("SetTextI18n") // Debug-only test host label, not user-visible text
        val label = TextView(this).apply {
            text = "DevKey Test Host — EditText below"
            textSize = 14f
            gravity = Gravity.CENTER
        }

        val edit = EditText(this).apply {
            id = ID_TEST_EDIT
            hint = "type here"
            // Multi-line so Enter doesn't close the IME or submit anything.
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 6
            gravity = Gravity.TOP or Gravity.START
            isSingleLine = false
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            requestFocus()
        }

        root.addView(label)
        root.addView(edit)
        setContentView(root)
    }

    private val clearTextReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            findViewById<EditText>(ID_TEST_EDIT)?.let {
                it.text.clear()
                it.requestFocus()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-request focus on resume in case a layout broadcast caused the
        // keyboard to recompose and drop focus.
        findViewById<EditText>(ID_TEST_EDIT)?.requestFocus()
        ContextCompat.registerReceiver(
            this,
            clearTextReceiver,
            IntentFilter("dev.devkey.keyboard.debug.CLEAR_EDIT_TEXT"),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(clearTextReceiver) } catch (_: IllegalArgumentException) {}
    }

    companion object {
        // Stable resource ID so UIAutomator can find the EditText by id.
        // Not an R.id reference because we build the view hierarchy in code.
        const val ID_TEST_EDIT = 0x7f0a9001
    }
}
