package com.sza.fastmediasorter.wear.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Wear OS application preferences.
 * Manages settings for media types, slideshow, and album art.
 */
interface WearPreferencesRepository {
    
    // Media type toggles
    val isAudioEnabled: Flow<Boolean>
    val isVideoEnabled: Flow<Boolean>
    val isImagesEnabled: Flow<Boolean>
    
    suspend fun setAudioEnabled(enabled: Boolean)
    suspend fun setVideoEnabled(enabled: Boolean)
    suspend fun setImagesEnabled(enabled: Boolean)
    
    // Slideshow settings
    val isSlideshowEnabled: Flow<Boolean>
    val slideshowIntervalSeconds: Flow<Int>
    val slideshowWaitForFinish: Flow<Boolean>
    
    suspend fun setSlideshowEnabled(enabled: Boolean)
    suspend fun setSlideshowIntervalSeconds(seconds: Int)
    suspend fun setSlideshowWaitForFinish(wait: Boolean)
    
    // Album art settings
    val downloadAlbumArt: Flow<Boolean>
    suspend fun setDownloadAlbumArt(enabled: Boolean)

    /** S1701: playback order of the browsed set; remembered so it survives a restart. */
    val isShuffleEnabled: Flow<Boolean>
    suspend fun setShuffleEnabled(enabled: Boolean)
}
