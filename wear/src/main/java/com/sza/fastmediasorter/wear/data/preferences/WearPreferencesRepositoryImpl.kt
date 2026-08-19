package com.sza.fastmediasorter.wear.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

// Record separator of the joined history. A control character, so no expression can contain it.
private const val HISTORY_RECORD_SEPARATOR = "\u001E"

/**
 * DataStore-based implementation of WearPreferencesRepository.
 */
class WearPreferencesRepositoryImpl(
    private val context: Context
) : WearPreferencesRepository {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wear_settings")
    
    // Internal, not private: the one invariant that cannot be read off the code is that the file
    // list and the navigation screens address DIFFERENT keys (S1730 ADR-3), and a test has to see
    // both names to assert it.
    internal object PreferencesKeys {
        val AUDIO_ENABLED = booleanPreferencesKey("wear_audio_enabled")
        val VIDEO_ENABLED = booleanPreferencesKey("wear_video_enabled")
        val IMAGES_ENABLED = booleanPreferencesKey("wear_images_enabled")
        
        val SLIDESHOW_ENABLED = booleanPreferencesKey("wear_slideshow_enabled")
        val SLIDESHOW_INTERVAL = intPreferencesKey("wear_slideshow_interval_seconds")
        val SLIDESHOW_WAIT_FINISH = booleanPreferencesKey("wear_slideshow_wait_finish")
        
        val DOWNLOAD_ALBUM_ART = booleanPreferencesKey("wear_download_album_art")

        val SHUFFLE_ENABLED = booleanPreferencesKey("wear_shuffle_enabled")

        val VIEW_MODE = stringPreferencesKey("wear_view_mode")
        val FILE_LIST_VIEW_MODE = stringPreferencesKey("wear_file_list_view_mode")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("wear_keep_screen_awake")
        val LAST_USED_RESOURCE = stringPreferencesKey("wear_last_used_resource")
        val STREAMS_SECTION_ENABLED = booleanPreferencesKey("wear_streams_section_enabled")

        val CALCULATOR_HISTORY = stringPreferencesKey("wear_calculator_history")
        val CALCULATOR_MEMORY = stringPreferencesKey("wear_calculator_memory")
        val AUTO_ROTATION_ENABLED = booleanPreferencesKey("wear_auto_rotation_enabled")
        val APP_LANGUAGE = stringPreferencesKey("wear_app_language")
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

    // Screen settings
    override val viewMode: Flow<WearViewMode> = context.dataStore.data.map { prefs ->
        WearViewMode.fromNameOrDefault(prefs[PreferencesKeys.VIEW_MODE])
    }

    override suspend fun setViewMode(mode: WearViewMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.VIEW_MODE] = mode.name
        }
    }

    // S1730: its own key, never wear_view_mode - the home screen stays a list while a photo folder
    // is a grid, which one shared value cannot express.
    override val fileListViewMode: Flow<WearViewMode> = context.dataStore.data.map { prefs ->
        WearViewMode.fromNameOrDefault(prefs[PreferencesKeys.FILE_LIST_VIEW_MODE])
    }

    override suspend fun setFileListViewMode(mode: WearViewMode) {
        Timber.d("S1730: file list view stored as $mode")
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.FILE_LIST_VIEW_MODE] = mode.name
        }
    }

    override val keepScreenAwakeOutsidePlayers: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KEEP_SCREEN_AWAKE] ?: false
    }

    override suspend fun setKeepScreenAwakeOutsidePlayers(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.KEEP_SCREEN_AWAKE] = enabled
        }
    }

    // Home section state
    override val lastUsedResourceName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LAST_USED_RESOURCE]
    }

    override suspend fun setLastUsedResource(name: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_USED_RESOURCE] = name
        }
    }

    override suspend fun clearLastUsedResource() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.LAST_USED_RESOURCE)
        }
    }

    override val streamsSectionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.STREAMS_SECTION_ENABLED] ?: true
    }

    override suspend fun setStreamsSectionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.STREAMS_SECTION_ENABLED] = enabled
        }
    }

    // Calculator state. The history is one joined string rather than a string set: a set has no order,
    // and the history is defined newest first.
    override val calculatorHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.CALCULATOR_HISTORY]
            ?.split(HISTORY_RECORD_SEPARATOR)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    override suspend fun setCalculatorHistory(entries: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.CALCULATOR_HISTORY] = entries.joinToString(HISTORY_RECORD_SEPARATOR)
        }
    }

    override val calculatorMemory: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.CALCULATOR_MEMORY]
    }

    override suspend fun setCalculatorMemory(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(PreferencesKeys.CALCULATOR_MEMORY)
            } else {
                prefs[PreferencesKeys.CALCULATOR_MEMORY] = value
            }
        }
    }

    override val isAutoRotationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.AUTO_ROTATION_ENABLED] ?: false
    }

    override suspend fun setAutoRotationEnabled(enabled: Boolean) {
        Timber.d("S1718: setAutoRotationEnabled=$enabled")
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.AUTO_ROTATION_ENABLED] = enabled
        }
    }

    override val appLanguage: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.APP_LANGUAGE]
    }

    override suspend fun setAppLanguage(languageCode: String?) {
        context.dataStore.edit { prefs ->
            if (languageCode == null) {
                prefs.remove(PreferencesKeys.APP_LANGUAGE)
            } else {
                prefs[PreferencesKeys.APP_LANGUAGE] = languageCode
            }
        }
    }
}
