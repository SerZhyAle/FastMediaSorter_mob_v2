package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import javax.inject.Inject

/** S4231: the person deletes any packet the queue shows (contract DEVICE-EXCHANGE item CS). */
class DeleteCrossDevicePacketUseCase @Inject constructor(
    private val repository: CrossDeviceTransferRepository
) {

    suspend operator fun invoke(entry: CrossDevicePacketEntry): Result<Unit> = repository.deletePacket(entry)
}
