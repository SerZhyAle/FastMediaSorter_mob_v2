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

    // S1200: sets whose download was forced over an installed payload. Needed because "no work info
    // yet AND a payload on disk" otherwise reads as Installed - true for a set nobody asked to
    // re-download, but a lie in the gap between enqueue and WorkManager registering the request. That
    // lie is expensive: the caller stamps the payload as current on Installed, so a forced update that
    // had not started yet would be recorded as done and never offered again.
    private val forced = java.util.Collections.synchronizedSet(mutableSetOf<DeliverableSet>())

    override fun enqueue(set: DeliverableSet, force: Boolean) {
        if (!force && repository.isInstalledBlocking(set)) return
        if (force) forced.add(set)

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
                // S1200: the newest run wins. A stale payload is re-downloaded while its previous,
                // long-finished work entry is still on record, and reading that stale entry would
                // report the old outcome for the new attempt.
                val workInfo = workInfos.firstOrNull { !it.state.isFinished } ?: workInfos.firstOrNull()
                if (workInfo == null) {
                    when {
                        // A forced re-download is on its way; the payload still on disk is the OLD one.
                        forced.contains(set) -> DownloadProgress.Queued
                        repository.isInstalledBlocking(set) -> DownloadProgress.Installed
                        else -> DownloadProgress.Failed("Not enqueued")
                    }
                } else {
                    if (workInfo.state.isFinished) forced.remove(set)
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
