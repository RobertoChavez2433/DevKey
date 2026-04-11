package dev.devkey.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.devkey.keyboard.ui.settings.DevKeySettingsActivity

class NotificationReceiver(
    private val showSelf: () -> Unit
) : BroadcastReceiver() {

    init {
        Log.i(TAG, "NotificationReceiver created")
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "NotificationReceiver.onReceive called, action=$action")

        when (action) {
            ACTION_SHOW -> showSelf()
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
