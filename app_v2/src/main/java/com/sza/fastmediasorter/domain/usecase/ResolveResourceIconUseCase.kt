package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.icon.ResourceIconRegistry
import com.sza.fastmediasorter.ui.icon.ResourceIconSet
import javax.inject.Inject

/**
 * Resolves the initial icon id (`ico-XX-NNN`) for a newly created [MediaResource].
 *
 * Rules (in priority order):
 * 1. Virtual paths → deterministic fixed ids so the same virtual resource always
 *    shows the same icon on every device.
 * 2. Other resources → first icon of the set that matches the profile / type, giving
 *    a sensible default that the user can override via the icon picker (Phase 06).
 *
 * Returns null only if the combination is genuinely unmappable (should never happen
 * with the current enum coverage, but callers should treat null as "use legacy icon").
 */
class ResolveResourceIconUseCase @Inject constructor() {

    /** Set-id to "first icon" mapping - must stay in sync with [ResourceIconRegistry]. */
    private val SET_MUSIC = 1
    private val SET_VIDEO = 2
    private val SET_IMAGE = 3
    private val SET_DOCS  = 4
    private val SET_OTHER = 5

    operator fun invoke(
        path: String,
        profile: ResourceProfile,
        type: ResourceType
    ): String? {
        // 1. Fixed icons for known virtual paths
        fixedIconForVirtualPath(path)?.let { return it }

        // 2. Profile-based set selection
        val setId = when (profile) {
            ResourceProfile.AUDIO_LIBRARY -> SET_MUSIC
            ResourceProfile.VIDEO_LIBRARY -> SET_VIDEO
            ResourceProfile.PHOTO_STORAGE -> SET_IMAGE
            ResourceProfile.DOCUMENTS    -> SET_DOCS
            // NONE / ALL_FILES → fall through to type-based heuristic
            else -> typeHeuristicSetId(type)
        }
        // For "Other" set, pick a random icon so each new resource looks distinct
        return if (setId == SET_OTHER) ResourceIconRegistry.randomIdFor(ResourceIconSet.OTHER)
               else "ico-%02d-001".format(setId)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun fixedIconForVirtualPath(path: String): String? = when (path) {
        LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO    -> "ico-01-001"
        LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO    -> "ico-02-001"
        LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES   -> "ico-03-001"
        LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS     -> "ico-04-001"
        LocalMediaScanner.VIRTUAL_PATH_RECENT       -> "ico-05-001"
        LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS -> "ico-03-002"
        else -> null
    }

    private fun typeHeuristicSetId(type: ResourceType): Int = when (type) {
        // Network and cloud resources default to the "Other" abstract set
        ResourceType.LOCAL  -> SET_OTHER
        ResourceType.SMB    -> SET_OTHER
        ResourceType.SFTP   -> SET_OTHER
        ResourceType.FTP    -> SET_OTHER
        ResourceType.CLOUD  -> SET_OTHER
        ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> SET_OTHER
    }

    /** Resolves a new icon id when the user changes profile/type in the editor session. */
    fun resolveForProfileChange(profile: ResourceProfile, type: ResourceType): String =
        // Fallback to random Other icon so repeated profile-changes don't always yield the same icon
        invoke(path = "", profile = profile, type = type)
            ?: ResourceIconRegistry.randomIdFor(ResourceIconSet.OTHER)
}
