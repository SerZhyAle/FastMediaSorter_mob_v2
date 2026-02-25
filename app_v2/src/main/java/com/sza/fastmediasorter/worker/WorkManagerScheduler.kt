package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for scheduling and managing WorkManager tasks.
 * Handles periodic trash cleanup and background resource sync.
 */
@Singleton
class WorkManagerScheduler @Inject constructor(
    @ApplicationContext private val context: Context
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
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NetworkFilesSyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
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
}
