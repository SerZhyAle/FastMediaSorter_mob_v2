package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.transfer.drive.DriveChannelPollCoordinator
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4231: starts the Drive channel's foreground poll and keeps the background poll in step with the two
 * switches - scheduled at the platform's 15-minute minimum only while both the channel and its background
 * option are on, cancelled the moment either goes off (contract DEVICE-EXCHANGE item CQ). A build without
 * the cloud capability never touches either.
 */
@Singleton
class DriveChannelBackgroundScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: DriveChannelSettings,
    private val coordinator: DriveChannelPollCoordinator,
    private val capabilityAvailability: CapabilityAvailability,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val started = AtomicBoolean(false)

    fun start() {
        if (!capabilityAvailability.isCloudAvailable() || !started.compareAndSet(false, true)) return
        coordinator.start()
        scope.launch {
            combine(settings.enabled, settings.backgroundPoll) { enabled, background -> enabled && background }
                .distinctUntilChanged()
                .collect { wanted -> if (wanted) schedule() else cancel() }
        }
    }

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<DriveChannelPollWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(DriveChannelPollWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        Timber.i("DriveChannelBackgroundScheduler: background poll scheduled every %d min", INTERVAL_MINUTES)
    }

    private fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(DriveChannelPollWorker.WORK_NAME)
    }

    private companion object {
        /** The platform's periodic-work minimum. */
        const val INTERVAL_MINUTES = 15L
        const val BACKOFF_SECONDS = 30L
    }
}
