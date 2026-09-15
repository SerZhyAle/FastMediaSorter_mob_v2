package com.sza.fastmediasorter.broadcast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons

object BroadcastNotificationFactory {

    const val CHANNEL_ID = "broadcast_channel"

    /** [stopIntent] must address the service that owns the notification, or its stop action stops nothing. */
    fun createNotification(
        context: Context,
        stopIntent: Intent = Intent(context, BroadcastCaptureService::class.java).apply {
            action = BroadcastCaptureService.ACTION_STOP
        },
        @StringRes titleRes: Int = R.string.broadcast_notification_title,
        @StringRes textRes: Int = R.string.broadcast_notification_text,
    ): Notification {
        ensureChannelCreated(context)

        val stopPendingIntent = PendingIntent.getService(
            context,
            stopIntent.component?.className.hashCode(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(textRes))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_stop,
                context.getString(R.string.broadcast_action_stop),
                stopPendingIntent
            )
            .build()
    }

    private fun ensureChannelCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.broadcast_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.broadcast_channel_description)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
