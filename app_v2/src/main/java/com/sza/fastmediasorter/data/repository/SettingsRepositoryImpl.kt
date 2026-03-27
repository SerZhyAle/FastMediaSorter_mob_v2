package com.sza.fastmediasorter.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.model.TouchZoneHintType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    companion object {
        // General settings keys
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_PREVENT_SLEEP = booleanPreferencesKey("prevent_sleep")
        private val KEY_SHOW_SMALL_CONTROLS = booleanPreferencesKey("show_small_controls")
        private val KEY_DEFAULT_USER = stringPreferencesKey("default_user")
        private val KEY_DEFAULT_PASSWORD = stringPreferencesKey("default_password")
        private val KEY_NETWORK_PARALLELISM = intPreferencesKey("network_parallelism")
        private val KEY_CACHE_SIZE_MB = intPreferencesKey("cache_size_mb")
        private val KEY_IS_CACHE_SIZE_USER_MODIFIED = booleanPreferencesKey("is_cache_size_user_modified")
        
        // Network sync settings keys
        private val KEY_ENABLE_BACKGROUND_SYNC = booleanPreferencesKey("enable_background_sync")
        private val KEY_BACKGROUND_SYNC_INTERVAL_HOURS = intPreferencesKey("background_sync_interval_hours")
        private val KEY_ALL_FILES = booleanPreferencesKey("all_files")
        private val KEY_SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        private val KEY_SHOW_SUBFOLDERS_AS_ITEMS = booleanPreferencesKey("show_subfolders_as_items")
        
        // Media Files settings keys
        private val KEY_SUPPORT_IMAGES = booleanPreferencesKey("support_images")
        private val KEY_IMAGE_SIZE_MIN = longPreferencesKey("image_size_min")
        private val KEY_IMAGE_SIZE_MAX = longPreferencesKey("image_size_max")
        private val KEY_LOAD_FULL_SIZE_IMAGES = booleanPreferencesKey("load_full_size_images")
        private val KEY_CROP_IMAGES_TO_FULLSCREEN = booleanPreferencesKey("crop_images_to_fullscreen")
        private val KEY_SUPPORT_GIFS = booleanPreferencesKey("support_gifs")
        private val KEY_SUPPORT_VIDEOS = booleanPreferencesKey("support_videos")
        private val KEY_VIDEO_SIZE_MIN = longPreferencesKey("video_size_min")
        private val KEY_VIDEO_SIZE_MAX = longPreferencesKey("video_size_max")
        private val KEY_SUPPORT_AUDIO = booleanPreferencesKey("support_audio")
        private val KEY_AUDIO_SIZE_MIN = longPreferencesKey("audio_size_min")
        private val KEY_AUDIO_SIZE_MAX = longPreferencesKey("audio_size_max")
        private val KEY_SEARCH_AUDIO_COVERS_ONLINE = booleanPreferencesKey("search_audio_covers_online")
        private val KEY_SEARCH_AUDIO_COVERS_ONLY_ON_WIFI = booleanPreferencesKey("search_audio_covers_only_on_wifi")
        private val KEY_SAVE_AUDIO_METADATA_LOCALLY = booleanPreferencesKey("save_audio_metadata_locally")
        private val KEY_ENABLE_PHOTOS_DURING_AUDIO = booleanPreferencesKey("enable_photos_during_audio")
        private val KEY_AUDIO_BACKGROUND_PHOTOS_RESOURCE_ID = stringPreferencesKey("audio_background_photos_resource_id")
        // Persistent audio playback: continue playing audio via foreground service when app is minimized/screen locked (like YouTube Music).
        // NOT related to enableSlideshowBackgroundMusic (in-app slideshow music) or enablePhotosDuringAudio (photo overlay during audio).
        // Key string "enable_background_audio" preserved for backward compatibility with existing user settings.
        private val KEY_ENABLE_BACKGROUND_AUDIO = booleanPreferencesKey("enable_background_audio")
        private val KEY_AUDIO_EMPTY_STATE_MODE = stringPreferencesKey("audio_empty_state_mode")
        private val KEY_SUPPORT_TEXT = booleanPreferencesKey("support_text")
        private val KEY_SUPPORT_PDF = booleanPreferencesKey("support_pdf")
        private val KEY_SUPPORT_EPUB = booleanPreferencesKey("support_epub")
        private val KEY_SHOW_PDF_THUMBNAILS = booleanPreferencesKey("show_pdf_thumbnails")
        private val KEY_TEXT_SIZE_MAX = longPreferencesKey("text_size_max")
        private val KEY_SHOW_TEXT_LINE_NUMBERS = booleanPreferencesKey("show_text_line_numbers")
        private val KEY_TEXT_READER_THEME = stringPreferencesKey("text_reader_theme")
        private val KEY_MARKDOWN_RENDERED = booleanPreferencesKey("markdown_rendered")
        private val KEY_SYNTAX_HIGHLIGHTING = booleanPreferencesKey("syntax_highlighting")
        private val KEY_PDF_SCROLL_MODE = booleanPreferencesKey("pdf_scroll_mode")
        private val KEY_PDF_COLOR_MODE = stringPreferencesKey("pdf_color_mode")
        private val KEY_EPUB_LINE_HEIGHT = floatPreferencesKey("epub_line_height")
        private val KEY_EPUB_HORIZONTAL_MARGIN = intPreferencesKey("epub_horizontal_margin")
        
        // Translation settings keys
        private val KEY_ENABLE_TRANSLATION = booleanPreferencesKey("enable_translation")
        private val KEY_TRANSLATION_SOURCE_LANGUAGE = stringPreferencesKey("translation_source_language")
        private val KEY_TRANSLATION_TARGET_LANGUAGE = stringPreferencesKey("translation_target_language")
        private val KEY_TRANSLATION_LENS_STYLE = booleanPreferencesKey("translation_lens_style")
        private val KEY_ENABLE_GOOGLE_LENS = booleanPreferencesKey("enable_google_lens")
        private val KEY_ENABLE_OCR = booleanPreferencesKey("enable_ocr")
        private val KEY_OCR_DEFAULT_FONT_SIZE = stringPreferencesKey("ocr_default_font_size")
        private val KEY_OCR_DEFAULT_FONT_FAMILY = stringPreferencesKey("ocr_default_font_family")
        
        // Playback and Sorting settings keys
        private val KEY_DEFAULT_SORT_MODE = stringPreferencesKey("default_sort_mode")
        private val KEY_SLIDESHOW_INTERVAL = intPreferencesKey("slideshow_interval")
        private val KEY_SLIDESHOW_MUSIC_URI = stringPreferencesKey("slideshow_music_uri")
        private val KEY_ENABLE_SLIDESHOW_BACKGROUND_MUSIC = booleanPreferencesKey("enable_slideshow_background_music")
        private val KEY_SLIDESHOW_MUSIC_RESOURCE_ID = longPreferencesKey("slideshow_music_resource_id")
        private val KEY_PLAY_TO_END = booleanPreferencesKey("play_to_end_in_slideshow")
        private val KEY_ALLOW_RENAME = booleanPreferencesKey("allow_rename")
        private val KEY_ALLOW_DELETE = booleanPreferencesKey("allow_delete")
        private val KEY_USE_TRASH = booleanPreferencesKey("use_trash")
        private val KEY_CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
        private val KEY_CONFIRM_MOVE = booleanPreferencesKey("confirm_move")
        private val KEY_DEFAULT_GRID_MODE = booleanPreferencesKey("default_grid_mode")
        private val KEY_HIDE_GRID_ACTION_BUTTONS = booleanPreferencesKey("hide_grid_action_buttons")
        private val KEY_HIDE_SYSTEM_UI_IN_FULLSCREEN = booleanPreferencesKey("hide_system_ui_in_fullscreen")
        private val KEY_DEFAULT_ICON_SIZE = intPreferencesKey("default_icon_size")
        private val KEY_DEFAULT_SHOW_COMMAND_PANEL = booleanPreferencesKey("default_show_command_panel")
        private val KEY_SHOW_DETAILED_ERRORS = booleanPreferencesKey("show_detailed_errors")
        private val KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN = booleanPreferencesKey("show_player_hint_on_first_run")
        private val KEY_ALWAYS_SHOW_TOUCH_ZONES_OVERLAY = booleanPreferencesKey("always_show_touch_zones_overlay")
        private val KEY_SHOW_VIDEO_THUMBNAILS = booleanPreferencesKey("show_video_thumbnails")
        private val KEY_ENABLE_PLAYER_WARMUP = booleanPreferencesKey("enable_player_warmup")
        private val KEY_RENDERER_MIGRATION_ENABLED = booleanPreferencesKey("renderer_migration_enabled")
        
        // Safe Mode settings key (Phase 2.1)
        private val KEY_ENABLE_SAFE_MODE = booleanPreferencesKey("enable_safe_mode")
        
        // Scheduled operations
        private val KEY_ENABLE_SCHEDULED_OPERATIONS = booleanPreferencesKey("enable_scheduled_operations")

        // Destinations settings keys
        private val KEY_ENABLE_COPYING = booleanPreferencesKey("enable_copying")
        private val KEY_GO_TO_NEXT_AFTER_COPY = booleanPreferencesKey("go_to_next_after_copy")
        private val KEY_OVERWRITE_ON_COPY = booleanPreferencesKey("overwrite_on_copy")
        private val KEY_ENABLE_MOVING = booleanPreferencesKey("enable_moving")
        private val KEY_OVERWRITE_ON_MOVE = booleanPreferencesKey("overwrite_on_move")
        private val KEY_ENABLE_UNDO = booleanPreferencesKey("enable_undo")
        private val KEY_MAX_RECIPIENTS = intPreferencesKey("max_recipients")
        private val KEY_ENABLE_FAVORITES = booleanPreferencesKey("enable_favorites")
        private val KEY_IS_PLAYER_FIRST_RUN = booleanPreferencesKey("is_player_first_run")
        
        // Per-type touch zone hint tracking keys (Task 6)
        private val KEY_HINT_SHOWN_9ZONE = booleanPreferencesKey("hint_shown_9zone")
        private val KEY_HINT_SHOWN_3ZONE = booleanPreferencesKey("hint_shown_3zone")
        private val KEY_HINT_SHOWN_MEDIA = booleanPreferencesKey("hint_shown_media_bottom")
        
        // Player UI settings keys
        private val KEY_COPY_PANEL_COLLAPSED = booleanPreferencesKey("copy_panel_collapsed")
        private val KEY_MOVE_PANEL_COLLAPSED = booleanPreferencesKey("move_panel_collapsed")
        private val KEY_ENABLE_PICTURE_IN_PICTURE = booleanPreferencesKey("enable_picture_in_picture")
        
        // Last used resource key
        private val KEY_LAST_USED_RESOURCE_ID = longPreferencesKey("last_used_resource_id")
        
        // File list caching
        private val KEY_DEFAULT_REMEMBER_FILE_LIST = booleanPreferencesKey("default_remember_file_list")
        
        // UI State keys
        private val KEY_IS_RESOURCE_GRID_MODE = booleanPreferencesKey("is_resource_grid_mode")
        
        // Dynamic Background Expansion
        private val KEY_DYNAMIC_BACKGROUND_EXTENSION = booleanPreferencesKey("dynamic_background_extension")

        // Phase 5: Use as primary media player
        private val KEY_IS_PRIMARY_MEDIA_PLAYER = booleanPreferencesKey("is_primary_media_player")

        // Phase 6: Accept shared media files
        private val KEY_ACCEPT_SHARED_FILES = booleanPreferencesKey("accept_shared_files")
    }

    // Cached once per singleton — avoids repeated getSharedPreferences() calls inside DataStore map {}
    private val glidePrefs by lazy {
        context.getSharedPreferences("app_settings_glide", Context.MODE_PRIVATE)
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
                val languageFromDataStore = preferences[KEY_LANGUAGE]   // null = not explicitly set
                val language = languageFromDataStore ?: "en"
                // NOTE: Do NOT sync language to SharedPreferences here.
                // LocaleHelper.getLanguage() manages SharedPreferences independently:
                // - Falls back to system locale (uk/ru) when no preference saved yet
                // - SharedPreferences is updated only via explicit LocaleHelper.saveLanguage() calls
                // Syncing here would overwrite the system-locale fallback with the DataStore default "en".
                
                // Cache size for Glide (GlideAppModule reads from SharedPreferences during init)
                // glidePrefs is a lazy singleton field — no disk access on every emission
                val cacheSizeMb = preferences[KEY_CACHE_SIZE_MB] ?: 2048
                val savedCacheSize = glidePrefs.getInt("cache_size_mb_cached", 0)
                if (savedCacheSize != cacheSizeMb) {
                    glidePrefs.edit().putInt("cache_size_mb_cached", cacheSizeMb).apply()
                    if (BuildConfig.DEBUG) Timber.d("SettingsRepositoryImpl: Synced cacheSizeMb to SharedPreferences: ${cacheSizeMb}MB")
                }
                
                AppSettings(
                    // General
                    language = language,
                    preventSleep = preferences[KEY_PREVENT_SLEEP] ?: true,
                    showSmallControls = preferences[KEY_SHOW_SMALL_CONTROLS] ?: false,
                    defaultUser = preferences[KEY_DEFAULT_USER] ?: "",
                    defaultPassword = decryptPassword(preferences[KEY_DEFAULT_PASSWORD]),
                    networkParallelism = preferences[KEY_NETWORK_PARALLELISM] ?: 4,
                    cacheSizeMb = preferences[KEY_CACHE_SIZE_MB] ?: 2048,
                    isCacheSizeUserModified = preferences[KEY_IS_CACHE_SIZE_USER_MODIFIED] ?: false,
                    
                    // UI State
                    isResourceGridMode = preferences[KEY_IS_RESOURCE_GRID_MODE] ?: false,
                    
                    // Network sync
                    enableBackgroundSync = preferences[KEY_ENABLE_BACKGROUND_SYNC] ?: false,
                    backgroundSyncIntervalHours = preferences[KEY_BACKGROUND_SYNC_INTERVAL_HOURS] ?: 4,
                    allFiles = preferences[KEY_ALL_FILES] ?: false,
                    showHiddenFiles = preferences[KEY_SHOW_HIDDEN_FILES] ?: false,
                    showSubfoldersAsItems = preferences[KEY_SHOW_SUBFOLDERS_AS_ITEMS] ?: false,
                    
                    // Media Files
                    supportImages = preferences[KEY_SUPPORT_IMAGES] ?: true,
                    imageSizeMin = preferences[KEY_IMAGE_SIZE_MIN] ?: 1024L,
                    imageSizeMax = preferences[KEY_IMAGE_SIZE_MAX] ?: 10485760L,
                    loadFullSizeImages = preferences[KEY_LOAD_FULL_SIZE_IMAGES] ?: true,
                    cropImagesToFullscreen = preferences[KEY_CROP_IMAGES_TO_FULLSCREEN] ?: true,
                    supportGifs = preferences[KEY_SUPPORT_GIFS] ?: true,
                    supportVideos = preferences[KEY_SUPPORT_VIDEOS] ?: true,
                    videoSizeMin = preferences[KEY_VIDEO_SIZE_MIN] ?: 1048576L, // 1MB in bytes
                    videoSizeMax = preferences[KEY_VIDEO_SIZE_MAX] ?: 107374182400L,
                    supportAudio = preferences[KEY_SUPPORT_AUDIO] ?: true,
                    audioSizeMin = preferences[KEY_AUDIO_SIZE_MIN] ?: 0L,
                    audioSizeMax = preferences[KEY_AUDIO_SIZE_MAX] ?: 1073741824L, // 1GB
                    searchAudioCoversOnline = preferences[KEY_SEARCH_AUDIO_COVERS_ONLINE] ?: false,
                    searchAudioCoversOnlyOnWifi = preferences[KEY_SEARCH_AUDIO_COVERS_ONLY_ON_WIFI] ?: true,
                    saveAudioMetadataLocally = preferences[KEY_SAVE_AUDIO_METADATA_LOCALLY] ?: true,
                    enablePhotosDuringAudio = preferences[KEY_ENABLE_PHOTOS_DURING_AUDIO] ?: false,
                    audioBackgroundPhotosResourceId = preferences[KEY_AUDIO_BACKGROUND_PHOTOS_RESOURCE_ID],
                    enablePersistentAudioPlayback = preferences[KEY_ENABLE_BACKGROUND_AUDIO] ?: false,
                    audioEmptyStateMode = preferences[KEY_AUDIO_EMPTY_STATE_MODE] ?: "CANVAS_WAVES",
                    supportText = preferences[KEY_SUPPORT_TEXT] ?: true,
                    supportPdf = preferences[KEY_SUPPORT_PDF] ?: true,
                    supportEpub = preferences[KEY_SUPPORT_EPUB] ?: true,
                    showPdfThumbnails = preferences[KEY_SHOW_PDF_THUMBNAILS] ?: false,
                    textSizeMax = preferences[KEY_TEXT_SIZE_MAX] ?: 104857600L,
                    showTextLineNumbers = preferences[KEY_SHOW_TEXT_LINE_NUMBERS] ?: false,
                    textReaderTheme = preferences[KEY_TEXT_READER_THEME] ?: "SYSTEM",
                    markdownRendered = preferences[KEY_MARKDOWN_RENDERED] ?: true,
                    syntaxHighlighting = preferences[KEY_SYNTAX_HIGHLIGHTING] ?: true,
                    pdfScrollMode = preferences[KEY_PDF_SCROLL_MODE] ?: false,
                    pdfColorMode = preferences[KEY_PDF_COLOR_MODE] ?: "NORMAL",
                    epubLineHeight = preferences[KEY_EPUB_LINE_HEIGHT] ?: 1.6f,
                    epubHorizontalMargin = preferences[KEY_EPUB_HORIZONTAL_MARGIN] ?: 16,
                    
                    // Translation
                    enableTranslation = preferences[KEY_ENABLE_TRANSLATION] ?: true,
                    translationSourceLanguage = preferences[KEY_TRANSLATION_SOURCE_LANGUAGE] ?: "auto",
                    translationTargetLanguage = preferences[KEY_TRANSLATION_TARGET_LANGUAGE] ?: "ru",
                    translationLensStyle = preferences[KEY_TRANSLATION_LENS_STYLE] ?: true,
                    enableGoogleLens = preferences[KEY_ENABLE_GOOGLE_LENS] ?: true,
                    enableOcr = preferences[KEY_ENABLE_OCR] ?: true,
                    ocrDefaultFontSize = preferences[KEY_OCR_DEFAULT_FONT_SIZE] ?: "AUTO",
                    ocrDefaultFontFamily = preferences[KEY_OCR_DEFAULT_FONT_FAMILY] ?: "DEFAULT",
                    
                    // Playback and Sorting
                    defaultSortMode = SortMode.valueOf(
                        preferences[KEY_DEFAULT_SORT_MODE] ?: SortMode.NAME_ASC.name
                    ),
                    slideshowInterval = preferences[KEY_SLIDESHOW_INTERVAL] ?: 10,
                    slideshowMusicUri = preferences[KEY_SLIDESHOW_MUSIC_URI],
                    enableSlideshowBackgroundMusic = preferences[KEY_ENABLE_SLIDESHOW_BACKGROUND_MUSIC] ?: false,
                    slideshowMusicResourceId = preferences[KEY_SLIDESHOW_MUSIC_RESOURCE_ID],
                    playToEndInSlideshow = preferences[KEY_PLAY_TO_END] ?: true,
                    allowRename = preferences[KEY_ALLOW_RENAME] ?: true,
                    allowDelete = preferences[KEY_ALLOW_DELETE] ?: true,
                    useTrash = preferences[KEY_USE_TRASH] ?: true,
                    confirmDelete = preferences[KEY_CONFIRM_DELETE] ?: true,
                    confirmMove = preferences[KEY_CONFIRM_MOVE] ?: false,
                    defaultGridMode = preferences[KEY_DEFAULT_GRID_MODE] ?: false,
                    hideGridActionButtons = preferences[KEY_HIDE_GRID_ACTION_BUTTONS] ?: true,
                    hideSystemUiInFullscreen = preferences[KEY_HIDE_SYSTEM_UI_IN_FULLSCREEN] ?: true,
                    defaultIconSize = run {
                        val savedSize = preferences[KEY_DEFAULT_ICON_SIZE] ?: 96
                        // Validate: must be 32 + 8*N (valid range: 32..256)
                        if (savedSize < 32 || savedSize > 256 || (savedSize - 32) % 8 != 0) 96 else savedSize
                    },
                    defaultShowCommandPanel = preferences[KEY_DEFAULT_SHOW_COMMAND_PANEL] ?: true,
                    showDetailedErrors = preferences[KEY_SHOW_DETAILED_ERRORS] ?: false,
                    showPlayerHintOnFirstRun = preferences[KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN] ?: true,
                    alwaysShowTouchZonesOverlay = preferences[KEY_ALWAYS_SHOW_TOUCH_ZONES_OVERLAY] ?: false,
                    showVideoThumbnails = preferences[KEY_SHOW_VIDEO_THUMBNAILS] ?: true,
                    enablePlayerWarmup = preferences[KEY_ENABLE_PLAYER_WARMUP] ?: false,
                    rendererMigrationEnabled = preferences[KEY_RENDERER_MIGRATION_ENABLED] ?: false,
                    
                    // Safe Mode (Phase 2.1)
                    enableSafeMode = preferences[KEY_ENABLE_SAFE_MODE] ?: true,
                    
                    // Scheduled operations
                    enableScheduledOperations = preferences[KEY_ENABLE_SCHEDULED_OPERATIONS] ?: false,

                    // Destinations
                    enableCopying = preferences[KEY_ENABLE_COPYING] ?: true,
                    goToNextAfterCopy = preferences[KEY_GO_TO_NEXT_AFTER_COPY] ?: true,
                    overwriteOnCopy = preferences[KEY_OVERWRITE_ON_COPY] ?: false,
                    enableMoving = preferences[KEY_ENABLE_MOVING] ?: true,
                    overwriteOnMove = preferences[KEY_OVERWRITE_ON_MOVE] ?: false,
                    enableUndo = preferences[KEY_ENABLE_UNDO] ?: true,
                    maxRecipients = run {
                        val value = preferences[KEY_MAX_RECIPIENTS] ?: 10
                        value.coerceIn(1, 10)
                    },
                    enableFavorites = preferences[KEY_ENABLE_FAVORITES] ?: true,
                    
                    // Player UI
                    copyPanelCollapsed = preferences[KEY_COPY_PANEL_COLLAPSED] ?: false,
                    movePanelCollapsed = preferences[KEY_MOVE_PANEL_COLLAPSED] ?: false,
                    enablePictureInPicture = preferences[KEY_ENABLE_PICTURE_IN_PICTURE] ?: false,
                    
                    // Last used resource
                    lastUsedResourceId = preferences[KEY_LAST_USED_RESOURCE_ID] ?: -1L,
                    
                    // File list caching
                    defaultRememberFileList = preferences[KEY_DEFAULT_REMEMBER_FILE_LIST] ?: false,
                    
                    // Dynamic Background Expansion
                    dynamicBackgroundExtension = preferences[KEY_DYNAMIC_BACKGROUND_EXTENSION] ?: false,

                    // Phase 5+6: Default Player
                    isPrimaryMediaPlayer = preferences[KEY_IS_PRIMARY_MEDIA_PLAYER] ?: false,
                    acceptSharedFiles = preferences[KEY_ACCEPT_SHARED_FILES] ?: false
                )
            }
            .distinctUntilChanged() // OPTIMIZATION: Prevent redundant reads when settings unchanged
    }

    override suspend fun updateSettings(settings: AppSettings) {
        Timber.d("SettingsRepo: updateSettings called with allFiles=${settings.allFiles}")

        // DEBUG: compare incoming settings with current stored values to detect no-op writes
        if (BuildConfig.DEBUG) {
            try {
                val current = getSettings().first()
                val diffs = buildList {
                    if (current.allFiles != settings.allFiles) add("allFiles: ${current.allFiles} → ${settings.allFiles}")
                    if (current.isPrimaryMediaPlayer != settings.isPrimaryMediaPlayer) add("isPrimaryMediaPlayer: ${current.isPrimaryMediaPlayer} → ${settings.isPrimaryMediaPlayer}")
                    if (current.language != settings.language) add("language: ${current.language} → ${settings.language}")
                    if (current.preventSleep != settings.preventSleep) add("preventSleep: ${current.preventSleep} → ${settings.preventSleep}")
                    if (current.enableBackgroundSync != settings.enableBackgroundSync) add("enableBackgroundSync: ${current.enableBackgroundSync} → ${settings.enableBackgroundSync}")
                    if (current.cacheSizeMb != settings.cacheSizeMb) add("cacheSizeMb: ${current.cacheSizeMb} → ${settings.cacheSizeMb}")
                    if (current.defaultSortMode != settings.defaultSortMode) add("defaultSortMode: ${current.defaultSortMode} → ${settings.defaultSortMode}")
                    if (current.networkParallelism != settings.networkParallelism) add("networkParallelism: ${current.networkParallelism} → ${settings.networkParallelism}")
                    if (current.slideshowMusicResourceId != settings.slideshowMusicResourceId) add("slideshowMusicResourceId: ${current.slideshowMusicResourceId} → ${settings.slideshowMusicResourceId}")
                    if (current.acceptSharedFiles != settings.acceptSharedFiles) add("acceptSharedFiles: ${current.acceptSharedFiles} → ${settings.acceptSharedFiles}")
                }
                if (diffs.isEmpty()) {
                    Timber.w("SettingsRepo: updateSettings called but NO fields changed — possible no-op write")
                } else {
                    Timber.d("SettingsRepo: updateSettings diff (${diffs.size} fields): ${diffs.joinToString(", ")}")
                }
            } catch (e: Exception) {
                Timber.w(e, "SettingsRepo: failed to compute diff for updateSettings")
            }
        }

        // NOTE: Language is NOT synced to SharedPreferences here.
        // LocaleHelper.saveLanguage() must be called explicitly when user changes the language.
        // Syncing here would overwrite system-locale fallback (uk/ru) with the DataStore default "en".
        dataStore.edit { preferences ->
            // General
            preferences[KEY_LANGUAGE] = settings.language
            preferences[KEY_PREVENT_SLEEP] = settings.preventSleep
            preferences[KEY_SHOW_SMALL_CONTROLS] = settings.showSmallControls
            preferences[KEY_DEFAULT_USER] = settings.defaultUser
            preferences[KEY_DEFAULT_PASSWORD] = encryptPassword(settings.defaultPassword)
            preferences[KEY_NETWORK_PARALLELISM] = settings.networkParallelism
            preferences[KEY_CACHE_SIZE_MB] = settings.cacheSizeMb
            preferences[KEY_IS_CACHE_SIZE_USER_MODIFIED] = settings.isCacheSizeUserModified
            preferences[KEY_ENABLE_BACKGROUND_SYNC] = settings.enableBackgroundSync
            preferences[KEY_BACKGROUND_SYNC_INTERVAL_HOURS] = settings.backgroundSyncIntervalHours
            preferences[KEY_ALL_FILES] = settings.allFiles
            Timber.d("SettingsRepo: Saved allFiles=${settings.allFiles} to DataStore")
            preferences[KEY_SHOW_HIDDEN_FILES] = settings.showHiddenFiles
            preferences[KEY_SHOW_SUBFOLDERS_AS_ITEMS] = settings.showSubfoldersAsItems
            
            // Media Files
            preferences[KEY_SUPPORT_IMAGES] = settings.supportImages
            preferences[KEY_IMAGE_SIZE_MIN] = settings.imageSizeMin
            preferences[KEY_IMAGE_SIZE_MAX] = settings.imageSizeMax
            preferences[KEY_LOAD_FULL_SIZE_IMAGES] = settings.loadFullSizeImages
            preferences[KEY_CROP_IMAGES_TO_FULLSCREEN] = settings.cropImagesToFullscreen
            preferences[KEY_SUPPORT_GIFS] = settings.supportGifs
            preferences[KEY_SUPPORT_VIDEOS] = settings.supportVideos
            preferences[KEY_VIDEO_SIZE_MIN] = settings.videoSizeMin
            preferences[KEY_VIDEO_SIZE_MAX] = settings.videoSizeMax
            preferences[KEY_SUPPORT_AUDIO] = settings.supportAudio
            preferences[KEY_AUDIO_SIZE_MIN] = settings.audioSizeMin
            preferences[KEY_AUDIO_SIZE_MAX] = settings.audioSizeMax
            preferences[KEY_SEARCH_AUDIO_COVERS_ONLINE] = settings.searchAudioCoversOnline
            preferences[KEY_SEARCH_AUDIO_COVERS_ONLY_ON_WIFI] = settings.searchAudioCoversOnlyOnWifi
            preferences[KEY_SAVE_AUDIO_METADATA_LOCALLY] = settings.saveAudioMetadataLocally
            preferences[KEY_ENABLE_PHOTOS_DURING_AUDIO] = settings.enablePhotosDuringAudio
            if (settings.audioBackgroundPhotosResourceId != null) {
                preferences[KEY_AUDIO_BACKGROUND_PHOTOS_RESOURCE_ID] = settings.audioBackgroundPhotosResourceId
            } else {
                preferences.remove(KEY_AUDIO_BACKGROUND_PHOTOS_RESOURCE_ID)
            }
            preferences[KEY_ENABLE_BACKGROUND_AUDIO] = settings.enablePersistentAudioPlayback
            preferences[KEY_AUDIO_EMPTY_STATE_MODE] = settings.audioEmptyStateMode
            preferences[KEY_SUPPORT_TEXT] = settings.supportText
            preferences[KEY_SUPPORT_PDF] = settings.supportPdf
            preferences[KEY_SUPPORT_EPUB] = settings.supportEpub
            preferences[KEY_SHOW_PDF_THUMBNAILS] = settings.showPdfThumbnails
            preferences[KEY_TEXT_SIZE_MAX] = settings.textSizeMax
            preferences[KEY_SHOW_TEXT_LINE_NUMBERS] = settings.showTextLineNumbers
            preferences[KEY_TEXT_READER_THEME] = settings.textReaderTheme
            preferences[KEY_MARKDOWN_RENDERED] = settings.markdownRendered
            preferences[KEY_SYNTAX_HIGHLIGHTING] = settings.syntaxHighlighting
            preferences[KEY_PDF_SCROLL_MODE] = settings.pdfScrollMode
            preferences[KEY_PDF_COLOR_MODE] = settings.pdfColorMode
            preferences[KEY_EPUB_LINE_HEIGHT] = settings.epubLineHeight
            preferences[KEY_EPUB_HORIZONTAL_MARGIN] = settings.epubHorizontalMargin
            
            // Translation
            preferences[KEY_ENABLE_TRANSLATION] = settings.enableTranslation
            preferences[KEY_TRANSLATION_SOURCE_LANGUAGE] = settings.translationSourceLanguage
            preferences[KEY_TRANSLATION_TARGET_LANGUAGE] = settings.translationTargetLanguage
            preferences[KEY_ENABLE_GOOGLE_LENS] = settings.enableGoogleLens
            preferences[KEY_TRANSLATION_LENS_STYLE] = settings.translationLensStyle
            preferences[KEY_ENABLE_OCR] = settings.enableOcr
            preferences[KEY_OCR_DEFAULT_FONT_SIZE] = settings.ocrDefaultFontSize
            preferences[KEY_OCR_DEFAULT_FONT_FAMILY] = settings.ocrDefaultFontFamily
            
            // Playback and Sorting
            preferences[KEY_DEFAULT_SORT_MODE] = settings.defaultSortMode.name
            preferences[KEY_SLIDESHOW_INTERVAL] = settings.slideshowInterval
            if (settings.slideshowMusicUri != null) {
                preferences[KEY_SLIDESHOW_MUSIC_URI] = settings.slideshowMusicUri
            } else {
                preferences.remove(KEY_SLIDESHOW_MUSIC_URI)
            }
            preferences[KEY_ENABLE_SLIDESHOW_BACKGROUND_MUSIC] = settings.enableSlideshowBackgroundMusic
            if (settings.slideshowMusicResourceId != null) {
                preferences[KEY_SLIDESHOW_MUSIC_RESOURCE_ID] = settings.slideshowMusicResourceId
                Timber.d("SettingsRepo: Saved slideshowMusicResourceId=${settings.slideshowMusicResourceId} to DataStore")
            } else {
                preferences.remove(KEY_SLIDESHOW_MUSIC_RESOURCE_ID)
                Timber.d("SettingsRepo: Removed slideshowMusicResourceId from DataStore")
            }
            preferences[KEY_PLAY_TO_END] = settings.playToEndInSlideshow
            preferences[KEY_ALLOW_RENAME] = settings.allowRename
            preferences[KEY_ALLOW_DELETE] = settings.allowDelete
            preferences[KEY_USE_TRASH] = settings.useTrash
            preferences[KEY_CONFIRM_DELETE] = settings.confirmDelete
            preferences[KEY_CONFIRM_MOVE] = settings.confirmMove
            preferences[KEY_DEFAULT_GRID_MODE] = settings.defaultGridMode
            preferences[KEY_HIDE_GRID_ACTION_BUTTONS] = settings.hideGridActionButtons
            preferences[KEY_HIDE_SYSTEM_UI_IN_FULLSCREEN] = settings.hideSystemUiInFullscreen
            preferences[KEY_DEFAULT_ICON_SIZE] = settings.defaultIconSize
            preferences[KEY_DEFAULT_SHOW_COMMAND_PANEL] = settings.defaultShowCommandPanel
            preferences[KEY_SHOW_DETAILED_ERRORS] = settings.showDetailedErrors
            preferences[KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN] = settings.showPlayerHintOnFirstRun
            preferences[KEY_ALWAYS_SHOW_TOUCH_ZONES_OVERLAY] = settings.alwaysShowTouchZonesOverlay
            preferences[KEY_SHOW_VIDEO_THUMBNAILS] = settings.showVideoThumbnails
            preferences[KEY_ENABLE_PLAYER_WARMUP] = settings.enablePlayerWarmup
            preferences[KEY_RENDERER_MIGRATION_ENABLED] = settings.rendererMigrationEnabled
            
            // Safe Mode (Phase 2.1)
            preferences[KEY_ENABLE_SAFE_MODE] = settings.enableSafeMode
            
            // Scheduled operations
            preferences[KEY_ENABLE_SCHEDULED_OPERATIONS] = settings.enableScheduledOperations

            // Destinations
            preferences[KEY_ENABLE_COPYING] = settings.enableCopying
            preferences[KEY_GO_TO_NEXT_AFTER_COPY] = settings.goToNextAfterCopy
            preferences[KEY_OVERWRITE_ON_COPY] = settings.overwriteOnCopy
            preferences[KEY_ENABLE_MOVING] = settings.enableMoving
            preferences[KEY_OVERWRITE_ON_MOVE] = settings.overwriteOnMove
            preferences[KEY_ENABLE_UNDO] = settings.enableUndo
            preferences[KEY_MAX_RECIPIENTS] = settings.maxRecipients.coerceIn(1, 10)
            preferences[KEY_ENABLE_FAVORITES] = settings.enableFavorites
            
            // Player UI
            preferences[KEY_COPY_PANEL_COLLAPSED] = settings.copyPanelCollapsed
            preferences[KEY_MOVE_PANEL_COLLAPSED] = settings.movePanelCollapsed
            preferences[KEY_ENABLE_PICTURE_IN_PICTURE] = settings.enablePictureInPicture
            
            // Last used resource
            preferences[KEY_LAST_USED_RESOURCE_ID] = settings.lastUsedResourceId
            
            // File list caching
            preferences[KEY_DEFAULT_REMEMBER_FILE_LIST] = settings.defaultRememberFileList
            
            // UI State
            preferences[KEY_IS_RESOURCE_GRID_MODE] = settings.isResourceGridMode
            
            // Dynamic Background Expansion
            preferences[KEY_DYNAMIC_BACKGROUND_EXTENSION] = settings.dynamicBackgroundExtension

            // Phase 5+6: Default Player
            preferences[KEY_IS_PRIMARY_MEDIA_PLAYER] = settings.isPrimaryMediaPlayer
            preferences[KEY_ACCEPT_SHARED_FILES] = settings.acceptSharedFiles
        }
    }

    override suspend fun resetToDefaults() {
        updateSettings(AppSettings())
    }
    
    override suspend fun setPlayerFirstRun(isFirstRun: Boolean) {
        // Migrated from SharedPreferences to DataStore (P0-2): avoids synchronous disk I/O on callers
        // that may run on a non-IO coroutine context.
        dataStore.edit { preferences ->
            preferences[KEY_IS_PLAYER_FIRST_RUN] = isFirstRun
        }
    }
    
    override suspend fun isPlayerFirstRun(): Boolean {
        // Migrated from SharedPreferences to DataStore (P0-2).
        return dataStore.data.map { preferences ->
            preferences[KEY_IS_PLAYER_FIRST_RUN] ?: true // Default: true on first launch
        }.catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading isPlayerFirstRun from DataStore")
                emit(true)
            } else {
                throw exception
            }
        }.first()
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

    override suspend fun setResourceGridMode(isGridMode: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_RESOURCE_GRID_MODE] = isGridMode
        }
    }

    override suspend fun isTouchZoneHintShown(type: TouchZoneHintType): Boolean {
        val key = when (type) {
            TouchZoneHintType.FULLSCREEN_9ZONE -> KEY_HINT_SHOWN_9ZONE
            TouchZoneHintType.COMMAND_PANEL_3ZONE -> KEY_HINT_SHOWN_3ZONE
            TouchZoneHintType.MEDIA_BOTTOM_RESERVED -> KEY_HINT_SHOWN_MEDIA
        }
        return dataStore.data.map { preferences ->
            preferences[key] ?: false
        }.catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading touch zone hint shown for $type")
                emit(false)
            } else {
                throw exception
            }
        }.first()
    }

    override suspend fun setTouchZoneHintShown(type: TouchZoneHintType, shown: Boolean) {
        val key = when (type) {
            TouchZoneHintType.FULLSCREEN_9ZONE -> KEY_HINT_SHOWN_9ZONE
            TouchZoneHintType.COMMAND_PANEL_3ZONE -> KEY_HINT_SHOWN_3ZONE
            TouchZoneHintType.MEDIA_BOTTOM_RESERVED -> KEY_HINT_SHOWN_MEDIA
        }
        dataStore.edit { preferences ->
            preferences[key] = shown
        }
    }

    override suspend fun resetAllTouchZoneHints() {
        dataStore.edit { preferences ->
            preferences[KEY_HINT_SHOWN_9ZONE] = false
            preferences[KEY_HINT_SHOWN_3ZONE] = false
            preferences[KEY_HINT_SHOWN_MEDIA] = false
        }
    }
    
    /**
     * Encrypts password using CryptoHelper.
     * @param plainPassword Plaintext password
     * @return Encrypted Base64 string, or empty string on error
     */
    private fun encryptPassword(plainPassword: String): String {
        if (plainPassword.isEmpty()) return ""
        return CryptoHelper.encrypt(plainPassword) ?: run {
            Timber.e("Failed to encrypt password, storing empty string")
            ""
        }
    }
    
    /**
     * Decrypts password using CryptoHelper.
     * Handles migration from plaintext passwords.
     * @param encryptedPassword Encrypted password from DataStore (or plaintext if legacy)
     * @return Decrypted plaintext password
     */
    private suspend fun decryptPassword(encryptedPassword: String?): String {
        if (encryptedPassword.isNullOrEmpty()) return ""
        
        // Check if password is already encrypted (Base64 format)
        // Encrypted passwords are always Base64-encoded and start with specific pattern
        val isEncrypted = try {
            android.util.Base64.decode(encryptedPassword, android.util.Base64.NO_WRAP)
            true
        } catch (e: Exception) {
            false
        }
        
        if (!isEncrypted) {
            // Legacy plaintext password - migrate to encrypted
            Timber.w("Detected plaintext password in DataStore, migrating to encrypted format")
            val encrypted = CryptoHelper.encrypt(encryptedPassword)
            if (encrypted != null) {
                // Save encrypted version back to DataStore
                dataStore.edit { preferences ->
                    preferences[KEY_DEFAULT_PASSWORD] = encrypted
                }
                Timber.d("Successfully migrated plaintext password to encrypted format")
            }
            return encryptedPassword
        }
        
        // Decrypt encrypted password
        return CryptoHelper.decrypt(encryptedPassword) ?: run {
            Timber.e("Failed to decrypt password, returning empty string")
            ""
        }
    }
}
