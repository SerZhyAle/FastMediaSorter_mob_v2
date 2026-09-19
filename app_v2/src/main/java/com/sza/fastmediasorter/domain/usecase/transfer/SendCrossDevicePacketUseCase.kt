package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import java.util.UUID
import javax.inject.Inject

/**
 * S3040: package settings or files as one cross-device packet and leave it in the Drive queue.
 *
 * The device names arrive as parameters rather than being read here: the domain layer has no device
 * identity of its own, and the sending surface already knows both ends of the transfer.
 */
class SendCrossDevicePacketUseCase @Inject constructor(
    private val repository: CrossDeviceTransferRepository
) {

    suspend operator fun invoke(
        payloadKind: CrossDevicePayloadKind,
        senderDeviceName: String,
        targetDeviceName: String?,
        payloadBytes: Map<String, ByteArray>,
        ttlDays: Int = CrossDevicePacketManifest.DEFAULT_TTL_DAYS
    ): Result<CrossDevicePacketManifest> {
        val manifest = CrossDevicePacketManifest(
            packetId = UUID.randomUUID().toString(),
            createdAtEpochMs = System.currentTimeMillis(),
            senderDeviceName = senderDeviceName,
            targetDeviceName = targetDeviceName,
            payloadKind = payloadKind,
            fileNames = payloadBytes.keys.toList(),
            totalSizeBytes = payloadBytes.values.sumOf { it.size.toLong() },
            ttlDays = ttlDays
        )
        return repository.publishPacket(manifest, payloadBytes).map { manifest }
    }
}
