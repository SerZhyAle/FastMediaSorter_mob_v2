package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.transfer.OwnDriveDevice
import com.sza.fastmediasorter.domain.transfer.OwnDriveDevices
import javax.inject.Inject

/** S4231: the possible targets of a send via Google Drive, the most recently seen first. */
class GetOwnDriveDevicesUseCase @Inject constructor(
    private val devices: OwnDriveDevices
) {

    suspend operator fun invoke(): Result<List<OwnDriveDevice>> =
        devices.list().map { list -> list.sortedByDescending { it.lastSeenMs ?: Long.MIN_VALUE } }
}
