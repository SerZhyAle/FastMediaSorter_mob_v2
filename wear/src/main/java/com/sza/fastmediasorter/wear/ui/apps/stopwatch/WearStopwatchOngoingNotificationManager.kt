package com.sza.fastmediasorter.wear.ui.apps.stopwatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.sza.fastmediasorter.wear.MainActivity
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.core.notification.NotificationIcons
import com.sza.fastmediasorter.wear.core.notification.WearNotificationIds
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3529: Manages the Wear OS Ongoing Activity lifecycle for the watch stopwatch.
 *
 * Exposes active stopwatch execution to the watch face indicator and the Recent Apps
 * carousel via AndroidX Ongoing Activity API, fulfilling Wear OS Quality guideline Wear-OA.
 */
@Singleton
class WearStopwatchOngoingNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "wear_stopwatch_ongoing"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.wear_stopwatch_ongoing_channel_name)
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = name
                setShowBadge(false)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun showOngoing(startTimeElapsedRealtime: Long) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("route", "app/stopwatch")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                WearNotificationIds.STOPWATCH_ONGOING,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = context.getString(R.string.wear_stopwatch_ongoing_title)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setSmallIcon(NotificationIcons.STATUS_BAR)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setWhen(startTimeElapsedRealtime)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setSilent(true)

            val ongoingActivityStatus = Status.Builder()
                .addTemplate(title)
                .build()

            val ongoingActivity = OngoingActivity.Builder(
                context,
                WearNotificationIds.STOPWATCH_ONGOING,
                builder
            )
                .setTouchIntent(pendingIntent)
                .setStatus(ongoingActivityStatus)
                .setTitle(title)
                .build()

            ongoingActivity.apply(context)

            NotificationManagerCompat.from(context).notify(
                WearNotificationIds.STOPWATCH_ONGOING,
                builder.build()
            )
        } catch (e: Exception) {
            Timber.w(e, "WearStopwatchOngoingNotificationManager: failed to post ongoing notification")
        }
    }

    fun hideOngoing() {
        try {
            NotificationManagerCompat.from(context).cancel(WearNotificationIds.STOPWATCH_ONGOING)
        } catch (e: Exception) {
            Timber.w(e, "WearStopwatchOngoingNotificationManager: failed to cancel ongoing notification")
        }
    }
}
