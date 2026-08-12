package com.sza.fastmediasorter.domain.usecase.devicestatus

import com.sza.fastmediasorter.domain.model.StorageVolumeInfo
import com.sza.fastmediasorter.domain.model.devicestatus.DeviceStatusProvider
import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue
import com.sza.fastmediasorter.domain.model.devicestatus.StorageStatus
import com.sza.fastmediasorter.domain.usecase.GetStorageVolumesUseCase
import javax.inject.Inject

/**
 * S1178: the storage gadget's metric.
 *
 * Volume enumeration is not re-implemented here - it goes through the one use case every storage
 * surface already reads, so the gadget and the pickers can never disagree about what is mounted. One
 * enumeration per [read], not one per rendered figure.
 */
class GetStorageStatusUseCase @Inject constructor(
    private val getStorageVolumes: GetStorageVolumesUseCase
) : DeviceStatusProvider<StorageStatus> {

    override val refreshIntervalMs: Long = REFRESH_INTERVAL_MS

    override suspend fun read(): StorageStatus {
        val volumes = getStorageVolumes()
        val internal = volumes.firstOrNull { it.isPrimary }
        val card = volumes.firstOrNull { it.isRemovable && it.isMounted }
        return StorageStatus(
            internalTotalBytes = bytes(internal?.totalBytes),
            internalAvailableBytes = bytes(internal?.availableBytes),
            card = card?.let {
                StorageStatus.CardSpace(
                    totalBytes = bytes(it.totalBytes),
                    availableBytes = bytes(it.availableBytes)
                )
            }
        )
    }

    /**
     * A volume that is absent, or that reports a negative size because the platform could not stat it,
     * is unknown - not empty. [StorageVolumeInfo] carries plain longs, so this is the seam where a
     * missing figure stops looking like a real one.
     */
    private fun bytes(value: Long?): MetricValue<Long> =
        if (value == null || value < 0L) MetricValue.Unknown else MetricValue.Known(value)

    private companion object {
        /** Free space moves slowly; ten seconds keeps the row current without polling for nothing. */
        const val REFRESH_INTERVAL_MS = 10_000L
    }
}
