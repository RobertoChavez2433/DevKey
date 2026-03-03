package dev.devkey.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.inputmethod.InputMethodManager
import dev.devkey.keyboard.ui.settings.DevKeySettingsActivity

class NotificationReceiver(
    private val mIME: LatinIME
) : BroadcastReceiver() {

    init {
        Log.i(TAG, "NotificationReceiver created, ime=$mIME")
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "NotificationReceiver.onReceive called, action=$action")

        when (action) {
            ACTION_SHOW -> mIME.requestShowSelf(InputMethodManager.SHOW_FORCED)
            ACTION_SETTINGS -> {
                val intent = Intent(context, DevKeySettingsActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    companion object {
        const val TAG = "DevKey/Notification"
        const val ACTION_SHOW = "dev.devkey.keyboard.SHOW"
        const val ACTION_SETTINGS = "dev.devkey.keyboard.SETTINGS"
    }
}
