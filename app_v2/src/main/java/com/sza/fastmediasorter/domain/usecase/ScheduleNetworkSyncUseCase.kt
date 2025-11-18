package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sza.fastmediasorter.worker.NetworkFilesSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * UseCase for scheduling periodic background sync of network files
 */
class ScheduleNetworkSyncUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Schedule periodic network file sync
     * @param intervalHours Sync interval in hours (default 4 hours)
     * @param requiresNetwork Whether to require network connectivity (default true)
     * @param initialDelayMinutes Initial delay before first sync (default 5 minutes)
     */
    operator fun invoke(
        intervalHours: Long = 4, 
        requiresNetwork: Boolean = true,
        initialDelayMinutes: Long = 5
    ) {
        Timber.d("ScheduleNetworkSyncUseCase: Scheduling sync with interval=$intervalHours hours, requiresNetwork=$requiresNetwork, initialDelay=${initialDelayMinutes}min")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true) // Don't run on low battery
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<NetworkFilesSyncWorker>(
            intervalHours, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES) // Delay first sync
            .addTag(NetworkFilesSyncWorker.WORK_NAME)
            .build()
        
        // Replace existing work (REPLACE policy ensures only one instance runs)
        @Suppress("DEPRECATION")
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NetworkFilesSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            syncRequest
        )
        
        Timber.i("ScheduleNetworkSyncUseCase: Network sync scheduled successfully with ${initialDelayMinutes}min initial delay")
    }
    
    /**
     * Cancel scheduled network sync
     */
    fun cancel() {
        Timber.d("ScheduleNetworkSyncUseCase: Cancelling network sync")
        WorkManager.getInstance(context).cancelUniqueWork(NetworkFilesSyncWorker.WORK_NAME)
    }
}
