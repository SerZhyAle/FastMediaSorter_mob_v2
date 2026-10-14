package com.sza.fastmediasorter.core.notification

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
import com.sza.fastmediasorter.ui.exchange.ExchangeCastOfferActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4116: the receiver's question about a cast (contract DEVICE-EXCHANGE 7.8). A notification, because the offer
 * arrives on a background control stream and a background process may not start an activity since Android 10;
 * the user's tap on Accept is what opens the player.
 */
@Singleton
class ExchangeCastNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Asks whether to play [title] cast from [fromName]; false when notifications are not allowed. */
    fun ask(broadcastId: String, fromName: String?, title: String?, playUrl: String): Boolean =
        post(
            broadcastId = broadcastId,
            title = context.getString(R.string.exchange_cast_offer_title, fromName ?: unknownDevice()),
            text = title ?: context.getString(R.string.streams_device_broadcast_untitled),
            playUrl = playUrl,
            asking = true,
        )

    /** The play-at-once case: already accepted, so the only action left is opening the player. */
    fun accepted(broadcastId: String, fromName: String?, title: String?, playUrl: String): Boolean =
        post(
            broadcastId = broadcastId,
            title = context.getString(R.string.exchange_cast_started_title, fromName ?: unknownDevice()),
            text = title ?: context.getString(R.string.streams_device_broadcast_untitled),
            playUrl = playUrl,
            asking = false,
        )

    fun dismiss(broadcastId: String) {
        NotificationManagerCompat.from(context).cancel(idOf(broadcastId))
    }

    // S3155 pattern: a POST_NOTIFICATIONS check above and runCatching for a revocation between it and the post.
    @SuppressLint("MissingPermission")
    private fun post(broadcastId: String, title: String, text: String, playUrl: String, asking: Boolean): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Timber.i("ExchangeCastNotifier: notifications not allowed, the cast cannot be shown")
            return false
        }
        ensureChannel()
        val play = pendingIntent(broadcastId, playUrl, accept = true)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(play)
            .setAutoCancel(true)
        if (asking) {
            builder
                .addAction(NotificationIcons.STATUS_BAR, context.getString(R.string.exchange_cast_accept), play)
                .addAction(
                    NotificationIcons.STATUS_BAR,
                    context.getString(R.string.exchange_cast_decline),
                    pendingIntent(broadcastId, playUrl, accept = false),
                )
        }
        return runCatching { manager.notify(idOf(broadcastId), builder.build()) }
            .onFailure { Timber.i(it, "ExchangeCastNotifier: notification suppressed") }
            .isSuccess
    }

    private fun pendingIntent(broadcastId: String, playUrl: String, accept: Boolean): PendingIntent {
        val intent = ExchangeCastOfferActivity.intent(context, broadcastId, playUrl, accept)
        // One request code per offer and action, so Decline never reuses Accept's intent or another offer's.
        val requestCode = idOf(broadcastId) * 2 + if (accept) 1 else 0
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun unknownDevice() = context.getString(R.string.exchange_cast_unknown_device)

    private fun idOf(broadcastId: String) =
        NotificationIds.slotIn(NotificationIds.EXCHANGE_CAST, broadcastId.hashCode())

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.exchange_cast_channel_name),
                // HIGH: the caster waits for the answer and the server gives up after 60 s (item R).
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    private companion object {
        const val CHANNEL_ID = "exchange_cast_offers"
    }
}
