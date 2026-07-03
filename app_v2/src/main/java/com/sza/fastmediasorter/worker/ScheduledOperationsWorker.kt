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
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MIN_SCHEDULED_INTERVAL_MS
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.usecase.ExecuteScheduledOperationUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class ScheduledOperationsWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val executeScheduledOperationUseCase: ExecuteScheduledOperationUseCase,
    private val scheduledOperationRepository: ScheduledOperationRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_OPERATION_ID = "operation_id"
        const val NOTIFICATION_CHANNEL_ID = "scheduled_ops_channel"
        private const val NOTIFICATION_ID = 4200
        // S0710: separate id so the permission advisory survives the ongoing "running" notification.
        private const val PERMISSION_NOTIFICATION_ID = 4201

        // S0759: live counter of in-flight scheduled file operations. This worker runs as a foreground
        // service (DATA_SYNC) for its whole doWork(), so a non-zero count means a background file op is
        // actually keeping the app alive right now - the exit button reads this to decide minimize vs
        // close. WorkManager has no query-by-worker-class API and the op ids are dynamic, so a companion
        // counter mirrors the service flags (AudioPlaybackService.isRunning / QuickAudioRecorderService.
        // isRecording) and avoids the deprecated ActivityManager.getRunningServices.
        @Volatile
        private var runningCount: Int = 0

        @get:Synchronized
        val isRunning: Boolean
            get() = runningCount > 0

        @Synchronized
        private fun onWorkStarted() {
            runningCount++
        }

        @Synchronized
        private fun onWorkFinished() {
            if (runningCount > 0) runningCount--
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo()

    override suspend fun doWork(): Result {
        val operationId = inputData.getLong(KEY_OPERATION_ID, -1L)
        if (operationId == -1L) {
            Timber.e("ScheduledOperationsWorker: no operation_id in inputData")
            return Result.failure()
        }

        Timber.d("ScheduledOperationsWorker: starting op=$operationId")

        // S0759: mark this op in-flight for the whole foreground run so the exit button can detect it.
        onWorkStarted()
        try {
            try {
                setForeground(createForegroundInfo())
            } catch (e: Exception) {
                Timber.w(e, "ScheduledOperationsWorker: setForeground failed (non-fatal)")
            }

            val execResult = executeScheduledOperationUseCase(operationId)

            // Update lastRunAt, lastRunStatus, nextRunAt in DB; rescheduling is handled by the
            // WorkManagerScheduler observer.
            val operation = scheduledOperationRepository.getById(operationId)
            if (operation != null && operation.isEnabled) {
                val now = System.currentTimeMillis()
                val nextRunAt = calculateNextRunAt(operation, now)
                val updated = operation.copy(
                    lastRunAt = now,
                    lastRunStatus = execResult.statusString,
                    nextRunAt = nextRunAt,
                    workerId = "sched_op_$operationId"
                )
                scheduledOperationRepository.update(updated)
            }

            // S0710: a permission-required halt cannot be resolved in the background. Tell the user once so
            // they can grant All-files access, instead of silently re-running the same failing batch hourly.
            if (execResult.permissionRequired) {
                notifyPermissionRequired()
            }

            Timber.i("ScheduledOperationsWorker: op=$operationId done - ${execResult.filesProcessed} files, errors=${execResult.errors.size}")
            com.sza.fastmediasorter.widget.ScheduledTasksWidgetRefresher.refresh(context)
            return Result.success()
        } finally {
            onWorkFinished()
        }
    }

    private fun notifyPermissionRequired() {
        createNotificationChannel()
        val text = context.getString(R.string.scheduled_ops_notif_permission_text)
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.scheduled_ops_notif_permission_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification_audio)
            .setAutoCancel(true)
            .apply { buildAllFilesAccessIntent()?.let { setContentIntent(it) } }
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(PERMISSION_NOTIFICATION_ID, notification)
    }

    private fun buildAllFilesAccessIntent(): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            Timber.w(e, "ScheduledOperationsWorker: failed to build All-Files-Access intent")
            null
        }
    }

    private fun calculateNextRunAt(op: ScheduledOperation, fromMs: Long): Long {
        val intervalMs = (op.intervalHours * 3600L + op.intervalMinutes * 60L) * 1000L
        return fromMs + intervalMs.coerceAtLeast(MIN_SCHEDULED_INTERVAL_MS)
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
            .setContentTitle(context.getString(R.string.scheduled_ops_notif_channel_name))
            .setContentText(context.getString(R.string.scheduled_ops_notif_running))
            .setSmallIcon(R.drawable.ic_notification_audio)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.scheduled_ops_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
