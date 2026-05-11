package com.sza.fastmediasorter.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator
import com.sza.fastmediasorter.ui.share.ReceiveShareActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * S0161: executes a link auto-download in the background so the user can return to the
 * source app (Instagram, browser, etc.) immediately after sharing a URL.
 *
 * Input data:
 *  - [KEY_URL]        — single URL (mutually exclusive with [KEY_URLS]).
 *  - [KEY_URLS]       — string array for batch (multiple URLs from one share intent).
 *  - [KEY_ACCOUNT_ID] — account whose cookies the coordinator should use (single-URL only).
 *
 * The worker posts a [NOTIF_ID_PROGRESS] foreground notification during download, then
 * a separate auto-cancel result notification when done.  For [SocialPreviewOnly] results
 * the result notification includes a "Sign in" action that re-opens [ReceiveShareActivity]
 * with [ReceiveShareActivity.EXTRA_REAUTH_URL] so the full auth flow can restart.
 */
@HiltWorker
class LinkDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val coordinator: LinkAutoDownloadCoordinator,
    private val authSessionRepository: AuthSessionRepository,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_URL = "link_dl_url"
        const val KEY_ACCOUNT_ID = "link_dl_account_id"
        const val KEY_URLS = "link_dl_urls"

        const val NOTIFICATION_CHANNEL_ID = "link_download_channel"
        private const val NOTIF_ID_PROGRESS = 7100
        // Result notifications use NOTIF_ID_RESULT_BASE + (abs(url.hashCode) % 100)
        // to give each download its own slot while avoiding unbounded ID growth.
        private const val NOTIF_ID_RESULT_BASE = 7200
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL)
        val urls = inputData.getStringArray(KEY_URLS)
        val accountId = inputData.getString(KEY_ACCOUNT_ID)

        if (url == null && urls.isNullOrEmpty()) {
            Timber.e("LinkDownloadWorker: no url(s) in inputData — aborting")
            return Result.failure()
        }

        // setForeground() is intentionally NOT called here.
        // The work request uses setExpedited(), which is the correct mechanism for
        // short-lived tasks (<30 sec). Calling setForeground() from an expedited worker
        // on Android 12+ causes a SecurityException (WakeLock conflict with WorkManager's
        // internal expedited foreground service). Result notifications are posted directly
        // via NotificationManager so no ForegroundInfo is needed at all.
        Timber.i(
            "LinkDownloadWorker: start url=%s batch=%d accountId=%s",
            url ?: "(batch)",
            urls?.size ?: 0,
            accountId,
        )

        val result: LinkAutoDownloadCoordinator.Result = if (!urls.isNullOrEmpty()) {
            coordinator.handleBatch(urls.toList(), silentCallbacks())
        } else {
            coordinator.handle(url!!, silentCallbacks(), accountId)
        }

        Timber.i("LinkDownloadWorker: done result=%s", result::class.java.simpleName)
        // Resolve dismiss status here (suspend context) so postResultNotification stays non-suspend.
        val isDismissedHost = (result as? LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly)
            ?.let { runCatching { authSessionRepository.isDismissedForHost(it.host) }.getOrDefault(false) }
            ?: false
        postResultNotification(result, originalUrl = url ?: urls?.firstOrNull() ?: "", isDismissedHost = isDismissedHost)
        return Result.success()
    }

    /** No-op callbacks — progress is not reflected in the notification for simplicity. */
    private fun silentCallbacks() = object : LinkAutoDownloadCoordinator.Callbacks {
        override fun onProgress(state: LinkAutoDownloadCoordinator.ProgressState) = Unit
    }

    // ── Result notification ───────────────────────────────────────────────────

    private fun postResultNotification(
        result: LinkAutoDownloadCoordinator.Result,
        originalUrl: String,
        isDismissedHost: Boolean = false,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_download)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        when (result) {
            is LinkAutoDownloadCoordinator.Result.Saved -> {
                builder
                    .setContentTitle(context.getString(R.string.link_download_notif_title_done))
                    .setContentText(context.getString(R.string.link_download_notif_text_saved, result.fileName))
            }
            is LinkAutoDownloadCoordinator.Result.FellBackToDownloads -> {
                builder
                    .setContentTitle(context.getString(R.string.link_download_notif_title_done))
                    .setContentText(context.getString(R.string.link_download_notif_text_saved, result.fileName))
            }
            is LinkAutoDownloadCoordinator.Result.BatchCompleted -> {
                val s = result.summary
                builder
                    .setContentTitle(context.getString(R.string.link_download_notif_title_done))
                    .setContentText(
                        context.getString(R.string.link_download_notif_text_batch_done, s.successCount, s.totalItems),
                    )
            }
            is LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly -> {
                // Worker cannot show dialogs. Check dismissed status:
                // - if user previously said "don't ask" for this host — post a quiet failure notification.
                // - otherwise — post a heads-up sign-in notification so user can tap and re-auth.
                if (isDismissedHost) {
                    builder
                        .setContentTitle(context.getString(R.string.link_download_notif_title_done))
                        .setContentText(context.getString(R.string.link_download_notif_text_failed))
                } else {
                    builder
                        .setContentTitle(context.getString(R.string.link_download_notif_title_sign_in_needed))
                        .setContentText(
                            context.getString(R.string.link_download_notif_text_sign_in_needed, result.host),
                        )
                        .addAction(buildSignInAction(result.originalUrl))
                }
            }
            is LinkAutoDownloadCoordinator.Result.Failed -> {
                builder
                    .setContentTitle(context.getString(R.string.link_download_notif_title_done))
                    .setContentText(context.getString(R.string.link_download_notif_text_failed))
            }
        }

        // Spread result notifications across 100 slots keyed by URL hash to avoid
        // overwriting unrelated results while bounding the ID range.
        val notifId = NOTIF_ID_RESULT_BASE + Math.floorMod(originalUrl.hashCode(), 100)
        nm.notify(notifId, builder.build())
    }

    /**
     * Builds a "Sign in" notification action that opens [ReceiveShareActivity] with
     * [ReceiveShareActivity.EXTRA_REAUTH_URL] so the auth flow can restart for [url].
     *
     * PendingIntent targets a non-exported Activity within the same app; the system fires
     * it using the app's own identity so `android:exported="false"` is not a problem.
     */
    private fun buildSignInAction(url: String): NotificationCompat.Action {
        val intent = Intent(context, ReceiveShareActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReceiveShareActivity.EXTRA_REAUTH_URL, url)
        }
        val pi = PendingIntent.getActivity(
            context,
            // Request code derived from URL to avoid clobbering PendingIntents for different URLs.
            Math.floorMod(url.hashCode(), 10_000),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action(
            R.drawable.ic_lock,
            context.getString(R.string.link_download_notif_action_sign_in),
            pi,
        )
    }

    // ── Channel ───────────────────────────────────────────────────────────────

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.link_download_notif_channel_name),
            // HIGH = heads-up notification (pops up visibly without needing to open the shade)
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.link_download_notif_channel_description)
        }
        nm.createNotificationChannel(channel)
    }
}
