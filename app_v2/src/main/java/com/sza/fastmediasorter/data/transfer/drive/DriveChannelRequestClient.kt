package com.sza.fastmediasorter.data.transfer.drive

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.cloud.GoogleDriveSftpRendezvousDataSource
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketCodec
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketExpiry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueSnapshot
import com.sza.fastmediasorter.domain.model.transfer.DriveChannelRequest
import com.sza.fastmediasorter.domain.model.transfer.DriveListing
import com.sza.fastmediasorter.domain.model.transfer.DriveReplyOutcome
import com.sza.fastmediasorter.domain.model.transfer.PacketIntegrityFailure
import com.sza.fastmediasorter.domain.port.IncomingFileResult
import com.sza.fastmediasorter.domain.port.IncomingTransferFileSink
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentity
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentitySource
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.transfer.DriveQueueSnapshots
import com.sza.fastmediasorter.domain.transfer.DriveRequestProgress
import com.sza.fastmediasorter.domain.transfer.PollDemand
import com.sza.fastmediasorter.domain.usecase.transfer.ReceiveCrossDevicePacketUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** S4231: who answers a request - the name and the last Drive time its device record was written. */
data class DriveProducer(val deviceId: String, val name: String, val lastSeenMs: Long?)

/** S4231: looks up a producer's device record; split out so the request client is testable without Drive. */
fun interface DriveProducerLookup {
    suspend fun find(deviceId: String): DriveProducer
}

/** S4231: the device records this phone already reads for the "From my devices" directory. */
class RendezvousDriveProducerLookup @Inject constructor(
    private val records: GoogleDriveSftpRendezvousDataSource,
) : DriveProducerLookup {

    override suspend fun find(deviceId: String): DriveProducer {
        val entry = records.snapshot().getOrNull()?.devices?.firstOrNull { it.record.deviceId == deviceId }
        return DriveProducer(deviceId, entry?.record?.deviceName ?: deviceId, entry?.modifiedMs)
    }
}

/**
 * S4231: the consumer half of contract DEVICE-EXCHANGE item CO. Writes a `REQUEST`, waits on the shared
 * poll for the `REPLY` packets naming it, takes their files through the integrity-checking sink, deletes
 * each reply it took, and gives up after 120 seconds without a reply, deleting its own request (item CQ).
 */
@Singleton
class DriveChannelRequestClient @Inject constructor(
    private val repository: CrossDeviceTransferRepository,
    private val sink: IncomingTransferFileSink,
    private val snapshots: DriveQueueSnapshots,
    private val identity: LocalDeviceIdentitySource,
    private val settings: DriveChannelSettings,
    private val producers: DriveProducerLookup,
) {

    internal var waitMs: Long = DEFAULT_WAIT_MS

    fun request(producerDeviceId: String, request: DriveChannelRequest): Flow<DriveRequestProgress> = flow {
        if (!settings.enabled.first()) {
            emit(DriveRequestProgress.ChannelOff)
        } else {
            val self = identity.current()
            val producer = producers.find(producerDeviceId)
            val written = repository.publishPacket(requestManifest(self, producer, request), emptyList())
            written.fold(
                onSuccess = { manifest -> await(manifest.packetId, self, producer) },
                onFailure = { emit(DriveRequestProgress.Failed(it)) }
            )
        }
    }

    private suspend fun FlowCollector<DriveRequestProgress>.await(
        requestId: String,
        self: LocalDeviceIdentity,
        producer: DriveProducer
    ) {
        val demand = snapshots.acquire(PollDemand.ACTIVE)
        var finished = false
        try {
            emit(DriveRequestProgress.Waiting(producer.name, producer.lastSeenMs))
            val taken = HashSet<String>()
            while (!finished) {
                val reply = withTimeoutOrNull(waitMs) {
                    snapshots.snapshots.mapNotNull { nextReply(it, requestId, self, taken) }.first()
                }
                if (reply == null) {
                    emit(DriveRequestProgress.TimedOut(producer.name, producer.lastSeenMs))
                    break
                }
                taken += reply.packetId
                finished = take(reply)
            }
        } finally {
            demand.close()
            // Giving up - a timeout or a collector that left - removes the request so the producer does not
            // answer into a queue nobody reads (item CR); after the final reply the producer deletes it.
            if (!finished) withContext(NonCancellable) { deleteRequest(requestId) }
        }
    }

    /** Process one reply; true when it was the last of the answer. */
    private suspend fun FlowCollector<DriveRequestProgress>.take(reply: CrossDevicePacketEntry): Boolean {
        val header = reply.manifest.reply
        val final = header?.final ?: true
        when {
            header?.outcome == DriveReplyOutcome.REFUSED ->
                emit(DriveRequestProgress.Refused(requireNotNull(header.reason)))
            DriveListing.FILE_NAME in reply.manifest.fileNames -> emit(readListing(reply))
            else -> emit(DriveRequestProgress.Files(receiveFiles(reply), header?.asOf, final))
        }
        repository.deletePacket(reply).onFailure { it.rethrowIfCancellation() }
        return final || header?.outcome == DriveReplyOutcome.REFUSED
    }

    private suspend fun readListing(reply: CrossDevicePacketEntry): DriveRequestProgress {
        val raw = repository.openPayload(reply, DriveListing.FILE_NAME).getOrThrow()
            .use { it.readBytes().decodeToString() }
        val listing = CrossDevicePacketCodec.listingFromJson(raw)
        return if (listing != null) {
            DriveRequestProgress.Listing(listing)
        } else {
            DriveRequestProgress.Failed(PacketIntegrityFailure(DriveListing.FILE_NAME))
        }
    }

    private suspend fun receiveFiles(reply: CrossDevicePacketEntry): List<String> =
        reply.manifest.fileNames.mapNotNull { name ->
            val described = reply.manifest.files.firstOrNull { it.name == name }
            val result = repository.openPayload(reply, name).getOrThrow().use { stream ->
                sink.receive(name, described?.size, described?.sha256, stream)
            }
            (result as? IncomingFileResult.Placed)?.path
        }

    private suspend fun deleteRequest(requestId: String) {
        repository.listQueue().getOrNull()?.entries
            ?.firstOrNull { it.packetId == requestId }
            ?.let { repository.deletePacket(it) }
    }

    private fun requestManifest(
        self: LocalDeviceIdentity,
        producer: DriveProducer,
        request: DriveChannelRequest
    ) = CrossDevicePacketManifest(
        packetId = UUID.randomUUID().toString().replace("-", ""),
        createdAtEpochMs = System.currentTimeMillis(),
        senderDeviceName = self.displayName,
        targetDeviceName = producer.name,
        payloadKind = CrossDevicePayloadKind.REQUEST,
        fileNames = emptyList(),
        totalSizeBytes = 0L,
        schemaVersion = CrossDevicePacketManifest.SCHEMA_V2,
        senderDeviceId = self.deviceId,
        targetDeviceId = producer.deviceId,
        ttlSeconds = CrossDevicePacketExpiry.REQUEST_TTL_SECONDS,
        request = request
    )

    companion object {
        /** The foreground wait of item CQ. */
        val DEFAULT_WAIT_MS: Long = TimeUnit.SECONDS.toMillis(WAIT_SECONDS)

        /** The earliest not yet taken `REPLY` to [requestId] addressed to [self], in Drive order. */
        fun nextReply(
            snapshot: CrossDeviceQueueSnapshot,
            requestId: String,
            self: LocalDeviceIdentity,
            taken: Set<String>
        ): CrossDevicePacketEntry? = snapshot.entries
            .filter { it.manifest.payloadKind == CrossDevicePayloadKind.REPLY && it.manifest.inReplyTo == requestId }
            .filter { ReceiveCrossDevicePacketUseCase.isTargetedAt(it.manifest, self) && it.packetId !in taken }
            .minByOrNull { it.createdTimeMs }
    }
}

private const val WAIT_SECONDS = 120L
