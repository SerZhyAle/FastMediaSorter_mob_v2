package com.sza.fastmediasorter.wear.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sza.fastmediasorter.wear.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2087: posts a system notification on the watch when a deferred upload to a remote resource fails.
 * Success remains silent because it was already reported inline on the watch's result row at send time.
 */
@Singleton
class WearUploadOutcomeNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun notifyUploadFailed(fileName: String, destination: String): Boolean {
        if (!canPostNotification()) {
            Timber.w("WearUploadOutcomeNotifier: notification permission denied or disabled")
            return false
        }

        createNotificationChannel()

        val title = context.getString(R.string.wear_upload_outcome_failed_title)
        val text = context.getString(R.string.wear_upload_outcome_failed_text, fileName, destination)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_app_logo)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(WearNotificationIds.UPLOAD_OUTCOME, notification)
        return true
    }

    private fun canPostNotification(): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.areNotificationsEnabled()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wear_upload_outcome_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "wear_upload_outcome"
    }
}
