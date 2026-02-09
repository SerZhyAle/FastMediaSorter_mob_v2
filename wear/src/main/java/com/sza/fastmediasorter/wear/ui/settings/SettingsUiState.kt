package com.sza.fastmediasorter.wear.ui.settings

/**
 * UI state for Settings screen.
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    
    // Media types
    val isAudioEnabled: Boolean = true,
    val isVideoEnabled: Boolean = true,
    val isImagesEnabled: Boolean = true,
    
    // Slideshow
    val isSlideshowEnabled: Boolean = false,
    val slideshowIntervalSeconds: Int = 5,
    val slideshowWaitForFinish: Boolean = false,
    
    // Album art
    val downloadAlbumArt: Boolean = false,
    
    // App info
    val appVersion: String = "",
    val buildNumber: String = ""
)
