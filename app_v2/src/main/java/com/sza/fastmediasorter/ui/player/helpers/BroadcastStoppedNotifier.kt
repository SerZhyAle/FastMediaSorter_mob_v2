package com.sza.fastmediasorter.ui.player.helpers

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.ui.player.MediaNotificationManager
import com.sza.fastmediasorter.ui.streams.StreamsActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S4609: the one line a broadcast left to the background audio service leaves behind when it cannot go on - a
 * password, a refused certificate, an ended or unreachable broadcast. The service has no window to ask from, so
 * the tap opens the Streams screen, which explains a refusal (S4160). An ended broadcast says so (LIVE-BROADCAST
 * item T, DEVICE-EXCHANGE item DY), and the Streams screen shows its ended dialog with Reconnect when it opens.
 */
class BroadcastStoppedNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // S3155 pattern: a POST_NOTIFICATIONS check first and runCatching for a revocation between it and the post.
    @SuppressLint("MissingPermission")
    fun show(channelTitle: String, ended: Boolean = false) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Timber.i("BroadcastStoppedNotifier: notifications not allowed, the stop cannot be shown")
            return
        }
        MediaNotificationManager.createNotificationChannel(context)
        val intent = Intent(context, StreamsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.getString(
            if (ended) R.string.s4874_broadcast_ended_text else R.string.s4609_broadcast_stopped_text,
        )
        val notification = NotificationCompat.Builder(context, MediaNotificationManager.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(channelTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Timber.i(it, "BroadcastStoppedNotifier: notification suppressed") }
    }

    private companion object {
        const val NOTIFICATION_ID = NotificationIds.BROADCAST_STOPPED_IN_BACKGROUND
    }
}
