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
import com.sza.fastmediasorter.domain.model.DuplicateDetectionResult
import com.sza.fastmediasorter.domain.model.DuplicateScanProgress
import com.sza.fastmediasorter.domain.model.ScanPhase
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.DetectDuplicatesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collect
import timber.log.Timber

@HiltWorker
class DuplicateDetectionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val detectDuplicatesUseCase: DetectDuplicatesUseCase,
    private val resourceRepository: ResourceRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_RESOURCE_IDS = "resource_ids"
        const val KEY_GROUP_COUNT = "group_count"
        const val KEY_WASTED_BYTES = "wasted_bytes"
        const val KEY_PHASE = "phase"
        const val KEY_FILES_PROCESSED = "files_processed"
        const val KEY_TOTAL_FILES = "total_files"
        const val NOTIFICATION_CHANNEL_ID = "duplicate_scan_channel"
        private const val NOTIFICATION_ID = 4201
        const val UNIQUE_WORK_NAME = "duplicate_scan"
    }

    override suspend fun doWork(): Result {
        val resourceIds = inputData.getLongArray(KEY_RESOURCE_IDS)?.toList()
        if (resourceIds.isNullOrEmpty()) {
            Timber.e("DuplicateDetectionWorker: no resource_ids in inputData")
            return Result.failure()
        }

        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Timber.w(e, "DuplicateDetectionWorker: setForeground failed (non-fatal)")
        }

        val resources = resourceIds.mapNotNull { id ->
            try { resourceRepository.getResourceById(id) } catch (e: Exception) { null }
        }

        if (resources.isEmpty()) {
            Timber.e("DuplicateDetectionWorker: no valid resources found for ids=$resourceIds")
            return Result.failure()
        }

        Timber.d("DuplicateDetectionWorker: starting scan on ${resources.size} resource(s)")

        var detectionResult: DuplicateDetectionResult? = null
        detectDuplicatesUseCase.detect(resources).collect { emission ->
            when (emission) {
                is DuplicateScanProgress -> {
                    setProgress(
                        workDataOf(
                            KEY_PHASE to emission.phase.name,
                            KEY_FILES_PROCESSED to emission.filesProcessed,
                            KEY_TOTAL_FILES to emission.totalFiles
                        )
                    )
                }
                is DuplicateDetectionResult -> {
                    detectionResult = emission
                }
            }
        }

        val result = detectionResult
        return if (result != null) {
            Timber.i(
                "DuplicateDetectionWorker: done — ${result.groups.size} groups, " +
                "${result.totalWastedBytes / 1024} KB wasted, ${result.durationMs}ms"
            )
            Result.success(
                workDataOf(
                    KEY_GROUP_COUNT to result.groups.size,
                    KEY_WASTED_BYTES to result.totalWastedBytes,
                    KEY_PHASE to ScanPhase.DONE.name
                )
            )
        } else {
            Timber.e("DuplicateDetectionWorker: no result emitted")
            Result.failure()
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = buildNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.duplicate_scan_notif_title))
            .setContentText(context.getString(R.string.duplicate_scan_notif_running))
            .setSmallIcon(R.drawable.ic_notification_audio)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.duplicate_scan_notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
                nm.createNotificationChannel(channel)
            }
        }
    }
}
