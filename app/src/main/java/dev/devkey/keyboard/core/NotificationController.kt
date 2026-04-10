package dev.devkey.keyboard.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.NotificationReceiver
import dev.devkey.keyboard.R

internal class NotificationController(private val context: Context, private val ime: LatinIME) {
    private var notificationReceiver: NotificationReceiver? = null

    fun setNotification(visible: Boolean) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (visible && notificationReceiver == null) {
            createChannel()
            notificationReceiver = NotificationReceiver(ime)
            val pFilter = IntentFilter(NotificationReceiver.ACTION_SHOW).apply {
                addAction(NotificationReceiver.ACTION_SETTINGS)
            }
            context.registerReceiver(notificationReceiver, pFilter, Context.RECEIVER_NOT_EXPORTED)

            val contentIntent = PendingIntent.getBroadcast(
                context, 1, Intent(NotificationReceiver.ACTION_SHOW), PendingIntent.FLAG_IMMUTABLE
            )
            val configIntent = PendingIntent.getBroadcast(
                context, 2, Intent(NotificationReceiver.ACTION_SETTINGS), PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_hk_notification)
                .setColor(0xff220044.toInt())
                .setAutoCancel(false)
                .setTicker("Keyboard notification enabled.")
                .setContentTitle("Show DevKey")
                .setContentText("Select this to open the keyboard. Disable in settings.")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .addAction(R.drawable.icon_hk_notification, context.getString(R.string.notification_action_settings), configIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            NotificationManagerCompat.from(context).notify(ONGOING_ID, builder.build())
        } else if (notificationReceiver != null) {
            mgr.cancel(ONGOING_ID)
            context.unregisterReceiver(notificationReceiver)
            notificationReceiver = null
        }
    }

    fun destroy() {
        if (notificationReceiver != null) {
            context.unregisterReceiver(notificationReceiver)
            notificationReceiver = null
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.notification_channel_description) }
            (context.getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "DevKey"
        private const val ONGOING_ID = 1001
    }
}
