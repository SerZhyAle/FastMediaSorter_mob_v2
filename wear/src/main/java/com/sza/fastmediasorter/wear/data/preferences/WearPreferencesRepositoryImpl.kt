package com.sza.fastmediasorter.wear.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-based implementation of WearPreferencesRepository.
 */
class WearPreferencesRepositoryImpl(
    private val context: Context
) : WearPreferencesRepository {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wear_settings")
    
    private object PreferencesKeys {
        val AUDIO_ENABLED = booleanPreferencesKey("wear_audio_enabled")
        val VIDEO_ENABLED = booleanPreferencesKey("wear_video_enabled")
        val IMAGES_ENABLED = booleanPreferencesKey("wear_images_enabled")
        
        val SLIDESHOW_ENABLED = booleanPreferencesKey("wear_slideshow_enabled")
        val SLIDESHOW_INTERVAL = intPreferencesKey("wear_slideshow_interval_seconds")
        val SLIDESHOW_WAIT_FINISH = booleanPreferencesKey("wear_slideshow_wait_finish")
        
        val DOWNLOAD_ALBUM_ART = booleanPreferencesKey("wear_download_album_art")

        val SHUFFLE_ENABLED = booleanPreferencesKey("wear_shuffle_enabled")
    }
    
    // Media type toggles
    override val isAudioEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.AUDIO_ENABLED] ?: true
    }
    
    override val isVideoEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VIDEO_ENABLED] ?: true
    }
    
    override val isImagesEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.IMAGES_ENABLED] ?: true
    }
    
    override suspend fun setAudioEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.AUDIO_ENABLED] = enabled
        }
    }
    
    override suspend fun setVideoEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.VIDEO_ENABLED] = enabled
        }
    }
    
    override suspend fun setImagesEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.IMAGES_ENABLED] = enabled
        }
    }
    
    // Slideshow settings
    override val isSlideshowEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SLIDESHOW_ENABLED] ?: false
    }
    
    override val slideshowIntervalSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SLIDESHOW_INTERVAL] ?: 5
    }
    
    override val slideshowWaitForFinish: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SLIDESHOW_WAIT_FINISH] ?: false
    }
    
    override suspend fun setSlideshowEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SLIDESHOW_ENABLED] = enabled
        }
    }
    
    override suspend fun setSlideshowIntervalSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SLIDESHOW_INTERVAL] = seconds
        }
    }
    
    override suspend fun setSlideshowWaitForFinish(wait: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SLIDESHOW_WAIT_FINISH] = wait
        }
    }
    
    // Album art settings
    override val downloadAlbumArt: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DOWNLOAD_ALBUM_ART] ?: false
    }
    
    override suspend fun setDownloadAlbumArt(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DOWNLOAD_ALBUM_ART] = enabled
        }
    }

    // Playback order
    override val isShuffleEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SHUFFLE_ENABLED] ?: false
    }

    override suspend fun setShuffleEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SHUFFLE_ENABLED] = enabled
        }
    }
}
