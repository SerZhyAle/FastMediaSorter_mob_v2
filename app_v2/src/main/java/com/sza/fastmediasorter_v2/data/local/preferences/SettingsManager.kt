package com.sza.fastmediasorter_v2.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

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
    val language: String = "system" // en, ru, uk, system
)

@Singleton
class SettingsManager @Inject constructor(
    private val context: Context
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
    }
    
    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
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
            language = preferences[LANGUAGE] ?: "system"
        )
    }
    
    suspend fun setConfirmDeletion(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CONFIRM_DELETION] = value
        }
    }
    
    suspend fun setEnableUndo(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_UNDO] = value
        }
    }
    
    suspend fun setOverwriteFiles(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[OVERWRITE_FILES] = value
        }
    }
    
    suspend fun setShowRenameButton(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_RENAME_BUTTON] = value
        }
    }
    
    suspend fun setDefaultViewMode(value: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_VIEW_MODE] = value
        }
    }
    
    suspend fun setSlideshowInterval(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[SLIDESHOW_INTERVAL] = value
        }
    }
    
    suspend fun setTheme(value: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME] = value
        }
    }
    
    suspend fun setEnableImages(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_IMAGES] = value
        }
    }
    
    suspend fun setEnableVideos(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_VIDEOS] = value
        }
    }
    
    suspend fun setEnableAudio(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_AUDIO] = value
        }
    }
    
    suspend fun setEnableGifs(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_GIFS] = value
        }
    }
    
    suspend fun setLanguage(value: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE] = value
        }
    }
}
