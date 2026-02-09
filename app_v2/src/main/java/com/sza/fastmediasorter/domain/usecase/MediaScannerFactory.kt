package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.cloud.CloudMediaScanner
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.data.network.SmbMediaScanner
import com.sza.fastmediasorter.data.remote.ftp.FtpMediaScanner
import com.sza.fastmediasorter.data.remote.sftp.SftpMediaScanner
import com.sza.fastmediasorter.domain.model.ResourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory to provide appropriate MediaScanner implementation
 * based on resource type.
 */
@Singleton
class MediaScannerFactory @Inject constructor(
    private val localMediaScanner: LocalMediaScanner,
    private val smbMediaScanner: SmbMediaScanner,
    private val sftpMediaScanner: SftpMediaScanner,
    private val ftpMediaScanner: FtpMediaScanner,
    private val cloudMediaScanner: CloudMediaScanner
) {
    /**
     * Get scanner for specific resource type
     */
    fun getScanner(resourceType: ResourceType): MediaScanner {
        return when (resourceType) {
            ResourceType.LOCAL -> localMediaScanner
            ResourceType.SMB -> smbMediaScanner
            ResourceType.SFTP -> sftpMediaScanner
            ResourceType.FTP -> ftpMediaScanner
            ResourceType.CLOUD -> cloudMediaScanner
        }
    }
}
