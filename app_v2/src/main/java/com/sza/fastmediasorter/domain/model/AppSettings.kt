package com.sza.fastmediasorter.domain.model

/**
 * Application settings model
 * Based on V2 Specification: Settings Screen
 */
data class AppSettings(
    // UI State settings (Persisted view modes)
    val isResourceGridMode: Boolean = false, // Resource list view mode (List/Grid)

    // General settings
    val language: String = "en",
    val preventSleep: Boolean = true,
    val showSmallControls: Boolean = false,
    val defaultUser: String = "",
    val defaultPassword: String = "",
    val networkParallelism: Int = 4, // Parallel threads for network operations (1, 2, 4, 8, 12, 24)
    val cacheSizeMb: Int = 2048, // Glide disk cache size in MB (512, 1024, 2048, 4096, 8192, 16384) - Default: 2GB after installation
    val isCacheSizeUserModified: Boolean = false, // Flag indicating if user manually changed cache size
    
    // Network sync settings
    val enableBackgroundSync: Boolean = false,
    val backgroundSyncIntervalHours: Int = 4, // hours (1-24)
    
    val allFiles: Boolean = false, // Show all file types (not just media). When ON: auto-enables all media types.
    val showHiddenFiles: Boolean = false, // Show hidden files and folders (those starting with a dot). Depends on allFiles being ON.
    val showSubfoldersAsItems: Boolean = false, // Show subfolders as separate browsable items instead of flat file list
    
    // Media Files settings
    val supportImages: Boolean = true,
    val imageSizeMin: Long = 1024L, // 1KB
    val imageSizeMax: Long = 10485760L, // 10MB
    val loadFullSizeImages: Boolean = true, // Load full resolution images (for zoom support)
    val cropImagesToFullscreen: Boolean = true, // Crop images to fill screen when orientations match (fullscreen & slideshow)
    val supportGifs: Boolean = true,
    val supportVideos: Boolean = true,
    val videoSizeMin: Long = 1048576L, // 1MB
    val videoSizeMax: Long = 107374182400L, // 100GB
    val supportAudio: Boolean = true,
    val audioSizeMin: Long = 0L, // 0MB
    val audioSizeMax: Long = 1073741824L, // 1GB
    val searchAudioCoversOnline: Boolean = false, // Search for audio covers online (iTunes API) when embedded cover not found
    val searchAudioCoversOnlyOnWifi: Boolean = true, // Search for covers only when connected to Wi-Fi
    val saveAudioMetadataLocally: Boolean = true, // Save downloaded covers and metadata to local cache
    val enablePhotosDuringAudio: Boolean = false, // Enable random photos from resource during audio playback
    val audioBackgroundPhotosResourceId: String? = null, // ID of resource containing photos for audio background
    val enablePersistentAudioPlayback: Boolean = false, // Continue audio when app minimized/screen locked (foreground service)
    val backgroundAudioExitBehavior: BackgroundAudioExitBehavior = BackgroundAudioExitBehavior.ASK, // What to do when leaving player while background audio is active
    val showNowPlayingPanel: Boolean = false, // Show mini now-playing bar when browsing non-audio/video files while background audio is active
    val audioEmptyStateMode: String = "CANVAS_WAVES", // Animation mode when no cover art: NONE, AVD_PULSE, CANVAS_BARS, CANVAS_WAVES, VISUALIZATION (GIF_LOOP=legacy alias)
    
    val supportText: Boolean = true, // Optional support for text files
    val supportPdf: Boolean = true, // Optional support for PDF files
    val supportEpub: Boolean = true, // Optional support for EPUB files
    val showPdfThumbnails: Boolean = false, // "Large PDF Thumbnails" - increases size limit for network PDF thumbnails
    val textSizeMax: Long = 104857600L, // 100MB max for internal text viewer
    val showTextLineNumbers: Boolean = false, // Show line numbers for text files
    val textReaderTheme: String = "SYSTEM", // Reader theme: LIGHT, DARK, SEPIA (SYSTEM follows device dark-mode)
    val markdownRendered: Boolean = true, // Render Markdown (.md) or show raw text
    val syntaxHighlighting: Boolean = true, // Enable syntax highlighting for code files
    val pdfScrollMode: Boolean = false, // PDF vertical scroll mode (true) vs page mode (false)
    val pdfColorMode: String = "NORMAL", // PDF color mode: NORMAL, NIGHT, SEPIA
    val epubLineHeight: Float = 1.6f, // EPUB line height multiplier (1.0 - 3.0)
    val epubHorizontalMargin: Int = 16, // EPUB horizontal margin in px (0 - 48)
    
    // Translation settings (always available, works with Images/PDF/TXT)
    val enableTranslation: Boolean = true, // Enable translation feature using ML Kit OCR + Translate
    val translationSourceLanguage: String = "auto", // Source language code (auto = auto-detect, en, ru, uk, etc.)
    val translationTargetLanguage: String = "ru", // Target language code (en, ru, uk, etc.)
    val translationLensStyle: Boolean = true, // Google Lens style - draw translated text blocks over original positions (for images and PDFs)
    val enableGoogleLens: Boolean = false, // Enable sending to Google Lens app
    val enableOcr: Boolean = true, // Enable OCR text recognition (extract text from images/PDF for copying)
    val ocrDefaultFontSize: String = "AUTO", // Default font size for OCR results (AUTO, MINIMUM, SMALL, MEDIUM, LARGE, HUGE)
    val ocrDefaultFontFamily: String = "DEFAULT", // Default font family for OCR results (DEFAULT, SERIF, MONOSPACE)
    
    // Playback and Sorting settings
    val defaultSortMode: SortMode = SortMode.NAME_ASC,
    val slideshowInterval: Int = 10, // seconds (default 10, range 1-3600)
    val slideshowMusicUri: String? = null, // URI of background music file for slideshow (legacy - unused)
    val enableSlideshowBackgroundMusic: Boolean = false, // Enable background music during image/GIF slideshows
    val slideshowMusicResourceId: Long? = null, // ID of resource containing music files for slideshow background
    val playToEndInSlideshow: Boolean = true,
    val allowRename: Boolean = true,
    val allowDelete: Boolean = true,
    val useTrash: Boolean = true, // Move deleted files to trash instead of permanent delete
    val confirmDelete: Boolean = true, // Confirm before deleting files (used by Safe Mode)
    val confirmMove: Boolean = false, // Confirm before moving files (used by Safe Mode)
    val defaultGridMode: Boolean = false,
    val hideGridActionButtons: Boolean = true, // Hide quick action buttons (copy/move/rename/delete) on grid thumbnails
    val hideSystemUiInFullscreen: Boolean = true, // Hide OS system UI (status bar, navigation bar) in fullscreen/slideshow mode
    val defaultIconSize: Int = 48, // dp (must be 32 + 8*N for slider validation)
    val defaultShowCommandPanel: Boolean = true, // Play media with command panel visible by default
    val showDetailedErrors: Boolean = false,
    val showPlayerHintOnFirstRun: Boolean = true, // Show touch zones hint overlay on first PlayerActivity launch
    val alwaysShowTouchZonesOverlay: Boolean = false, // Always show semi-transparent touch zones overlay in fullscreen mode
    val showVideoThumbnails: Boolean = true, // Extract and show first frame for video thumbnails (may be slow for network files)
    val enablePlayerWarmup: Boolean = false, // Optional Browse-side player infrastructure warm-up (no media preload)
    val rendererMigrationEnabled: Boolean = true, // Migration flag for new static image renderer pipeline (enabled as default)
    
    // Safe Mode settings (Phase 2.1) - Master toggle for confirmations
    val enableSafeMode: Boolean = true, // When ON: show confirmDelete/confirmMove dialogs. When OFF: skip confirmations
    
    // Scheduled operations
    val enableScheduledOperations: Boolean = true,

    // Destinations settings
    val enableCopying: Boolean = true,
    val goToNextAfterCopy: Boolean = true,
    val overwriteOnCopy: Boolean = false,
    val enableMoving: Boolean = true,
    val overwriteOnMove: Boolean = false,
    val enableUndo: Boolean = true,
    val maxRecipients: Int = 10, // Maximum number of destination buttons (1-10)
    val enableFavorites: Boolean = true, // Enable "Favorites" feature (enabled by default)
    val disableCameraCapture: Boolean = false,   // Hide camera-capture button in Browse globally
    val skipCameraFilenameDialog: Boolean = false, // Skip rename dialog after capture; use timestamp name

    // Player UI settings
    val copyPanelCollapsed: Boolean = false,
    val movePanelCollapsed: Boolean = false,
    val enablePictureInPicture: Boolean = true,
    
    // Last used resource for quick slideshow
    val lastUsedResourceId: Long = -1L,
    
    // File list caching
    val defaultRememberFileList: Boolean = false,
    
    // Dynamic Background Effect
    val dynamicBackgroundExtension: Boolean = false,

    // Phase 5: Use as primary media player (enables ACTION_VIEW aliases + MediaButtonReceiver)
    val isPrimaryMediaPlayer: Boolean = false,

    // Phase 6: Accept shared media files (enables ACTION_SEND aliases in Share sheet)
    val acceptSharedFiles: Boolean = false,

    // X.11: Background thumbnail pre-generation
    val enableThumbnailPreload: Boolean = false,       // Background thumbnail pre-generation (opt-in, consumes network bandwidth)
    val thumbnailPreloadWifiOnly: Boolean = true,       // Restrict preload to unmetered (Wi-Fi) connections

    // Last selected folder for folder picker persistence (FR-8).
    // Stores the content:// URI string from OpenDocumentTree.
    val lastSelectedLocalFolder: String? = null,

    // Compact elements mode (0.5x scale)
    val useCompactElements: Boolean = false,

    // Video frame snapshot destination (Save Frame command in player).
    // Stores the destination resource ID; null = fallback to Downloads.
    val videoSnapshotResourceId: Long? = null,

    // Video frame snapshot file format: "PNG" (lossless, default) or "JPG" (85% quality, smaller).
    val videoSnapshotFormat: String = "JPG",

    // VR settings (spec §5.7/§8 — visible only when SUPPORT_VR_PLAYER == true)
    val vrAutoDetectFormat: Boolean = true,          // Detect SBS/OU/Mono plus panoramic layouts via metadata & heuristics
    val vrForcedPlatFormat: String = "AUTO",        // Forced flat-family override: AUTO, SBS, OU, MONO
    val vrForcedSphericalFormat: String = "AUTO",   // Forced spherical-family override: AUTO or spherical StereoMode enum name
    val vrRenderingMode: String = "CINEMA",         // Cinema (flat screen in VR) / FULL_SBS / FULL_OU
    val vrRememberFileFormat: Boolean = true,        // Remember manual VR format per file in the local Room override cache
    // Global VR kill-switch: when true, bypasses all 3D/VR classification; all content plays as plain 2D
    val disable3dVr: Boolean = false,

    // Playback resume on next launch: if true, app reopens last played file on cold start
    val resumeOnNextLaunch: Boolean = true,

    // Adaptive pre-cache strategy (spec §5)
    val prefetchCacheMultiplier: PrefetchCacheMultiplier = PrefetchCacheMultiplier.AUTO,
    val streamingCacheCleanupMode: StreamingCacheCleanupMode = StreamingCacheCleanupMode.ASK,
    val streamingCacheTtlDays: Int = 7          // 0 = off, 1, 3, 7, 30
) {
    /**
     * Returns set of MediaTypes that are globally enabled in app settings.
     * Resource-level mediaTypes should be intersected with this set.
     */
    fun getGloballyEnabledMediaTypes(): Set<MediaType> {
        // If 'All Files' mode is ON, return all 7 types regardless of individual settings
        if (allFiles) {
            return MediaType.entries.toSet()
        }
        
        // Otherwise, return individually enabled types
        val types = mutableSetOf<MediaType>()
        if (supportImages) types.add(MediaType.IMAGE)
        if (supportVideos) types.add(MediaType.VIDEO)
        if (supportAudio) types.add(MediaType.AUDIO)
        if (supportGifs) types.add(MediaType.GIF)
        if (supportText) types.add(MediaType.TEXT)
        if (supportPdf) types.add(MediaType.PDF)
        if (supportEpub) types.add(MediaType.EPUB)
        return types
    }
}
