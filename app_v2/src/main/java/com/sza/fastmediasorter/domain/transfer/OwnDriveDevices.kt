package com.sza.fastmediasorter.domain.transfer

/** S4231: another device of the same Google account, as its Drive device record names it. */
data class OwnDriveDevice(val deviceId: String, val displayName: String, val lastSeenMs: Long?)

/** S4231: who to ask, via Google Drive, for the folder of an attached resource of another own device. */
data class DriveProducerTarget(
    val deviceId: String,
    val deviceName: String,
    val lastSeenMs: Long?,
    val resourceId: String,
    val rootPath: String,
    val pinProtected: Boolean
)

/** S4231: a resource another own device published on Drive, as far as the Drive channel needs it. */
data class PublishedDriveResource(val resourceId: String, val deviceId: String, val pinProtected: Boolean)

/**
 * S4231: the person's other devices that wrote a device record on Drive - the possible targets of a
 * send via Google Drive (contract DEVICE-EXCHANGE item CN addresses them by `deviceId`) - and the
 * resources they published there.
 */
interface OwnDriveDevices {
    suspend fun list(): Result<List<OwnDriveDevice>>

    suspend fun publishedResource(resourceId: String): Result<PublishedDriveResource?>
}
