package com.sza.fastmediasorter_v2.domain.usecase

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sza.fastmediasorter_v2.worker.NetworkFilesSyncWorker
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
     */
    operator fun invoke(intervalHours: Long = 4, requiresNetwork: Boolean = true) {
        Timber.d("ScheduleNetworkSyncUseCase: Scheduling sync with interval=$intervalHours hours, requiresNetwork=$requiresNetwork")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true) // Don't run on low battery
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<NetworkFilesSyncWorker>(
            intervalHours, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(NetworkFilesSyncWorker.WORK_NAME)
            .build()
        
        // Replace existing work (REPLACE policy ensures only one instance runs)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NetworkFilesSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            syncRequest
        )
        
        Timber.i("ScheduleNetworkSyncUseCase: Network sync scheduled successfully")
    }
    
    /**
     * Cancel scheduled network sync
     */
    fun cancel() {
        Timber.d("ScheduleNetworkSyncUseCase: Cancelling network sync")
        WorkManager.getInstance(context).cancelUniqueWork(NetworkFilesSyncWorker.WORK_NAME)
    }
}
