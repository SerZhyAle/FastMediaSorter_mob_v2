package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
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
    private val scheduledOperationRepository: ScheduledOperationRepository
) {
    
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
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sched_op_${operation.id}",
                ExistingWorkPolicy.REPLACE,
                request
            )
            Timber.i("WorkManagerScheduler: scheduled op=${operation.id} in ${delayMs / 1000}s")
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
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("sched_op_$operationId")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sched_op_$operationId",
                ExistingWorkPolicy.REPLACE,
                request
            )
            Timber.i("WorkManagerScheduler: runNow op=$operationId")
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
            WorkManager.getInstance(context).cancelAllWorkByTag("sched_op")
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
