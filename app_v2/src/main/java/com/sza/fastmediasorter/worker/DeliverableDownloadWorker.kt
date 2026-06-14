package com.sza.fastmediasorter.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetDownloader
import com.sza.fastmediasorter.domain.delivery.DownloadProgress
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
        val setName = inputData.getString(KEY_DELIVERABLE_SET) ?: return Result.failure()
        val set = try {
            DeliverableSet.valueOf(setName)
        } catch (e: Exception) {
            return Result.failure()
        }

        if (repository.isInstalledBlocking(set)) {
            return Result.success()
        }

        ensureNotificationChannel()
        try {
            setForeground(createForegroundInfo(set, context.getString(R.string.delivery_state_downloading), 0))
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
                        updateNotification(set, context.getString(R.string.delivery_state_downloading), progress.percent)
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
        } catch (e: Exception) {
            Timber.e(e, "DeliverableDownloadWorker failed for $set")
            showFailureNotification(set)
            result = Result.failure(workDataOf("reason" to (e.message ?: "Unknown error")))
        }

        return result
    }

    private fun createForegroundInfo(set: DeliverableSet, contentText: String, percent: Int): ForegroundInfo {
        Timber.d("S0416: deliverable-download foreground notification built (small icon without ?attr tint)")
        val notification = buildNotification(set, contentText, percent)
        val notifId = notificationId(set)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    private fun updateNotification(set: DeliverableSet, contentText: String, percent: Int) {
        val notification = buildNotification(set, contentText, percent)
        notificationManager.notify(notificationId(set), notification)
    }

    private fun buildNotification(set: DeliverableSet, contentText: String, percent: Int): Notification {
        val featureName = context.getString(featureNameRes(set))
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_cloud_download)
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
            .setSmallIcon(R.drawable.ic_notification_cloud_download)
            .setContentTitle(context.getString(R.string.download_runner_notif_title_installed, featureName))
            .setContentText(context.getString(R.string.friendly_copy_success_generic))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId(set) + 1000, notification)
    }

    private fun showFailureNotification(set: DeliverableSet) {
        val featureName = context.getString(featureNameRes(set))
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_cloud_download)
            .setContentTitle(context.getString(R.string.download_runner_notif_title_failed, featureName))
            .setContentText(context.getString(R.string.delivery_state_error))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId(set) + 1000, notification)
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
    }

    private fun notificationId(set: DeliverableSet): Int = 7300 + set.ordinal

    companion object {
        const val KEY_DELIVERABLE_SET = "deliverable_set"
        const val NOTIFICATION_CHANNEL_ID = "deliverable_download_channel"
    }
}
