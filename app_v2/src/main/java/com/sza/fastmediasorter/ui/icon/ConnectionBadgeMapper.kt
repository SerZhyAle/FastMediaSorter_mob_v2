package com.sza.fastmediasorter.ui.icon

import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType

/**
 * Maps a [MediaResource] to the small connection-type badge drawable shown in the
 * bottom-left corner of the composite resource icon.
 *
 * Virtual/predefined resources have no badge (null) because they already convey
 * the source type through their dedicated icons.
 */
object ConnectionBadgeMapper {

    @DrawableRes
    fun badgeFor(resource: MediaResource): Int? {
        // Virtual paths are self-describing - no extra badge needed
        val virtualPaths = setOf(
            LocalMediaScanner.VIRTUAL_PATH_RECENT,
            LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
            LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO,
            LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
            LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS,
            LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS,
        )
        if (resource.path in virtualPaths) return null
        // Favorites pseudo-resource also needs no badge
        if (resource.id == -100L) return null

        return when (resource.type) {
            ResourceType.LOCAL -> null // local has no network badge
            ResourceType.SMB   -> R.drawable.ic_resource_smb
            ResourceType.SFTP  -> R.drawable.ic_resource_sftp
            ResourceType.FTP   -> R.drawable.ic_resource_ftp
            ResourceType.CLOUD -> when (resource.cloudProvider?.name) {
                "GOOGLE_DRIVE" -> R.drawable.ic_provider_google_drive
                "ONEDRIVE"     -> R.drawable.ic_provider_onedrive
                "DROPBOX"      -> R.drawable.ic_provider_dropbox
                else           -> R.drawable.ic_resource_cloud
            }
            ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> R.drawable.ic_cast
        }
    }
}
