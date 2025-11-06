package com.sza.fastmediasorter_v2.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.sza.fastmediasorter_v2.domain.model.AppSettings
import com.sza.fastmediasorter_v2.domain.model.SortMode
import com.sza.fastmediasorter_v2.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    companion object {
        // General settings keys
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_PREVENT_SLEEP = booleanPreferencesKey("prevent_sleep")
        private val KEY_SHOW_SMALL_CONTROLS = booleanPreferencesKey("show_small_controls")
        private val KEY_DEFAULT_USER = stringPreferencesKey("default_user")
        private val KEY_DEFAULT_PASSWORD = stringPreferencesKey("default_password")
        
        // Media Files settings keys
        private val KEY_SUPPORT_IMAGES = booleanPreferencesKey("support_images")
        private val KEY_IMAGE_SIZE_MIN = longPreferencesKey("image_size_min")
        private val KEY_IMAGE_SIZE_MAX = longPreferencesKey("image_size_max")
        private val KEY_SUPPORT_GIFS = booleanPreferencesKey("support_gifs")
        private val KEY_SUPPORT_VIDEOS = booleanPreferencesKey("support_videos")
        private val KEY_VIDEO_SIZE_MIN = longPreferencesKey("video_size_min")
        private val KEY_VIDEO_SIZE_MAX = longPreferencesKey("video_size_max")
        private val KEY_SUPPORT_AUDIO = booleanPreferencesKey("support_audio")
        private val KEY_AUDIO_SIZE_MIN = longPreferencesKey("audio_size_min")
        private val KEY_AUDIO_SIZE_MAX = longPreferencesKey("audio_size_max")
        
        // Playback and Sorting settings keys
        private val KEY_DEFAULT_SORT_MODE = stringPreferencesKey("default_sort_mode")
        private val KEY_SLIDESHOW_INTERVAL = intPreferencesKey("slideshow_interval")
        private val KEY_PLAY_TO_END = booleanPreferencesKey("play_to_end_in_slideshow")
        private val KEY_ALLOW_RENAME = booleanPreferencesKey("allow_rename")
        private val KEY_ALLOW_DELETE = booleanPreferencesKey("allow_delete")
        private val KEY_CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
        private val KEY_DEFAULT_GRID_MODE = booleanPreferencesKey("default_grid_mode")
        private val KEY_DEFAULT_ICON_SIZE = intPreferencesKey("default_icon_size")
        private val KEY_FULL_SCREEN_MODE = booleanPreferencesKey("full_screen_mode")
        private val KEY_SHOW_DETAILED_ERRORS = booleanPreferencesKey("show_detailed_errors")
        
        // Destinations settings keys
        private val KEY_ENABLE_COPYING = booleanPreferencesKey("enable_copying")
        private val KEY_GO_TO_NEXT_AFTER_COPY = booleanPreferencesKey("go_to_next_after_copy")
        private val KEY_OVERWRITE_ON_COPY = booleanPreferencesKey("overwrite_on_copy")
        private val KEY_ENABLE_MOVING = booleanPreferencesKey("enable_moving")
        private val KEY_OVERWRITE_ON_MOVE = booleanPreferencesKey("overwrite_on_move")
        private val KEY_ENABLE_UNDO = booleanPreferencesKey("enable_undo")
    }

    override fun getSettings(): Flow<AppSettings> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Timber.e(exception, "Error reading settings")
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                AppSettings(
                    // General
                    language = preferences[KEY_LANGUAGE] ?: "en",
                    preventSleep = preferences[KEY_PREVENT_SLEEP] ?: true,
                    showSmallControls = preferences[KEY_SHOW_SMALL_CONTROLS] ?: false,
                    defaultUser = preferences[KEY_DEFAULT_USER] ?: "",
                    defaultPassword = preferences[KEY_DEFAULT_PASSWORD] ?: "",
                    
                    // Media Files
                    supportImages = preferences[KEY_SUPPORT_IMAGES] ?: true,
                    imageSizeMin = preferences[KEY_IMAGE_SIZE_MIN] ?: 1024L,
                    imageSizeMax = preferences[KEY_IMAGE_SIZE_MAX] ?: 10485760L,
                    supportGifs = preferences[KEY_SUPPORT_GIFS] ?: false,
                    supportVideos = preferences[KEY_SUPPORT_VIDEOS] ?: true,
                    videoSizeMin = preferences[KEY_VIDEO_SIZE_MIN] ?: 102400L,
                    videoSizeMax = preferences[KEY_VIDEO_SIZE_MAX] ?: 1073741824L,
                    supportAudio = preferences[KEY_SUPPORT_AUDIO] ?: true,
                    audioSizeMin = preferences[KEY_AUDIO_SIZE_MIN] ?: 10240L,
                    audioSizeMax = preferences[KEY_AUDIO_SIZE_MAX] ?: 104857600L,
                    
                    // Playback and Sorting
                    defaultSortMode = SortMode.valueOf(
                        preferences[KEY_DEFAULT_SORT_MODE] ?: SortMode.NAME_ASC.name
                    ),
                    slideshowInterval = preferences[KEY_SLIDESHOW_INTERVAL] ?: 3,
                    playToEndInSlideshow = preferences[KEY_PLAY_TO_END] ?: false,
                    allowRename = preferences[KEY_ALLOW_RENAME] ?: true,
                    allowDelete = preferences[KEY_ALLOW_DELETE] ?: true,
                    confirmDelete = preferences[KEY_CONFIRM_DELETE] ?: true,
                    defaultGridMode = preferences[KEY_DEFAULT_GRID_MODE] ?: false,
                    defaultIconSize = preferences[KEY_DEFAULT_ICON_SIZE] ?: 100,
                    fullScreenMode = preferences[KEY_FULL_SCREEN_MODE] ?: true,
                    showDetailedErrors = preferences[KEY_SHOW_DETAILED_ERRORS] ?: false,
                    
                    // Destinations
                    enableCopying = preferences[KEY_ENABLE_COPYING] ?: true,
                    goToNextAfterCopy = preferences[KEY_GO_TO_NEXT_AFTER_COPY] ?: true,
                    overwriteOnCopy = preferences[KEY_OVERWRITE_ON_COPY] ?: false,
                    enableMoving = preferences[KEY_ENABLE_MOVING] ?: true,
                    overwriteOnMove = preferences[KEY_OVERWRITE_ON_MOVE] ?: false,
                    enableUndo = preferences[KEY_ENABLE_UNDO] ?: true
                )
            }
    }

    override suspend fun updateSettings(settings: AppSettings) {
        dataStore.edit { preferences ->
            // General
            preferences[KEY_LANGUAGE] = settings.language
            preferences[KEY_PREVENT_SLEEP] = settings.preventSleep
            preferences[KEY_SHOW_SMALL_CONTROLS] = settings.showSmallControls
            preferences[KEY_DEFAULT_USER] = settings.defaultUser
            preferences[KEY_DEFAULT_PASSWORD] = settings.defaultPassword
            
            // Media Files
            preferences[KEY_SUPPORT_IMAGES] = settings.supportImages
            preferences[KEY_IMAGE_SIZE_MIN] = settings.imageSizeMin
            preferences[KEY_IMAGE_SIZE_MAX] = settings.imageSizeMax
            preferences[KEY_SUPPORT_GIFS] = settings.supportGifs
            preferences[KEY_SUPPORT_VIDEOS] = settings.supportVideos
            preferences[KEY_VIDEO_SIZE_MIN] = settings.videoSizeMin
            preferences[KEY_VIDEO_SIZE_MAX] = settings.videoSizeMax
            preferences[KEY_SUPPORT_AUDIO] = settings.supportAudio
            preferences[KEY_AUDIO_SIZE_MIN] = settings.audioSizeMin
            preferences[KEY_AUDIO_SIZE_MAX] = settings.audioSizeMax
            
            // Playback and Sorting
            preferences[KEY_DEFAULT_SORT_MODE] = settings.defaultSortMode.name
            preferences[KEY_SLIDESHOW_INTERVAL] = settings.slideshowInterval
            preferences[KEY_PLAY_TO_END] = settings.playToEndInSlideshow
            preferences[KEY_ALLOW_RENAME] = settings.allowRename
            preferences[KEY_ALLOW_DELETE] = settings.allowDelete
            preferences[KEY_CONFIRM_DELETE] = settings.confirmDelete
            preferences[KEY_DEFAULT_GRID_MODE] = settings.defaultGridMode
            preferences[KEY_DEFAULT_ICON_SIZE] = settings.defaultIconSize
            preferences[KEY_FULL_SCREEN_MODE] = settings.fullScreenMode
            preferences[KEY_SHOW_DETAILED_ERRORS] = settings.showDetailedErrors
            
            // Destinations
            preferences[KEY_ENABLE_COPYING] = settings.enableCopying
            preferences[KEY_GO_TO_NEXT_AFTER_COPY] = settings.goToNextAfterCopy
            preferences[KEY_OVERWRITE_ON_COPY] = settings.overwriteOnCopy
            preferences[KEY_ENABLE_MOVING] = settings.enableMoving
            preferences[KEY_OVERWRITE_ON_MOVE] = settings.overwriteOnMove
            preferences[KEY_ENABLE_UNDO] = settings.enableUndo
        }
    }

    override suspend fun resetToDefaults() {
        updateSettings(AppSettings())
    }
}
