package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTarget
import com.sza.fastmediasorter.domain.model.transfer.DriveQueueFull
import com.sza.fastmediasorter.domain.model.transfer.DriveQuota
import com.sza.fastmediasorter.domain.model.transfer.OutgoingPacketFile
import com.sza.fastmediasorter.domain.model.transfer.PacketTooLarge
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentity
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import java.util.UUID
import javax.inject.Inject

/**
 * S3040: package settings or files as one cross-device packet and leave it in the Drive queue.
 *
 * The device identity arrives as a parameter rather than being read here: the domain layer has no
 * device identity of its own, and the sending surface already knows both ends of the transfer.
 *
 * S4231: a version 2 packet - device ids, per-file size and SHA-256 - refused before any byte when it
 * exceeds `packetBytes` or would not fit on Drive with 1% of the limit to spare (contract item CS).
 * Success means the manifest was written; delivery is the target deleting the packet (item CT).
 */
class SendCrossDevicePacketUseCase @Inject constructor(
    private val repository: CrossDeviceTransferRepository,
    private val settings: DriveChannelSettings
) {

    suspend operator fun invoke(
        payloadKind: CrossDevicePayloadKind,
        sender: LocalDeviceIdentity,
        target: CrossDeviceTarget?,
        files: List<OutgoingPacketFile>,
        onProgress: ((Long) -> Unit)? = null
    ): Result<CrossDevicePacketManifest> {
        val total = files.sumOf { it.size }
        val limit = settings.packetBytes()
        return if (total > limit) {
            Result.failure(PacketTooLarge(limit))
        } else {
            repository.readStorageQuota().fold(
                onSuccess = { quota ->
                    spaceRefusal(quota, total)?.let { Result.failure(it) }
                        ?: repository.publishPacket(manifestFor(payloadKind, sender, target, files), files, onProgress)
                },
                onFailure = { Result.failure(it) }
            )
        }
    }

    private fun manifestFor(
        payloadKind: CrossDevicePayloadKind,
        sender: LocalDeviceIdentity,
        target: CrossDeviceTarget?,
        files: List<OutgoingPacketFile>
    ): CrossDevicePacketManifest = CrossDevicePacketManifest(
        // The share-id alphabet of item CN: no dashes, so the id is safe as a Drive folder name.
        packetId = UUID.randomUUID().toString().replace("-", ""),
        createdAtEpochMs = System.currentTimeMillis(),
        senderDeviceName = sender.displayName,
        targetDeviceName = target?.displayName,
        payloadKind = payloadKind,
        fileNames = files.map { it.name },
        totalSizeBytes = files.sumOf { it.size },
        schemaVersion = CrossDevicePacketManifest.SCHEMA_V2,
        senderDeviceId = sender.deviceId,
        targetDeviceId = target?.deviceId
    )

    companion object {
        private const val RESERVE_DIVISOR = 100L

        /** The refusal for a packet of [total] bytes on [quota], or null when it fits with 1% to spare. */
        fun spaceRefusal(quota: DriveQuota, total: Long): DriveQueueFull? {
            val limit = quota.limitBytes ?: return null
            val free = (limit - quota.usageBytes).coerceAtLeast(0L)
            return if (quota.usageBytes + total > limit - limit / RESERVE_DIVISOR) DriveQueueFull(free) else null
        }
    }
}
