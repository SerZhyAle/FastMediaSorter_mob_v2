package com.sza.fastmediasorter.wear.ui.apps.stopwatch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.sza.fastmediasorter.wear.MainActivity
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.core.notification.NotificationIcons
import com.sza.fastmediasorter.wear.core.notification.WearNotificationIds
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.writeTo
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchOngoingIndicator
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchState
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3555: posts the running stopwatch as a Wear OS ongoing activity (WO-V4).
 *
 * One notification feeds all three surfaces Play checks - the indicator at the bottom of the watch face,
 * the chip in the launcher's Recents, and the entry the programs tile points at. On Wear OS 4+ the
 * platform drops it without a word when the notification permission is missing, which is how S3529's
 * version never appeared on a review watch; so nothing is posted unless notifications are enabled, and
 * the caller learns whether a runtime request could change that.
 *
 * The time on the chip and in the notification is counted by the system from one instant, so the app
 * does no work between the start and the stop of a measurement.
 */
@Singleton
class WearStopwatchOngoingNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) : WearStopwatchOngoingIndicator {

    companion object {
        const val CHANNEL_ID = "wear_stopwatch_ongoing"

        // The Recents chip gives the title and the status one line each and cuts both at the chip's
        // width: "Stopwatch running 03:32" lost its time on the 227 dp review watch. The program's name
        // is the title and the running time alone is the status, so the time is never the part cut off.
        private const val STATUS_TEMPLATE = "#time#"
        private const val STATUS_TIME = "time"
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

    override fun show(state: WearStopwatchState): Boolean {
        val runner = state.participants.firstOrNull { it.isRunning }
        val timeZero = runner?.startedAtMillis?.let { started -> started - runner.accumulatedMillis }
        return timeZero != null && NotificationManagerCompat.from(context).areNotificationsEnabled() && post(timeZero)
    }

    override fun hide() {
        try {
            NotificationManagerCompat.from(context).cancel(WearNotificationIds.STOPWATCH_ONGOING)
        } catch (e: SecurityException) {
            Timber.w(e, "WearStopwatchOngoingNotificationManager: security exception cancelling ongoing notification")
        } catch (e: IllegalStateException) {
            Timber.w(e, "WearStopwatchOngoingNotificationManager: illegal state cancelling ongoing notification")
        }
    }

    override fun blockedByMissingPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    /** [timeZero] is the elapsedRealtime instant the measurement would read zero at. */
    private fun post(timeZero: Long): Boolean = try {
        val touchIntent = PendingIntent.getActivity(
            context,
            WearNotificationIds.STOPWATCH_ONGOING,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                WearLaunchTarget.Destination(WearDestinationId.STOPWATCH).writeTo(this)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = context.getString(R.string.wear_stopwatch_ongoing_title)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentIntent(touchIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            // The chronometer counts from a wall-clock instant; this is the one matching timeZero.
            .setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - timeZero))
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setSilent(true)

        val status = Status.Builder()
            .addTemplate(STATUS_TEMPLATE)
            .addPart(STATUS_TIME, Status.StopwatchPart(timeZero))
            .build()

        OngoingActivity.Builder(context, WearNotificationIds.STOPWATCH_ONGOING, builder)
            .setStaticIcon(NotificationIcons.STATUS_BAR)
            .setTouchIntent(touchIntent)
            .setStatus(status)
            .setTitle(context.getString(R.string.wear_app_stopwatch))
            .build()
            .apply(context)

        NotificationManagerCompat.from(context).notify(WearNotificationIds.STOPWATCH_ONGOING, builder.build())
        true
    } catch (e: SecurityException) {
        Timber.w(e, "WearStopwatchOngoingNotificationManager: security exception posting ongoing notification")
        false
    } catch (e: IllegalStateException) {
        Timber.w(e, "WearStopwatchOngoingNotificationManager: illegal state posting ongoing notification")
        false
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "WearStopwatchOngoingNotificationManager: invalid argument posting ongoing notification")
        false
    }
}
