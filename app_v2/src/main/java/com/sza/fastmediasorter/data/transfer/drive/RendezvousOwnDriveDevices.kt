package com.sza.fastmediasorter.data.transfer.drive

import com.sza.fastmediasorter.data.cloud.GoogleDriveSftpRendezvousDataSource
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentitySource
import com.sza.fastmediasorter.domain.transfer.OwnDriveDevice
import com.sza.fastmediasorter.domain.transfer.OwnDriveDevices
import com.sza.fastmediasorter.domain.transfer.PublishedDriveResource
import javax.inject.Inject

/**
 * S4231: own devices from the device records in the app's Drive folder - the same records "From my
 * devices" reads - minus this device. Every record in that hidden folder belongs to the one account.
 */
class RendezvousOwnDriveDevices @Inject constructor(
    private val records: GoogleDriveSftpRendezvousDataSource,
    private val identity: LocalDeviceIdentitySource,
) : OwnDriveDevices {

    override suspend fun list(): Result<List<OwnDriveDevice>> {
        val self = identity.current().deviceId
        return records.snapshot().map { snapshot ->
            snapshot.devices
                .filter { it.record.deviceId != self }
                .map { OwnDriveDevice(it.record.deviceId, it.record.deviceName, it.modifiedMs) }
        }
    }

    override suspend fun publishedResource(resourceId: String): Result<PublishedDriveResource?> =
        records.snapshot().map { snapshot ->
            snapshot.resources.firstOrNull { it.record.resourceId == resourceId }?.record?.let { record ->
                PublishedDriveResource(record.resourceId, record.deviceId, record.pinProtected)
            }
        }
}
