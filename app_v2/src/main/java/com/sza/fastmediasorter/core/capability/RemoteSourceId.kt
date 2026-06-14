package com.sza.fastmediasorter.core.capability

import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.ResourceType

/**
 * Stable identity for each remote source the app can independently enable or disable at runtime.
 *
 * The six ids stay granular even though the settings UI groups them into SMB / (S)FTP / Cloud
 * toggles: the availability gate reasons per source, while a group toggle mass-writes its members.
 */
enum class RemoteSourceId {
    SMB,
    SFTP,
    FTP,
    GOOGLE_DRIVE,
    ONEDRIVE,
    DROPBOX;

    companion object {
        /** Network (file-protocol) sources. */
        val NETWORK: Set<RemoteSourceId> = setOf(SMB, SFTP, FTP)

        /** Cloud-provider sources, gated additionally by compile-time cloud support. */
        val CLOUD: Set<RemoteSourceId> = setOf(GOOGLE_DRIVE, ONEDRIVE, DROPBOX)

        fun fromCloudProvider(provider: CloudProvider): RemoteSourceId = when (provider) {
            CloudProvider.GOOGLE_DRIVE -> GOOGLE_DRIVE
            CloudProvider.ONEDRIVE -> ONEDRIVE
            CloudProvider.DROPBOX -> DROPBOX
        }

        /** Maps a network resource type to its id; null for LOCAL and CLOUD (cloud uses provider). */
        fun networkFromResourceType(type: ResourceType): RemoteSourceId? = when (type) {
            ResourceType.SMB -> SMB
            ResourceType.SFTP -> SFTP
            ResourceType.FTP -> FTP
            ResourceType.LOCAL, ResourceType.CLOUD -> null
        }
    }
}
