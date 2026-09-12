package com.sza.fastmediasorter.wear.service.helpers

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
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.core.notification.NotificationIcons
import com.sza.fastmediasorter.wear.core.notification.WearNotificationIds
import com.sza.fastmediasorter.wear.ui.listen.ListenRequestActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Only one listening request exists at a time, so the same pending-intent slot is always reused. */
private const val REQUEST_CODE = 2550

private const val CHANNEL_ID = "wear_listen_request"

/**
 * S2550 ADR-6: the notification is the whole start path, not a courtesy around it.
 *
 * Strategic §6.1 measured both barriers that close the silent path: API 31 refuses a background
 * foreground-service start outside fourteen exemptions that a Data Layer delivery matches nowhere,
 * and API 34 separately refuses to CREATE a `microphone`-typed service from the background even when
 * the first barrier has been waived. Interaction with a notification is the only exemption present in
 * both lists, so this class is what makes the feature possible at all.
 *
 * It posts and it cancels, and it reaches neither the recorder, the LAN server nor any service - the
 * invariant Phase 03 hands to Phase 04 is that only the window this notification opens may start the
 * microphone. The recorder class is deliberately not named anywhere in this file: Step 03.1 checks
 * that absence by grep, and prose explaining it reads to a grep exactly like a use of it.
 */
@Singleton
class ListenRequestNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * How long an unanswered request stays on the watch before it cancels itself.
     *
     * The strategic spec fixes no value for this: it requires only that a command nobody answered
     * leaves no sound and no running service (acceptance criterion 1a). Two minutes is long enough
     * for a wrist to be raised and short enough that the invitation is not still live when the owner
     * next looks at the watch - a stale one a stray tap could still honour is exactly the covert
     * listening the Non-goals put outside the product.
     */
    private val expiryMillis = EXPIRY_MINUTES * MILLIS_PER_MINUTE

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Volatile for [hasPendingRequest]'s reason: it is written from the Data Layer delivery thread
     * that posts the request and read from Main when the owner answers, so an unpublished write would
     * leave a live timer nobody cancelled - and that timer withdraws the request out from under a
     * session the owner had already confirmed.
     */
    @Volatile
    private var expiryJob: Job? = null

    /** True between a posted request and the tap, decline or expiry that clears it. */
    @Volatile
    var hasPendingRequest: Boolean = false
        private set

    /**
     * Posts the request, or reports that it could not.
     *
     * The boolean is not decoration: a denied POST_NOTIFICATIONS makes this a silent no-op, and the
     * phone must be told the watch was never asked rather than be left waiting for a tap on something
     * that was never shown.
     */
    fun notifyListenRequest(onExpired: () -> Unit): Boolean {
        if (!canPostNotification()) {
            return false
        }
        val manager = notificationManager()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wear_listen_request_channel_name),
                // HIGH: incoming call/listen request requires high importance for heads-up / full-screen intent.
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        Timber.d("S2941: posting listen request with full-screen intent")
        manager.notify(WearNotificationIds.LISTEN_REQUEST, build())
        hasPendingRequest = true
        scheduleExpiry(onExpired)
        return true
    }

    /**
     * Clears the request and its pending state, on every outcome - the tap that honoured it, the
     * decline that refused it, and the expiry that outlived it. Calling it twice is a no-op, which is
     * what makes a decline arriving after an expiry not a second anything.
     */
    fun cancel() {
        expiryJob?.cancel()
        expiryJob = null
        withdrawRequest()
    }

    /**
     * [onExpired] runs only when the delay actually elapsed, never when [cancel] killed the job -
     * a tap and a decline have already answered the phone by then, and a second answer would tell it
     * the request went unheard after it was heard.
     *
     * The timed-out path withdraws the request without touching [expiryJob]: cancelling the job from
     * inside the job is how the callback after it would stop running.
     */
    private fun scheduleExpiry(onExpired: () -> Unit) {
        expiryJob?.cancel()
        expiryJob = scope.launch {
            delay(expiryMillis)
            withdrawRequest()
            onExpired()
        }
    }

    private fun withdrawRequest() {
        hasPendingRequest = false
        notificationManager().cancel(WearNotificationIds.LISTEN_REQUEST)
    }

    private fun build() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle(context.getString(R.string.wear_listen_request_notification_title))
        .setContentText(context.getString(R.string.wear_listen_request_notification_text))
        .setSmallIcon(NotificationIcons.STATUS_BAR)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent())
        .setFullScreenIntent(pendingIntent(), true)
        .build()

    /**
     * FLAG_IMMUTABLE is required from API 31 and correct everywhere - nothing outside this process
     * has any business rewriting where a microphone request points.
     */
    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ListenRequestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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

    private companion object {
        const val EXPIRY_MINUTES = 2L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
