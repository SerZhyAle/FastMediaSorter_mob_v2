package com.sza.fastmediasorter_v2.domain.model

/**
 * Application settings model
 * Based on V2 Specification: Settings Screen
 */
data class AppSettings(
    // General settings
    val language: String = "en",
    val preventSleep: Boolean = true,
    val showSmallControls: Boolean = false,
    val defaultUser: String = "",
    val defaultPassword: String = "",
    
    // Media Files settings
    val supportImages: Boolean = true,
    val imageSizeMin: Long = 1024L, // 1KB
    val imageSizeMax: Long = 10485760L, // 10MB
    val loadFullSizeImages: Boolean = false, // Load full resolution images (for zoom support)
    val supportGifs: Boolean = true,
    val supportVideos: Boolean = true,
    val videoSizeMin: Long = 102400L, // 100KB
    val videoSizeMax: Long = 107374182400L, // 100GB (1024MB * 100)
    val supportAudio: Boolean = true,
    val audioSizeMin: Long = 10240L, // 10KB
    val audioSizeMax: Long = 1048576000L, // 1000MB (100MB * 10)
    
    // Playback and Sorting settings
    val defaultSortMode: SortMode = SortMode.NAME_ASC,
    val slideshowInterval: Int = 10, // seconds (default 10, range 1-3600)
    val playToEndInSlideshow: Boolean = false,
    val allowRename: Boolean = true,
    val allowDelete: Boolean = true,
    val confirmDelete: Boolean = true,
    val defaultGridMode: Boolean = false,
    val defaultIconSize: Int = 96, // dp (must be 32 + 8*N for slider validation)
    val fullScreenMode: Boolean = true,
    val showDetailedErrors: Boolean = false,
    
    // Destinations settings
    val enableCopying: Boolean = true,
    val goToNextAfterCopy: Boolean = true,
    val overwriteOnCopy: Boolean = false,
    val enableMoving: Boolean = true,
    val overwriteOnMove: Boolean = false,
    val enableUndo: Boolean = true,
    
    // Player UI settings
    val copyPanelCollapsed: Boolean = false,
    val movePanelCollapsed: Boolean = false
)
