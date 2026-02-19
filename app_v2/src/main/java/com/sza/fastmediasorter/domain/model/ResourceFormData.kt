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

    val metadata: Map<String, String> = emptyMap()
)

/**
 * Applies a [ResourceProfile] preset to this form data, overwriting relevant fields.
 * Selecting [ResourceProfile.NONE] is a no-op (returns unchanged data).
 */
fun ResourceFormData.applyProfile(profile: ResourceProfile): ResourceFormData = when (profile) {
    ResourceProfile.NONE -> this
    ResourceProfile.AUDIO_LIBRARY -> copy(
        profile = profile,
        supportedMediaTypes = setOf(MediaType.AUDIO),
        allFiles = false,
        rememberFileList = true
    )
    ResourceProfile.VIDEO_LIBRARY -> copy(
        profile = profile,
        supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.AUDIO),
        allFiles = false
    )
    ResourceProfile.PHOTO_STORAGE -> copy(
        profile = profile,
        supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.GIF),
        allFiles = false
    )
    ResourceProfile.DOCUMENTS -> copy(
        profile = profile,
        supportedMediaTypes = setOf(MediaType.TEXT, MediaType.PDF, MediaType.EPUB),
        allFiles = false
    )
    ResourceProfile.ALL_FILES -> copy(
        profile = profile,
        allFiles = true
    )
}