package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for scheduling and managing WorkManager tasks.
 * Handles periodic trash cleanup, background resource sync,
 * and one-time scheduled file operations.
 */
@Singleton
class WorkManagerScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scheduledOperationRepository: ScheduledOperationRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private companion object {
        // Shared tag carried by EVERY scheduled-operation work request. Bulk cancellation goes
        // through cancelAllWorkByTag, which matches an exact tag - the per-operation unique names
        // (sched_op_<id> / sched_op_run_all) cannot be cancelled as a group, so a single common tag
        // is the only handle that reaches all of them at once.
        const val TAG_SCHEDULED_OP = "sched_op"
    }

    /**
     * Schedule periodic trash cleanup worker
     * Runs every 15 minutes to clean up trash folders older than 5 minutes
     * First run delayed by 1 minute to avoid blocking app startup
     */
    fun scheduleTrashCleanup() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<TrashCleanupWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setInitialDelay(1, TimeUnit.MINUTES) // Delay first run to reduce startup load
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TrashCleanupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
                workRequest
            )
            
            Timber.i("WorkManagerScheduler: Scheduled periodic trash cleanup (every 15 minutes, first run in 1 minute)")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: Failed to schedule trash cleanup")
        }
    }
    
    /**
     * Cancel trash cleanup worker
     */
    fun cancelTrashCleanup() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(TrashCleanupWorker.WORK_NAME)
            Timber.i("WorkManagerScheduler: Cancelled trash cleanup worker")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: Failed to cancel trash cleanup")
        }
    }

    /**
     * Schedule periodic background sync for all resources (local + network).
     * Network constraint is NOT required so local directories sync even offline.
     * Network resources are scanned when connectivity is available; failures are non-fatal.
     *
     * @param intervalHours How often to sync (minimum 1 hour enforced by WorkManager).
     */
    fun scheduleResourcesSync(intervalHours: Long) {
        try {
            val effectiveInterval = intervalHours.coerceAtLeast(1L)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // local sync must work offline
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<NetworkFilesSyncWorker>(
                effectiveInterval, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .addTag(NetworkFilesSyncWorker.WORK_NAME)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NetworkFilesSyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Timber.i("WorkManagerScheduler: Resource sync scheduled every $effectiveInterval h")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: Failed to schedule resource sync")
        }
    }

    /**
     * Cancel the periodic background resource sync worker.
     */
    fun cancelResourcesSync() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(NetworkFilesSyncWorker.WORK_NAME)
            Timber.i("WorkManagerScheduler: Resource sync worker cancelled")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: Failed to cancel resource sync")
        }
    }

    /**
     * Schedule periodic orphan cleanup worker.
     * Runs once per day to remove cached file lists that reference deleted resources,
     * and to audit credentials that have no associated resource.
     */
    fun scheduleOrphanCleanup() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<OrphanCleanupWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setInitialDelay(10, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                OrphanCleanupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Timber.i("WorkManagerScheduler: Orphan cleanup scheduled (every 24 h, first run in 10 min)")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: Failed to schedule orphan cleanup")
        }
    }

    /**
     * Schedule periodic worker to retry OAuth token revocations that failed at sign-out time (B5-T3).
     * Requires network. Runs every 6 hours; first attempt delayed 2 minutes after app launch.
     */
    // -------------------------------------------------------------------------
    // Scheduled file operations (OneTimeWork + self-rescheduling)
    // -------------------------------------------------------------------------

    /**
     * Schedule a one-time run of [operation] at [operation.nextRunAt].
     * If nextRunAt is null or in the past, runs immediately.
     */
    fun scheduleOperation(operation: ScheduledOperation) {
        if (!operation.isEnabled) {
            cancelOperation(operation.id)
            return
        }
        try {
            val delayMs = ((operation.nextRunAt ?: 0L) - System.currentTimeMillis())
                .coerceAtLeast(0L)
            val inputData = Data.Builder()
                .putLong(ScheduledOperationsWorker.KEY_OPERATION_ID, operation.id)
                .build()
            val request = OneTimeWorkRequestBuilder<ScheduledOperationsWorker>()
                .setInputData(inputData)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("sched_op_${operation.id}")
                .addTag(TAG_SCHEDULED_OP)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sched_op_${operation.id}",
                ExistingWorkPolicy.REPLACE,
                request
            )
            Timber.i("WorkManagerScheduler: scheduled op=${operation.id} in ${delayMs / 1000}s")
            observeAndReschedule(request.id, operation.id)
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: failed to schedule op=${operation.id}")
        }
    }

    /**
     * Run [operationId] immediately regardless of its next scheduled time.
     * Used by the "Run now" button in the UI.
     */
    fun runNow(operationId: Long) {
        try {
            val inputData = Data.Builder()
                .putLong(ScheduledOperationsWorker.KEY_OPERATION_ID, operationId)
                .build()
            val request = OneTimeWorkRequestBuilder<ScheduledOperationsWorker>()
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("sched_op_$operationId")
                .addTag(TAG_SCHEDULED_OP)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sched_op_$operationId",
                ExistingWorkPolicy.REPLACE,
                request
            )
            Timber.i("WorkManagerScheduler: runNow op=$operationId")
            observeAndReschedule(request.id, operationId)
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: failed to runNow op=$operationId")
        }
    }

    /** Cancel a specific scheduled operation worker. */
    fun cancelOperation(operationId: Long) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork("sched_op_$operationId")
            Timber.i("WorkManagerScheduler: cancelled op=$operationId")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: failed to cancel op=$operationId")
        }
    }

    /** Cancel ALL scheduled operation workers (e.g. when the user clears the table). */
    fun cancelAllScheduledOperations() {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG_SCHEDULED_OP)
            Timber.i("WorkManagerScheduler: cancelled all scheduled operations")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: failed to cancel all scheduled ops")
        }
    }

    /**
     * Re-schedule all enabled operations from the DB.
     * Called on BOOT_COMPLETED and on app start if the feature is enabled.
     */
    suspend fun rescheduleAll() {
        try {
            val ops = scheduledOperationRepository.getAllEnabled()
            ops.forEach { scheduleOperation(it) }
            Timber.i("WorkManagerScheduler: rescheduled ${ops.size} scheduled operations")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: rescheduleAll failed")
        }
    }

    /**
     * Run every enabled operation once, as a single serialized WorkManager chain so at most one
     * operation runs at a time (no parallel foreground services). Skipped while the scheduler is
     * paused. The timed schedule continues independently of this manual burst.
     */
    suspend fun runAllNow() {
        try {
            if (settingsRepository.getSettings().first().scheduledOperationsPaused) {
                Timber.i("WorkManagerScheduler: runAllNow skipped - scheduler paused")
                return
            }
            val ops = scheduledOperationRepository.getAllEnabled()
            if (ops.isEmpty()) {
                Timber.i("WorkManagerScheduler: runAllNow - no enabled operations")
                return
            }
            val requests = ops.map { op ->
                OneTimeWorkRequestBuilder<ScheduledOperationsWorker>()
                    .setInputData(Data.Builder().putLong(ScheduledOperationsWorker.KEY_OPERATION_ID, op.id).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .addTag("sched_op_run_all")
                    .addTag(TAG_SCHEDULED_OP)
                    .build()
            }
            var continuation = WorkManager.getInstance(context)
                .beginUniqueWork("sched_op_run_all", ExistingWorkPolicy.REPLACE, requests.first())
            for (r in requests.drop(1)) { continuation = continuation.then(r) }
            continuation.enqueue()
            Timber.i("WorkManagerScheduler: runAllNow enqueued ${requests.size} serialized op(s)")
            com.sza.fastmediasorter.widget.ScheduledTasksWidgetRefresher.refresh(context)
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: runAllNow failed")
        }
    }

    /** Durably pause the scheduler: persist the paused flag and cancel all scheduled workers. */
    suspend fun pauseAll() {
        try {
            settingsRepository.updateScheduledOperationsPaused(true)
            cancelAllScheduledOperations()
            Timber.i("WorkManagerScheduler: pauseAll - scheduler paused, workers cancelled")
            com.sza.fastmediasorter.widget.ScheduledTasksWidgetRefresher.refresh(context)
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: pauseAll failed")
        }
    }

    /** Durably resume the scheduler: clear the paused flag and reschedule all enabled operations. */
    suspend fun resumeAll() {
        try {
            settingsRepository.updateScheduledOperationsPaused(false)
            rescheduleAll()
            Timber.i("WorkManagerScheduler: resumeAll - scheduler resumed, operations rescheduled")
            com.sza.fastmediasorter.widget.ScheduledTasksWidgetRefresher.refresh(context)
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: resumeAll failed")
        }
    }

    // -------------------------------------------------------------------------
    // X.1: Duplicate detection scan (one-time)
    // -------------------------------------------------------------------------

    fun enqueueDuplicateScan(resourceIds: List<Long>) {
        try {
            val inputData = androidx.work.Data.Builder()
                .putLongArray(DuplicateDetectionWorker.KEY_RESOURCE_IDS, resourceIds.toLongArray())
                .build()
            val request = OneTimeWorkRequestBuilder<DuplicateDetectionWorker>()
                .setInputData(inputData)
                .addTag("duplicate_scan")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                DuplicateDetectionWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Timber.i("WorkManagerScheduler: duplicate scan enqueued for ${resourceIds.size} resource(s)")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: failed to enqueue duplicate scan")
        }
    }

    fun cancelDuplicateScan() {
        try {
            WorkManager.getInstance(context)
                .cancelUniqueWork(DuplicateDetectionWorker.UNIQUE_WORK_NAME)
            Timber.i("WorkManagerScheduler: duplicate scan cancelled")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: failed to cancel duplicate scan")
        }
    }

    // -------------------------------------------------------------------------
    // X.11: Background thumbnail preload (one-time per resource)
    // -------------------------------------------------------------------------

    /**
     * Enqueue a one-time thumbnail preload job for [resourceId].
     * Uses KEEP policy - if a job is already pending/running for this resource it is not replaced.
     *
     * @param resourceId ID of the network resource to preload thumbnails for.
     * @param wifiOnly If true, adds NetworkType.UNMETERED constraint (default: true).
     */
    fun scheduleThumbnailPreload(resourceId: Long, wifiOnly: Boolean = true) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            val inputData = Data.Builder()
                .putLong(ThumbnailPreloadWorker.KEY_RESOURCE_ID, resourceId)
                .build()
            val request = OneTimeWorkRequestBuilder<ThumbnailPreloadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .addTag("thumbnail_preload")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ThumbnailPreloadWorker.workName(resourceId),
                ExistingWorkPolicy.KEEP,
                request
            )
            Timber.i("WorkManagerScheduler: queued thumbnail preload for resource $resourceId (wifiOnly=$wifiOnly)")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: failed to schedule thumbnail preload for resource $resourceId")
        }
    }

    /** Cancel any pending/running thumbnail preload for [resourceId]. */
    fun cancelThumbnailPreload(resourceId: Long) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(ThumbnailPreloadWorker.workName(resourceId))
            Timber.i("WorkManagerScheduler: cancelled thumbnail preload for resource $resourceId")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: failed to cancel thumbnail preload for resource $resourceId")
        }
    }

    /** Cancel ALL pending/running thumbnail preload jobs across all resources. */
    fun cancelAllThumbnailPreloads() {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag("thumbnail_preload")
            Timber.i("WorkManagerScheduler: cancelled all thumbnail preload jobs")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: failed to cancel all thumbnail preloads")
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * After [requestId] reaches a terminal state, reschedule the next run of [operationId]
     * if the work succeeded. Called from scheduleOperation/runNow to break the self-scheduling
     * race that occurred when enqueueUniqueWork(REPLACE) was called inside doWork.
     */
    private fun observeAndReschedule(requestId: UUID, operationId: Long) {
        scope.launch {
            try {
                val workInfo = WorkManager.getInstance(context)
                    .getWorkInfoByIdFlow(requestId)
                    .filter { it?.state?.isFinished == true }
                    .first()
                if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
                    val updated = scheduledOperationRepository.getById(operationId)
                    if (updated != null && updated.isEnabled) {
                        scheduleOperation(updated)
                    }
                } else {
                    Timber.w("WorkManagerScheduler: op=$operationId finished with state=${workInfo?.state} - skipping reschedule")
                }
            } catch (e: Exception) {
                Timber.e(e, "WorkManagerScheduler: reschedule observer failed for op=$operationId")
            }
        }
    }

    /**
     * Enqueue a one-shot streaming-cache GC pass on app start.
     * Safe to call on every launch - WorkManager dedupes by unique name.
     *
     * @param ttlDays TTL in days for the eviction pass; `0` disables TTL eviction
     *   but still runs the verify-and-prune step.
     */
    fun scheduleStreamingCacheGc(ttlDays: Int = StreamingCacheStartupGcWorker.DEFAULT_TTL_DAYS) {
        try {
            val inputData = Data.Builder()
                .putInt(StreamingCacheStartupGcWorker.KEY_TTL_DAYS, ttlDays)
                .build()
            val request = OneTimeWorkRequestBuilder<StreamingCacheStartupGcWorker>()
                .setInputData(inputData)
                .setInitialDelay(30, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                StreamingCacheStartupGcWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
            Timber.i("WorkManagerScheduler: streaming-cache GC enqueued (ttlDays=$ttlDays)")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: failed to enqueue streaming-cache GC")
        }
    }

    fun schedulePendingRevocation() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<PendingRevocationWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(2, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PendingRevocationWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Timber.i("WorkManagerScheduler: Pending revocation worker scheduled (every 6 h)")
        } catch (e: Exception) {
            Timber.e(e, "WorkManagerScheduler: Failed to schedule pending revocation worker")
        }
    }
}
