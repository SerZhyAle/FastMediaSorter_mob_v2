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
import com.sza.fastmediasorter.R
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
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo()

    override suspend fun doWork(): Result {
        val operationId = inputData.getLong(KEY_OPERATION_ID, -1L)
        if (operationId == -1L) {
            Timber.e("ScheduledOperationsWorker: no operation_id in inputData")
            return Result.failure()
        }

        Timber.d("ScheduledOperationsWorker: starting op=$operationId")

        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Timber.w(e, "ScheduledOperationsWorker: setForeground failed (non-fatal)")
        }

        val execResult = executeScheduledOperationUseCase(operationId)

        // Update lastRunAt, lastRunStatus, nextRunAt in DB; rescheduling is handled by WorkManagerScheduler observer
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

        Timber.i("ScheduledOperationsWorker: op=$operationId done - ${execResult.filesProcessed} files, errors=${execResult.errors.size}")
        Timber.d("S0353: scheduled-tasks widget refreshed after run")
        com.sza.fastmediasorter.widget.ScheduledTasksWidgetRefresher.refresh(context)
        return Result.success()
    }

    private fun calculateNextRunAt(op: ScheduledOperation, fromMs: Long): Long {
        val intervalMs = (op.intervalHours * 3600L + op.intervalMinutes * 60L) * 1000L
        return fromMs + intervalMs.coerceAtLeast(15 * 60 * 1000L)
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
