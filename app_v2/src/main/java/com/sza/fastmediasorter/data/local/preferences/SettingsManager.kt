package com.sza.fastmediasorter.data.local.preferences

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
    val movePanelCollapsed: Boolean = false,  // Remember collapsed state for Move to panel
    val showDetailedErrors: Boolean = false, // Show detailed error messages with stack traces
    val enableFavorites: Boolean = false, // Enable "Favorites" feature
    val maxRecipients: Int = 10, // Maximum number of destination buttons (1-30)
    val supportText: Boolean = false,
    val supportPdf: Boolean = false,
    val textSizeMax: Long = 104857600L,
    val enableTranslation: Boolean = false,
    val translationSourceLanguage: String = "en",
    val translationTargetLanguage: String = "ru",
    val translationLensStyle: Boolean = false,
    val enableSlideshowBackgroundMusic: Boolean = false, // Enable slideshow background music
    val slideshowMusicResourceId: Long? = null, // Resource ID of selected slideshow music
    val useTrash: Boolean = true, // Use .trash folder for recoverable deletion
    val cropImagesToFullscreen: Boolean = true // Crop images to fill screen when orientations match (fullscreen & slideshow)
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
        private val SHOW_DETAILED_ERRORS = booleanPreferencesKey("show_detailed_errors")
        private val ENABLE_FAVORITES = booleanPreferencesKey("enable_favorites")
        private val MAX_RECIPIENTS = intPreferencesKey("max_recipients")
        private val SUPPORT_TEXT = booleanPreferencesKey("support_text")
        private val SUPPORT_PDF = booleanPreferencesKey("support_pdf")
        private val TEXT_SIZE_MAX = longPreferencesKey("text_size_max")
        private val ENABLE_TRANSLATION = booleanPreferencesKey("enable_translation")
        private val TRANSLATION_SOURCE_LANGUAGE = stringPreferencesKey("translation_source_language")
        private val TRANSLATION_TARGET_LANGUAGE = stringPreferencesKey("translation_target_language")
        private val TRANSLATION_LENS_STYLE = booleanPreferencesKey("translation_lens_style")
        private val ENABLE_SLIDESHOW_BACKGROUND_MUSIC = booleanPreferencesKey("enable_slideshow_background_music")
        private val SLIDESHOW_MUSIC_RESOURCE_ID = longPreferencesKey("slideshow_music_resource_id")
        private val USE_TRASH = booleanPreferencesKey("use_trash")
        private val CROP_IMAGES_TO_FULLSCREEN = booleanPreferencesKey("crop_images_to_fullscreen")
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
            movePanelCollapsed = preferences[MOVE_PANEL_COLLAPSED] ?: false,
            showDetailedErrors = preferences[SHOW_DETAILED_ERRORS] ?: false,
            enableFavorites = preferences[ENABLE_FAVORITES] ?: false,
            maxRecipients = preferences[MAX_RECIPIENTS] ?: 10,
            supportText = preferences[SUPPORT_TEXT] ?: false,
            supportPdf = preferences[SUPPORT_PDF] ?: false,
            textSizeMax = preferences[TEXT_SIZE_MAX] ?: 104857600L,
            enableTranslation = preferences[ENABLE_TRANSLATION] ?: false,
            translationSourceLanguage = preferences[TRANSLATION_SOURCE_LANGUAGE] ?: "en",
            translationTargetLanguage = preferences[TRANSLATION_TARGET_LANGUAGE] ?: "ru",
            translationLensStyle = preferences[TRANSLATION_LENS_STYLE] ?: true,
            enableSlideshowBackgroundMusic = preferences[ENABLE_SLIDESHOW_BACKGROUND_MUSIC] ?: false,
            slideshowMusicResourceId = preferences[SLIDESHOW_MUSIC_RESOURCE_ID],
            useTrash = preferences[USE_TRASH] ?: true,
            cropImagesToFullscreen = preferences[CROP_IMAGES_TO_FULLSCREEN] ?: true
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
    
    suspend fun setShowDetailedErrors(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_DETAILED_ERRORS] = value
        }
    }

    suspend fun setEnableFavorites(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[ENABLE_FAVORITES] = value
        }
    }

    suspend fun setMaxRecipients(value: Int) {
        dataStore.edit { preferences ->
            preferences[MAX_RECIPIENTS] = value
        }
    }

    suspend fun setSupportText(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[SUPPORT_TEXT] = value
        }
    }

    suspend fun setSupportPdf(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[SUPPORT_PDF] = value
        }
    }
    
    suspend fun setTextSizeMax(value: Long) {
        dataStore.edit { preferences ->
            preferences[TEXT_SIZE_MAX] = value
        }
    }

    suspend fun setEnableTranslation(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[ENABLE_TRANSLATION] = value
        }
    }

    suspend fun setTranslationSourceLanguage(value: String) {
        dataStore.edit { preferences ->
            preferences[TRANSLATION_SOURCE_LANGUAGE] = value
        }
    }

    suspend fun setTranslationTargetLanguage(value: String) {
        dataStore.edit { preferences ->
            preferences[TRANSLATION_TARGET_LANGUAGE] = value
        }
    }

    suspend fun setTranslationLensStyle(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[TRANSLATION_LENS_STYLE] = value
        }
    }

    suspend fun setEnableSlideshowBackgroundMusic(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[ENABLE_SLIDESHOW_BACKGROUND_MUSIC] = value
        }
    }

    suspend fun setSlideshowMusicResourceId(value: Long?) {
        dataStore.edit { preferences ->
            if (value != null) {
                preferences[SLIDESHOW_MUSIC_RESOURCE_ID] = value
            } else {
                preferences.remove(SLIDESHOW_MUSIC_RESOURCE_ID)
            }
        }
    }

    suspend fun setUseTrash(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[USE_TRASH] = value
        }
    }

    suspend fun setCropImagesToFullscreen(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[CROP_IMAGES_TO_FULLSCREEN] = value
        }
    }
}
