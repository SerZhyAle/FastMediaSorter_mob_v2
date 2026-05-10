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
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_PREVENT_SLEEP = booleanPreferencesKey("prevent_sleep")
        private val KEY_SHOW_SMALL_CONTROLS = booleanPreferencesKey("show_small_controls")
        private val KEY_DEFAULT_USER = stringPreferencesKey("default_user")
        private val KEY_DEFAULT_PASSWORD = stringPreferencesKey("default_password")
        private val KEY_NETWORK_PARALLELISM = intPreferencesKey("network_parallelism")
        private val KEY_CACHE_SIZE_MB = intPreferencesKey("cache_size_mb")
        private val KEY_IS_CACHE_SIZE_USER_MODIFIED = booleanPreferencesKey("is_cache_size_user_modified")

        private val KEY_ENABLE_BACKGROUND_SYNC = booleanPreferencesKey("enable_background_sync")
        private val KEY_BACKGROUND_SYNC_INTERVAL_HOURS = intPreferencesKey("background_sync_interval_hours")
        private val KEY_ALL_FILES = booleanPreferencesKey("all_files")
        private val KEY_SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        private val KEY_SHOW_SUBFOLDERS_AS_ITEMS = booleanPreferencesKey("show_subfolders_as_items")

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
        private val KEY_BACKGROUND_AUDIO_EXIT_BEHAVIOR = stringPreferencesKey("background_audio_exit_behavior")
        private val KEY_SHOW_NOW_PLAYING_PANEL = booleanPreferencesKey("show_now_playing_panel")
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

        private val KEY_ENABLE_TRANSLATION = booleanPreferencesKey("enable_translation")
        private val KEY_TRANSLATION_SOURCE_LANGUAGE = stringPreferencesKey("translation_source_language")
        private val KEY_TRANSLATION_TARGET_LANGUAGE = stringPreferencesKey("translation_target_language")
        private val KEY_TRANSLATION_LENS_STYLE = booleanPreferencesKey("translation_lens_style")
        private val KEY_ENABLE_GOOGLE_LENS = booleanPreferencesKey("enable_google_lens")
        private val KEY_ENABLE_OCR = booleanPreferencesKey("enable_ocr")
        private val KEY_OCR_DEFAULT_FONT_SIZE = stringPreferencesKey("ocr_default_font_size")
        private val KEY_OCR_DEFAULT_FONT_FAMILY = stringPreferencesKey("ocr_default_font_family")

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

        private val KEY_ENABLE_SAFE_MODE = booleanPreferencesKey("enable_safe_mode")
        private val KEY_ENABLE_SCHEDULED_OPERATIONS = booleanPreferencesKey("enable_scheduled_operations")

        private val KEY_ENABLE_COPYING = booleanPreferencesKey("enable_copying")
        private val KEY_GO_TO_NEXT_AFTER_COPY = booleanPreferencesKey("go_to_next_after_copy")
        private val KEY_OVERWRITE_ON_COPY = booleanPreferencesKey("overwrite_on_copy")
        private val KEY_ENABLE_MOVING = booleanPreferencesKey("enable_moving")
        private val KEY_OVERWRITE_ON_MOVE = booleanPreferencesKey("overwrite_on_move")
        private val KEY_ENABLE_UNDO = booleanPreferencesKey("enable_undo")
        private val KEY_MAX_RECIPIENTS = intPreferencesKey("max_recipients")
        private val KEY_ENABLE_FAVORITES = booleanPreferencesKey("enable_favorites")
        private val KEY_DISABLE_CAMERA_CAPTURE = booleanPreferencesKey("disable_camera_capture")
        private val KEY_SKIP_CAMERA_FILENAME_DIALOG = booleanPreferencesKey("skip_camera_filename_dialog")
        // S0100: Microphone recording feature
        private val KEY_MIC_RECORDING_ENABLED = booleanPreferencesKey("mic_recording_enabled")
        private val KEY_MIC_RECORDING_ASK_FILENAME = booleanPreferencesKey("mic_recording_ask_filename")
        private val KEY_IS_PLAYER_FIRST_RUN = booleanPreferencesKey("is_player_first_run")
        
        // Per-type touch zone hint tracking keys (Task 6)
        private val KEY_HINT_SHOWN_9ZONE = booleanPreferencesKey("hint_shown_9zone")
        private val KEY_HINT_SHOWN_3ZONE = booleanPreferencesKey("hint_shown_3zone")
        private val KEY_HINT_SHOWN_MEDIA = booleanPreferencesKey("hint_shown_media_bottom")

        private val KEY_COPY_PANEL_COLLAPSED = booleanPreferencesKey("copy_panel_collapsed")
        private val KEY_MOVE_PANEL_COLLAPSED = booleanPreferencesKey("move_panel_collapsed")
        private val KEY_ENABLE_PICTURE_IN_PICTURE = booleanPreferencesKey("enable_picture_in_picture")
        private val KEY_LAST_USED_RESOURCE_ID = longPreferencesKey("last_used_resource_id")
        private val KEY_DEFAULT_REMEMBER_FILE_LIST = booleanPreferencesKey("default_remember_file_list")
        private val KEY_IS_RESOURCE_GRID_MODE = booleanPreferencesKey("is_resource_grid_mode")
        private val KEY_DYNAMIC_BACKGROUND_EXTENSION = booleanPreferencesKey("dynamic_background_extension")

        private val KEY_IS_PRIMARY_MEDIA_PLAYER = booleanPreferencesKey("is_primary_media_player")
        private val KEY_ACCEPT_SHARED_FILES = booleanPreferencesKey("accept_shared_files")
        private val KEY_ENABLE_THUMBNAIL_PRELOAD = booleanPreferencesKey("enable_thumbnail_preload")
        private val KEY_THUMBNAIL_PRELOAD_WIFI_ONLY = booleanPreferencesKey("thumbnail_preload_wifi_only")

        // FR-8: Folder picker persistence (stores content:// URI string)
        private val KEY_LAST_SELECTED_LOCAL_FOLDER = stringPreferencesKey("last_selected_local_folder")

        // Compact elements mode (0.5x scale)
        private val KEY_USE_COMPACT_ELEMENTS = booleanPreferencesKey("use_compact_elements")

        private val KEY_VIDEO_SNAPSHOT_RESOURCE_ID = longPreferencesKey("video_snapshot_resource_id")
        private val KEY_VIDEO_SNAPSHOT_FORMAT = stringPreferencesKey("video_snapshot_format")

        // Link auto-download (S0003): master toggle, optional destination resource id, auto-open toggle
        private val KEY_LINK_AUTO_DOWNLOAD_ENABLED = booleanPreferencesKey("link_auto_download_enabled")
        private val KEY_LINK_AUTO_DOWNLOAD_RESOURCE_ID = longPreferencesKey("link_auto_download_resource_id")
        private val KEY_LINK_AUTO_DOWNLOAD_OPEN_IN_PLAYER = booleanPreferencesKey("link_auto_download_open_in_player")

        // S0116 §5.1 pillar J: streaming/quality preference for url-download pipeline.
        private val KEY_LINK_DOWNLOAD_MAX_RESOLUTION = stringPreferencesKey("link_download_max_resolution")
        private val KEY_LINK_DOWNLOAD_AUDIO_ONLY = booleanPreferencesKey("link_download_audio_only")
        private val KEY_LINK_DOWNLOAD_LOGIN_WALL_HEURISTIC_ENABLED = booleanPreferencesKey("link_download_login_wall_heuristic_enabled")

        private val KEY_RESUME_ON_NEXT_LAUNCH = booleanPreferencesKey("resume_on_next_launch")

        // S0050: Black Screen button visibility in player toolbar
        private val KEY_SHOW_BLACK_SCREEN_BUTTON = booleanPreferencesKey("show_black_screen_button")

        // VR settings (spec §5.7)
        private val KEY_VR_AUTO_DETECT_FORMAT = booleanPreferencesKey("vr_auto_detect_format")
        private val KEY_VR_FORCED_FORMAT = stringPreferencesKey("vr_forced_format")
        private val KEY_VR_FORCED_PLAT_FORMAT = stringPreferencesKey("vr_forced_plat_format")
        private val KEY_VR_FORCED_SPHERICAL_FORMAT = stringPreferencesKey("vr_forced_spherical_format")
        private val KEY_VR_RENDERING_MODE = stringPreferencesKey("vr_rendering_mode")
        private val KEY_VR_REMEMBER_FILE_FORMAT = booleanPreferencesKey("vr_remember_file_format")
        private val KEY_VR_AUTO_IMMERSIVE = booleanPreferencesKey("vr_auto_immersive")
        // Global VR kill-switch (spec §3.0.2): disables all 3D/VR classification when true
        private val KEY_VR_DISABLE_3D = booleanPreferencesKey("vr_disable_3d")
        // Panel-mode single-eye crop (spec_panel-stereo-single-eye)
        private val KEY_PANEL_STEREO_SINGLE_EYE = booleanPreferencesKey("panel_stereo_single_eye")
        private val KEY_VR_SHOW_FPS = booleanPreferencesKey("vr_show_fps")
        private val KEY_PLAYER_SHOW_FPS = booleanPreferencesKey("player_show_fps")

        // S0028: Multi-window mode
        private val KEY_ALLOW_SEPARATE_WINDOW = booleanPreferencesKey("allow_separate_window")

        // Adaptive pre-cache strategy (spec §5)
        private val KEY_PREFETCH_CACHE_MULTIPLIER = stringPreferencesKey("prefetch_cache_multiplier")
        private val KEY_STREAMING_CACHE_CLEANUP_MODE = stringPreferencesKey("streaming_cache_cleanup_mode")
        private val KEY_STREAMING_CACHE_TTL_DAYS = intPreferencesKey("streaming_cache_ttl_days")

        /** Allowed values for [KEY_STREAMING_CACHE_TTL_DAYS]; `0` means "off". */
        private val STREAMING_CACHE_TTL_VALID = setOf(0, 1, 3, 7, 30)

        private val VR_FORCED_PLAT_VALUES = setOf("AUTO", "SBS", "OU", "MONO")
        private val VR_FORCED_SPHERICAL_VALUES = setOf(
            "AUTO",
            "EQUIRECT_360_MONO",
            "EQUIRECT_360_SBS",
            "EQUIRECT_360_OU",
            "EQUIRECT_180_MONO",
            "EQUIRECT_180_SBS",
            "VR180_FISHEYE_SBS",
            "CYLINDER_180"
        )
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
                // When DataStore has no saved language (first launch / data cleared), fall back to
                // LocaleHelper which resolves SharedPreferences → system locale → "en".
                // This keeps DataStore in sync with the active locale so the Settings spinner
                // shows the correct selection instead of defaulting to English.
                val language = languageFromDataStore
                    ?: com.sza.fastmediasorter.core.util.LocaleHelper.getLanguage(context)
                
                // Cache size for Glide (GlideAppModule reads from SharedPreferences during init)
                // glidePrefs is a lazy singleton field — no disk access on every emission
                val cacheSizeMb = preferences[KEY_CACHE_SIZE_MB] ?: 2048
                val savedCacheSize = glidePrefs.getInt("cache_size_mb_cached", 0)
                if (savedCacheSize != cacheSizeMb) {
                    glidePrefs.edit().putInt("cache_size_mb_cached", cacheSizeMb).apply()
                    if (BuildConfig.DEBUG) Timber.d("SettingsRepositoryImpl: Synced cacheSizeMb to SharedPreferences: ${cacheSizeMb}MB")
                }
                
                AppSettings(
                    language = language,
                    preventSleep = preferences[KEY_PREVENT_SLEEP] ?: true,
                    showSmallControls = preferences[KEY_SHOW_SMALL_CONTROLS] ?: false,
                    defaultUser = preferences[KEY_DEFAULT_USER] ?: "",
                    defaultPassword = decryptPassword(preferences[KEY_DEFAULT_PASSWORD]),
                    networkParallelism = preferences[KEY_NETWORK_PARALLELISM] ?: 4,
                    cacheSizeMb = preferences[KEY_CACHE_SIZE_MB] ?: 2048,
                    isCacheSizeUserModified = preferences[KEY_IS_CACHE_SIZE_USER_MODIFIED] ?: false,
                    isResourceGridMode = preferences[KEY_IS_RESOURCE_GRID_MODE] ?: false,
                    enableBackgroundSync = preferences[KEY_ENABLE_BACKGROUND_SYNC] ?: false,
                    backgroundSyncIntervalHours = preferences[KEY_BACKGROUND_SYNC_INTERVAL_HOURS] ?: 4,
                    allFiles = preferences[KEY_ALL_FILES] ?: false,
                    showHiddenFiles = preferences[KEY_SHOW_HIDDEN_FILES] ?: false,
                    showSubfoldersAsItems = preferences[KEY_SHOW_SUBFOLDERS_AS_ITEMS] ?: false,
                    supportImages = preferences[KEY_SUPPORT_IMAGES] ?: true,
                    imageSizeMin = preferences[KEY_IMAGE_SIZE_MIN] ?: 1024L,
                    imageSizeMax = preferences[KEY_IMAGE_SIZE_MAX] ?: 10485760L,
                    loadFullSizeImages = preferences[KEY_LOAD_FULL_SIZE_IMAGES] ?: true,
                    cropImagesToFullscreen = preferences[KEY_CROP_IMAGES_TO_FULLSCREEN] ?: true,
                    supportGifs = preferences[KEY_SUPPORT_GIFS] ?: true,
                    supportVideos = preferences[KEY_SUPPORT_VIDEOS] ?: true,
                    videoSizeMin = preferences[KEY_VIDEO_SIZE_MIN] ?: 102400L, // 100KB in bytes
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
                    backgroundAudioExitBehavior = preferences[KEY_BACKGROUND_AUDIO_EXIT_BEHAVIOR]
                        ?.let { runCatching { com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior.valueOf(it) }.getOrNull() }
                        ?: com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior.ASK,
                    showNowPlayingPanel = preferences[KEY_SHOW_NOW_PLAYING_PANEL] ?: false,
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
                    enableTranslation = preferences[KEY_ENABLE_TRANSLATION] ?: true,
                    translationSourceLanguage = preferences[KEY_TRANSLATION_SOURCE_LANGUAGE] ?: "auto",
                    translationTargetLanguage = preferences[KEY_TRANSLATION_TARGET_LANGUAGE] ?: "ru",
                    translationLensStyle = preferences[KEY_TRANSLATION_LENS_STYLE] ?: true,
                    enableGoogleLens = preferences[KEY_ENABLE_GOOGLE_LENS] ?: true,
                    enableOcr = preferences[KEY_ENABLE_OCR] ?: true,
                    ocrDefaultFontSize = preferences[KEY_OCR_DEFAULT_FONT_SIZE] ?: "AUTO",
                    ocrDefaultFontFamily = preferences[KEY_OCR_DEFAULT_FONT_FAMILY] ?: "DEFAULT",
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
                    defaultIconSize = (preferences[KEY_DEFAULT_ICON_SIZE] ?: 96)
                        .let { if (it < 32 || it > 256 || (it - 32) % 8 != 0) 96 else it },
                    defaultShowCommandPanel = preferences[KEY_DEFAULT_SHOW_COMMAND_PANEL] ?: true,
                    showDetailedErrors = preferences[KEY_SHOW_DETAILED_ERRORS] ?: false,
                    showPlayerHintOnFirstRun = preferences[KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN] ?: true,
                    alwaysShowTouchZonesOverlay = preferences[KEY_ALWAYS_SHOW_TOUCH_ZONES_OVERLAY] ?: false,
                    showVideoThumbnails = preferences[KEY_SHOW_VIDEO_THUMBNAILS] ?: true,
                    enablePlayerWarmup = preferences[KEY_ENABLE_PLAYER_WARMUP] ?: false,
                    rendererMigrationEnabled = preferences[KEY_RENDERER_MIGRATION_ENABLED] ?: false,
                    enableSafeMode = preferences[KEY_ENABLE_SAFE_MODE] ?: true,
                    enableScheduledOperations = preferences[KEY_ENABLE_SCHEDULED_OPERATIONS] ?: true,
                    enableCopying = preferences[KEY_ENABLE_COPYING] ?: true,
                    goToNextAfterCopy = preferences[KEY_GO_TO_NEXT_AFTER_COPY] ?: true,
                    overwriteOnCopy = preferences[KEY_OVERWRITE_ON_COPY] ?: false,
                    enableMoving = preferences[KEY_ENABLE_MOVING] ?: true,
                    overwriteOnMove = preferences[KEY_OVERWRITE_ON_MOVE] ?: false,
                    enableUndo = preferences[KEY_ENABLE_UNDO] ?: true,
                    maxRecipients = (preferences[KEY_MAX_RECIPIENTS] ?: 10).coerceIn(1, 10),
                    enableFavorites = preferences[KEY_ENABLE_FAVORITES] ?: true,
                    disableCameraCapture = preferences[KEY_DISABLE_CAMERA_CAPTURE] ?: false,
                    skipCameraFilenameDialog = preferences[KEY_SKIP_CAMERA_FILENAME_DIALOG] ?: false,
                    micRecordingEnabled = preferences[KEY_MIC_RECORDING_ENABLED] ?: false,
                    micRecordingAskFilename = preferences[KEY_MIC_RECORDING_ASK_FILENAME] ?: true,
                    copyPanelCollapsed = preferences[KEY_COPY_PANEL_COLLAPSED] ?: false,
                    movePanelCollapsed = preferences[KEY_MOVE_PANEL_COLLAPSED] ?: false,
                    enablePictureInPicture = preferences[KEY_ENABLE_PICTURE_IN_PICTURE] ?: true,
                    lastUsedResourceId = preferences[KEY_LAST_USED_RESOURCE_ID] ?: -1L,
                    defaultRememberFileList = preferences[KEY_DEFAULT_REMEMBER_FILE_LIST] ?: false,
                    dynamicBackgroundExtension = preferences[KEY_DYNAMIC_BACKGROUND_EXTENSION] ?: false,
                    isPrimaryMediaPlayer = preferences[KEY_IS_PRIMARY_MEDIA_PLAYER] ?: false,
                    acceptSharedFiles = preferences[KEY_ACCEPT_SHARED_FILES] ?: true, // S0133: default ON when key absent
                    enableThumbnailPreload = preferences[KEY_ENABLE_THUMBNAIL_PRELOAD] ?: false,
                    thumbnailPreloadWifiOnly = preferences[KEY_THUMBNAIL_PRELOAD_WIFI_ONLY] ?: true,
                    // FR-8: Folder picker persistence (stores content:// URI)
                    lastSelectedLocalFolder = preferences[KEY_LAST_SELECTED_LOCAL_FOLDER],
                    useCompactElements = preferences[KEY_USE_COMPACT_ELEMENTS] ?: false,
                    videoSnapshotResourceId = preferences[KEY_VIDEO_SNAPSHOT_RESOURCE_ID],

                    // Video frame snapshot format (default JPG)
                    videoSnapshotFormat = preferences[KEY_VIDEO_SNAPSHOT_FORMAT]
                        ?.takeIf { it == "PNG" || it == "JPG" } ?: "JPG",

                    // Link auto-download (S0003)
                    linkAutoDownloadEnabled = preferences[KEY_LINK_AUTO_DOWNLOAD_ENABLED] ?: true,
                    linkAutoDownloadResourceId = preferences[KEY_LINK_AUTO_DOWNLOAD_RESOURCE_ID],
                    linkAutoDownloadOpenInPlayer = preferences[KEY_LINK_AUTO_DOWNLOAD_OPEN_IN_PLAYER] ?: true,
                    // S0116 §5.1 pillar J: whitelist guard mirrors the videoSnapshotFormat pattern.
                    linkDownloadMaxResolution = preferences[KEY_LINK_DOWNLOAD_MAX_RESOLUTION]
                        ?.takeIf { it in setOf("480p", "720p", "1080p", "best") } ?: "1080p",
                    linkDownloadAudioOnly = preferences[KEY_LINK_DOWNLOAD_AUDIO_ONLY] ?: false,
                    linkDownloadLoginWallHeuristicEnabled = preferences[KEY_LINK_DOWNLOAD_LOGIN_WALL_HEURISTIC_ENABLED] ?: true,

                    // VR settings (spec §5.7 / Phase 8)
                    vrAutoDetectFormat = preferences[KEY_VR_AUTO_DETECT_FORMAT] ?: true,
                    vrForcedPlatFormat = readVrForcedPlatFormat(preferences),
                    vrForcedSphericalFormat = readVrForcedSphericalFormat(preferences),
                    vrRenderingMode = preferences[KEY_VR_RENDERING_MODE]
                        ?.takeIf { it in listOf("CINEMA", "FULL_SBS", "FULL_OU") }
                        ?: "CINEMA",
                    vrRememberFileFormat = preferences[KEY_VR_REMEMBER_FILE_FORMAT] ?: true,
                    vrAutoImmersive = preferences[KEY_VR_AUTO_IMMERSIVE] ?: true,
                    disable3dVr = preferences[KEY_VR_DISABLE_3D] ?: false,
                    panelStereoSingleEye = preferences[KEY_PANEL_STEREO_SINGLE_EYE] ?: !BuildConfig.SUPPORT_VR_PLAYER,
                    vrShowFps = preferences[KEY_VR_SHOW_FPS] ?: false,
                    playerShowFps = preferences[KEY_PLAYER_SHOW_FPS] ?: false,

                    // Default true: resumes playback on fresh installs and on update from old versions
                    // (absent key → null → default true, matching the user's existing behaviour)
                    resumeOnNextLaunch = preferences[KEY_RESUME_ON_NEXT_LAUNCH] ?: true,

                    // S0050: absent key → false (opt-in feature, disabled by default)
                    showBlackScreenButton = preferences[KEY_SHOW_BLACK_SCREEN_BUTTON] ?: false,

                    // Adaptive pre-cache strategy (spec §5)
                    prefetchCacheMultiplier = com.sza.fastmediasorter.domain.model.PrefetchCacheMultiplier
                        .fromName(preferences[KEY_PREFETCH_CACHE_MULTIPLIER]),
                    streamingCacheCleanupMode = com.sza.fastmediasorter.domain.model.StreamingCacheCleanupMode
                        .fromName(preferences[KEY_STREAMING_CACHE_CLEANUP_MODE]),
                    streamingCacheTtlDays = preferences[KEY_STREAMING_CACHE_TTL_DAYS]
                        ?.takeIf { it in STREAMING_CACHE_TTL_VALID }
                        ?: 7,

                    // S0028: Multi-window mode
                    allowSeparateWindow = preferences[KEY_ALLOW_SEPARATE_WINDOW] ?: BuildConfig.SUPPORT_VR_PLAYER
                )
            }
            .distinctUntilChanged()
    }

    override suspend fun updateSettings(settings: AppSettings) {
        Timber.d("SettingsRepo: updateSettings called with allFiles=${settings.allFiles}")

        // S0018 idempotency guard: if the incoming AppSettings equals the currently stored
        // value (data-class equality across all fields), skip the DataStore write entirely.
        // This eliminates the spam of "NO fields changed" warnings produced when settings
        // fragments fire setOnCheckedChangeListener callbacks during initial UI inflation.
        val current = runCatching { getSettings().first() }.getOrNull()
        if (current != null && current == settings) {
            Timber.v("SettingsRepo: updateSettings idempotent — skipping DataStore write")
            return
        }
        if (BuildConfig.DEBUG && current != null) {
            Timber.d("SettingsRepo: updateSettings diff detected — proceeding with DataStore write")
        }

        // NOTE: Language is NOT synced to SharedPreferences here.
        // LocaleHelper.saveLanguage() must be called explicitly when user changes the language.
        // Syncing here would overwrite system-locale fallback (uk/ru) with the DataStore default "en".
        dataStore.edit { preferences ->
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
            preferences.setOrRemove(KEY_AUDIO_BACKGROUND_PHOTOS_RESOURCE_ID, settings.audioBackgroundPhotosResourceId)
            preferences[KEY_ENABLE_BACKGROUND_AUDIO] = settings.enablePersistentAudioPlayback
            preferences[KEY_BACKGROUND_AUDIO_EXIT_BEHAVIOR] = settings.backgroundAudioExitBehavior.name
            preferences[KEY_SHOW_NOW_PLAYING_PANEL] = settings.showNowPlayingPanel
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
            preferences[KEY_ENABLE_TRANSLATION] = settings.enableTranslation
            preferences[KEY_TRANSLATION_SOURCE_LANGUAGE] = settings.translationSourceLanguage
            preferences[KEY_TRANSLATION_TARGET_LANGUAGE] = settings.translationTargetLanguage
            preferences[KEY_ENABLE_GOOGLE_LENS] = settings.enableGoogleLens
            preferences[KEY_TRANSLATION_LENS_STYLE] = settings.translationLensStyle
            preferences[KEY_ENABLE_OCR] = settings.enableOcr
            preferences[KEY_OCR_DEFAULT_FONT_SIZE] = settings.ocrDefaultFontSize
            preferences[KEY_OCR_DEFAULT_FONT_FAMILY] = settings.ocrDefaultFontFamily
            preferences[KEY_DEFAULT_SORT_MODE] = settings.defaultSortMode.name
            preferences[KEY_SLIDESHOW_INTERVAL] = settings.slideshowInterval
            preferences.setOrRemove(KEY_SLIDESHOW_MUSIC_URI, settings.slideshowMusicUri)
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
            preferences[KEY_ENABLE_SAFE_MODE] = settings.enableSafeMode
            preferences[KEY_ENABLE_SCHEDULED_OPERATIONS] = settings.enableScheduledOperations
            preferences[KEY_ENABLE_COPYING] = settings.enableCopying
            preferences[KEY_GO_TO_NEXT_AFTER_COPY] = settings.goToNextAfterCopy
            preferences[KEY_OVERWRITE_ON_COPY] = settings.overwriteOnCopy
            preferences[KEY_ENABLE_MOVING] = settings.enableMoving
            preferences[KEY_OVERWRITE_ON_MOVE] = settings.overwriteOnMove
            preferences[KEY_ENABLE_UNDO] = settings.enableUndo
            preferences[KEY_MAX_RECIPIENTS] = settings.maxRecipients.coerceIn(1, 10)
            preferences[KEY_ENABLE_FAVORITES] = settings.enableFavorites
            preferences[KEY_DISABLE_CAMERA_CAPTURE] = settings.disableCameraCapture
            preferences[KEY_SKIP_CAMERA_FILENAME_DIALOG] = settings.skipCameraFilenameDialog
            preferences[KEY_MIC_RECORDING_ENABLED] = settings.micRecordingEnabled
            preferences[KEY_MIC_RECORDING_ASK_FILENAME] = settings.micRecordingAskFilename
            preferences[KEY_COPY_PANEL_COLLAPSED] = settings.copyPanelCollapsed
            preferences[KEY_MOVE_PANEL_COLLAPSED] = settings.movePanelCollapsed
            preferences[KEY_ENABLE_PICTURE_IN_PICTURE] = settings.enablePictureInPicture
            preferences[KEY_LAST_USED_RESOURCE_ID] = settings.lastUsedResourceId
            preferences[KEY_DEFAULT_REMEMBER_FILE_LIST] = settings.defaultRememberFileList
            preferences[KEY_IS_RESOURCE_GRID_MODE] = settings.isResourceGridMode
            preferences[KEY_DYNAMIC_BACKGROUND_EXTENSION] = settings.dynamicBackgroundExtension
            preferences[KEY_IS_PRIMARY_MEDIA_PLAYER] = settings.isPrimaryMediaPlayer
            preferences[KEY_ACCEPT_SHARED_FILES] = settings.acceptSharedFiles
            preferences[KEY_ENABLE_THUMBNAIL_PRELOAD] = settings.enableThumbnailPreload
            preferences[KEY_THUMBNAIL_PRELOAD_WIFI_ONLY] = settings.thumbnailPreloadWifiOnly
            // FR-8: Folder picker persistence
            preferences.setOrRemove(KEY_LAST_SELECTED_LOCAL_FOLDER, settings.lastSelectedLocalFolder)

            preferences[KEY_USE_COMPACT_ELEMENTS] = settings.useCompactElements
            preferences.setOrRemove(KEY_VIDEO_SNAPSHOT_RESOURCE_ID, settings.videoSnapshotResourceId)

            // Video frame snapshot format — always present with "PNG" default
            preferences[KEY_VIDEO_SNAPSHOT_FORMAT] = if (settings.videoSnapshotFormat == "JPG") "JPG" else "PNG"

            // Link auto-download (S0003)
            preferences[KEY_LINK_AUTO_DOWNLOAD_ENABLED] = settings.linkAutoDownloadEnabled
            preferences.setOrRemove(KEY_LINK_AUTO_DOWNLOAD_RESOURCE_ID, settings.linkAutoDownloadResourceId)
            preferences[KEY_LINK_AUTO_DOWNLOAD_OPEN_IN_PLAYER] = settings.linkAutoDownloadOpenInPlayer
            // S0116 §5.1 pillar J
            preferences[KEY_LINK_DOWNLOAD_MAX_RESOLUTION] = settings.linkDownloadMaxResolution
            preferences[KEY_LINK_DOWNLOAD_AUDIO_ONLY] = settings.linkDownloadAudioOnly
            preferences[KEY_LINK_DOWNLOAD_LOGIN_WALL_HEURISTIC_ENABLED] = settings.linkDownloadLoginWallHeuristicEnabled

            // VR settings (spec §5.7 / Phase 8 split). Legacy key is removed on write so
            // existing installs migrate forward after the first successful save.
            preferences[KEY_VR_AUTO_DETECT_FORMAT] = settings.vrAutoDetectFormat
            preferences[KEY_VR_FORCED_PLAT_FORMAT] = settings.vrForcedPlatFormat
            preferences[KEY_VR_FORCED_SPHERICAL_FORMAT] = settings.vrForcedSphericalFormat
            preferences.remove(KEY_VR_FORCED_FORMAT)
            preferences[KEY_VR_RENDERING_MODE] = settings.vrRenderingMode
            preferences[KEY_VR_REMEMBER_FILE_FORMAT] = settings.vrRememberFileFormat
            preferences[KEY_VR_AUTO_IMMERSIVE] = settings.vrAutoImmersive
            preferences[KEY_VR_DISABLE_3D] = settings.disable3dVr
            preferences[KEY_PANEL_STEREO_SINGLE_EYE] = settings.panelStereoSingleEye
            preferences[KEY_VR_SHOW_FPS] = settings.vrShowFps
            preferences[KEY_PLAYER_SHOW_FPS] = settings.playerShowFps

            preferences[KEY_RESUME_ON_NEXT_LAUNCH] = settings.resumeOnNextLaunch
            // S0050: Black Screen button (opt-in)
            preferences[KEY_SHOW_BLACK_SCREEN_BUTTON] = settings.showBlackScreenButton

            // Adaptive pre-cache strategy (spec §5)
            preferences[KEY_PREFETCH_CACHE_MULTIPLIER] = settings.prefetchCacheMultiplier.name
            preferences[KEY_STREAMING_CACHE_CLEANUP_MODE] = settings.streamingCacheCleanupMode.name
            preferences[KEY_STREAMING_CACHE_TTL_DAYS] = settings.streamingCacheTtlDays
                .takeIf { it in STREAMING_CACHE_TTL_VALID } ?: 7

            // S0028: Multi-window mode
            preferences[KEY_ALLOW_SEPARATE_WINDOW] = settings.allowSeparateWindow
        }
    }

    override suspend fun resetToDefaults() {
        updateSettings(AppSettings())
    }
    
    override suspend fun setPlayerFirstRun(isFirstRun: Boolean) {
        dataStore.edit { it[KEY_IS_PLAYER_FIRST_RUN] = isFirstRun }
    }
    
    override suspend fun isPlayerFirstRun(): Boolean = readFirst(KEY_IS_PLAYER_FIRST_RUN, true)

    override suspend fun saveLastUsedResourceId(resourceId: Long) {
        dataStore.edit { it[KEY_LAST_USED_RESOURCE_ID] = resourceId }
    }

    override suspend fun getLastUsedResourceId(): Long = readFirst(KEY_LAST_USED_RESOURCE_ID, -1L)

    override suspend fun setResourceGridMode(isGridMode: Boolean) {
        dataStore.edit { it[KEY_IS_RESOURCE_GRID_MODE] = isGridMode }
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value != null) this[key] = value else remove(key)
    }

    private suspend fun <T> readFirst(key: Preferences.Key<T>, default: T): T =
        dataStore.data
            .map { it[key] ?: default }
            .catch { e -> if (e is IOException) { Timber.e(e, "Error reading ${key.name}"); emit(default) } else throw e }
            .first()

    override suspend fun isTouchZoneHintShown(type: TouchZoneHintType): Boolean =
        readFirst(keyFor(type), false)

    override suspend fun setTouchZoneHintShown(type: TouchZoneHintType, shown: Boolean) {
        dataStore.edit { it[keyFor(type)] = shown }
    }

    private fun keyFor(type: TouchZoneHintType) = when (type) {
        TouchZoneHintType.FULLSCREEN_9ZONE -> KEY_HINT_SHOWN_9ZONE
        TouchZoneHintType.COMMAND_PANEL_3ZONE -> KEY_HINT_SHOWN_3ZONE
        TouchZoneHintType.MEDIA_BOTTOM_RESERVED -> KEY_HINT_SHOWN_MEDIA
    }

    override suspend fun resetAllTouchZoneHints() {
        dataStore.edit { preferences ->
            preferences[KEY_HINT_SHOWN_9ZONE] = false
            preferences[KEY_HINT_SHOWN_3ZONE] = false
            preferences[KEY_HINT_SHOWN_MEDIA] = false
        }
    }
    
    /** Encrypts password; returns encrypted Base64 string or empty on error. */
    private fun encryptPassword(plainPassword: String): String {
        if (plainPassword.isEmpty()) return ""
        return CryptoHelper.encrypt(plainPassword) ?: run {
            Timber.e("Failed to encrypt password, storing empty string")
            ""
        }
    }
    
    /** Decrypts password; handles migration from legacy plaintext passwords. */
    private suspend fun decryptPassword(encryptedPassword: String?): String {
        if (encryptedPassword.isNullOrEmpty()) return ""
        val isEncrypted = runCatching {
            android.util.Base64.decode(encryptedPassword, android.util.Base64.NO_WRAP)
        }.isSuccess
        if (!isEncrypted) {
            Timber.w("Detected plaintext password in DataStore, migrating to encrypted format")
            val encrypted = CryptoHelper.encrypt(encryptedPassword)
            if (encrypted != null) {
                dataStore.edit { it[KEY_DEFAULT_PASSWORD] = encrypted }
                Timber.d("Migrated plaintext password to encrypted format")
            }
            return encryptedPassword
        }
        return CryptoHelper.decrypt(encryptedPassword) ?: run {
            Timber.e("Failed to decrypt password, returning empty string")
            ""
        }
    }

    private fun readVrForcedPlatFormat(preferences: Preferences): String {
        preferences[KEY_VR_FORCED_PLAT_FORMAT]
            ?.uppercase()
            ?.takeIf { it in VR_FORCED_PLAT_VALUES }
            ?.let { return it }

        val legacy = preferences[KEY_VR_FORCED_FORMAT]?.uppercase() ?: return "AUTO"
        return legacy.takeIf { it in VR_FORCED_PLAT_VALUES } ?: "AUTO"
    }

    private fun readVrForcedSphericalFormat(preferences: Preferences): String {
        preferences[KEY_VR_FORCED_SPHERICAL_FORMAT]
            ?.uppercase()
            ?.takeIf { it in VR_FORCED_SPHERICAL_VALUES }
            ?.let { return it }

        val legacy = preferences[KEY_VR_FORCED_FORMAT]?.uppercase() ?: return "AUTO"
        return legacy.takeIf { it in VR_FORCED_SPHERICAL_VALUES } ?: "AUTO"
    }
}
