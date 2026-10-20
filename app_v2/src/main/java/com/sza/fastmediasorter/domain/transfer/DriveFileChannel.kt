package com.sza.fastmediasorter.domain.transfer

import com.sza.fastmediasorter.domain.model.transfer.ClipKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTarget
import com.sza.fastmediasorter.domain.model.transfer.DriveListing
import com.sza.fastmediasorter.domain.model.transfer.DriveRefusalReason
import com.sza.fastmediasorter.domain.model.transfer.OutgoingPacketFile
import com.sza.fastmediasorter.domain.port.IncomingFileResult
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * S4231: the Drive channel as its consumers see it - sending to own devices, reading another own
 * device's published folder, and the clipboard (contract DEVICE-EXCHANGE items CO and CP).
 *
 * One implementation owns OAuth, the queue listing and the timers, so the schedule (S4232) and the
 * clipboard (S4226) never poll Drive on their own.
 */
interface DriveFileChannel {

    /** Write a packet for [target] (null = any own device); success means the manifest landed, not delivery. */
    suspend fun sendFiles(
        target: CrossDeviceTarget?,
        files: List<OutgoingPacketFile>,
        kind: CrossDevicePayloadKind = CrossDevicePayloadKind.FILES
    ): Result<CrossDevicePacketManifest>

    /** The entries of one folder of a resource [producerDeviceId] published. */
    fun list(producerDeviceId: String, resourceId: String, path: String): Flow<DriveRequestProgress>

    /** Up to 100 files of a published resource, saved to the incoming folder. */
    fun fetch(producerDeviceId: String, resourceId: String, paths: List<String>): Flow<DriveRequestProgress>

    /** The files under [path] newer than [modifiedAfter], the producer's `asOf` from the previous run. */
    fun changes(
        producerDeviceId: String,
        resourceId: String,
        path: String,
        modifiedAfter: String?
    ): Flow<DriveRequestProgress>

    suspend fun putClipboard(
        kind: ClipKind,
        name: String,
        mimeType: String,
        size: Long,
        open: () -> InputStream
    ): Result<Unit>

    /** The account's clipboard value: the committed clipboard packet with the latest Drive time. */
    suspend fun latestClipboard(): Result<DriveClipboardValue?>

    suspend fun receiveClipboard(value: DriveClipboardValue): Result<IncomingFileResult>
}

/** S4231: one clipboard value as the queue holds it. */
data class DriveClipboardValue(val entry: CrossDevicePacketEntry, val kind: ClipKind?)

/**
 * S4231: where a request stands. A collector that stops collecting gives the request up, which deletes
 * its own `REQUEST` (item CR).
 */
sealed interface DriveRequestProgress {

    /** Waiting for the producer to poll; [lastSeenMs] is when it last wrote its device record (Drive time). */
    data class Waiting(val producerName: String, val lastSeenMs: Long?) : DriveRequestProgress

    data class Listing(val listing: DriveListing) : DriveRequestProgress

    data class Files(val placed: List<String>, val asOf: String?, val final: Boolean) : DriveRequestProgress

    data class Refused(val reason: DriveRefusalReason) : DriveRequestProgress

    /** No reply within the foreground wait; the request was deleted (item CQ). */
    data class TimedOut(val producerName: String, val lastSeenMs: Long?) : DriveRequestProgress

    data object ChannelOff : DriveRequestProgress

    data class Failed(val error: Throwable) : DriveRequestProgress
}
