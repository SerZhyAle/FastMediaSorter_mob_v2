package com.sza.fastmediasorter.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator
import com.sza.fastmediasorter.ui.share.ReceiveShareActivity
import com.sza.fastmediasorter.ui.share.ShareDownloadResultBus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import timber.log.Timber

/**
 * S0161: executes a link auto-download in the background so the user can return to the
 * source app (Instagram, browser, etc.) immediately after sharing a URL.
 *
 * Input data:
 *  - [KEY_URL]        - single URL (mutually exclusive with [KEY_URLS]).
 *  - [KEY_URLS]       - string array for batch (multiple URLs from one share intent).
 *  - [KEY_ACCOUNT_ID] - account whose cookies the coordinator should use (single-URL only).
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
    private val resultBus: ShareDownloadResultBus,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_URL = "link_dl_url"
        const val KEY_ACCOUNT_ID = "link_dl_account_id"
        const val KEY_URLS = "link_dl_urls"
        // S0202: passed through workData so the worker can mirror the activity's auth-retry
        // context. Reserved for future single-URL retry-aware logic; currently unused inside
        // the worker but the share-Activity sets it on every enqueue.
        const val KEY_IS_AUTH_RETRY = "link_dl_is_auth_retry"
        // S0202: encodes the coordinator Result's kind in WorkInfo.outputData so the
        // activity-side observer can trigger NoMediaFound escalation when the worker
        // completes within the watchdog window.
        const val KEY_RESULT_KIND = "link_dl_result_kind"

        const val NOTIFICATION_CHANNEL_ID = "link_download_channel"
        private const val NOTIF_ID_PROGRESS = 7100
        // Result notifications use NOTIF_ID_RESULT_BASE + (abs(url.hashCode) % 100)
        // to give each download its own slot while avoiding unbounded ID growth.
        private const val NOTIF_ID_RESULT_BASE = 7200
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        return buildForegroundInfo(context.getString(R.string.link_download_notif_text_probing))
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL)
        val urls = inputData.getStringArray(KEY_URLS)
        val accountId = inputData.getString(KEY_ACCOUNT_ID)

        if (url == null && urls.isNullOrEmpty()) {
            Timber.e("LinkDownloadWorker: no url(s) in inputData - aborting")
            return Result.failure()
        }

        // S0202: dual-mode worker - single-URL share runs as a long-running foreground
        // worker (setForeground from getForegroundInfo) so backgrounding the share Activity
        // does not kill the download. Batch mode keeps the existing short-lived expedited
        // path (no setForeground), because batch is enqueued from the share-Activity in a
        // fire-and-forget style and result notifications already cover its UX.
        Timber.i(
            "LinkDownloadWorker: start url=%s batch=%d accountId=%s",
            url ?: "(batch)",
            urls?.size ?: 0,
            accountId,
        )

        val result: LinkAutoDownloadCoordinator.Result = coroutineScope {
            ensureActive()
            if (!urls.isNullOrEmpty()) {
                coordinator.handleBatch(urls.toList(), silentCallbacks())
            } else {
                try {
                    setForeground(buildForegroundInfo(context.getString(R.string.link_download_notif_text_probing)))
                } catch (e: Exception) {
                    Timber.w(e, "LinkDownloadWorker: setForeground failed (non-fatal)")
                }
                ensureActive()
                coordinator.handle(url!!, progressCallbacks(), accountId)
            }
        }

        Timber.i("LinkDownloadWorker: done result=%s", result::class.java.simpleName)
        // Resolve dismiss status here (suspend context) so postResultNotification stays non-suspend.
        val isDismissedHost = (result as? LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly)
            ?.let { runCatching { authSessionRepository.isDismissedForHost(it.host) }.getOrDefault(false) }
            ?: false
        postResultNotification(result, originalUrl = url ?: urls?.firstOrNull() ?: "", isDismissedHost = isDismissedHost)
        // S0202: surface the coordinator Result kind in outputData so the share Activity
        // observer can dispatch on it (e.g. unknown-host NoMediaFound auth escalation) when
        // the worker completes before the activity's 4-second watchdog.
        val outputData = androidx.work.Data.Builder()
            .putString(KEY_RESULT_KIND, result::class.java.simpleName)
            .build()
        // S0202: also push the terminal result to the in-memory bus so the foreground
        // Activity (typically MainActivity when the user returns from Instagram/Threads)
        // can present it on resume - toast, open-in-player, or auth-required dialog -
        // without depending on whether the share Activity is still alive.
        runCatching {
            resultBus.publish(
                ShareDownloadResultBus.Pending(
                    url = url ?: urls?.firstOrNull() ?: "",
                    result = result,
                    notificationShown = true,
                )
            )
        }.onFailure { Timber.w(it, "S0202: result bus emit failed") }
        return Result.success(outputData)
    }

    /** No-op callbacks - progress is not reflected in the notification for simplicity. */
    private fun silentCallbacks() = object : LinkAutoDownloadCoordinator.Callbacks {
        override fun onProgress(state: LinkAutoDownloadCoordinator.ProgressState) = Unit
    }

    /**
     * S0202: progress callbacks for the single-URL foreground mode. Each emission is
     * mirrored into `WorkInfo.progress` (consumed by `ReceiveShareActivity`'s observer
     * via `LinkDownloadProgressCodec.decode`) AND into the ongoing foreground notification
     * (so backgrounded users still see live progress in the system shade).
     *
     * `setProgressAsync` is fire-and-forget - `onProgress` is a non-suspending interface
     * method called from Dispatchers.IO; awaiting the ListenableFuture would force us to
     * either block the IO thread or wrap each callback in a coroutine builder, both of
     * which add latency for no observable benefit.
     */
    private fun progressCallbacks() = object : LinkAutoDownloadCoordinator.Callbacks {
        override fun onProgress(state: LinkAutoDownloadCoordinator.ProgressState) {
            runCatching { setProgressAsync(LinkDownloadProgressCodec.encode(state)) }
            runCatching { updateNotification(state) }
        }
    }

    private fun updateNotification(state: LinkAutoDownloadCoordinator.ProgressState) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_download)
            .setContentTitle(context.getString(R.string.link_download_notif_title_downloading))
            .setOngoing(true)
            .addAction(buildCancelAction())
        when (state) {
            LinkAutoDownloadCoordinator.ProgressState.Probing -> {
                builder.setContentText(context.getString(R.string.link_download_notif_text_probing))
                builder.setProgress(0, 0, true)
            }
            LinkAutoDownloadCoordinator.ProgressState.AnalyzingPage -> {
                builder.setContentText(context.getString(R.string.link_download_notif_text_analyzing))
                builder.setProgress(0, 0, true)
            }
            is LinkAutoDownloadCoordinator.ProgressState.Downloading -> {
                val total = state.total
                if (total != null && total > 0L) {
                    val pct = ((state.bytesRead * 100L) / total).toInt().coerceIn(0, 100)
                    builder.setContentText(context.getString(R.string.link_download_notif_text_downloading_pct, pct))
                    builder.setProgress(100, pct, false)
                } else {
                    builder.setContentText(context.getString(R.string.link_download_notif_text_probing))
                    builder.setProgress(0, 0, true)
                }
            }
            is LinkAutoDownloadCoordinator.ProgressState.BatchDownloading -> {
                val pct = if (state.itemCount > 0) {
                    ((state.itemIndex.toLong() * 100L) / state.itemCount).toInt().coerceIn(0, 100)
                } else 0
                builder.setContentText(context.getString(R.string.link_download_notif_text_downloading_pct, pct))
                builder.setProgress(100, pct, false)
            }
        }
        nm.notify(NOTIF_ID_PROGRESS, builder.build())
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
                // S0224 §5.4: observability log so the batch notification total can be verified from logcat
                // without device-side testing. Uses `Timber.i` (info) - not `Timber.d("S0224:` - because that
                // tag idiom is bound to BlockNeedUserTest status only (CLAUDE.md "Debug Verification Tags").
                Timber.i(
                    "LinkDownloadNotification set total=%d success=%d label=%s",
                    s.totalItems,
                    s.successCount,
                    s.label ?: "<none>",
                )
                builder
                    .setContentTitle(context.getString(R.string.link_download_notif_title_done))
                    .setContentText(
                        context.getString(R.string.link_download_notif_text_batch_done, s.successCount, s.totalItems),
                    )
            }
            LinkAutoDownloadCoordinator.Result.Failed.UnsupportedYouTubeCommunityPost -> {
                builder
                    .setContentTitle(context.getString(R.string.link_download_notif_title_done))
                    .setContentText(context.getString(R.string.link_autodownload_error_youtube_community_post))
                    .addAction(buildOpenUrlAction(originalUrl))
            }
            is LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly -> {
                // S0211: worker cannot show dialogs. Three branches:
                // - extractor already ran with a valid stored session (hadExistingSession) - honest
                //   notice, no Sign-In CTA. Mirrors LinkAutoDownloadResultPresenter.presentSocialPreviewOnly.
                // - "don't ask" recorded for this host - quiet failure notification.
                // - no session yet - heads-up sign-in notification with re-auth CTA.
                when {
                    result.hadExistingSession -> {
                        Timber.d("S0211: LinkDownloadWorker notify preview-only-signed-in host=%s", result.host)
                        builder
                            .setContentTitle(context.getString(R.string.link_download_notif_title_done))
                            .setContentText(
                                context.getString(R.string.link_download_notif_text_preview_only_signed_in),
                            )
                    }
                    isDismissedHost -> {
                        builder
                            .setContentTitle(context.getString(R.string.link_download_notif_title_done))
                            .setContentText(context.getString(R.string.link_download_notif_text_failed))
                    }
                    else -> {
                        builder
                            .setContentTitle(context.getString(R.string.link_download_notif_title_sign_in_needed))
                            .setContentText(
                                context.getString(R.string.link_download_notif_text_sign_in_needed, result.host),
                            )
                            .addAction(buildSignInAction(result.originalUrl))
                    }
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

    private fun buildOpenUrlAction(url: String): NotificationCompat.Action {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pi = PendingIntent.getActivity(
            context,
            10_000 + Math.floorMod(url.hashCode(), 10_000),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action(
            android.R.drawable.ic_menu_view,
            context.getString(R.string.action_open),
            pi,
        )
    }

    // ── Foreground notification (S0202) ───────────────────────────────────────

    /**
     * Builds the ongoing foreground-service notification used by the single-URL
     * share path. The notification is updated incrementally by `updateNotification`
     * (Step 01.5) as the coordinator emits ProgressState events.
     *
     * Uses the existing `link_download_channel` (no new channel introduced).
     */
    private fun buildForegroundInfo(text: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_download)
            .setContentTitle(context.getString(R.string.link_download_notif_title_downloading))
            .setContentText(text)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .addAction(buildCancelAction())
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID_PROGRESS, notification)
        }
    }

    /**
     * Cancel action wired to WorkManager.createCancelPendingIntent - tapping it
     * cancels this worker's unique work, which in turn raises CancellationException
     * inside coordinator.handle (Phase 03 ensures atomic cleanup).
     */
    private fun buildCancelAction(): NotificationCompat.Action {
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        return NotificationCompat.Action(
            R.drawable.ic_cancel,
            context.getString(R.string.link_download_notif_action_cancel),
            cancelIntent,
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
