package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import javax.inject.Inject

/**
 * S3040: the packets this device may claim - everything pending except its own sends.
 *
 * A packet naming another device is hidden rather than merely sorted down: the queue is one shared
 * space, and offering the tablet's packet on the phone is how a transfer gets claimed twice.
 */
class GetPendingCrossDevicePacketsUseCase @Inject constructor(
    private val repository: CrossDeviceTransferRepository
) {

    suspend operator fun invoke(localDeviceName: String): Result<List<CrossDevicePacketManifest>> =
        repository.listPendingPackets().map { packets ->
            packets
                .filterNot { it.senderDeviceName == localDeviceName }
                .filter { it.targetDeviceName == null || it.targetDeviceName == localDeviceName }
                .sortedByDescending { it.createdAtEpochMs }
        }
}
