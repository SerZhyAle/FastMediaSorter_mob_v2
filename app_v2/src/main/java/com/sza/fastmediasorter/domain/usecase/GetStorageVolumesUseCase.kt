package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.StorageVolumeInfo
import com.sza.fastmediasorter.domain.repository.StorageVolumeRepository
import javax.inject.Inject

/**
 * Lists the device's storage volumes in the one order every surface renders them in: built-in
 * storage first, then removable volumes by display name, so the picker's sections do not reshuffle
 * between openings.
 */
class GetStorageVolumesUseCase @Inject constructor(
    private val storageVolumeRepository: StorageVolumeRepository
) {

    suspend operator fun invoke(): List<StorageVolumeInfo> =
        storageVolumeRepository.getVolumes().sortedWith(VOLUME_ORDER)

    /** Only volumes that live on a removable medium - the ones a card picker offers. */
    suspend fun removableOnly(): List<StorageVolumeInfo> = invoke().filter { it.isRemovable }

    private companion object {
        val VOLUME_ORDER = compareByDescending<StorageVolumeInfo> { it.isPrimary }
            .thenBy { it.displayName }
    }
}
