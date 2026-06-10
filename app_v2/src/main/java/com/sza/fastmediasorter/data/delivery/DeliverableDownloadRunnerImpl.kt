package com.sza.fastmediasorter.data.delivery

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableDownloadRunner
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DownloadProgress
import com.sza.fastmediasorter.worker.DeliverableDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [DeliverableDownloadRunner] that enqueues element downloads
 * in WorkManager and maps WorkInfo updates to [DownloadProgress] flows (S0397).
 */
@Singleton
class DeliverableDownloadRunnerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DeliverableCapabilityRepository
) : DeliverableDownloadRunner {

    private val workManager = WorkManager.getInstance(context)

    override fun enqueue(set: DeliverableSet) {
        if (repository.isInstalledBlocking(set)) return

        val data = workDataOf(DeliverableDownloadWorker.KEY_DELIVERABLE_SET to set.name)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DeliverableDownloadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .addTag("download_deliverable_${set.name}")
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(set),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    override fun progressOf(set: DeliverableSet): Flow<DownloadProgress> {
        return workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(set))
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull()
                if (workInfo == null) {
                    if (repository.isInstalledBlocking(set)) {
                        DownloadProgress.Installed
                    } else {
                        DownloadProgress.Failed("Not enqueued")
                    }
                } else {
                    mapWorkInfoToProgress(workInfo, set)
                }
            }
            .distinctUntilChanged()
    }

    private fun uniqueWorkName(set: DeliverableSet): String =
        "download_deliverable_${set.name}"

    private fun mapWorkInfoToProgress(workInfo: WorkInfo, set: DeliverableSet): DownloadProgress {
        return when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> DownloadProgress.Queued
            WorkInfo.State.RUNNING -> {
                val progressData = workInfo.progress
                when (progressData.getString("status")) {
                    "queued" -> DownloadProgress.Queued
                    "running" -> {
                        val percent = progressData.getInt("percent", 0)
                        val bytesDownloaded = progressData.getLong("bytesDownloaded", 0L)
                        val totalBytes = progressData.getLong("totalBytes", 0L)
                        DownloadProgress.Running(percent, bytesDownloaded, totalBytes)
                    }
                    "verifying" -> DownloadProgress.Verifying
                    else -> DownloadProgress.Queued
                }
            }
            WorkInfo.State.SUCCEEDED -> DownloadProgress.Installed
            WorkInfo.State.FAILED -> {
                val reason = workInfo.outputData.getString("reason") ?: "Failed"
                DownloadProgress.Failed(reason)
            }
            WorkInfo.State.CANCELLED -> DownloadProgress.Failed("Cancelled")
            WorkInfo.State.BLOCKED -> DownloadProgress.Queued
        }
    }
}
