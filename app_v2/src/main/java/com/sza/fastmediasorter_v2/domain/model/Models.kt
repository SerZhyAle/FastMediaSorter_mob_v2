package com.sza.fastmediasorter_v2.domain.model

/**
 * Resource type enum matching specification
 */
enum class ResourceType {
    LOCAL,
    SMB,
    SFTP,
    CLOUD
}

/**
 * Media type enum matching specification
 */
enum class MediaType {
    IMAGE,
    VIDEO,
    AUDIO,
    GIF
}

/**
 * Sort mode enum matching specification
 */
enum class SortMode {
    NAME_ASC,
    NAME_DESC,
    DATE_ASC,
    DATE_DESC,
    SIZE_ASC,
    SIZE_DESC,
    TYPE_ASC,
    TYPE_DESC
}

/**
 * Display mode enum
 */
enum class DisplayMode {
    LIST,
    GRID
}

/**
 * Domain model for Resource (Folder)
 * Represents a folder that can contain media files
 */
data class MediaResource(
    val id: Long = 0,
    val name: String,
    val path: String,
    val type: ResourceType,
    val credentialsId: String? = null,
    val supportedMediaTypes: Set<MediaType> = setOf(MediaType.IMAGE, MediaType.VIDEO),
    val sortMode: SortMode = SortMode.NAME_ASC,
    val displayMode: DisplayMode = DisplayMode.LIST,
    val lastViewedFile: String? = null,
    val fileCount: Int = 0,
    val lastAccessedDate: Long = System.currentTimeMillis(),
    val slideshowInterval: Int = 10,
    val isDestination: Boolean = false,
    val destinationOrder: Int? = null,
    val destinationColor: Int = 0xFF4CAF50.toInt(), // Default green color
    val isWritable: Boolean = false,
    val createdDate: Long = System.currentTimeMillis()
)

/**
 * Domain model for Media File
 */
data class MediaFile(
    val name: String,
    val path: String,
    val type: MediaType,
    val size: Long,
    val createdDate: Long,
    val duration: Long? = null,
    val width: Int? = null,
    val height: Int? = null
)
