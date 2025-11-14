package com.sza.fastmediasorter_v2.domain.model

/**
 * Resource type enum matching specification
 */
enum class ResourceType {
    LOCAL,
    SMB,
    SFTP,
    FTP,
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
    MANUAL,      // Manual ordering by displayOrder (used when user manually reorders items)
    NAME_ASC,
    NAME_DESC,
    DATE_ASC,
    DATE_DESC,
    SIZE_ASC,
    SIZE_DESC,
    TYPE_ASC,
    TYPE_DESC,
    RANDOM       // Random order (useful for slideshows)
}

/**
 * Display mode enum
 */
enum class DisplayMode {
    LIST,
    GRID
}

/**
 * Filter criteria for media files
 * According to specification: filename (case-insensitive), creation date (>=Date;<=Date), file size (>=Mb;<=Mb)
 */
data class FileFilter(
    val nameContains: String? = null,
    val minDate: Long? = null,
    val maxDate: Long? = null,
    val minSizeMb: Float? = null,
    val maxSizeMb: Float? = null
) {
    fun isEmpty(): Boolean = nameContains.isNullOrBlank() && minDate == null && maxDate == null && minSizeMb == null && maxSizeMb == null
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
    val createdDate: Long = System.currentTimeMillis(),
    val displayOrder: Int = 0 // Order for display in resource list
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
    val height: Int? = null,
    // EXIF metadata fields (for IMAGE type)
    val exifOrientation: Int? = null, // ExifInterface.TAG_ORIENTATION value (1-8)
    val exifDateTime: Long? = null, // Photo capture timestamp from EXIF (milliseconds)
    val exifLatitude: Double? = null, // GPS latitude from EXIF
    val exifLongitude: Double? = null, // GPS longitude from EXIF
    // Video metadata fields (for VIDEO type)
    val videoCodec: String? = null, // Video codec name (e.g., "avc1", "vp9", "hevc")
    val videoBitrate: Int? = null, // Video bitrate in bits per second
    val videoFrameRate: Float? = null, // Video frame rate (fps)
    val videoRotation: Int? = null // Video rotation angle (0, 90, 180, 270 degrees)
)

/**
 * File operation type for undo functionality
 */
enum class FileOperationType {
    COPY,
    MOVE,
    RENAME,
    DELETE
}

/**
 * File operation record for undo functionality
 * According to specification: store operation details to enable undo
 */
data class UndoOperation(
    val type: FileOperationType,
    val sourceFiles: List<String>, // Original file paths
    val destinationFolder: String? = null, // For COPY/MOVE operations
    val copiedFiles: List<String>? = null, // Destination paths for copied/moved files
    val oldNames: List<Pair<String, String>>? = null, // (oldPath, newPath) for RENAME
    val timestamp: Long = System.currentTimeMillis()
)
