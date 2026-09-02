package com.sza.fastmediasorter.data.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.ui.player.dispatch.StandalonePlayerDispatcherActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2004: announces a file the watch asked this phone to show, when the phone was not in front.
 *
 * A notification rather than a launch: the request arrives in a background
 * `WearableListenerService`, and a background process has not been allowed to start an activity since
 * Android 10 (strategic ADR-3). The mirror of this decision is `WearLogReportReceiver`, which answers
 * the same platform limit the same way and is the shape followed here.
 */
@Singleton
class OpenOnPhoneNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Posts the notice for [target] and reports whether the user will actually see it.
     *
     * A boolean rather than `Unit` because the watch has to be able to say why nothing happened: a
     * phone with notifications switched off answers the request and still shows nothing, and telling
     * the user to bring the phone closer would send them after the wrong fix (strategic 11
     * criterion 9). [token] is not read - it only keeps two pending opens of different files from
     * collapsing onto one `PendingIntent`.
     */
    fun notifyPendingOpen(token: String, displayName: String, target: Uri): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Timber.i("Open on phone: POST_NOTIFICATIONS not granted, nothing was shown")
            return false
        }
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.open_on_phone_notification_title))
            .setContentText(context.getString(R.string.open_on_phone_notification_text, displayName))
            .setContentIntent(openPendingIntent(token, target))
            .setAutoCancel(true)
            .build()
        return runCatching { manager.notify(NotificationIds.WEAR_OPEN_ON_PHONE, notification) }
            .onFailure {
                // Revoked between the check above and the post; the watch is told the same thing it
                // would have been told had the check itself failed.
                Timber.i(it, "Open on phone: notification suppressed - POST_NOTIFICATIONS not granted")
            }
            .isSuccess
    }

    /**
     * The dispatcher rather than a viewer: it reads the type off the URI and forwards to the surface
     * that renders that family, so this class never has to know which one a file belongs to.
     */
    private fun openPendingIntent(token: String, target: Uri): PendingIntent {
        val view = Intent(context, StandalonePlayerDispatcherActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = target
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return PendingIntent.getActivity(
            context,
            // The request code separates one file's pending open from another's; without it the
            // second request would reuse the first intent and open the wrong file.
            token.hashCode(),
            view,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.open_on_phone_channel_name),
                // DEFAULT rather than LOW: the user is holding the watch and waiting for the phone to
                // react, so a silent entry in the shade would read as the action having failed.
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private companion object {
        const val CHANNEL_ID = "wear_open_on_phone"
    }
}
