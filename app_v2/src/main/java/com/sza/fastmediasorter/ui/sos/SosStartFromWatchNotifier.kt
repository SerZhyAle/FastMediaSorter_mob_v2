package com.sza.fastmediasorter.ui.sos

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.domain.model.sos.SosMode
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3908: the way back to a running signal when the phone refused to raise it from the background.
 *
 * A watch start arrives in a background listener, and from API 31 the system may refuse the foreground
 * service that carries the siren. A tap on a notification is the exemption the platform grants: it
 * opens [SosActivity], and the window, now in the foreground, starts the service itself.
 *
 * HIGH importance rather than the signal channel's LOW: this entry is the whole of what the owner sees
 * of an emergency the watch already announced, so it has to arrive as a heads-up, not wait in the shade.
 */
@Singleton
class SosStartFromWatchNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // Same double guard as CameraSessionConsentNotifier: the enabled check above the post and the
    // runCatching that absorbs a revocation between the two. Lint accepts neither.
    @SuppressLint("MissingPermission")
    fun show(mode: SosMode) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Timber.w("SOS: notifications are off, the refused watch start cannot be offered to the owner")
            return
        }
        ensureChannel()
        val openPending = PendingIntent.getActivity(
            context,
            // Not 0: SosService's own notification opens the same activity with request code 0, and
            // FLAG_UPDATE_CURRENT on a shared token would overwrite the mode extra carried here.
            NotificationIds.SOS_START_FROM_WATCH,
            SosActivity.createIntent(context, mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.sos_watch_start_title))
            .setContentText(context.getString(R.string.sos_watch_start_text))
            .setContentIntent(openPending)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NotificationIds.SOS_START_FROM_WATCH, notification) }
            .onFailure { Timber.w(it, "SOS: the refused watch start notification was suppressed") }
    }

    /** Called by [SosActivity] on open, so the offer never outlives the window it leads to. */
    fun dismiss() {
        NotificationManagerCompat.from(context).cancel(NotificationIds.SOS_START_FROM_WATCH)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sos_watch_start_channel),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    private companion object {
        const val CHANNEL_ID = "sos_start_from_watch"
    }
}
