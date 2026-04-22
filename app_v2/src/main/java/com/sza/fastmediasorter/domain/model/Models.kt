package com.sza.fastmediasorter.domain.model

/**
 * Resource type enum matching specification
 */
enum class ResourceType {
    LOCAL,
    SMB,
    SFTP,
    FTP,
    CLOUD;

    val isNetworkResource: Boolean
        get() = this in listOf(SMB, SFTP, FTP, CLOUD)
}

/**
 * Media type enum matching specification
 */
enum class MediaType {
    IMAGE,
    VIDEO,
    AUDIO,
    GIF,
    TEXT,
    PDF,
    EPUB,
    
    // Binary file types (Task 6)
    BINARY_ARCHIVE,    // ZIP, RAR, 7z, TAR, GZ
    BINARY_DISK,       // ISO, DMG, IMG
    BINARY_EXECUTABLE, // APK, EXE, DLL, SO
    BINARY_OTHER;      // Other binary files

    /**
     * Check if this is a binary file type
     */
    fun isBinaryFile(): Boolean {
        return this in listOf(
            BINARY_ARCHIVE,
            BINARY_DISK,
            BINARY_EXECUTABLE,
            BINARY_OTHER
        )
    }
}

/**
 * Sort mode enum matching specification
 */
enum class SortMode {
    RANDOM,          // Random order (useful for slideshows) — first in sort menu
    NAME_ASC,
    NAME_DESC,
    DATE_ASC,
    DATE_DESC,
    SIZE_ASC,
    SIZE_DESC,
    MANUAL,          // Manual ordering by displayOrder — placed after SIZE per UX requirement
    ARTIST_ASC,
    ARTIST_DESC,
    TITLE_ASC,
    TITLE_DESC,
    DURATION_ASC,
    DURATION_DESC,
    TYPE_ASC,        // Placed below DURATION per UX requirement
    TYPE_DESC,
    DATE_TAKEN_ASC,
    DATE_TAKEN_DESC,
}

/**
 * Display mode enum
 */
enum class DisplayMode {
    LIST,
    GRID
}

/**
 * Resource profile enum — quick-setup presets that apply media type and flag defaults.
 * The selected profile is persisted in the resource entity for informational purposes.
 */
enum class ResourceProfile {
    NONE,          // No preset — manual configuration
    AUDIO_LIBRARY, // Audio only + rememberFileList recommended
    VIDEO_LIBRARY, // Video + Audio
    PHOTO_STORAGE, // Image + GIF
    DOCUMENTS,     // Text + PDF + EPUB
    ALL_FILES      // allFiles flag enabled (show everything)
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
    val maxSizeMb: Float? = null,
    val mediaTypes: Set<MediaType>? = null
) {
    fun isEmpty(): Boolean = nameContains.isNullOrBlank() && minDate == null && maxDate == null && minSizeMb == null && maxSizeMb == null && mediaTypes == null
    
    /**
     * Count active filter criteria for badge display
     */
    fun activeFilterCount(): Int {
        var count = 0
        if (!nameContains.isNullOrBlank()) {
            count++
            timber.log.Timber.d("activeFilterCount: nameContains active, count=$count")
        }
        if (minDate != null || maxDate != null) {
            count++
            timber.log.Timber.d("activeFilterCount: date filter active (min=$minDate, max=$maxDate), count=$count")
        }
        if (minSizeMb != null || maxSizeMb != null) {
            count++
            timber.log.Timber.d("activeFilterCount: size filter active (min=$minSizeMb, max=$maxSizeMb), count=$count")
        }
        if (!mediaTypes.isNullOrEmpty()) {
            count++
            timber.log.Timber.d("activeFilterCount: mediaTypes filter active (types=$mediaTypes), count=$count")
        }
        timber.log.Timber.d("activeFilterCount: TOTAL count=$count")
        return count
    }
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
    val cloudProvider: com.sza.fastmediasorter.data.cloud.CloudProvider? = null,
    val cloudFolderId: String? = null,
    val accountId: String? = null,
    val supportedMediaTypes: Set<MediaType> = setOf(MediaType.IMAGE, MediaType.VIDEO),
    val sortMode: SortMode = SortMode.NAME_ASC,
    val displayMode: DisplayMode = DisplayMode.LIST,
    val lastViewedFile: String? = null,
    val lastScrollPosition: Int = 0, // First visible item position in RecyclerView
    val fileCount: Int = 0,
    val subfolderCount: Int = 0, // Number of subfolders in resource root (from last testConnection)
    val lastAccessedDate: Long = System.currentTimeMillis(),
    val slideshowInterval: Int = 10,
    val isDestination: Boolean = false,
    val destinationOrder: Int? = null,
    val destinationColor: Int = 0xFF4CAF50.toInt(), // Default green color
    val isWritable: Boolean = false,
    val isReadOnly: Boolean = false, // Read-only mode: prevents file operations
    val isAvailable: Boolean = true, // Resource availability indicator
    val showCommandPanel: Boolean? = null, // User preference: null = use global default, true/false = override
    val createdDate: Long = System.currentTimeMillis(),
    val lastBrowseDate: Long? = null, // Last time resource was opened in BrowseActivity
    val lastSyncDate: Long? = null, // Last time network resource was synced (for SMB/SFTP/FTP only)
    val displayOrder: Int = 0, // Order for display in resource list
    val scanSubdirectories: Boolean = false, // Scan subdirectories for media files (default: disabled, user selects during resource creation)
    val disableThumbnails: Boolean = false, // Disable thumbnail loading (use extension icons only). Auto-enabled for >10000 files.
    val allFiles: Boolean = false, // Show all files regardless of type (when true, overrides supportedMediaTypes filter)
    val showHiddenFiles: Boolean = false, // Show hidden files and folders (those starting with a dot). Depends on allFiles being ON.
    val showSubfoldersAsItems: Boolean = false, // Show subfolders as separate clickable items in Browse. Only works when scanSubdirectories is enabled.
    val rememberFileList: Boolean = false, // Persist file list in DB for faster subsequent loads
    val accessPin: String? = null, // PIN code to access the resource (null = no PIN protection)
    val comment: String? = null, // User comment for the resource
    val profile: ResourceProfile = ResourceProfile.NONE, // Quick-setup profile (preset applied during creation)
    
    // Network speed test results
    val readSpeedMbps: Double? = null,
    val writeSpeedMbps: Double? = null,
    val recommendedThreads: Int? = null,
    val lastSpeedTestDate: Long? = null
) {
    fun isAudioOnly(): Boolean {
        return !allFiles && supportedMediaTypes.size == 1 && supportedMediaTypes.contains(MediaType.AUDIO)
    }
    
    fun isOnlyImage(): Boolean {
        return !allFiles && supportedMediaTypes.size == 1 && supportedMediaTypes.contains(MediaType.IMAGE)
    }
}

/**
 * Domain model for Media File
 */
data class MediaFile(
    val name: String,
    val path: String,
    val type: MediaType,
    val size: Long,
    val createdDate: Long,
    val contentUri: String? = null, // MediaStore content:// URI for local file access (Android 11+)
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
    val videoRotation: Int? = null, // Video rotation angle (0, 90, 180, 270 degrees)
    // Audio metadata fields
    val artist: String? = null,
    val album: String? = null,
    val title: String? = null,
    // Cloud storage fields (for CLOUD resources)
    val thumbnailUrl: String? = null, // Cloud thumbnail URL
    val webViewUrl: String? = null, // Cloud web view URL
    val isFavorite: Boolean = false, // True if file is marked as favorite
    val resourceId: Long? = null, // ID of the resource this file belongs to (null for legacy/unknown)
    // Directory support for subfolder navigation
    val isDirectory: Boolean = false, // True if this is a folder (for subfolder navigation)
    val childCount: Int? = null, // Number of children in directory (files + folders), null for files
    val lastModified: Long = 0L, // File last modified timestamp (ms); 0 if unavailable
    // Cloud display metadata (for CLOUD resources)
    val cloudDisplayPath: String? = null, // Human-readable cloud path (folders + file name)
    val cloudItemId: String? = null // Provider-specific internal file/folder ID
)

/**
 * File operation type for undo functionality
 */
enum class FileOperationType {
    COPY,
    MOVE,
    RENAME,
    DELETE,
    // Destination-picker-only mode: the dialog captures the picked folder and hands it back
    // to the caller via onDestinationSelected, without running any copy/move operation.
    ARCHIVE
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

