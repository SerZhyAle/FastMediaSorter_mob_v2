package com.sza.fastmediasorter.wear.ui.settings

import com.sza.fastmediasorter.wear.domain.model.WearViewMode

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

    /** S1781: one view shared by the home screen and the Resources page. */
    val viewMode: WearViewMode = WearViewMode.LIST,

    /** S1730: the view of a file list inside a resource, separate from [viewMode] by owner ruling. */
    val fileListViewMode: WearViewMode = WearViewMode.LIST,

    /** S1781: the Streams section ships on and is switched off from the Media types settings. */
    val streamsSectionEnabled: Boolean = true,

    /** S1781: the three players hold the screen unconditionally, so this covers every other screen. */
    val keepScreenAwakeOutsidePlayers: Boolean = false,

    /** S1718: watch screen auto-rotation preference. Default: false (forbidden per owner decision 2026-08-16). */
    val isAutoRotationEnabled: Boolean = false,

    /** S1718: true if watch has accelerometer sensor; when false, toggle row is hidden. */
    val hasAutoRotationSensor: Boolean = false,
    
    // App info
    val appVersion: String = "",
    val buildNumber: String = ""
)
