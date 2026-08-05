package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.StorageVolumeInfo

/**
 * Single source of truth about the device's storage volumes.
 *
 * Consumers depend on this contract rather than on the platform storage service, so a new kind of
 * medium can be added without touching the layers above.
 */
interface StorageVolumeRepository {

    /** Every volume the device knows about, mounted or not. */
    suspend fun getVolumes(): List<StorageVolumeInfo>

    /** The volume with [volumeId], or null when this device has no such volume. */
    suspend fun getVolume(volumeId: String): StorageVolumeInfo?

    /** Filesystem root of [volumeId], or null when the volume is unknown or not mounted. */
    suspend fun resolveMountPath(volumeId: String): String?
}
