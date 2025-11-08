package com.sza.fastmediasorter_v2.domain.usecase

import com.sza.fastmediasorter_v2.data.local.LocalMediaScanner
import com.sza.fastmediasorter_v2.data.network.SmbMediaScanner
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory to provide appropriate MediaScanner implementation
 * based on resource type.
 */
@Singleton
class MediaScannerFactory @Inject constructor(
    private val localMediaScanner: LocalMediaScanner,
    private val smbMediaScanner: SmbMediaScanner
) {
    /**
     * Get scanner for specific resource type
     */
    fun getScanner(resourceType: ResourceType): MediaScanner {
        return when (resourceType) {
            ResourceType.LOCAL -> localMediaScanner
            ResourceType.SMB -> smbMediaScanner
            ResourceType.SFTP -> throw UnsupportedOperationException("SFTP not yet implemented")
            ResourceType.CLOUD -> throw UnsupportedOperationException("CLOUD not yet implemented")
        }
    }
}
