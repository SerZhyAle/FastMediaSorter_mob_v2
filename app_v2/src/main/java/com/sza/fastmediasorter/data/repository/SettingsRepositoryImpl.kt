package com.sza.fastmediasorter.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    companion object {
        // General settings keys
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_PREVENT_SLEEP = booleanPreferencesKey("prevent_sleep")
        private val KEY_SHOW_SMALL_CONTROLS = booleanPreferencesKey("show_small_controls")
        private val KEY_DEFAULT_USER = stringPreferencesKey("default_user")
        private val KEY_DEFAULT_PASSWORD = stringPreferencesKey("default_password")
        
        // Network sync settings keys
        private val KEY_ENABLE_BACKGROUND_SYNC = booleanPreferencesKey("enable_background_sync")
        private val KEY_BACKGROUND_SYNC_INTERVAL_HOURS = intPreferencesKey("background_sync_interval_hours")
        
        // Media Files settings keys
        private val KEY_SUPPORT_IMAGES = booleanPreferencesKey("support_images")
        private val KEY_IMAGE_SIZE_MIN = longPreferencesKey("image_size_min")
        private val KEY_IMAGE_SIZE_MAX = longPreferencesKey("image_size_max")
        private val KEY_LOAD_FULL_SIZE_IMAGES = booleanPreferencesKey("load_full_size_images")
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
        private val KEY_DEFAULT_SHOW_COMMAND_PANEL = booleanPreferencesKey("default_show_command_panel")
        private val KEY_SHOW_DETAILED_ERRORS = booleanPreferencesKey("show_detailed_errors")
        private val KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN = booleanPreferencesKey("show_player_hint_on_first_run")
        private val KEY_SHOW_VIDEO_THUMBNAILS = booleanPreferencesKey("show_video_thumbnails")
        
        // Destinations settings keys
        private val KEY_ENABLE_COPYING = booleanPreferencesKey("enable_copying")
        private val KEY_GO_TO_NEXT_AFTER_COPY = booleanPreferencesKey("go_to_next_after_copy")
        private val KEY_OVERWRITE_ON_COPY = booleanPreferencesKey("overwrite_on_copy")
        private val KEY_ENABLE_MOVING = booleanPreferencesKey("enable_moving")
        private val KEY_OVERWRITE_ON_MOVE = booleanPreferencesKey("overwrite_on_move")
        private val KEY_ENABLE_UNDO = booleanPreferencesKey("enable_undo")
        private val KEY_IS_PLAYER_FIRST_RUN = booleanPreferencesKey("is_player_first_run")
        
        // Player UI settings keys
        private val KEY_COPY_PANEL_COLLAPSED = booleanPreferencesKey("copy_panel_collapsed")
        private val KEY_MOVE_PANEL_COLLAPSED = booleanPreferencesKey("move_panel_collapsed")
        
        // Last used resource key
        private val KEY_LAST_USED_RESOURCE_ID = longPreferencesKey("last_used_resource_id")
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
                val language = preferences[KEY_LANGUAGE] ?: "en"
                
                // Sync language to SharedPreferences for LocaleHelper (if not already synced)
                val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val savedLanguage = sharedPrefs.getString("selected_language", null)
                if (savedLanguage != language) {
                    sharedPrefs.edit().putString("selected_language", language).apply()
                }
                
                AppSettings(
                    // General
                    language = language,
                    preventSleep = preferences[KEY_PREVENT_SLEEP] ?: true,
                    showSmallControls = preferences[KEY_SHOW_SMALL_CONTROLS] ?: false,
                    defaultUser = preferences[KEY_DEFAULT_USER] ?: "",
                    defaultPassword = preferences[KEY_DEFAULT_PASSWORD] ?: "",
                    
                    // Network sync
                    enableBackgroundSync = preferences[KEY_ENABLE_BACKGROUND_SYNC] ?: true,
                    backgroundSyncIntervalHours = preferences[KEY_BACKGROUND_SYNC_INTERVAL_HOURS] ?: 4,
                    
                    // Media Files
                    supportImages = preferences[KEY_SUPPORT_IMAGES] ?: true,
                    imageSizeMin = preferences[KEY_IMAGE_SIZE_MIN] ?: 1024L,
                    imageSizeMax = preferences[KEY_IMAGE_SIZE_MAX] ?: 10485760L,
                    loadFullSizeImages = preferences[KEY_LOAD_FULL_SIZE_IMAGES] ?: false,
                    supportGifs = preferences[KEY_SUPPORT_GIFS] ?: false,
                    supportVideos = preferences[KEY_SUPPORT_VIDEOS] ?: true,
                    videoSizeMin = preferences[KEY_VIDEO_SIZE_MIN] ?: 102400L,
                    videoSizeMax = preferences[KEY_VIDEO_SIZE_MAX] ?: 107374182400L,
                    supportAudio = preferences[KEY_SUPPORT_AUDIO] ?: true,
                    audioSizeMin = preferences[KEY_AUDIO_SIZE_MIN] ?: 10240L,
                    audioSizeMax = preferences[KEY_AUDIO_SIZE_MAX] ?: 104857600L,
                    
                    // Playback and Sorting
                    defaultSortMode = SortMode.valueOf(
                        preferences[KEY_DEFAULT_SORT_MODE] ?: SortMode.NAME_ASC.name
                    ),
                    slideshowInterval = preferences[KEY_SLIDESHOW_INTERVAL] ?: 10,
                    playToEndInSlideshow = preferences[KEY_PLAY_TO_END] ?: false,
                    allowRename = preferences[KEY_ALLOW_RENAME] ?: true,
                    allowDelete = preferences[KEY_ALLOW_DELETE] ?: true,
                    confirmDelete = preferences[KEY_CONFIRM_DELETE] ?: true,
                    defaultGridMode = preferences[KEY_DEFAULT_GRID_MODE] ?: false,
                    defaultIconSize = run {
                        val savedSize = preferences[KEY_DEFAULT_ICON_SIZE] ?: 96
                        // Validate: must be 32 + 8*N (valid range: 32..256)
                        if (savedSize < 32 || savedSize > 256 || (savedSize - 32) % 8 != 0) 96 else savedSize
                    },
                    defaultShowCommandPanel = preferences[KEY_DEFAULT_SHOW_COMMAND_PANEL] ?: true,
                    showDetailedErrors = preferences[KEY_SHOW_DETAILED_ERRORS] ?: false,
                    showPlayerHintOnFirstRun = preferences[KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN] ?: true,
                    showVideoThumbnails = preferences[KEY_SHOW_VIDEO_THUMBNAILS] ?: true,
                    
                    // Destinations
                    enableCopying = preferences[KEY_ENABLE_COPYING] ?: true,
                    goToNextAfterCopy = preferences[KEY_GO_TO_NEXT_AFTER_COPY] ?: true,
                    overwriteOnCopy = preferences[KEY_OVERWRITE_ON_COPY] ?: false,
                    enableMoving = preferences[KEY_ENABLE_MOVING] ?: true,
                    overwriteOnMove = preferences[KEY_OVERWRITE_ON_MOVE] ?: false,
                    enableUndo = preferences[KEY_ENABLE_UNDO] ?: true,
                    
                    // Player UI
                    copyPanelCollapsed = preferences[KEY_COPY_PANEL_COLLAPSED] ?: false,
                    movePanelCollapsed = preferences[KEY_MOVE_PANEL_COLLAPSED] ?: false,
                    
                    // Last used resource
                    lastUsedResourceId = preferences[KEY_LAST_USED_RESOURCE_ID] ?: -1L
                )
            }
    }

    override suspend fun updateSettings(settings: AppSettings) {
        // Sync language to SharedPreferences for LocaleHelper (synchronous access in attachBaseContext)
        val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("selected_language", settings.language).apply()
        
        dataStore.edit { preferences ->
            // General
            preferences[KEY_LANGUAGE] = settings.language
            preferences[KEY_PREVENT_SLEEP] = settings.preventSleep
            preferences[KEY_SHOW_SMALL_CONTROLS] = settings.showSmallControls
            preferences[KEY_DEFAULT_USER] = settings.defaultUser
            preferences[KEY_DEFAULT_PASSWORD] = settings.defaultPassword
            preferences[KEY_ENABLE_BACKGROUND_SYNC] = settings.enableBackgroundSync
            preferences[KEY_BACKGROUND_SYNC_INTERVAL_HOURS] = settings.backgroundSyncIntervalHours
            
            // Media Files
            preferences[KEY_SUPPORT_IMAGES] = settings.supportImages
            preferences[KEY_IMAGE_SIZE_MIN] = settings.imageSizeMin
            preferences[KEY_IMAGE_SIZE_MAX] = settings.imageSizeMax
            preferences[KEY_LOAD_FULL_SIZE_IMAGES] = settings.loadFullSizeImages
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
            preferences[KEY_DEFAULT_SHOW_COMMAND_PANEL] = settings.defaultShowCommandPanel
            preferences[KEY_SHOW_DETAILED_ERRORS] = settings.showDetailedErrors
            preferences[KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN] = settings.showPlayerHintOnFirstRun
            preferences[KEY_SHOW_VIDEO_THUMBNAILS] = settings.showVideoThumbnails
            
            // Destinations
            preferences[KEY_ENABLE_COPYING] = settings.enableCopying
            preferences[KEY_GO_TO_NEXT_AFTER_COPY] = settings.goToNextAfterCopy
            preferences[KEY_OVERWRITE_ON_COPY] = settings.overwriteOnCopy
            preferences[KEY_ENABLE_MOVING] = settings.enableMoving
            preferences[KEY_OVERWRITE_ON_MOVE] = settings.overwriteOnMove
            preferences[KEY_ENABLE_UNDO] = settings.enableUndo
            
            // Player UI
            preferences[KEY_COPY_PANEL_COLLAPSED] = settings.copyPanelCollapsed
            preferences[KEY_MOVE_PANEL_COLLAPSED] = settings.movePanelCollapsed
            
            // Last used resource
            preferences[KEY_LAST_USED_RESOURCE_ID] = settings.lastUsedResourceId
        }
    }

    override suspend fun resetToDefaults() {
        updateSettings(AppSettings())
    }
    
    override suspend fun setPlayerFirstRun(isFirstRun: Boolean) {
        val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("is_player_first_run", isFirstRun).apply()
    }
    
    override suspend fun isPlayerFirstRun(): Boolean {
        val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        // Default to true for first launch
        return sharedPrefs.getBoolean("is_player_first_run", true)
    }
    
    override suspend fun saveLastUsedResourceId(resourceId: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_USED_RESOURCE_ID] = resourceId
        }
    }
    
    override suspend fun getLastUsedResourceId(): Long {
        return dataStore.data.map { preferences ->
            preferences[KEY_LAST_USED_RESOURCE_ID] ?: -1L
        }.catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading last used resource ID")
                emit(-1L)
            } else {
                throw exception
            }
        }.first()
    }
}
