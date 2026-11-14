package com.sza.fastmediasorter.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetDownloader
import com.sza.fastmediasorter.domain.delivery.DownloadProgress
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Background worker to run deliverable set downloads in application scope via WorkManager (S0397).
 * Updates progress notifications in the system tray and exposes work progress.
 */
@HiltWorker
class DeliverableDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloader: DeliverableSetDownloader,
    private val repository: DeliverableCapabilityRepository
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val set = deliverableSetNamed(inputData.getString(KEY_DELIVERABLE_SET)) ?: return Result.failure()

        // A forced run replaces a stale payload that is still on disk; only an unforced one may stop here.
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (isAlreadyDone(force, repository.isInstalled(set))) {
            return Result.success()
        }

        ensureNotificationChannel()
        try {
            val downloading = context.getString(R.string.delivery_state_downloading)
            setDataSyncForegroundIfAllowed(createForegroundInfo(set, downloading, 0))
        } catch (e: Exception) {
            Timber.w(e, "DeliverableDownloadWorker: setForeground failed (non-fatal)")
        }

        var result: Result = Result.failure()
        try {
            downloader.download(set).collect { progress ->
                val progressData = when (progress) {
                    DownloadProgress.Queued -> {
                        updateNotification(set, context.getString(R.string.delivery_state_downloading), 0)
                        workDataOf("status" to "queued")
                    }
                    is DownloadProgress.Running -> {
                        updateNotification(
                            set,
                            context.getString(R.string.delivery_state_downloading),
                            progress.percent
                        )
                        workDataOf(
                            "status" to "running",
                            "percent" to progress.percent,
                            "bytesDownloaded" to progress.bytesDownloaded,
                            "totalBytes" to progress.totalBytes
                        )
                    }
                    DownloadProgress.Verifying -> {
                        updateNotification(set, context.getString(R.string.delivery_state_verifying), -1)
                        workDataOf("status" to "verifying")
                    }
                    DownloadProgress.Installed -> {
                        showSuccessNotification(set)
                        workDataOf("status" to "installed")
                    }
                    is DownloadProgress.Failed -> {
                        showFailureNotification(set)
                        workDataOf(
                            "status" to "failed",
                            "reason" to progress.reason
                        )
                    }
                }
                setProgress(progressData)

                if (progress is DownloadProgress.Installed) {
                    result = Result.success()
                } else if (progress is DownloadProgress.Failed) {
                    result = Result.failure(workDataOf("reason" to progress.reason))
                }
            }
        } catch (e: CancellationException) {
            // The system or the runner stopped the job: that is not a failure to report, and swallowing it
            // would let a cancelled job finish as if it had been allowed to.
            throw e
        } catch (e: Exception) {
            Timber.e(e, "DeliverableDownloadWorker failed for $set")
            showFailureNotification(set)
            result = Result.failure(workDataOf("reason" to (e.message ?: "Unknown error")))
        } finally {
            notificationManager.cancel(notificationId(set))
        }

        return result
    }

    private fun createForegroundInfo(set: DeliverableSet, contentText: String, percent: Int): ForegroundInfo {
        val notification = buildNotification(set, contentText, percent)
        val notifId = notificationId(set)
        return DataSyncForeground.workerForegroundInfo(applicationContext, notifId, notification)
    }

    private fun updateNotification(set: DeliverableSet, contentText: String, percent: Int) {
        val notification = buildNotification(set, contentText, percent)
        notificationManager.notify(notificationId(set), notification)
    }

    private fun buildNotification(set: DeliverableSet, contentText: String, percent: Int): Notification {
        val featureName = context.getString(featureNameRes(set))
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.download_runner_notif_title_running, featureName))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (percent >= 0) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(100, 0, true) // indeterminate
        }

        return builder.build()
    }

    private fun showSuccessNotification(set: DeliverableSet) {
        val featureName = context.getString(featureNameRes(set))
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.download_runner_notif_title_installed, featureName))
            .setContentText(context.getString(R.string.friendly_copy_success_generic))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(resultNotificationId(set), notification)
    }

    private fun showFailureNotification(set: DeliverableSet) {
        val featureName = context.getString(featureNameRes(set))
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.download_runner_notif_title_failed, featureName))
            .setContentText(context.getString(R.string.delivery_state_error))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(resultNotificationId(set), notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.download_runner_notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    private fun featureNameRes(set: DeliverableSet): Int = when (set) {
        DeliverableSet.OCR_ENGINES -> R.string.ext_ocr_engines_title
        DeliverableSet.TRANSLATION -> R.string.ext_translation_title
        DeliverableSet.AUDIO_VISUALIZATIONS -> R.string.ext_audio_viz_title
        DeliverableSet.FFMPEG_DTS -> R.string.ext_ffmpeg_dts_title
        DeliverableSet.CHANNEL_PREVIEW_ATLAS -> R.string.ext_channel_preview_atlas_title
        DeliverableSet.STREAM_LOGO_ATLAS -> R.string.ext_stream_logo_atlas_title
        DeliverableSet.VLC_ENGINE -> R.string.ext_vlc_engine_title
    }

    private fun notificationId(set: DeliverableSet): Int =
        NotificationIds.slotIn(NotificationIds.DELIVERABLE_DOWNLOAD_PROGRESS, set.ordinal)

    private fun resultNotificationId(set: DeliverableSet): Int =
        NotificationIds.slotIn(NotificationIds.DELIVERABLE_DOWNLOAD_RESULTS, set.ordinal)

    companion object {
        const val KEY_DELIVERABLE_SET = "deliverable_set"
        const val KEY_FORCE = "deliverable_force"
        const val NOTIFICATION_CHANNEL_ID = "deliverable_download_channel"

        internal fun deliverableSetNamed(name: String?): DeliverableSet? =
            DeliverableSet.entries.firstOrNull { it.name == name }

        /** True when there is nothing to fetch: a payload is present and nobody asked to replace it. */
        internal fun isAlreadyDone(force: Boolean, installed: Boolean): Boolean = installed && !force
    }
}
