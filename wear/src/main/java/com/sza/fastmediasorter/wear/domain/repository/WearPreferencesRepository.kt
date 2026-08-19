package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearViewMode
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

    /** S1781: one view shared by the navigation screens - the home screen and the Resources page. */
    val viewMode: Flow<WearViewMode>
    suspend fun setViewMode(mode: WearViewMode)

    /** S1730: the view of a file list inside a resource, deliberately separate from [viewMode]. */
    val fileListViewMode: Flow<WearViewMode>
    suspend fun setFileListViewMode(mode: WearViewMode)

    /** S1781: the players hold the screen unconditionally, so this covers only the rest of the app. */
    val keepScreenAwakeOutsidePlayers: Flow<Boolean>
    suspend fun setKeepScreenAwakeOutsidePlayers(enabled: Boolean)

    /** S1781: name of the resource opened last; null means the history is empty and the section is hidden. */
    val lastUsedResourceName: Flow<String?>
    suspend fun setLastUsedResource(name: String)
    suspend fun clearLastUsedResource()

    /** S1781: the Streams section ships on and can be switched off from the Media types settings. */
    val streamsSectionEnabled: Flow<Boolean>
    suspend fun setStreamsSectionEnabled(enabled: Boolean)

    /**
     * S1710: the calculator's own state, kept on the watch and never reconciled with the phone.
     *
     * An empty history and a null memory are a first run, not a broken store.
     */
    val calculatorHistory: Flow<List<String>>
    suspend fun setCalculatorHistory(entries: List<String>)

    val calculatorMemory: Flow<String?>
    suspend fun setCalculatorMemory(value: String?)

    /** S1718: watch screen auto-rotation setting. Default: false (forbidden). */
    val isAutoRotationEnabled: Flow<Boolean>
    suspend fun setAutoRotationEnabled(enabled: Boolean)

    /** S1814: active app language inherited from the phone companion; null means system locale default. */
    val appLanguage: Flow<String?>
    suspend fun setAppLanguage(languageCode: String?)
}
