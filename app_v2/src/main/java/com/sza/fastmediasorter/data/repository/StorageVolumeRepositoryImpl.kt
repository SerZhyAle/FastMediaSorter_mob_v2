package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.domain.model.StorageVolumeInfo
import com.sza.fastmediasorter.domain.repository.StorageVolumeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [StorageVolumeRepository] over [StorageVolumeSource].
 *
 * Every platform call is moved to [Dispatchers.IO]: reading a volume's free space goes through
 * `StatFs`, which stats the filesystem and can stall on a slow or dying card.
 */
@Singleton
class StorageVolumeRepositoryImpl @Inject constructor(
    private val source: StorageVolumeSource
) : StorageVolumeRepository {

    override suspend fun getVolumes(): List<StorageVolumeInfo> = withContext(Dispatchers.IO) {
        source.listVolumes()
    }

    override suspend fun getVolume(volumeId: String): StorageVolumeInfo? =
        getVolumes().firstOrNull { it.id.equals(volumeId, ignoreCase = true) }

    override suspend fun resolveMountPath(volumeId: String): String? =
        getVolume(volumeId)?.takeIf { it.isMounted }?.mountPath
}
