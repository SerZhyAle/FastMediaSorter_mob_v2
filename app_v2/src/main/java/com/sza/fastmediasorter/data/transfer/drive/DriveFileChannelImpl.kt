package com.sza.fastmediasorter.data.transfer.drive

import com.sza.fastmediasorter.domain.model.transfer.ClipKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTarget
import com.sza.fastmediasorter.domain.model.transfer.DriveChannelOff
import com.sza.fastmediasorter.domain.model.transfer.DriveChannelRequest
import com.sza.fastmediasorter.domain.model.transfer.OutgoingPacketFile
import com.sza.fastmediasorter.domain.port.IncomingFileResult
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentitySource
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.transfer.DriveClipboardValue
import com.sza.fastmediasorter.domain.transfer.DriveFileChannel
import com.sza.fastmediasorter.domain.transfer.DriveQueueSnapshots
import com.sza.fastmediasorter.domain.transfer.DriveRequestProgress
import com.sza.fastmediasorter.domain.transfer.PollDemand
import com.sza.fastmediasorter.domain.usecase.transfer.SendCrossDevicePacketUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** S4231: the one [DriveFileChannel] - sending, request/reply and the clipboard over the shared poll. */
@Singleton
class DriveFileChannelImpl @Inject constructor(
    private val sendPacket: SendCrossDevicePacketUseCase,
    private val identity: LocalDeviceIdentitySource,
    private val settings: DriveChannelSettings,
    private val snapshots: DriveQueueSnapshots,
    private val requests: DriveChannelRequestClient,
    private val clipboard: DriveClipboardChannel,
) : DriveFileChannel {

    override suspend fun sendFiles(
        target: CrossDeviceTarget?,
        files: List<OutgoingPacketFile>,
        kind: CrossDevicePayloadKind
    ): Result<CrossDevicePacketManifest> =
        if (!settings.enabled.first()) {
            Result.failure(DriveChannelOff())
        } else {
            // A send in progress is an active demand: the poll runs at 10 s while it lasts (item CQ).
            snapshots.acquire(PollDemand.ACTIVE).use {
                sendPacket(kind, identity.current(), target, files)
            }
        }

    override fun list(producerDeviceId: String, resourceId: String, path: String): Flow<DriveRequestProgress> =
        requests.request(producerDeviceId, DriveChannelRequest.ListFolder(resourceId, path))

    override fun fetch(
        producerDeviceId: String,
        resourceId: String,
        paths: List<String>
    ): Flow<DriveRequestProgress> = requests.request(producerDeviceId, DriveChannelRequest.Fetch(resourceId, paths))

    override fun changes(
        producerDeviceId: String,
        resourceId: String,
        path: String,
        modifiedAfter: String?
    ): Flow<DriveRequestProgress> =
        requests.request(producerDeviceId, DriveChannelRequest.Changes(resourceId, path, modifiedAfter))

    override suspend fun putClipboard(
        kind: ClipKind,
        name: String,
        mimeType: String,
        size: Long,
        open: () -> InputStream
    ): Result<Unit> = clipboard.put(kind, name, mimeType, size, open)

    override suspend fun latestClipboard(): Result<DriveClipboardValue?> = clipboard.latest()

    override suspend fun receiveClipboard(value: DriveClipboardValue): Result<IncomingFileResult> =
        clipboard.receive(value)
}
