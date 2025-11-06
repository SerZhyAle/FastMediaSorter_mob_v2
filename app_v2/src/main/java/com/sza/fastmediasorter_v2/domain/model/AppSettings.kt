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
    val supportGifs: Boolean = false,
    val supportVideos: Boolean = true,
    val videoSizeMin: Long = 102400L, // 100KB
    val videoSizeMax: Long = 1073741824L, // 1GB
    val supportAudio: Boolean = true,
    val audioSizeMin: Long = 10240L, // 10KB
    val audioSizeMax: Long = 104857600L, // 100MB
    
    // Playback and Sorting settings
    val defaultSortMode: SortMode = SortMode.NAME_ASC,
    val slideshowInterval: Int = 3, // seconds
    val playToEndInSlideshow: Boolean = false,
    val allowRename: Boolean = true,
    val allowDelete: Boolean = true,
    val confirmDelete: Boolean = true,
    val defaultGridMode: Boolean = false,
    val defaultIconSize: Int = 100, // dp
    val fullScreenMode: Boolean = true,
    val showDetailedErrors: Boolean = false,
    
    // Destinations settings
    val enableCopying: Boolean = true,
    val goToNextAfterCopy: Boolean = true,
    val overwriteOnCopy: Boolean = false,
    val enableMoving: Boolean = true,
    val overwriteOnMove: Boolean = false,
    val enableUndo: Boolean = true
)
