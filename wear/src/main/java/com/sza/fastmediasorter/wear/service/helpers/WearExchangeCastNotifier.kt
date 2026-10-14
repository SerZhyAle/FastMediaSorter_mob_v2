package com.sza.fastmediasorter.wear.service.helpers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.core.notification.NotificationIcons
import com.sza.fastmediasorter.wear.core.notification.WearNotificationIds
import com.sza.fastmediasorter.wear.service.WearExchangeCastActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "wear_exchange_cast_offer"

/**
 * Asks the owner about a `cast-offer` (contract DEVICE-EXCHANGE 7.8: the default asks). A heads-up with Listen and
 * Decline rather than a screen: the action tap is the interaction that lets the playback service start while the
 * app is in the background, the same exemption the listening request relies on. The question lapses with the
 * server's own 60 s answer window, after which the caster has already been told `timeout`.
 */
@Singleton
class WearExchangeCastNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** False when notifications are not allowed, so the caller declines instead of leaving the caster waiting. */
    fun ask(broadcastId: String, title: String?): Boolean {
        if (!canPostNotification()) return false
        val manager = notificationManager()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wear_exchange_cast_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title ?: context.getString(R.string.wear_exchange_cast_title))
            .setContentText(context.getString(R.string.wear_exchange_cast_text))
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setTimeoutAfter(ANSWER_WINDOW_MS)
            .addAction(0, context.getString(R.string.wear_exchange_cast_play), answer(broadcastId, true))
            .addAction(0, context.getString(R.string.wear_exchange_cast_decline), answer(broadcastId, false))
            .build()
        manager.notify(WearNotificationIds.EXCHANGE_CAST_OFFER, notification)
        return true
    }

    fun cancel() {
        notificationManager().cancel(WearNotificationIds.EXCHANGE_CAST_OFFER)
    }

    // FLAG_IMMUTABLE: nothing outside this process may rewrite which broadcast an answer is for.
    private fun answer(broadcastId: String, accepted: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (accepted) REQUEST_PLAY else REQUEST_DECLINE,
        WearExchangeCastActionReceiver.answerIntent(context, broadcastId, accepted),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notificationManager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun canPostNotification(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        /** Contract 7.8: the server answers the caster `timeout` after 60 s. */
        const val ANSWER_WINDOW_MS = 60_000L
        private const val REQUEST_PLAY = 4207
        private const val REQUEST_DECLINE = 4208
    }
}
