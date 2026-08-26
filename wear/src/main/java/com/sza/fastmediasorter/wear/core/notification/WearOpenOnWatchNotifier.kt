package com.sza.fastmediasorter.wear.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.MainActivity
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.writeTo
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Only one pending open exists at a time, so the same slot is always reused. */
private const val REQUEST_CODE = 1961

private const val CHANNEL_ID = "wear_open_on_watch"

/**
 * S1961: the only legal way to raise this watch's screen on a command from the phone.
 *
 * Delivering a Data Layer message is not one of the platform's exceptions to the background
 * Activity-start restriction, and the service the platform started to deliver it gains no such right
 * by having been started. A notification the user taps is - the resulting Activity start is attributed
 * to them, not to the app (strategic §2). So the watch cannot open itself, and this is what it does
 * instead: it says a command arrived and lets one tap act on it.
 *
 * The target rides the intent through [WearLaunchTarget], which S1955 already minted for the tile
 * entrance - the same address, written and read by the same pair of functions. A private `kind`/
 * `payload` encoding here would be a second wire format for the same journey, and the two would drift
 * apart at the first change to either (see the note atop `WearLaunchTarget.kt`).
 */
@Singleton
class WearOpenOnWatchNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Posts the "the phone sent you this" notification, or reports that it could not.
     *
     * The boolean is not decoration: a denied POST_NOTIFICATIONS makes this a silent no-op, and a
     * caller that assumed success would tell the phone the user was shown something they were not.
     *
     * [subtitle] names the thing that arrived - a channel name - so the notification says what will
     * open rather than only that something will.
     */
    fun notifyPendingOpen(target: WearLaunchTarget, subtitle: String): Boolean {
        if (!canPostNotification()) {
            Timber.d("S1961: notifyPendingOpen POST_NOTIFICATIONS is denied")
            return false
        }
        val manager = notificationManager()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wear_open_on_watch_channel_name),
                // DEFAULT, not LOW: the user pressed a button on their phone a second ago and is
                // waiting for the watch to answer. A silent entry in the shade is the silence this
                // ticket exists to end.
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.notify(WearNotificationIds.OPEN_ON_WATCH, build(target, subtitle))
        Timber.d("S1961: notifyPendingOpen posted notification subtitle=%s", subtitle)

        return true
    }

    /**
     * Clears the pending open.
     *
     * Called when the app comes to the front, because by then the command is spent: the user is
     * already looking at the app and can act directly. Leaving an expired command in the watch's
     * shade is worse than never having shown it (strategic §3.2).
     */
    fun cancel() {
        Timber.d("S1961: cancel notification OPEN_ON_WATCH")
        notificationManager().cancel(WearNotificationIds.OPEN_ON_WATCH)
    }

    private fun build(target: WearLaunchTarget, subtitle: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.wear_open_on_watch_notification_title))
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_cast)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            // Half of "it puts itself away": the tap clears it. The other half is [cancel].
            .setAutoCancel(true)
            .setContentIntent(pendingIntentFor(target))
            .build()

    /**
     * FLAG_UPDATE_CURRENT because a second command replaces the first: the slot is reused, and
     * without it the stale extras of the earlier command would be delivered for the newer one.
     * FLAG_IMMUTABLE is required from API 31 and correct everywhere - nothing outside this process
     * has any business rewriting where this intent points.
     */
    private fun pendingIntentFor(target: WearLaunchTarget): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .also(target::writeTo)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Mirrors `VoiceRecordingService.canPostNotification` - the permission is module-wide. */
    private fun canPostNotification(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
