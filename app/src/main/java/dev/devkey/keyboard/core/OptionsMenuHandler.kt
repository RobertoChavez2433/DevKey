package dev.devkey.keyboard.core

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import dev.devkey.keyboard.R
import dev.devkey.keyboard.ui.settings.DevKeySettingsActivity

/**
 * Manages the IME options menu and settings launch.
 * Extracted from LatinIME to reduce god-class size.
 */
internal class OptionsMenuHandler(
    private val context: Context,
    private val resources: Resources,
    private val handleClose: () -> Unit,
    private val getImeWindow: () -> android.view.Window?
) {
    private var mOptionsDialog: AlertDialog? = null

    fun isShowingOptionDialog(): Boolean =
        mOptionsDialog != null && mOptionsDialog!!.isShowing

    fun onOptionKeyPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            launchSettings(DevKeySettingsActivity::class.java)
        } else {
            if (!isShowingOptionDialog()) showOptionsMenu()
        }
    }

    fun onOptionKeyLongPressed() {
        if (!isShowingOptionDialog()) showInputMethodPicker()
    }

    fun dismissDialog() {
        if (mOptionsDialog != null && mOptionsDialog!!.isShowing) {
            mOptionsDialog!!.dismiss()
            mOptionsDialog = null
        }
    }

    fun launchSettings() = launchSettings(DevKeySettingsActivity::class.java)

    fun launchSettings(settingsClass: Class<out Activity>) {
        handleClose()
        val intent = Intent().apply {
            setClass(context, settingsClass)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun showInputMethodPicker() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showInputMethodPicker()
    }

    private fun showOptionsMenu() {
        val builder = AlertDialog.Builder(context)
        builder.setCancelable(true)
        builder.setIcon(R.drawable.ic_dialog_keyboard)
        builder.setNegativeButton(android.R.string.cancel, null)
        val itemSettings = context.getString(R.string.english_ime_settings)
        val itemInputMethod = context.getString(R.string.selectInputMethod)
        builder.setItems(arrayOf<CharSequence>(itemInputMethod, itemSettings)) { di, position ->
            di.dismiss()
            when (position) {
                POS_SETTINGS -> launchSettings()
                POS_METHOD -> showInputMethodPicker()
            }
        }
        builder.setTitle(resources.getString(R.string.english_ime_input_options))
        mOptionsDialog = builder.create()
        val window = mOptionsDialog!!.window!!
        val lp = window.attributes
        val imeWindow = getImeWindow()
        if (imeWindow?.decorView != null) {
            lp.token = imeWindow.decorView.windowToken
        }
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        window.attributes = lp
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        mOptionsDialog!!.show()
    }

    companion object {
        private const val POS_SETTINGS = 1
        private const val POS_METHOD = 0
    }
}
