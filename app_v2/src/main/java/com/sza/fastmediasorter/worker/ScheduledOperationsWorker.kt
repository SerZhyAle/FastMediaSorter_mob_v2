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
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.core.util.StoragePermissionRule
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.ScheduledRunOutcome
import com.sza.fastmediasorter.domain.model.ScheduledRunReason
import com.sza.fastmediasorter.domain.model.ScheduledRunRecord
import com.sza.fastmediasorter.domain.model.afterRun
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.ScheduledRunJournalRepository
import com.sza.fastmediasorter.domain.usecase.ExecuteScheduledOperationUseCase
import com.sza.fastmediasorter.domain.usecase.ScheduledExecutionResult
import com.sza.fastmediasorter.ui.scheduledops.ScheduledOperationsActivity
import com.sza.fastmediasorter.ui.scheduledops.ScheduledRunReasonText
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class ScheduledOperationsWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val executeScheduledOperationUseCase: ExecuteScheduledOperationUseCase,
    private val scheduledOperationRepository: ScheduledOperationRepository,
    private val runJournal: ScheduledRunJournalRepository,
    private val resourceRepository: ResourceRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_OPERATION_ID = "operation_id"
        const val NOTIFICATION_CHANNEL_ID = "scheduled_ops_channel"
        private const val NOTIFICATION_ID = NotificationIds.SCHEDULED_OPERATIONS
        // S0710: separate id so the permission advisory survives the ongoing "running" notification.
        // S1292: ids now come from NotificationIds - two sync workers used to reuse this very value.
        private const val PERMISSION_NOTIFICATION_ID = NotificationIds.SCHEDULED_OPERATIONS_PERMISSION

        // Kept short: the journal row and the notification body are one or two lines on a phone.
        private const val MAX_MESSAGE_LENGTH = 300

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
            // The marker the next SINCE_LAST run compares against is this moment, not the finish: a file
            // that appears while the run is copying must stay newer than it.
            val startedAt = System.currentTimeMillis()
            val foregroundNote = enterForeground()

            val execResult = executeScheduledOperationUseCase(operationId)

            // Update lastRunAt, lastRunStatus, lastSuccessAt, nextRunAt in DB; rescheduling is handled by the
            // WorkManagerScheduler observer. The row is re-read: the user may have edited it mid-run.
            val operation = scheduledOperationRepository.getById(operationId)
            if (operation != null && operation.isEnabled) {
                val finishedAt = System.currentTimeMillis()
                val afterRun = operation.afterRun(execResult.outcome, runReason(execResult), startedAt, finishedAt)
                // Cleared in the same write as the status that explains it; the reschedule observer only
                // re-queues an enabled row, so this is what ends the chain.
                scheduledOperationRepository.update(
                    if (execResult.pausesSchedule) afterRun.copy(isEnabled = false) else afterRun
                )
                if (execResult.pausesSchedule) {
                    Timber.w("ScheduledOperationsWorker: op=$operationId paused - ${execResult.reason}")
                    Timber.d("S4480: schedule op=$operationId paused after its resource was deleted")
                }
                reportRun(operation, execResult, journalRecord(execResult, startedAt, finishedAt, foregroundNote))
            }

            // S0710: a permission-required halt cannot be resolved in the background. Tell the user once so
            // they can grant All-files access, instead of silently re-running the same failing batch hourly.
            if (execResult.permissionRequired) {
                notifyPermissionRequired()
            }

            Timber.i("ScheduledOperationsWorker: op=$operationId done - ${execResult.filesProcessed} files, errors=${execResult.errors.size}")
            com.sza.fastmediasorter.widget.ScheduledTasksWidgetRefresher.refresh(context)
            // Always success on purpose: the owner rejected retries, the next slot is the retry, and the
            // reschedule observer only continues the chain from a SUCCEEDED state.
            return Result.success()
        } finally {
            onWorkFinished()
        }
    }

    /**
     * Promotes the run to a foreground service. A refusal (Android 12+ background-start limits, a
     * revoked notification channel) used to be swallowed; it is now returned as a note that the run
     * journal keeps, so a run that later dies mid-way is explainable from the screen.
     */
    private suspend fun enterForeground(): String? = try {
        setForeground(createForegroundInfo())
        null
    } catch (e: Exception) {
        Timber.w(e, "ScheduledOperationsWorker: setForeground failed (non-fatal)")
        "Foreground service could not start: ${e.message ?: e.javaClass.simpleName}"
    }

    /**
     * Journal entry and failure notification. Neither may fail the work: the reschedule observer only
     * continues the chain from a SUCCEEDED state, so an exception out of here would stop the schedule
     * for good, over a missing history line.
     */
    private suspend fun reportRun(
        operation: ScheduledOperation,
        result: ScheduledExecutionResult,
        record: ScheduledRunRecord
    ) {
        try {
            runJournal.append(operation.id, record)
            notifyRunFailed(operation, result)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.w(e, "ScheduledOperationsWorker: could not report run of op=${operation.id}")
        }
    }

    private fun runReason(result: ScheduledExecutionResult): String? =
        (result.skippedReason ?: result.errors.firstOrNull())?.take(MAX_MESSAGE_LENGTH)

    private fun journalRecord(
        result: ScheduledExecutionResult,
        startedAt: Long,
        finishedAt: Long,
        foregroundNote: String?
    ) = ScheduledRunRecord(
        startedAt = startedAt,
        finishedAt = finishedAt,
        outcome = result.outcome,
        filesProcessed = result.filesProcessed,
        message = runReason(result),
        note = foregroundNote,
        // A permission halt is raised by a boolean on the result, not by a coded producer.
        reason = result.reason ?: ScheduledRunReason.PERMISSION_REQUIRED.takeIf { result.permissionRequired }
    )

    /**
     * S4211: one notification for a run that ended in ERROR. A skipped run (offline server) is a normal
     * state and a permission halt has its own advisory, so neither posts here. The schedule card keeps
     * the warning badge either way - that is the mark for a device without the notification permission.
     */
    private suspend fun notifyRunFailed(operation: ScheduledOperation, result: ScheduledExecutionResult) {
        val wanted = result.outcome == ScheduledRunOutcome.ERROR &&
            !result.permissionRequired &&
            !operation.silentMode &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!wanted) return
        Timber.d("S4493: failure notification worded from the reason code")
        createNotificationChannel()
        val label = scheduleLabel(operation)
        // S4493: the English run message stays in the journal; the notification says it in the interface language.
        val text = context.getString(
            R.string.scheduled_ops_notif_failed_text,
            label,
            context.getString(ScheduledRunReasonText.of(result.reason, result.outcome))
        )
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.scheduled_ops_notif_failed_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setAutoCancel(true)
            .setContentIntent(buildScreenIntent())
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            NotificationIds.slotIn(NotificationIds.SCHEDULED_OPERATION_FAILURES, operation.id.toInt()),
            notification
        )
    }

    private suspend fun scheduleLabel(operation: ScheduledOperation): String {
        val source = operation.watchTitle?.takeIf { operation.operationType == ScheduledOpType.WATCH_MESSAGE }
            ?: operation.sourceResourceId?.let { resourceRepository.getResourceById(it) }?.name
            ?: context.getString(R.string.scheduled_op_unknown_resource)
        if (operation.operationType == ScheduledOpType.DELETE || operation.targetResourceId == null) return source
        val target = resourceRepository.getResourceById(operation.targetResourceId)?.name
            ?: context.getString(R.string.scheduled_op_unknown_resource)
        return "$source → $target"
    }

    private fun buildScreenIntent(): PendingIntent {
        val intent = Intent(context, ScheduledOperationsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifyPermissionRequired() {
        createNotificationChannel()
        val text = context.getString(R.string.scheduled_ops_notif_permission_text)
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.scheduled_ops_notif_permission_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setAutoCancel(true)
            .apply { buildAllFilesAccessIntent()?.let { setContentIntent(it) } }
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(PERMISSION_NOTIFICATION_ID, notification)
    }

    // S2012: no content intent where the merged manifest declares no all-files access - the
    // notification still reports the operation could not finish, but it must not send the user to a
    // system screen that would leave them exactly where they started.
    private fun buildAllFilesAccessIntent(): PendingIntent? {
        if (!StoragePermissionRule.requiresAllFilesAccess(context)) return null
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
            .setSmallIcon(NotificationIcons.STATUS_BAR)
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
