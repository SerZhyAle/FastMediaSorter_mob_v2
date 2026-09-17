package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketStatus
import com.sza.fastmediasorter.domain.model.transfer.PacketNotInQueue
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3040: the cross-device packet queue on top of the Drive AppData data source.
 *
 * A packet the queue cannot read - no manifest yet, or one from a newer build - is skipped rather
 * than reported: the receiver's list must stay usable while a sender is still uploading.
 */
@Singleton
class GoogleDriveCrossDeviceTransferRepositoryImpl @Inject constructor(
    private val dataSource: GoogleDriveCrossDeviceTransferDataSource
) : CrossDeviceTransferRepository {

    override suspend fun publishPacket(
        manifest: CrossDevicePacketManifest,
        payloadBytes: Map<String, ByteArray>
    ): Result<Unit> = guarded {
        val queueFolderId = dataSource.ensureQueueFolder().getOrThrow()
        val packetFolderId = dataSource.ensurePacketFolder(queueFolderId, manifest.packetId).getOrThrow()
        payloadBytes.forEach { (fileName, content) ->
            dataSource.uploadPayloadFile(packetFolderId, fileName, content).getOrThrow()
        }
        // Manifest last: until it lands the packet is invisible to listPendingPackets, so a failed
        // payload upload leaves nothing half-delivered for the receiver to apply.
        dataSource.writeManifest(packetFolderId, manifest).getOrThrow()
    }

    override suspend fun listPendingPackets(): Result<List<CrossDevicePacketManifest>> = guarded {
        val queueFolderId = dataSource.ensureQueueFolder().getOrThrow()
        val now = System.currentTimeMillis()
        dataSource.listPacketFolders(queueFolderId).getOrThrow()
            .mapNotNull { folder -> dataSource.readManifest(folder.id).getOrNull() }
            .filter { it.status == CrossDevicePacketStatus.PENDING && !it.isExpiredAt(now) }
    }

    override suspend fun fetchPayload(packetId: String, fileName: String): Result<ByteArray> = guarded {
        val packetFolderId = resolvePacketFolderId(packetId)
        dataSource.downloadPayloadFile(packetFolderId, fileName).getOrThrow()
    }

    override suspend fun claimPacket(packetId: String, deleteFromCloud: Boolean): Result<Unit> = guarded {
        val packetFolderId = resolvePacketFolderId(packetId)
        if (deleteFromCloud) {
            dataSource.deletePacketFolder(packetFolderId).getOrThrow()
        } else {
            val manifest = dataSource.readManifest(packetFolderId).getOrThrow()
                ?: throw PacketNotInQueue(packetId)
            dataSource.writeManifest(
                packetFolderId,
                manifest.copy(status = CrossDevicePacketStatus.CLAIMED)
            ).getOrThrow()
        }
    }

    override suspend fun purgeExpiredPackets(maxAgeMs: Long): Result<Int> = guarded {
        val queueFolderId = dataSource.ensureQueueFolder().getOrThrow()
        val cutoff = System.currentTimeMillis() - maxAgeMs
        dataSource.listPacketFolders(queueFolderId).getOrThrow()
            .count { folder -> purgeIfStale(folder, cutoff) }
    }

    /**
     * Delete one packet whose manifest, or failing that its Drive folder, predates [cutoff].
     *
     * The folder timestamp is the fallback rather than "purge whatever cannot be read": a sender
     * mid-upload has no manifest yet, and purging on that alone would delete the packet out from
     * under it. An orphan left by an interrupted upload still goes, one TTL later.
     */
    private suspend fun purgeIfStale(folder: CloudFile, cutoff: Long): Boolean {
        val manifest = dataSource.readManifest(folder.id).getOrNull()
        val createdAt = manifest?.createdAtEpochMs ?: folder.modifiedDate
        return if (createdAt < cutoff) {
            dataSource.deletePacketFolder(folder.id).isSuccess
        } else {
            false
        }
    }

    /**
     * `runCatching` with the one exception a coroutine must never absorb.
     *
     * Every call here is a cloud round trip on a screen the user can leave: a plain `runCatching`
     * would turn the resulting cancellation into `Result.failure` and the UI would report a failed
     * transfer for a screen that is already gone.
     */
    private suspend fun <T> guarded(block: suspend () -> T): Result<T> =
        runCatching { block() }.onFailure { it.rethrowIfCancellation() }

    private suspend fun resolvePacketFolderId(packetId: String): String {
        val queueFolderId = dataSource.ensureQueueFolder().getOrThrow()
        return dataSource.listPacketFolders(queueFolderId).getOrThrow()
            .firstOrNull { it.name == packetId }
            ?.id
            ?: throw PacketNotInQueue(packetId)
    }
}
