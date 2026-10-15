package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.data.transfer.drive.DriveChannelPollCycle
import com.sza.fastmediasorter.domain.model.transfer.DriveTemporarilyUnavailable
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * S4231: one background poll of the Drive queue (contract DEVICE-EXCHANGE item CQ) - answers requests
 * addressed to this phone and runs the expiry purge while no screen of the app is open.
 *
 * Only a transient Drive failure asks WorkManager to retry; the daily limit and a lost sign-in report
 * success, so the platform never turns them into a retry loop.
 */
@HiltWorker
class DriveChannelPollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cycle: DriveChannelPollCycle,
    private val settings: DriveChannelSettings,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.enabled.first() || !settings.backgroundPoll.first()) return Result.success()
        val failure = cycle.run().exceptionOrNull()
        failure?.let { Timber.w("DriveChannelPollWorker: poll failed (%s)", it.javaClass.simpleName) }
        return if (failure is DriveTemporarilyUnavailable) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "drive_channel_poll"
    }
}
