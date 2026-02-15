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

    val scanSubdirectories: Boolean = false,
    val disableThumbnails: Boolean = false,
    val allFiles: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val showSubfoldersAsItems: Boolean = false,

    val metadata: Map<String, String> = emptyMap()
)