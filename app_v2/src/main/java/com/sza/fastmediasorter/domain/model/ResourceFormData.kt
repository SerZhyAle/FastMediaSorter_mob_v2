package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.data.cloud.CloudProvider

data class ResourceFormData(
    val id: Long? = null,
    val mode: ResourceEditorMode = ResourceEditorMode.CREATE,
    val type: ResourceType = ResourceType.LOCAL,

    val name: String = "",
    val path: String = "",
    val comment: String = "",
    val accessPin: String = "",

    val isDestination: Boolean = false,
    val destinationOrder: Int? = null,
    val destinationColor: Int = 0xFF4CAF50.toInt(), // Default green color
    val isReadOnly: Boolean = false,

    val credentialsId: String? = null,
    val username: String = "",
    val password: String = "",
    val host: String = "",
    val port: Int? = null,

    val cloudProvider: CloudProvider? = null,
    val cloudFolderId: String? = null,

    val supportedMediaTypes: Set<MediaType> = setOf(MediaType.IMAGE, MediaType.VIDEO),
    val sortMode: SortMode = SortMode.NAME_ASC,
    val displayMode: DisplayMode = DisplayMode.LIST,

    val slideshowInterval: Int = 10, // Seconds between images in slideshow mode
    val showCommandPanel: Boolean? = null, // null = use global default, true/false = override

    val scanSubdirectories: Boolean = false,
    val disableThumbnails: Boolean = false,
    val allFiles: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val showSubfoldersAsItems: Boolean = false,
    val rememberFileList: Boolean = false,

    val profile: ResourceProfile = ResourceProfile.NONE, // Active quick-setup profile

    /** User-chosen or auto-resolved icon id (S0034). null = not yet assigned. */
    val iconId: String? = null,

    val metadata: Map<String, String> = emptyMap()
)

/**
 * Applies a [ResourceProfile] preset to this form data, overwriting relevant fields.
 * Selecting [ResourceProfile.NONE] is a no-op (returns unchanged data).
 *
 * Delegates to [mediaPreset] so the profile->media-type mapping lives in one place shared with
 * companion-config import (S1002). A null preset field leaves the current form value unchanged.
 */
fun ResourceFormData.applyProfile(profile: ResourceProfile): ResourceFormData {
    if (profile == ResourceProfile.NONE) return this
    val preset = profile.mediaPreset()
    return copy(
        profile = profile,
        supportedMediaTypes = preset.supportedMediaTypes ?: supportedMediaTypes,
        allFiles = preset.allFiles,
        rememberFileList = preset.rememberFileList ?: rememberFileList
    )
}
