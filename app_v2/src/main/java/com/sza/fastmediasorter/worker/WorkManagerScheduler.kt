package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for scheduling and managing WorkManager tasks
 * Currently handles periodic trash cleanup
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
}
