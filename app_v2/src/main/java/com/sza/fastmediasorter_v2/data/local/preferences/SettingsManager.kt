package com.sza.fastmediasorter_v2.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val confirmDeletion: Boolean = true,
    val enableUndo: Boolean = true,
    val overwriteFiles: Boolean = false,
    val showRenameButton: Boolean = true,
    val defaultViewMode: String = "LIST", // LIST or GRID
    val slideshowInterval: Int = 3, // seconds
    val theme: String = "SYSTEM", // LIGHT, DARK, SYSTEM
    val enableImages: Boolean = true,
    val enableVideos: Boolean = true,
    val enableAudio: Boolean = true,
    val enableGifs: Boolean = true,
    val language: String = "system", // en, ru, uk, system
    val showPlayerHintOnFirstRun: Boolean = true, // Show touch zones overlay on first PlayerActivity launch
    val copyPanelCollapsed: Boolean = false, // Remember collapsed state for Copy to panel
    val movePanelCollapsed: Boolean = false  // Remember collapsed state for Move to panel
)

@Singleton
class SettingsManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    
    companion object {
        private val CONFIRM_DELETION = booleanPreferencesKey("confirm_deletion")
        private val ENABLE_UNDO = booleanPreferencesKey("enable_undo")
        private val OVERWRITE_FILES = booleanPreferencesKey("overwrite_files")
        private val SHOW_RENAME_BUTTON = booleanPreferencesKey("show_rename_button")
        private val DEFAULT_VIEW_MODE = stringPreferencesKey("default_view_mode")
        private val SLIDESHOW_INTERVAL = intPreferencesKey("slideshow_interval")
        private val THEME = stringPreferencesKey("theme")
        private val ENABLE_IMAGES = booleanPreferencesKey("enable_images")
        private val ENABLE_VIDEOS = booleanPreferencesKey("enable_videos")
        private val ENABLE_AUDIO = booleanPreferencesKey("enable_audio")
        private val ENABLE_GIFS = booleanPreferencesKey("enable_gifs")
        private val LANGUAGE = stringPreferencesKey("language")
        private val SHOW_PLAYER_HINT_ON_FIRST_RUN = booleanPreferencesKey("show_player_hint_on_first_run")
        private val COPY_PANEL_COLLAPSED = booleanPreferencesKey("copy_panel_collapsed")
        private val MOVE_PANEL_COLLAPSED = booleanPreferencesKey("move_panel_collapsed")
    }
    
    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            confirmDeletion = preferences[CONFIRM_DELETION] ?: true,
            enableUndo = preferences[ENABLE_UNDO] ?: true,
            overwriteFiles = preferences[OVERWRITE_FILES] ?: false,
            showRenameButton = preferences[SHOW_RENAME_BUTTON] ?: true,
            defaultViewMode = preferences[DEFAULT_VIEW_MODE] ?: "LIST",
            slideshowInterval = preferences[SLIDESHOW_INTERVAL] ?: 3,
            theme = preferences[THEME] ?: "SYSTEM",
            enableImages = preferences[ENABLE_IMAGES] ?: true,
            enableVideos = preferences[ENABLE_VIDEOS] ?: true,
            enableAudio = preferences[ENABLE_AUDIO] ?: true,
            enableGifs = preferences[ENABLE_GIFS] ?: true,
            language = preferences[LANGUAGE] ?: "system",
            showPlayerHintOnFirstRun = preferences[SHOW_PLAYER_HINT_ON_FIRST_RUN] ?: true,
            copyPanelCollapsed = preferences[COPY_PANEL_COLLAPSED] ?: false,
            movePanelCollapsed = preferences[MOVE_PANEL_COLLAPSED] ?: false
        )
    }
    
    suspend fun setConfirmDeletion(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[CONFIRM_DELETION] = value
        }
    }
    
    suspend fun setEnableUndo(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[ENABLE_UNDO] = value
        }
    }
    
    suspend fun setOverwriteFiles(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[OVERWRITE_FILES] = value
        }
    }
    
    suspend fun setShowRenameButton(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_RENAME_BUTTON] = value
        }
    }
    
    suspend fun setDefaultViewMode(value: String) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_VIEW_MODE] = value
        }
    }
    
    suspend fun setSlideshowInterval(value: Int) {
        dataStore.edit { preferences ->
            preferences[SLIDESHOW_INTERVAL] = value
        }
    }
    
    suspend fun setTheme(value: String) {
        dataStore.edit { preferences ->
            preferences[THEME] = value
        }
    }
    
    suspend fun setEnableImages(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[ENABLE_IMAGES] = value
        }
    }
    
    suspend fun setEnableVideos(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[ENABLE_VIDEOS] = value
        }
    }
    
    suspend fun setEnableAudio(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[ENABLE_AUDIO] = value
        }
    }
    
    suspend fun setEnableGifs(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[ENABLE_GIFS] = value
        }
    }
    
    suspend fun setLanguage(value: String) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE] = value
        }
    }
    
    suspend fun setShowPlayerHintOnFirstRun(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_PLAYER_HINT_ON_FIRST_RUN] = value
        }
    }
    
    suspend fun setCopyPanelCollapsed(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[COPY_PANEL_COLLAPSED] = value
        }
    }
    
    suspend fun setMovePanelCollapsed(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[MOVE_PANEL_COLLAPSED] = value
        }
    }
}
