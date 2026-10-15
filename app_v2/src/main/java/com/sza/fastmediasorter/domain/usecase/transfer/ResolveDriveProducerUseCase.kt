package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.transfer.DriveRelativePath
import com.sza.fastmediasorter.domain.transfer.DriveProducerTarget
import com.sza.fastmediasorter.domain.transfer.OwnDriveDevices
import javax.inject.Inject

/**
 * S4231: resolves an attached "From my devices" resource to the device that published it on Drive
 * (contract DEVICE-EXCHANGE item CO: the producer is addressed by `deviceId`, the resource by
 * `resourceId`, and only a resource published on Drive is served).
 */
class ResolveDriveProducerUseCase @Inject constructor(
    private val devices: OwnDriveDevices
) {

    suspend operator fun invoke(resource: MediaResource): DriveProducerTarget? =
        invoke(resource.exchangeResourceId, resource.path)

    /** [resourceUri] is the address of the attached resource, `sftp://host:port/root`. */
    suspend operator fun invoke(exchangeResourceId: String?, resourceUri: String): DriveProducerTarget? {
        val resourceId = exchangeResourceId
        val rootPath = DriveRelativePath.parse(pathOf(resourceUri))
        val published = resourceId?.let { devices.publishedResource(it).getOrNull() }
        val device = published?.let { found ->
            devices.list().getOrNull()?.firstOrNull { it.deviceId == found.deviceId }
        }
        return if (published == null || device == null || rootPath == null) {
            null
        } else {
            DriveProducerTarget(
                deviceId = device.deviceId,
                deviceName = device.displayName,
                lastSeenMs = device.lastSeenMs,
                resourceId = published.resourceId,
                rootPath = DriveRelativePath.join(rootPath),
                pinProtected = published.pinProtected
            )
        }
    }

    // sftp://host:port/root/sub -> root/sub; the authority may carry a share id before an '@'.
    private fun pathOf(uri: String): String = uri.substringAfter("://", uri).substringAfter('/', "")
}
