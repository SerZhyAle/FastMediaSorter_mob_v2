package com.sza.fastmediasorter.ui.icon

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType

/**
 * Stateless helper that maps resource metadata to a [ResourceIconSet] and resolves
 * fixed icon ids for predefined virtual resources.
 */
object ResourceIconDefaults {

    /**
     * Returns the [ResourceIconSet] appropriate for the given [profile] / [type] combination.
     * Profile takes priority when it narrows to a specific media category.
     */
    fun setForResource(profile: ResourceProfile, type: ResourceType): ResourceIconSet =
        when (profile) {
            ResourceProfile.AUDIO_LIBRARY -> ResourceIconSet.MUSIC
            ResourceProfile.VIDEO_LIBRARY -> ResourceIconSet.VIDEO
            ResourceProfile.PHOTO_STORAGE -> ResourceIconSet.IMAGE
            ResourceProfile.DOCUMENTS    -> ResourceIconSet.DOCS
            // NONE and ALL_FILES do not narrow media type - fall through to OTHER
            else                         -> ResourceIconSet.OTHER
        }

    /**
     * Returns a fixed icon id for well-known virtual paths so predefined resources always
     * show the same icon on every device/install.
     * Returns null for custom paths - caller should assign a random icon from the set.
     */
    fun fixedIconForVirtualPath(path: String): String? = when (path) {
        LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO   -> "ico-01-001"
        LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO   -> "ico-02-001"
        LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES  -> "ico-03-001"
        LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS    -> "ico-04-001"
        LocalMediaScanner.VIRTUAL_PATH_RECENT      -> "ico-05-001"
        LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS -> "ico-03-002"
        else                                       -> null
    }
}
