package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.transfer.DriveRefusalReason
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.transfer.DriveFileChannel
import com.sza.fastmediasorter.domain.transfer.DriveProducerTarget
import com.sza.fastmediasorter.domain.transfer.DriveRequestProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import java.io.File
import java.time.Instant
import javax.inject.Inject

/**
 * S4232: the Google Drive leg of a scheduled copy whose source is another own device's published
 * folder and whose live paths all failed. It asks the producer for the files changed after the
 * schedule's own window start (contract DEVICE-EXCHANGE item CO `changes`) and waits for the `final`
 * reply; the files land in the incoming folder, and the schedule moves them on like any local file.
 */
class FetchScheduledChangesViaDriveUseCase @Inject constructor(
    private val settings: DriveChannelSettings,
    private val resolveProducer: ResolveDriveProducerUseCase,
    private val channel: DriveFileChannel,
) {

    sealed interface Outcome {
        /** The record has no Drive path: the channel is off, or the folder is not published on Drive. */
        data object NoDrivePath : Outcome

        /** Every reply arrived; [placed] are local paths in the incoming folder, possibly none. */
        data class Fetched(val placed: List<String>) : Outcome

        /** The Drive path answered with no usable result; nothing it placed is left behind. */
        data class Unavailable(val reason: String) : Outcome
    }

    /** The producer behind [resource], or null when the record carries no Drive path. */
    suspend fun producerFor(resource: MediaResource): DriveProducerTarget? {
        if (resource.exchangeResourceId == null || !settings.enabled.first()) return null
        // The lookup folds every Drive failure into null, which the run reads as "no Drive path".
        return resolveProducer(resource)
    }

    /** Files of [producer]'s folder modified after [modifiedAfterMs]; null asks for every file. */
    suspend operator fun invoke(producer: DriveProducerTarget, modifiedAfterMs: Long?): Outcome {
        if (producer.pinProtected) return Outcome.Unavailable(PIN_REASON)
        val placed = mutableListOf<String>()
        var ending: DriveRequestProgress? = null
        channel.changes(
            producer.deviceId,
            producer.resourceId,
            producer.rootPath,
            modifiedAfterMs?.let { Instant.ofEpochMilli(it).toString() }
        ).takeWhile { progress ->
            if (progress is DriveRequestProgress.Files) placed += progress.placed
            val goOn = progress is DriveRequestProgress.Waiting ||
                progress is DriveRequestProgress.Files && !progress.final
            if (!goOn) ending = progress
            goOn
        }.collect { }
        val last = ending
        return if (last is DriveRequestProgress.Files) {
            Outcome.Fetched(placed.toList())
        } else {
            // A partial answer cannot move the window on, so its files would only pile up in the incoming folder.
            placed.forEach { File(it).delete() }
            Outcome.Unavailable(reasonFor(last, producer.deviceName))
        }
    }

    private fun reasonFor(progress: DriveRequestProgress?, deviceName: String): String = when (progress) {
        is DriveRequestProgress.Refused -> refusalReason(progress.reason)
        is DriveRequestProgress.TimedOut -> "via Google Drive: '$deviceName' did not answer"
        DriveRequestProgress.ChannelOff -> "via Google Drive: Files via Google Drive is off"
        is DriveRequestProgress.Failed -> "via Google Drive: ${progress.error.message ?: "request failed"}"
        else -> "via Google Drive: the answer ended early"
    }

    private fun refusalReason(reason: DriveRefusalReason): String = when (reason) {
        DriveRefusalReason.PIN_REQUIRED -> PIN_REASON
        DriveRefusalReason.NOT_PERMITTED -> "via Google Drive: the folder is not shared via Google Drive"
        DriveRefusalReason.UNAVAILABLE -> "via Google Drive: the folder is not available on the other device"
        DriveRefusalReason.TOO_LARGE -> "via Google Drive: the answer is too large"
        DriveRefusalReason.DRIVE_FULL -> "via Google Drive: Google Drive is full"
    }

    private companion object {
        // The producer refuses a PIN-locked root on Drive (item CO), so the request is not even sent.
        const val PIN_REASON = "via Google Drive: the folder is protected by a PIN - only a direct connection opens it"
    }
}
