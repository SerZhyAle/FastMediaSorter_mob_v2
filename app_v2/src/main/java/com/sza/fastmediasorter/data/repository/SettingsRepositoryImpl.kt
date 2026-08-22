package com.sza.fastmediasorter.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.compat.MultiWindowCapabilityDetector
import com.sza.fastmediasorter.core.playback.RadioStreamBufferConfig
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.data.repository.settings.AudioSettingsStore
import com.sza.fastmediasorter.data.repository.settings.CaptureSettingsStore
import com.sza.fastmediasorter.data.repository.settings.LauncherSettingsStore
import com.sza.fastmediasorter.data.repository.settings.LinkSettingsStore
import com.sza.fastmediasorter.data.repository.settings.MediaSizeFilterSettingsStore
import com.sza.fastmediasorter.data.repository.settings.ProgramsSettingsStore
import com.sza.fastmediasorter.data.repository.settings.RemoteSourceSettingsStore
import com.sza.fastmediasorter.data.repository.settings.ScreenshotSettingsStore
import com.sza.fastmediasorter.data.repository.settings.SlideshowSettingsStore
import com.sza.fastmediasorter.data.repository.settings.StereoSettingsStore
import com.sza.fastmediasorter.data.repository.settings.StreamsSettingsStore
import com.sza.fastmediasorter.data.repository.settings.TextRecognitionSettingsStore
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.ScreenshotGestureSettings
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.helpers.PlayerLayoutModePrefs
import com.sza.fastmediasorter.ui.player.model.TouchZoneHintType
import com.sza.fastmediasorter.util.getPackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    // S1551: serializes whole-snapshot writes while S0876 keeps transform read-modify-write calls
    // in order. This prevents concurrent UI snapshots from overwriting one another in flight.
    private val settingsUpdateMutex = Mutex()
    private val transformMutex = Mutex()

    companion object {
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_COLOR_THEME = stringPreferencesKey("color_theme")
        private val KEY_PREVENT_SLEEP = booleanPreferencesKey("prevent_sleep")
        private val KEY_KEEP_SCREEN_ON_PLAYER = booleanPreferencesKey("keep_screen_on_player")
        private val KEY_SHOW_SMALL_CONTROLS = booleanPreferencesKey("show_small_controls")

        // S0755: main-window programs panel toggle (flat boolean alongside the other programs settings).
        private val KEY_DEFAULT_USER = stringPreferencesKey("default_user")
        private val KEY_DEFAULT_PASSWORD = stringPreferencesKey("default_password")
        private val KEY_NETWORK_PARALLELISM = intPreferencesKey("network_parallelism")
        private val KEY_CACHE_SIZE_MB = intPreferencesKey("cache_size_mb")
        private val KEY_IS_CACHE_SIZE_USER_MODIFIED = booleanPreferencesKey("is_cache_size_user_modified")
        private val KEY_ENABLED_SHARE_TARGETS = stringSetPreferencesKey("enabled_share_targets")
        private val KEY_DISABLED_SHARE_TARGETS = stringSetPreferencesKey("disabled_share_targets")

        private val KEY_ENABLE_BACKGROUND_SYNC = booleanPreferencesKey("enable_background_sync")
        private val KEY_BACKGROUND_SYNC_INTERVAL_HOURS = intPreferencesKey("background_sync_interval_hours")
        private val KEY_ALL_FILES = booleanPreferencesKey("all_files")
        private val KEY_SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        private val KEY_SHOW_SUBFOLDERS_AS_ITEMS = booleanPreferencesKey("show_subfolders_as_items")

        private val KEY_SUPPORT_IMAGES = booleanPreferencesKey("support_images")
        // Media size-filter keys moved to data.repository.settings.MediaSizeFilterSettingsStore (responsibility extraction).
        private val KEY_LOAD_FULL_SIZE_IMAGES = booleanPreferencesKey("load_full_size_images")
        private val KEY_CROP_IMAGES_TO_FULLSCREEN = booleanPreferencesKey("crop_images_to_fullscreen")
        private val KEY_SUPPORT_GIFS = booleanPreferencesKey("support_gifs")
        private val KEY_SUPPORT_VIDEOS = booleanPreferencesKey("support_videos")
        private val KEY_SUPPORT_AUDIO = booleanPreferencesKey("support_audio")
        // Audio keys moved to data.repository.settings.AudioSettingsStore (responsibility extraction).
        private val KEY_SEARCH_AUDIO_COVERS_ONLINE = booleanPreferencesKey("search_audio_covers_online")
        private val KEY_SEARCH_AUDIO_COVERS_ONLY_ON_WIFI = booleanPreferencesKey("search_audio_covers_only_on_wifi")
        private val KEY_SAVE_AUDIO_METADATA_LOCALLY = booleanPreferencesKey("save_audio_metadata_locally")
        private val KEY_ENABLE_PHOTOS_DURING_AUDIO = booleanPreferencesKey("enable_photos_during_audio")
        // Persistent audio playback: continue playing audio via foreground service when app is minimized/screen locked (like YouTube Music).
        // NOT related to enableSlideshowBackgroundMusic (in-app slideshow music) or enablePhotosDuringAudio (photo overlay during audio).
        // Key string "enable_background_audio" preserved for backward compatibility with existing user settings.
        private val KEY_ENABLE_BACKGROUND_AUDIO = booleanPreferencesKey("enable_background_audio")
        private val KEY_BACKGROUND_AUDIO_EXIT_BEHAVIOR = stringPreferencesKey("background_audio_exit_behavior")
        private val KEY_SHOW_NOW_PLAYING_PANEL = booleanPreferencesKey("show_now_playing_panel")
        private val KEY_SUPPORT_TEXT = booleanPreferencesKey("support_text")
        private val KEY_SUPPORT_PDF = booleanPreferencesKey("support_pdf")
        private val KEY_SUPPORT_EPUB = booleanPreferencesKey("support_epub")
        private val KEY_SUPPORT_OFFICE_DOCUMENTS = booleanPreferencesKey("support_office_documents")
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

        // Text-recognition / translation / OCR keys moved to data.repository.settings.TextRecognitionSettingsStore (responsibility extraction).

        private val KEY_DEFAULT_SORT_MODE = stringPreferencesKey("default_sort_mode")
        // Slideshow keys moved to data.repository.settings.SlideshowSettingsStore (responsibility extraction).
        private val KEY_PLAY_TO_END = booleanPreferencesKey("play_to_end_in_slideshow")
        private val KEY_ALLOW_RENAME = booleanPreferencesKey("allow_rename")
        private val KEY_ALLOW_DELETE = booleanPreferencesKey("allow_delete")
        private val KEY_USE_TRASH = booleanPreferencesKey("use_trash")
        private val KEY_CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
        private val KEY_CONFIRM_MOVE = booleanPreferencesKey("confirm_move")
        private val KEY_DEFAULT_GRID_MODE = booleanPreferencesKey("default_grid_mode")
        private val KEY_HIDE_GRID_ACTION_BUTTONS = booleanPreferencesKey("hide_grid_action_buttons")
        private val KEY_FILE_OPS_IN_OVERFLOW_MENU = booleanPreferencesKey("file_ops_in_overflow_menu")
        private val KEY_FILE_OPS_OVERFLOW_MENU_HINT_SHOWN = booleanPreferencesKey("file_ops_overflow_menu_hint_shown")
        private val KEY_HIDE_SYSTEM_UI_IN_FULLSCREEN = booleanPreferencesKey("hide_system_ui_in_fullscreen")
        private val KEY_DEFAULT_ICON_SIZE = intPreferencesKey("default_icon_size")
        private val KEY_DEFAULT_SHOW_COMMAND_PANEL = booleanPreferencesKey("default_show_command_panel")
        private val KEY_OPEN_VIDEO_IN_FULLSCREEN = booleanPreferencesKey("open_video_in_fullscreen")
        private val KEY_SHOW_DETAILED_ERRORS = booleanPreferencesKey("show_detailed_errors")
        private val KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN = booleanPreferencesKey("show_player_hint_on_first_run")
        private val KEY_ALWAYS_SHOW_TOUCH_ZONES_OVERLAY = booleanPreferencesKey("always_show_touch_zones_overlay")
        private val KEY_NINE_ZONE_GRID_ENABLED = booleanPreferencesKey("nine_zone_grid_enabled")
        private val KEY_SHOW_VIDEO_THUMBNAILS = booleanPreferencesKey("show_video_thumbnails")
        private val KEY_ENABLE_PLAYER_WARMUP = booleanPreferencesKey("enable_player_warmup")
        private val KEY_RENDERER_MIGRATION_ENABLED = booleanPreferencesKey("renderer_migration_enabled")

        private val KEY_ENABLE_SAFE_MODE = booleanPreferencesKey("enable_safe_mode")
        private val KEY_ENABLE_SCHEDULED_OPERATIONS = booleanPreferencesKey("enable_scheduled_operations")
        private val KEY_SCHEDULED_OPERATIONS_PAUSED = booleanPreferencesKey("scheduled_operations_paused")

        private val KEY_ENABLE_COPYING = booleanPreferencesKey("enable_copying")
        private val KEY_GO_TO_NEXT_AFTER_COPY = booleanPreferencesKey("go_to_next_after_copy")
        private val KEY_OVERWRITE_ON_COPY = booleanPreferencesKey("overwrite_on_copy")
        private val KEY_ENABLE_MOVING = booleanPreferencesKey("enable_moving")
        private val KEY_OVERWRITE_ON_MOVE = booleanPreferencesKey("overwrite_on_move")
        private val KEY_ENABLE_UNDO = booleanPreferencesKey("enable_undo")
        private val KEY_MAX_RECIPIENTS = intPreferencesKey("max_recipients")
        private val KEY_DISABLE_CAMERA_CAPTURE = booleanPreferencesKey("disable_camera_capture")
        private val KEY_SKIP_CAMERA_FILENAME_DIALOG = booleanPreferencesKey("skip_camera_filename_dialog")
        // Camera-capture + mic-recording keys moved to data.repository.settings.CaptureSettingsStore (responsibility extraction).
        // S0371: video recording to resource (master toggle stored inverted, like the camera flags)
        private val KEY_DISABLE_VIDEO_CAPTURE = booleanPreferencesKey("disable_video_capture")
        private val KEY_VIDEO_CAPTURE_OPEN_IN_PLAYER = booleanPreferencesKey("video_capture_open_in_player")
        private val KEY_VIDEO_RECORDING_DESTINATION_RESOURCE_ID = stringPreferencesKey("video_recording_destination_resource_id")
        // S0100: Microphone recording feature
        // S0367: default destination resource ids for capture flows (null = deterministic fallback)
        private val KEY_IS_PLAYER_FIRST_RUN = booleanPreferencesKey("is_player_first_run")
        
        // Per-type touch zone hint tracking keys (Task 6)
        private val KEY_HINT_SHOWN_9ZONE = booleanPreferencesKey("hint_shown_9zone")
        private val KEY_HINT_SHOWN_3ZONE = booleanPreferencesKey("hint_shown_3zone")
        private val KEY_HINT_SHOWN_MEDIA = booleanPreferencesKey("hint_shown_media_bottom")

        private val KEY_COPY_PANEL_COLLAPSED = booleanPreferencesKey("copy_panel_collapsed")
        private val KEY_MOVE_PANEL_COLLAPSED = booleanPreferencesKey("move_panel_collapsed")
        // S0781: main-window resource-type filter strip collapsed state.
        private val KEY_RESOURCE_TYPE_TAB_COLLAPSED = booleanPreferencesKey("resource_type_tab_collapsed")
        // S0807: main-window programs panel collapsed-strip state.
        private val KEY_PROGRAMS_PANEL_COLLAPSED = booleanPreferencesKey("programs_panel_collapsed")
        // S0808: main-window streams panel collapsed-strip state.
        private val KEY_STREAMS_PANEL_COLLAPSED = booleanPreferencesKey("streams_panel_collapsed")
        private val KEY_ENABLE_PICTURE_IN_PICTURE = booleanPreferencesKey("enable_picture_in_picture")
        private val KEY_LAST_USED_RESOURCE_ID = longPreferencesKey("last_used_resource_id")
        private val KEY_DEFAULT_REMEMBER_FILE_LIST = booleanPreferencesKey("default_remember_file_list")
        private val KEY_IS_RESOURCE_GRID_MODE = booleanPreferencesKey("is_resource_grid_mode")
        private val KEY_RESOURCE_OPS_IN_OVERFLOW_MENU = booleanPreferencesKey("resource_ops_in_overflow_menu")
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
        private val KEY_VIDEO_FRAME_COPY_TO_CLIPBOARD = booleanPreferencesKey("video_frame_copy_to_clipboard")

        // Link auto-download (S0003): master toggle, optional destination resource id, auto-open toggle
        // Link auto-download keys moved to data.repository.settings.LinkSettingsStore (responsibility extraction).

        // S0116 §5.1 pillar J: streaming/quality preference for url-download pipeline.

        private val KEY_RESUME_ON_NEXT_LAUNCH = booleanPreferencesKey("resume_on_next_launch")

        // S0050: Black Screen button visibility in player toolbar
        private val KEY_SHOW_BLACK_SCREEN_BUTTON = booleanPreferencesKey("show_black_screen_button")

        // S0473: opt-in local usage statistics (default OFF for privacy).
        private val KEY_ENABLE_STATISTICS = booleanPreferencesKey("enable_statistics")

        // S1045: secure-by-default flag for credential-bearing screens (blocks screenshots/Recents).
        private val KEY_SECURE_SENSITIVE_SCREENS = booleanPreferencesKey("secure_sensitive_screens")

        // Launcher desktop keys moved to data.repository.settings.LauncherSettingsStore (responsibility extraction).

        // Legacy keys removed by S0241 / S0251 (vr_auto_detect_format, vr_forced_format,
        // vr_forced_plat_format, vr_forced_spherical_format, vr_remember_file_format). Their
        // declarations are gone; orphan DataStore entries left over in existing installs are
        // ignored by the new build path.
        private val KEY_VR_RENDERING_MODE = stringPreferencesKey("vr_rendering_mode")
        private val KEY_VR_AUTO_IMMERSIVE = booleanPreferencesKey("vr_auto_immersive")
        private val KEY_VR_PLAYER_ENTRY_PROMPT_DISMISSED = booleanPreferencesKey("vr_player_entry_prompt_dismissed")
        // Global VR kill-switch (spec §3.0.2): disables all 3D/VR classification when true
        private val KEY_VR_DISABLE_3D = booleanPreferencesKey("vr_disable_3d")
        // Panel-mode single-eye crop (spec_panel-stereo-single-eye)
        private val KEY_PANEL_STEREO_SINGLE_EYE = booleanPreferencesKey("panel_stereo_single_eye")
        private val KEY_VR_SHOW_FPS = booleanPreferencesKey("vr_show_fps")
        private val KEY_PLAYER_SHOW_FPS = booleanPreferencesKey("player_show_fps")
        // S0326: global 3D/VR default settings (detection-source flags + UNKNOWN-fallback defaults)
        // Stereo/3D default keys moved to data.repository.settings.StereoSettingsStore (responsibility extraction).

        // S0028: Multi-window mode
        private val KEY_ALLOW_SEPARATE_WINDOW = booleanPreferencesKey("allow_separate_window")

        // S0162: Screen rotation control
        // S0439: KEY_FOLLOW_SYSTEM_ROTATION now backs the program-scope flag (key reused for upgrade-safe migration).
        private val KEY_FOLLOW_SYSTEM_ROTATION = booleanPreferencesKey("follow_system_rotation")
        private val KEY_PLAYER_FOLLOW_SYSTEM_ROTATION = booleanPreferencesKey("player_follow_system_rotation")
        private val KEY_PLAYER_ROTATION_SENSOR_ENABLED = booleanPreferencesKey("player_rotation_sensor_enabled")

        // Adaptive pre-cache strategy (spec §5)
        private val KEY_PREFETCH_CACHE_MULTIPLIER = stringPreferencesKey("prefetch_cache_multiplier")
        private val KEY_RESOURCE_GRID_CELL_SIZE = stringPreferencesKey("resource_grid_cell_size")
        private val KEY_STREAMING_CACHE_CLEANUP_MODE = stringPreferencesKey("streaming_cache_cleanup_mode")
        private val KEY_STREAMING_CACHE_TTL_DAYS = intPreferencesKey("streaming_cache_ttl_days")

        /** Allowed values for [KEY_STREAMING_CACHE_TTL_DAYS]; `0` means "off". */
        private val STREAMING_CACHE_TTL_VALID = setOf(0, 1, 3, 7, 30)

    }

    // Cached once per singleton - avoids repeated getSharedPreferences() calls inside DataStore map {}
    private val glidePrefs by lazy {
        context.getSharedPreferences("app_settings_glide", Context.MODE_PRIVATE)
    }

    // S0253: distinguishes a fresh install (firstInstallTime == lastUpdateTime) from an upgraded
    // existing install. Used only as the fallback default for opt-in settings that should be ON
    // for new users while keeping every existing user's behavior unchanged after they update
    // to this build (existing user has lastUpdateTime > firstInstallTime → fallback resolves to false).
    private val isFreshInstall: Boolean by lazy {
        runCatching {
            val info = context.packageManager.getPackageInfoCompat(context.packageName)
            val fresh = info.firstInstallTime == info.lastUpdateTime
            fresh
        }.getOrDefault(false)
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
                // LocaleHelper which resolves SharedPreferences → system locale → "en". Legacy
                // installs can still carry the old "system" sentinel here, so normalize that to
                // the effective app language before exposing AppSettings to the rest of the app.
                val language = when {
                    languageFromDataStore == null -> LocaleHelper.getLanguage(context)
                    LocaleHelper.isFollowSystemLanguage(languageFromDataStore) -> LocaleHelper.getLanguage(context)
                    else -> LocaleHelper.resolveSupportedLanguageCode(languageFromDataStore)
                }
                val colorTheme = ColorThemePrefs.normalizeValue(preferences[KEY_COLOR_THEME])
                
                // Cache size for Glide (GlideAppModule reads from SharedPreferences during init)
                // glidePrefs is a lazy singleton field - no disk access on every emission
                val cacheSizeMb = preferences[KEY_CACHE_SIZE_MB] ?: 2048
                val savedCacheSize = glidePrefs.getInt("cache_size_mb_cached", 0)
                if (savedCacheSize != cacheSizeMb) {
                    glidePrefs.edit().putInt("cache_size_mb_cached", cacheSizeMb).apply()
                    if (BuildConfig.DEBUG) Timber.d("SettingsRepositoryImpl: Synced cacheSizeMb to SharedPreferences: ${cacheSizeMb}MB")
                }
                
                val stereo = StereoSettingsStore.read(preferences)
                val textRec = TextRecognitionSettingsStore.read(preferences)
                val audio = AudioSettingsStore.read(preferences)
                val capture = CaptureSettingsStore.read(preferences)
                val screenshot = ScreenshotSettingsStore.read(preferences)
                val slideshow = SlideshowSettingsStore.read(preferences)
                val link = LinkSettingsStore.read(preferences)
                val mediaSize = MediaSizeFilterSettingsStore.read(preferences)
                val remoteSource = RemoteSourceSettingsStore.read(preferences)
                val streams = StreamsSettingsStore.read(preferences)
                val programs = ProgramsSettingsStore.read(preferences)
                val launcher = LauncherSettingsStore.read(preferences)

                AppSettings(
                    language = language,
                    colorTheme = colorTheme,
                    preventSleep = preferences[KEY_PREVENT_SLEEP] ?: true,
                    keepScreenOnPlayer = preferences[KEY_KEEP_SCREEN_ON_PLAYER] ?: true,
                    showSmallControls = preferences[KEY_SHOW_SMALL_CONTROLS] ?: false,
                    enableCalculator = programs.enableCalculator,
                    enableNetworkMonitor = programs.enableNetworkMonitor,
                    enableSystemInfo = programs.enableSystemInfo,
                    enableWearCompanion = programs.enableWearCompanion,
                    recordGnssTrack = programs.recordGnssTrack,
                    embeddedGameEnabled = programs.embeddedGameEnabled,
                    frontFlashlightEnabled = programs.frontFlashlightEnabled,
                    frontFlashlightColor = programs.frontFlashlightColor,
                    showProgramsPanelInMainWindow = programs.showProgramsPanelInMainWindow,
                    defaultUser = preferences[KEY_DEFAULT_USER] ?: "",
                    defaultPassword = decryptPassword(preferences[KEY_DEFAULT_PASSWORD]),
                    networkParallelism = preferences[KEY_NETWORK_PARALLELISM] ?: 4,
                    cacheSizeMb = preferences[KEY_CACHE_SIZE_MB] ?: 2048,
                    isCacheSizeUserModified = preferences[KEY_IS_CACHE_SIZE_USER_MODIFIED] ?: false,
                    enabledShareTargets = preferences[KEY_ENABLED_SHARE_TARGETS] ?: emptySet(),
                    disabledShareTargets = preferences[KEY_DISABLED_SHARE_TARGETS] ?: emptySet(),
                    isResourceGridMode = preferences[KEY_IS_RESOURCE_GRID_MODE] ?: false,
                    // fromName, not valueOf: a stored value from a renamed or corrupted entry has to
                    // degrade to the default instead of throwing while settings load.
                    resourceGridCellSize = com.sza.fastmediasorter.domain.model.ResourceGridCellSize
                        .fromName(preferences[KEY_RESOURCE_GRID_CELL_SIZE]),
                    resourceOpsInOverflowMenu = preferences[KEY_RESOURCE_OPS_IN_OVERFLOW_MENU] ?: isFreshInstall, // S0253: fresh install → ON; existing user → OFF
                    enableBackgroundSync = preferences[KEY_ENABLE_BACKGROUND_SYNC] ?: false,
                    backgroundSyncIntervalHours = preferences[KEY_BACKGROUND_SYNC_INTERVAL_HOURS] ?: 4,
                    smbEnabled = remoteSource.smbEnabled,
                    sftpEnabled = remoteSource.sftpEnabled,
                    ftpEnabled = remoteSource.ftpEnabled,
                    googleDriveEnabled = remoteSource.googleDriveEnabled,
                    oneDriveEnabled = remoteSource.oneDriveEnabled,
                    dropboxEnabled = remoteSource.dropboxEnabled,
                    allFiles = preferences[KEY_ALL_FILES] ?: false,
                    showHiddenFiles = preferences[KEY_SHOW_HIDDEN_FILES] ?: false,
                    showSubfoldersAsItems = preferences[KEY_SHOW_SUBFOLDERS_AS_ITEMS] ?: false,
                    supportImages = preferences[KEY_SUPPORT_IMAGES] ?: true,
                    imageSizeMin = mediaSize.imageSizeMin,
                    imageSizeMax = mediaSize.imageSizeMax,
                    loadFullSizeImages = preferences[KEY_LOAD_FULL_SIZE_IMAGES] ?: true,
                    cropImagesToFullscreen = preferences[KEY_CROP_IMAGES_TO_FULLSCREEN] ?: false,
                    supportGifs = preferences[KEY_SUPPORT_GIFS] ?: true,
                    supportVideos = preferences[KEY_SUPPORT_VIDEOS] ?: true,
                    videoSizeMin = mediaSize.videoSizeMin,
                    videoSizeMax = mediaSize.videoSizeMax,
                    supportAudio = preferences[KEY_SUPPORT_AUDIO] ?: true,
                    audioSizeMin = audio.audioSizeMin,
                    audioSizeMax = audio.audioSizeMax,
                    searchAudioCoversOnline = preferences[KEY_SEARCH_AUDIO_COVERS_ONLINE] ?: false,
                    searchAudioCoversOnlyOnWifi = preferences[KEY_SEARCH_AUDIO_COVERS_ONLY_ON_WIFI] ?: true,
                    saveAudioMetadataLocally = preferences[KEY_SAVE_AUDIO_METADATA_LOCALLY] ?: true,
                    enablePhotosDuringAudio = preferences[KEY_ENABLE_PHOTOS_DURING_AUDIO] ?: false,
                    audioBackgroundPhotosResourceId = audio.audioBackgroundPhotosResourceId,
                    enablePersistentAudioPlayback = preferences[KEY_ENABLE_BACKGROUND_AUDIO] ?: false,
                    backgroundAudioExitBehavior = preferences[KEY_BACKGROUND_AUDIO_EXIT_BEHAVIOR]
                        ?.let { runCatching { com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior.valueOf(it) }.getOrNull() }
                        ?: com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior.ASK,
                    showNowPlayingPanel = preferences[KEY_SHOW_NOW_PLAYING_PANEL] ?: false,
                    audioEmptyStateMode = audio.audioEmptyStateMode,
                    supportText = preferences[KEY_SUPPORT_TEXT] ?: true,
                    supportPdf = preferences[KEY_SUPPORT_PDF] ?: true,
                    supportEpub = preferences[KEY_SUPPORT_EPUB] ?: true,
                    supportOfficeDocuments = preferences[KEY_SUPPORT_OFFICE_DOCUMENTS]
                        ?: ((preferences[KEY_SUPPORT_TEXT] ?: true) ||
                            (preferences[KEY_SUPPORT_PDF] ?: true) ||
                            (preferences[KEY_SUPPORT_EPUB] ?: true)),
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
                    enableTranslation = textRec.enableTranslation,
                    enableStreams = streams.enableStreams,
                    streamsDefaultSort = streams.streamsDefaultSort,
                    streamsDefaultMediaFilter = streams.streamsDefaultMediaFilter,
                    streamsCatalogRefreshPolicy = streams.streamsCatalogRefreshPolicy,
                    showStreamsPanelInMainWindow = streams.showStreamsPanelInMainWindow,
                    streamsSmartBuffering = streams.streamsSmartBuffering,
                    streamsDefaultAudioLanguage = streams.streamsDefaultAudioLanguage,
                    streamsDefaultSubtitleLanguage = streams.streamsDefaultSubtitleLanguage,
                    translationSourceLanguage = textRec.translationSourceLanguage,
                    translationTargetLanguage = textRec.translationTargetLanguage,
                    translationLensStyle = textRec.translationLensStyle,
                    enableOcr = textRec.enableOcr,
                    cameraOcrTranslationEnabled = textRec.cameraOcrTranslationEnabled,
                    cameraOcrOnly = textRec.cameraOcrOnly,
                    ocrDefaultFontSize = textRec.ocrDefaultFontSize,
                    ocrDefaultFontFamily = textRec.ocrDefaultFontFamily,
                    ocrEngineType = textRec.ocrEngineType,
                    paddleOcrModel = textRec.paddleOcrModel,
                    defaultSortMode = SortMode.valueOf(
                        preferences[KEY_DEFAULT_SORT_MODE] ?: SortMode.NAME_ASC.name
                    ),
                    slideshowInterval = slideshow.slideshowInterval,
                    slideshowMusicUri = slideshow.slideshowMusicUri,
                    enableSlideshowBackgroundMusic = slideshow.enableSlideshowBackgroundMusic,
                    slideshowMusicResourceId = slideshow.slideshowMusicResourceId,
                    playToEndInSlideshow = preferences[KEY_PLAY_TO_END] ?: true,
                    allowRename = preferences[KEY_ALLOW_RENAME] ?: true,
                    allowDelete = preferences[KEY_ALLOW_DELETE] ?: true,
                    useTrash = preferences[KEY_USE_TRASH] ?: false,
                    confirmDelete = preferences[KEY_CONFIRM_DELETE] ?: true,
                    confirmMove = preferences[KEY_CONFIRM_MOVE] ?: false,
                    defaultGridMode = preferences[KEY_DEFAULT_GRID_MODE] ?: false,
                    hideGridActionButtons = preferences[KEY_HIDE_GRID_ACTION_BUTTONS] ?: true,
                    fileOpsInOverflowMenu = preferences[KEY_FILE_OPS_IN_OVERFLOW_MENU] ?: (MultiWindowCapabilityDetector.defaultFileOpsInOverflowMenu(context) || isFreshInstall).also {
                    }, // S0293: capability-detected devices (VR/XR/ChromeOS) get ON; otherwise S0253 fresh install → ON; existing non-capable user → OFF
                    fileOpsOverflowMenuHintShown = preferences[KEY_FILE_OPS_OVERFLOW_MENU_HINT_SHOWN] ?: (MultiWindowCapabilityDetector.defaultFileOpsInOverflowMenu(context) || isFreshInstall), // S0293: capability device or fresh install suppresses one-time "ops moved to menu" Toast (symmetric with fileOpsInOverflowMenu default)
                    hideSystemUiInFullscreen = preferences[KEY_HIDE_SYSTEM_UI_IN_FULLSCREEN] ?: true,
                    defaultIconSize = (preferences[KEY_DEFAULT_ICON_SIZE] ?: 96)
                        .let { if (it < 32 || it > 256 || (it - 32) % 8 != 0) 96 else it },
                    defaultShowCommandPanel = preferences[KEY_DEFAULT_SHOW_COMMAND_PANEL] ?: true,
                    openVideoInFullscreen = preferences[KEY_OPEN_VIDEO_IN_FULLSCREEN] ?: true,
                    showDetailedErrors = preferences[KEY_SHOW_DETAILED_ERRORS] ?: false,
                    showPlayerHintOnFirstRun = preferences[KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN] ?: true,
                    alwaysShowTouchZonesOverlay = preferences[KEY_ALWAYS_SHOW_TOUCH_ZONES_OVERLAY] ?: false,
                    nineZoneGridEnabled = preferences[KEY_NINE_ZONE_GRID_ENABLED] ?: true,
                    showVideoThumbnails = preferences[KEY_SHOW_VIDEO_THUMBNAILS] ?: true,
                    enablePlayerWarmup = preferences[KEY_ENABLE_PLAYER_WARMUP] ?: false,
                    rendererMigrationEnabled = preferences[KEY_RENDERER_MIGRATION_ENABLED] ?: false,
                    enableSafeMode = preferences[KEY_ENABLE_SAFE_MODE] ?: true,
                    enableScheduledOperations = preferences[KEY_ENABLE_SCHEDULED_OPERATIONS] ?: true,
                    scheduledOperationsPaused = preferences[KEY_SCHEDULED_OPERATIONS_PAUSED] ?: false,
                    enableCopying = preferences[KEY_ENABLE_COPYING] ?: true,
                    goToNextAfterCopy = preferences[KEY_GO_TO_NEXT_AFTER_COPY] ?: true,
                    overwriteOnCopy = preferences[KEY_OVERWRITE_ON_COPY] ?: false,
                    enableMoving = preferences[KEY_ENABLE_MOVING] ?: true,
                    overwriteOnMove = preferences[KEY_OVERWRITE_ON_MOVE] ?: false,
                    enableUndo = preferences[KEY_ENABLE_UNDO] ?: true,
                    maxRecipients = (preferences[KEY_MAX_RECIPIENTS] ?: 10).coerceIn(1, 10),
                    enableFavorites = programs.enableFavorites,
                    disableCameraCapture = preferences[KEY_DISABLE_CAMERA_CAPTURE] ?: false,
                    skipCameraFilenameDialog = preferences[KEY_SKIP_CAMERA_FILENAME_DIALOG] ?: false,
                    cameraCaptureOpenForEditing = capture.cameraCaptureOpenForEditing,
                    cameraCaptureCopyToClipboard = capture.cameraCaptureCopyToClipboard,
                    cameraGeotagEnabled = capture.cameraGeotagEnabled,
                    cameraAspectRatio = capture.cameraAspectRatio,
                    cameraLensSettings = capture.cameraLensSettings,
                    disableVideoCapture = preferences[KEY_DISABLE_VIDEO_CAPTURE] ?: false,
                    videoCaptureOpenInPlayer = preferences[KEY_VIDEO_CAPTURE_OPEN_IN_PLAYER] ?: false,
                    videoRecordingDestinationResourceId = preferences[KEY_VIDEO_RECORDING_DESTINATION_RESOURCE_ID],
                    micRecordingEnabled = capture.micRecordingEnabled,
                    micRecordingAskFilename = capture.micRecordingAskFilename,
                    micRecordingDestinationResourceId = capture.micRecordingDestinationResourceId,
                    screenRecordingEnabled = capture.screenRecordingEnabled,
                    screenRecordingDestinationResourceId = capture.screenRecordingDestinationResourceId,
                    screenRecordingDisclosureAccepted = capture.screenRecordingDisclosureAccepted,
                    cameraPhotosDestinationResourceId = capture.cameraPhotosDestinationResourceId,
                    gestureOverlayEnabled = screenshot.gestureOverlayEnabled,
                    screenshotGesture = ScreenshotGestureSettings(
                        zoneLeftTopEnabled = screenshot.zoneLeftTopEnabled,
                        zoneLeftBottomEnabled = screenshot.zoneLeftBottomEnabled,
                        zoneRightTopEnabled = screenshot.zoneRightTopEnabled,
                        zoneRightBottomEnabled = screenshot.zoneRightBottomEnabled,
                        zoneLeftTopStripVisible = screenshot.zoneLeftTopStripVisible,
                        zoneLeftBottomStripVisible = screenshot.zoneLeftBottomStripVisible,
                        zoneRightTopStripVisible = screenshot.zoneRightTopStripVisible,
                        zoneRightBottomStripVisible = screenshot.zoneRightBottomStripVisible,
                        leftTopDown = screenshot.leftTopDown,
                        leftTopRight = screenshot.leftTopRight,
                        leftTopUp = screenshot.leftTopUp,
                        leftBottomDown = screenshot.leftBottomDown,
                        leftBottomRight = screenshot.leftBottomRight,
                        leftBottomUp = screenshot.leftBottomUp,
                        rightTopDown = screenshot.rightTopDown,
                        rightTopRight = screenshot.rightTopRight,
                        rightTopUp = screenshot.rightTopUp,
                        rightBottomDown = screenshot.rightBottomDown,
                        rightBottomRight = screenshot.rightBottomRight,
                        rightBottomUp = screenshot.rightBottomUp,
                        payloadLeftTopDown = screenshot.payloadLeftTopDown,
                        payloadLeftTopRight = screenshot.payloadLeftTopRight,
                        payloadLeftTopUp = screenshot.payloadLeftTopUp,
                        payloadLeftBottomDown = screenshot.payloadLeftBottomDown,
                        payloadLeftBottomRight = screenshot.payloadLeftBottomRight,
                        payloadLeftBottomUp = screenshot.payloadLeftBottomUp,
                        payloadRightTopDown = screenshot.payloadRightTopDown,
                        payloadRightTopRight = screenshot.payloadRightTopRight,
                        payloadRightTopUp = screenshot.payloadRightTopUp,
                        payloadRightBottomDown = screenshot.payloadRightBottomDown,
                        payloadRightBottomRight = screenshot.payloadRightBottomRight,
                        payloadRightBottomUp = screenshot.payloadRightBottomUp,
                    ),
                    screenshotDestinationResourceId = screenshot.screenshotDestinationResourceId,
                    copyScreenshotToClipboard = screenshot.copyScreenshotToClipboard,
                    screenCaptureDisclosureAccepted = screenshot.screenCaptureDisclosureAccepted,
                    copyPanelCollapsed = preferences[KEY_COPY_PANEL_COLLAPSED] ?: false,
                    movePanelCollapsed = preferences[KEY_MOVE_PANEL_COLLAPSED] ?: false,
                    resourceTypeTabCollapsed = preferences[KEY_RESOURCE_TYPE_TAB_COLLAPSED] ?: false,
                    programsPanelCollapsed = preferences[KEY_PROGRAMS_PANEL_COLLAPSED] ?: false,
                    streamsPanelCollapsed = preferences[KEY_STREAMS_PANEL_COLLAPSED] ?: false,
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

                    // S0470: copy extracted frame to clipboard (default off)
                    videoFrameCopyToClipboard = preferences[KEY_VIDEO_FRAME_COPY_TO_CLIPBOARD] ?: false,

                    // Link auto-download (S0003) - owned by LinkSettingsStore.
                    linkAutoDownloadEnabled = link.linkAutoDownloadEnabled,
                    linkAutoDownloadResourceId = link.linkAutoDownloadResourceId,
                    linkAutoDownloadOpenInPlayer = link.linkAutoDownloadOpenInPlayer,
                    linkDownloadMaxResolution = link.linkDownloadMaxResolution,
                    linkDownloadAudioOnly = link.linkDownloadAudioOnly,
                    linkDownloadLoginWallHeuristicEnabled = link.linkDownloadLoginWallHeuristicEnabled,

                    // VR settings (spec §5.7 / Phase 8). S0251 removed forced-format fields.
                    vrRenderingMode = preferences[KEY_VR_RENDERING_MODE]
                        ?.takeIf { it in listOf("CINEMA", "FULL_SBS", "FULL_OU") }
                        ?: "CINEMA",
                    vrAutoImmersive = preferences[KEY_VR_AUTO_IMMERSIVE] ?: true,
                    vrPlayerEntryPromptDismissed = preferences[KEY_VR_PLAYER_ENTRY_PROMPT_DISMISSED] ?: false,
                    disable3dVr = preferences[KEY_VR_DISABLE_3D] ?: false,
                    // S0264: default ON for every flavor; immersive VR rendering overrides this at runtime.
                    panelStereoSingleEye = preferences[KEY_PANEL_STEREO_SINGLE_EYE] ?: true,
                    vrShowFps = preferences[KEY_VR_SHOW_FPS] ?: false,
                    playerShowFps = preferences[KEY_PLAYER_SHOW_FPS] ?: false,
                    // S0326: global 3D/VR default settings - owned by StereoSettingsStore.
                    stereoAutoDetectEnabled = stereo.autoDetectEnabled,
                    stereoTrustFilename = stereo.trustFilename,
                    stereoTrustMetadata = stereo.trustMetadata,
                    stereoTrustAspectRatio = stereo.trustAspectRatio,
                    stereoAmbiguityBestGuess = stereo.ambiguityBestGuess,
                    stereoDefaultLayout = stereo.defaultLayout,
                    stereoDefaultProjection = stereo.defaultProjection,

                    // Default true: resumes playback on fresh installs and on update from old versions
                    // (absent key → null → default true, matching the user's existing behaviour)
                    resumeOnNextLaunch = preferences[KEY_RESUME_ON_NEXT_LAUNCH] ?: true,

                    // S0050: absent key → false (opt-in feature, disabled by default)
                    showBlackScreenButton = preferences[KEY_SHOW_BLACK_SCREEN_BUTTON] ?: false,

                    // Absent key → true: local-only usage statistics are enabled by default on a
                    // fresh install (nothing leaves the device; user can opt out in onboarding/Settings).
                    enableStatistics = preferences[KEY_ENABLE_STATISTICS] ?: true,

                    // S1045: absent key → true (secure by default on fresh install).
                    secureSensitiveScreens = preferences[KEY_SECURE_SENSITIVE_SCREENS] ?: true,

                    // S0404: launcher desktop tuning - owned by LauncherSettingsStore.
                    launcherDensityFactor = launcher.launcherDensityFactor,
                    launcherTaskbarShowRecents = launcher.launcherTaskbarShowRecents,
                    launcherTaskbarShowPinned = launcher.launcherTaskbarShowPinned,
                    launcherTaskbarShowTray = launcher.launcherTaskbarShowTray,
                    launcherTrayShowClock = launcher.launcherTrayShowClock,
                    launcherTrayShowBluetooth = launcher.launcherTrayShowBluetooth,
                    launcherTrayShowSim1 = launcher.launcherTrayShowSim1,
                    launcherTrayShowSim2 = launcher.launcherTrayShowSim2,
                    launcherTrayShowNetwork = launcher.launcherTrayShowNetwork,
                    launcherTrayShowBattery = launcher.launcherTrayShowBattery,
                    launcherReplaceSystemStatusArea = launcher.launcherReplaceSystemStatusArea,
                    launcherTopStatusStripMode = launcher.launcherTopStatusStripMode,
                    launcherForeignNotificationsEnabled = launcher.launcherForeignNotificationsEnabled,
                    launcherTaskbarPlacement = launcher.launcherTaskbarPlacement,
                    launcherRotationHintShown = launcher.launcherRotationHintShown,
                    launcherDesktopLocked = launcher.launcherDesktopLocked,
                    launcherWallpaperMode = launcher.launcherWallpaperMode,
                    launcherWallpaperImagePath = launcher.launcherWallpaperImagePath,
                    allAppsSortOrder = launcher.allAppsSortOrder,
                    allAppsSortDescending = launcher.allAppsSortDescending,
                    launcherScreenBlackoutTimeoutSeconds = launcher.launcherScreenBlackoutTimeoutSeconds,

                    // Adaptive pre-cache strategy (spec §5)
                    prefetchCacheMultiplier = com.sza.fastmediasorter.domain.model.PrefetchCacheMultiplier
                        .fromName(preferences[KEY_PREFETCH_CACHE_MULTIPLIER]),
                    streamingCacheCleanupMode = com.sza.fastmediasorter.domain.model.StreamingCacheCleanupMode
                        .fromName(preferences[KEY_STREAMING_CACHE_CLEANUP_MODE]),
                    streamingCacheTtlDays = preferences[KEY_STREAMING_CACHE_TTL_DAYS]
                        ?.takeIf { it in STREAMING_CACHE_TTL_VALID }
                        ?: 7,

                    // S0028: Multi-window mode
                    allowSeparateWindow = preferences[KEY_ALLOW_SEPARATE_WINDOW]
                        ?: MultiWindowCapabilityDetector.defaultAllowSeparateWindow(context),

                    // S0162/S0439: absent key → default true (program flag inherits the legacy value on upgrade)
                    programFollowSystemRotation = preferences[KEY_FOLLOW_SYSTEM_ROTATION] ?: true,
                    // S0439: new player flag defaults off on upgrade (preserves prior behaviour)
                    playerFollowSystemRotation = preferences[KEY_PLAYER_FOLLOW_SYSTEM_ROTATION] ?: false,
                    playerRotationSensorEnabled = preferences[KEY_PLAYER_ROTATION_SENSOR_ENABLED] ?: true
                )
            }
            .distinctUntilChanged()
            // S1517: without this the whole mapping - including the Keystore round trip behind the
            // default password - runs in the collector's context, and the collector is BaseActivity,
            // which every screen in the app extends. The flow is delivered on the main thread either
            // way; only the work above moves off it.
            .flowOn(Dispatchers.IO)
    }

    override suspend fun updateSettings(settings: AppSettings) {
        settingsUpdateMutex.withLock {
        Timber.d("SettingsRepo: updateSettings called with allFiles=${settings.allFiles}")

        // S0018 idempotency guard: if the incoming AppSettings equals the currently stored
        // value (data-class equality across all fields), skip the DataStore write entirely.
        // This eliminates the spam of "NO fields changed" warnings produced when settings
        // fragments fire setOnCheckedChangeListener callbacks during initial UI inflation.
        val current = runCatching { getSettings().first() }.getOrNull()
        if (current != null && current == settings) {
            Timber.v("SettingsRepo: updateSettings idempotent - skipping DataStore write")
            return
        }
        if (BuildConfig.DEBUG && current != null) {
            Timber.d("SettingsRepo: updateSettings diff detected - proceeding with DataStore write")
        }

        // NOTE: Language is NOT synced to SharedPreferences here.
        // LocaleHelper.saveLanguage() must be called explicitly when user changes the language.
        // Syncing here would overwrite system-locale fallback (uk/ru) with the DataStore default "en".
        val storedLanguage = if (
            LocaleHelper.isFollowSystemLanguage(settings.language) ||
            LocaleHelper.isFollowingSystemLanguage(context)
        ) {
            LocaleHelper.FOLLOW_SYSTEM_LANGUAGE
        } else {
            LocaleHelper.resolveSupportedLanguageCode(settings.language)
        }
        val storedColorTheme = ColorThemePrefs.normalizeValue(settings.colorTheme)

        // S1148: mirror for synchronous reads at player-build time (see RadioStreamBufferConfig).
        if (current == null || current.streamsSmartBuffering != settings.streamsSmartBuffering) {
            RadioStreamBufferConfig.syncMirror(context, settings.streamsSmartBuffering)
        }

        // S1792: mirrors for synchronous reads of color theme and compact player elements.
        if (current == null || current.colorTheme != settings.colorTheme) {
            ColorThemePrefs.setMode(context, settings.colorTheme)
        }
        if (current == null || current.useCompactElements != settings.useCompactElements) {
            PlayerLayoutModePrefs.setCompact(context, settings.useCompactElements)
        }

        dataStore.edit { preferences ->
            // Preserve the follow-system sentinel so later settings writes do not silently pin the
            // app to the currently effective language.
            preferences[KEY_LANGUAGE] = storedLanguage
            preferences[KEY_COLOR_THEME] = storedColorTheme
            preferences[KEY_PREVENT_SLEEP] = settings.preventSleep
            preferences[KEY_KEEP_SCREEN_ON_PLAYER] = settings.keepScreenOnPlayer
            preferences[KEY_SHOW_SMALL_CONTROLS] = settings.showSmallControls
            ProgramsSettingsStore.write(preferences, settings)
            preferences[KEY_DEFAULT_USER] = settings.defaultUser
            preferences[KEY_DEFAULT_PASSWORD] = encryptPassword(settings.defaultPassword)
            preferences[KEY_NETWORK_PARALLELISM] = settings.networkParallelism
            preferences[KEY_CACHE_SIZE_MB] = settings.cacheSizeMb
            preferences[KEY_IS_CACHE_SIZE_USER_MODIFIED] = settings.isCacheSizeUserModified
            preferences[KEY_ENABLED_SHARE_TARGETS] = settings.enabledShareTargets
            preferences[KEY_DISABLED_SHARE_TARGETS] = settings.disabledShareTargets
            preferences[KEY_ENABLE_BACKGROUND_SYNC] = settings.enableBackgroundSync
            preferences[KEY_BACKGROUND_SYNC_INTERVAL_HOURS] = settings.backgroundSyncIntervalHours
            RemoteSourceSettingsStore.write(preferences, settings)
            preferences[KEY_ALL_FILES] = settings.allFiles
            Timber.d("SettingsRepo: Saved allFiles=${settings.allFiles} to DataStore")
            preferences[KEY_SHOW_HIDDEN_FILES] = settings.showHiddenFiles
            preferences[KEY_SHOW_SUBFOLDERS_AS_ITEMS] = settings.showSubfoldersAsItems
            preferences[KEY_SUPPORT_IMAGES] = settings.supportImages
            MediaSizeFilterSettingsStore.write(preferences, settings)
            preferences[KEY_LOAD_FULL_SIZE_IMAGES] = settings.loadFullSizeImages
            preferences[KEY_CROP_IMAGES_TO_FULLSCREEN] = settings.cropImagesToFullscreen
            preferences[KEY_SUPPORT_GIFS] = settings.supportGifs
            preferences[KEY_SUPPORT_VIDEOS] = settings.supportVideos
            preferences[KEY_SUPPORT_AUDIO] = settings.supportAudio
            AudioSettingsStore.write(preferences, settings)
            preferences[KEY_SEARCH_AUDIO_COVERS_ONLINE] = settings.searchAudioCoversOnline
            preferences[KEY_SEARCH_AUDIO_COVERS_ONLY_ON_WIFI] = settings.searchAudioCoversOnlyOnWifi
            preferences[KEY_SAVE_AUDIO_METADATA_LOCALLY] = settings.saveAudioMetadataLocally
            preferences[KEY_ENABLE_PHOTOS_DURING_AUDIO] = settings.enablePhotosDuringAudio
            preferences[KEY_ENABLE_BACKGROUND_AUDIO] = settings.enablePersistentAudioPlayback
            preferences[KEY_BACKGROUND_AUDIO_EXIT_BEHAVIOR] = settings.backgroundAudioExitBehavior.name
            preferences[KEY_SHOW_NOW_PLAYING_PANEL] = settings.showNowPlayingPanel
            preferences[KEY_SUPPORT_TEXT] = settings.supportText
            preferences[KEY_SUPPORT_PDF] = settings.supportPdf
            preferences[KEY_SUPPORT_EPUB] = settings.supportEpub
            preferences[KEY_SUPPORT_OFFICE_DOCUMENTS] = settings.supportOfficeDocuments
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
            TextRecognitionSettingsStore.write(preferences, settings)
            StreamsSettingsStore.write(preferences, settings)
            preferences[KEY_DEFAULT_SORT_MODE] = settings.defaultSortMode.name
            SlideshowSettingsStore.write(preferences, settings)
            preferences[KEY_PLAY_TO_END] = settings.playToEndInSlideshow
            preferences[KEY_ALLOW_RENAME] = settings.allowRename
            preferences[KEY_ALLOW_DELETE] = settings.allowDelete
            preferences[KEY_USE_TRASH] = settings.useTrash
            preferences[KEY_CONFIRM_DELETE] = settings.confirmDelete
            preferences[KEY_CONFIRM_MOVE] = settings.confirmMove
            preferences[KEY_DEFAULT_GRID_MODE] = settings.defaultGridMode
            preferences[KEY_HIDE_GRID_ACTION_BUTTONS] = settings.hideGridActionButtons
            preferences[KEY_FILE_OPS_IN_OVERFLOW_MENU] = settings.fileOpsInOverflowMenu
            preferences[KEY_FILE_OPS_OVERFLOW_MENU_HINT_SHOWN] = settings.fileOpsOverflowMenuHintShown
            preferences[KEY_HIDE_SYSTEM_UI_IN_FULLSCREEN] = settings.hideSystemUiInFullscreen
            preferences[KEY_DEFAULT_ICON_SIZE] = settings.defaultIconSize
            preferences[KEY_DEFAULT_SHOW_COMMAND_PANEL] = settings.defaultShowCommandPanel
            preferences[KEY_OPEN_VIDEO_IN_FULLSCREEN] = settings.openVideoInFullscreen
            preferences[KEY_SHOW_DETAILED_ERRORS] = settings.showDetailedErrors
            preferences[KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN] = settings.showPlayerHintOnFirstRun
            preferences[KEY_ALWAYS_SHOW_TOUCH_ZONES_OVERLAY] = settings.alwaysShowTouchZonesOverlay
            preferences[KEY_NINE_ZONE_GRID_ENABLED] = settings.nineZoneGridEnabled
            preferences[KEY_SHOW_VIDEO_THUMBNAILS] = settings.showVideoThumbnails
            preferences[KEY_ENABLE_PLAYER_WARMUP] = settings.enablePlayerWarmup
            preferences[KEY_RENDERER_MIGRATION_ENABLED] = settings.rendererMigrationEnabled
            preferences[KEY_ENABLE_SAFE_MODE] = settings.enableSafeMode
            preferences[KEY_ENABLE_SCHEDULED_OPERATIONS] = settings.enableScheduledOperations
            preferences[KEY_SCHEDULED_OPERATIONS_PAUSED] = settings.scheduledOperationsPaused
            preferences[KEY_ENABLE_COPYING] = settings.enableCopying
            preferences[KEY_GO_TO_NEXT_AFTER_COPY] = settings.goToNextAfterCopy
            preferences[KEY_OVERWRITE_ON_COPY] = settings.overwriteOnCopy
            preferences[KEY_ENABLE_MOVING] = settings.enableMoving
            preferences[KEY_OVERWRITE_ON_MOVE] = settings.overwriteOnMove
            preferences[KEY_ENABLE_UNDO] = settings.enableUndo
            preferences[KEY_MAX_RECIPIENTS] = settings.maxRecipients.coerceIn(1, 10)
            preferences[KEY_DISABLE_CAMERA_CAPTURE] = settings.disableCameraCapture
            preferences[KEY_SKIP_CAMERA_FILENAME_DIALOG] = settings.skipCameraFilenameDialog
            CaptureSettingsStore.write(preferences, settings)
            ScreenshotSettingsStore.write(preferences, settings)
            preferences[KEY_DISABLE_VIDEO_CAPTURE] = settings.disableVideoCapture
            preferences[KEY_VIDEO_CAPTURE_OPEN_IN_PLAYER] = settings.videoCaptureOpenInPlayer
            preferences.setOrRemove(KEY_VIDEO_RECORDING_DESTINATION_RESOURCE_ID, settings.videoRecordingDestinationResourceId)
            preferences[KEY_COPY_PANEL_COLLAPSED] = settings.copyPanelCollapsed
            preferences[KEY_MOVE_PANEL_COLLAPSED] = settings.movePanelCollapsed
            preferences[KEY_RESOURCE_TYPE_TAB_COLLAPSED] = settings.resourceTypeTabCollapsed
            preferences[KEY_PROGRAMS_PANEL_COLLAPSED] = settings.programsPanelCollapsed
            preferences[KEY_STREAMS_PANEL_COLLAPSED] = settings.streamsPanelCollapsed
            preferences[KEY_ENABLE_PICTURE_IN_PICTURE] = settings.enablePictureInPicture
            preferences[KEY_LAST_USED_RESOURCE_ID] = settings.lastUsedResourceId
            preferences[KEY_DEFAULT_REMEMBER_FILE_LIST] = settings.defaultRememberFileList
            preferences[KEY_IS_RESOURCE_GRID_MODE] = settings.isResourceGridMode
            preferences[KEY_RESOURCE_GRID_CELL_SIZE] = settings.resourceGridCellSize.name
            preferences[KEY_RESOURCE_OPS_IN_OVERFLOW_MENU] = settings.resourceOpsInOverflowMenu
            preferences[KEY_DYNAMIC_BACKGROUND_EXTENSION] = settings.dynamicBackgroundExtension
            preferences[KEY_IS_PRIMARY_MEDIA_PLAYER] = settings.isPrimaryMediaPlayer
            preferences[KEY_ACCEPT_SHARED_FILES] = settings.acceptSharedFiles
            preferences[KEY_ENABLE_THUMBNAIL_PRELOAD] = settings.enableThumbnailPreload
            preferences[KEY_THUMBNAIL_PRELOAD_WIFI_ONLY] = settings.thumbnailPreloadWifiOnly
            // FR-8: Folder picker persistence
            preferences.setOrRemove(KEY_LAST_SELECTED_LOCAL_FOLDER, settings.lastSelectedLocalFolder)

            preferences[KEY_USE_COMPACT_ELEMENTS] = settings.useCompactElements
            preferences.setOrRemove(KEY_VIDEO_SNAPSHOT_RESOURCE_ID, settings.videoSnapshotResourceId)

            // Video frame snapshot format - always present with "PNG" default
            preferences[KEY_VIDEO_SNAPSHOT_FORMAT] = if (settings.videoSnapshotFormat == "JPG") "JPG" else "PNG"
            preferences[KEY_VIDEO_FRAME_COPY_TO_CLIPBOARD] = settings.videoFrameCopyToClipboard

            // Link auto-download (S0003) - owned by LinkSettingsStore.
            LinkSettingsStore.write(preferences, settings)

            // VR settings (spec §5.7 / Phase 8 split). S0241/S0251 removed forced-format
            // keys; the old DataStore entries remain on disk in older installs but the new
            // read path no longer reads them.
            preferences[KEY_VR_RENDERING_MODE] = settings.vrRenderingMode
            preferences[KEY_VR_AUTO_IMMERSIVE] = settings.vrAutoImmersive
            preferences[KEY_VR_PLAYER_ENTRY_PROMPT_DISMISSED] = settings.vrPlayerEntryPromptDismissed
            preferences[KEY_VR_DISABLE_3D] = settings.disable3dVr
            preferences[KEY_PANEL_STEREO_SINGLE_EYE] = settings.panelStereoSingleEye
            preferences[KEY_VR_SHOW_FPS] = settings.vrShowFps
            preferences[KEY_PLAYER_SHOW_FPS] = settings.playerShowFps
            // S0326: global 3D/VR default settings - owned by StereoSettingsStore.
            StereoSettingsStore.write(preferences, settings)

            preferences[KEY_RESUME_ON_NEXT_LAUNCH] = settings.resumeOnNextLaunch
            // S0050: Black Screen button (opt-in)
            preferences[KEY_SHOW_BLACK_SCREEN_BUTTON] = settings.showBlackScreenButton
            // S0473: local usage statistics (opt-in)
            preferences[KEY_ENABLE_STATISTICS] = settings.enableStatistics
            // S1045: secure sensitive screens (opt-out)
            preferences[KEY_SECURE_SENSITIVE_SCREENS] = settings.secureSensitiveScreens
            // S0404: launcher desktop tuning - owned by LauncherSettingsStore.
            LauncherSettingsStore.write(preferences, settings)

            // Adaptive pre-cache strategy (spec §5)
            preferences[KEY_PREFETCH_CACHE_MULTIPLIER] = settings.prefetchCacheMultiplier.name
            preferences[KEY_STREAMING_CACHE_CLEANUP_MODE] = settings.streamingCacheCleanupMode.name
            preferences[KEY_STREAMING_CACHE_TTL_DAYS] = settings.streamingCacheTtlDays
                .takeIf { it in STREAMING_CACHE_TTL_VALID } ?: 7

            // S0028: Multi-window mode
            preferences[KEY_ALLOW_SEPARATE_WINDOW] = settings.allowSeparateWindow

            // S0162 / S0439: Screen rotation control
            preferences[KEY_FOLLOW_SYSTEM_ROTATION] = settings.programFollowSystemRotation
            preferences[KEY_PLAYER_FOLLOW_SYSTEM_ROTATION] = settings.playerFollowSystemRotation
            preferences[KEY_PLAYER_ROTATION_SENSOR_ENABLED] = settings.playerRotationSensorEnabled
        }
        }
    }

    override suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings) {
        transformMutex.withLock {
            val current = getSettings().first()
            updateSettings(transform(current))
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

    override suspend fun updateEmbeddedGameEnabled(enabled: Boolean) {
        dataStore.edit { ProgramsSettingsStore.writeEmbeddedGameEnabled(it, enabled) }
    }

    override suspend fun updateScheduledOperationsPaused(paused: Boolean) {
        dataStore.edit { it[KEY_SCHEDULED_OPERATIONS_PAUSED] = paused }
    }

    override suspend fun setStatisticsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLE_STATISTICS] = enabled }
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
    
    /**
     * Decrypts password; handles migration from legacy plaintext passwords.
     *
     * S1517: the Keystore round trip and the migration write are pinned to IO here, not only at the
     * flow that happens to call this today. `suspend` alone changes no thread, so a future caller on
     * the main thread would silently reintroduce the same block.
     */
    private suspend fun decryptPassword(encryptedPassword: String?): String = withContext(Dispatchers.IO) {
        if (encryptedPassword.isNullOrEmpty()) return@withContext ""
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
            return@withContext encryptedPassword
        }
        CryptoHelper.decrypt(encryptedPassword) ?: run {
            Timber.e("Failed to decrypt password, returning empty string")
            ""
        }
    }

    override suspend fun isConsolidatedStorageActive(): Boolean {
        return true
    }
}
