package com.sza.fastmediasorter.data.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.core.share.ShareTargetIconResolver
import com.sza.fastmediasorter.core.share.ShareTargetRegistry
import com.sza.fastmediasorter.ui.share.WearSendToDispatcherActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2142: offers the owner the «Send to..» action for a file the watch sent here to be sent on.
 *
 * A notification rather than a launch, for the platform reason [OpenOnPhoneNotifier] already answers
 * the same way: the file arrives in a background `WearableListenerService`, and a background process
 * has not been allowed to start an activity since Android 10. Strategic 6 question 5 settles this as
 * the mechanism, and with it what the watch is told - the errand is queued for a tap, never reported
 * as a send that already reached an external application.
 */
@Singleton
class WearSendToNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val registry: ShareTargetRegistry,
    private val iconResolver: ShareTargetIconResolver
) {

    /**
     * Posts the offer for [savedPath] and reports whether the owner will actually see it.
     *
     * A boolean rather than `Unit` because the watch has to be able to say why nothing happened: a
     * phone with notifications switched off takes the file and shows nothing, and telling the owner
     * to bring the phone closer would send them after the wrong fix (strategic 11 criterion 9).
     */
    fun notifyPendingSend(fileName: String, savedPath: String, receiverId: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Timber.i("Send to from watch: POST_NOTIFICATIONS not granted, nothing was offered")
            return false
        }
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.wear_send_to_notification_title))
            .setContentText(context.getString(R.string.wear_send_to_notification_text, fileName, labelOf(receiverId)))
            .setContentIntent(sendPendingIntent(savedPath, receiverId))
            .setAutoCancel(true)
            .build()
        return runCatching { manager.notify(NotificationIds.WEAR_SEND_TO_FROM_WATCH, notification) }
            .onFailure {
                // Revoked between the check above and the post; the watch is told the same thing it
                // would have been told had the check itself failed.
                Timber.i(it, "Send to from watch: notification suppressed - POST_NOTIFICATIONS not granted")
            }
            .isSuccess
    }

    /**
     * The receiver named the way this phone's own menu names it - the installed application's label
     * where there is one, its declared title otherwise. Resolved here rather than carried across the
     * bridge so the notification is worded in the phone's language, which is the one being read.
     */
    private fun labelOf(receiverId: String): CharSequence {
        val target = registry.all().firstOrNull { it.id == receiverId }
            ?: return context.getString(R.string.share_to_menu_title)
        return iconResolver.resolveLabel(target) ?: context.getString(target.titleRes)
    }

    private fun sendPendingIntent(savedPath: String, receiverId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            // Separates one pending errand from another; without it a second file would reuse the
            // first intent and hand the wrong file to the receiver.
            savedPath.hashCode(),
            WearSendToDispatcherActivity.intent(context, savedPath, receiverId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wear_send_to_channel_name),
                // DEFAULT rather than LOW: the owner is holding the watch and waiting for the phone
                // to react, so a silent entry in the shade would read as the action having failed.
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private companion object {
        const val CHANNEL_ID = "wear_send_to"
    }
}
