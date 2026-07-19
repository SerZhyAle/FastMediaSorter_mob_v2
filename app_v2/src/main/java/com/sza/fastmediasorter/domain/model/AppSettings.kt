package com.sza.fastmediasorter.domain.model

/**
 * Application settings model
 * Based on V2 Specification: Settings Screen
 *
 * Device-profile presets (S0327): every preset-able field here has a row in
 * `app_v2/src/main/assets/device_profile_presets.csv`. When you ADD a field, add a matching CSV row
 * (run `scripts/check_device_profile_presets.ps1` to verify, or `-AddMissing` to scaffold an empty
 * row) and, if it should be applied, a `when` case in `DeviceProfilePresetApplier`. State/credential
 * fields (e.g. defaultUser, lastUsedResourceId) intentionally have empty CSV rows and are never
 * applied. See `dev/DEVICE_PROFILE_PRESET_MATRIX.md`.
 */
data class AppSettings(
    // UI State settings (Persisted view modes)
    val isResourceGridMode: Boolean = false, // Resource list view mode (List/Grid)
    val resourceOpsInOverflowMenu: Boolean = true, // S0160: collapse resource action buttons into ⋮ overflow menu

    // S0328: app color theme override. AUTO = follow device night-mode (default, no behavior change),
    // LIGHT = force light, DARK = force dark. Applied at process start via AppCompatDelegate.
    val colorTheme: String = "AUTO",

    // General settings
    val language: String = "en",
    val preventSleep: Boolean = true,
    // S0438: dependent player-scoped keep-screen-on. Effective only when preventSleep is off;
    // when preventSleep is on, this is logically treated as on and hidden in the settings UI.
    val keepScreenOnPlayer: Boolean = true,
    val showSmallControls: Boolean = false,
    val enableCalculator: Boolean = false,
    val embeddedGameEnabled: Boolean = false,
    // S0755: mirror the programs "three-dots" menu as a horizontal panel on the main window. Default
    // OFF (no behaviour change on upgrade); when ON the top three-dots button is hidden (panel replaces it).
    val showProgramsPanelInMainWindow: Boolean = false,
    val defaultUser: String = "",
    val defaultPassword: String = "",
    val networkParallelism: Int = 4, // Parallel threads for network operations (1, 2, 4, 8, 12, 24)
    val cacheSizeMb: Int = 2048, // Glide disk cache size in MB (512, 1024, 2048, 4096, 8192, 16384) - Default: 2GB after installation
    val isCacheSizeUserModified: Boolean = false, // Flag indicating if user manually changed cache size
    
    // Network sync settings
    val enableBackgroundSync: Boolean = false,
    val backgroundSyncIntervalHours: Int = 4, // hours (1-24)

    // S0391: per-source runtime availability toggles. Default true preserves current behavior on
    // upgrade; a disabled source is gated everywhere via RemoteSourceAvailabilityGate (compile-time
    // support AND user toggle), never deleting already-added resources.
    val smbEnabled: Boolean = true,
    val sftpEnabled: Boolean = true,
    val ftpEnabled: Boolean = true,
    val googleDriveEnabled: Boolean = true,
    val oneDriveEnabled: Boolean = true,
    val dropboxEnabled: Boolean = true,

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
    val supportOfficeDocuments: Boolean = true, // Optional support for Office document files
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

    // S0575: Streams feature master switch (internet stream sources). Default OFF; the per-device-profile preset raises it for streaming-oriented devices.
    val enableStreams: Boolean = false,
    // S0659: Streams default profile - seeds the list screen on first open / after a cleared session; remembered session state overrides these on later opens.
    val streamsDefaultSort: StreamDefaultSort = StreamDefaultSort.NAME,
    val streamsDefaultMediaFilter: StreamMediaTypeFilter = StreamMediaTypeFilter.ALL,
    val streamsCatalogRefreshPolicy: StreamsCatalogRefreshPolicy = StreamsCatalogRefreshPolicy.ON_OPEN,
    // S0756: show pinned stream channels as a horizontal panel on the main window (entry button + pinned
    // channels in pin order). Default OFF; effective only when [enableStreams] is on and the flavor ships Streams.
    val showStreamsPanelInMainWindow: Boolean = false,

    // Translation settings (always available, works with Images/PDF/TXT)
    val enableTranslation: Boolean = false, // S0386: default OFF - translation engine delivered on demand
    val translationSourceLanguage: String = "auto", // Source language code (auto = auto-detect, en, ru, uk, etc.)
    val translationTargetLanguage: String = "ru", // Target language code (en, ru, uk, etc.)
    val translationLensStyle: Boolean = true, // Google Lens style - draw translated text blocks over original positions (for images and PDFs)
    // S0452: share-command visibility overrides. Empty = every target follows its registry default
    // rule. A target id in enabledShareTargets is explicitly ON; in disabledShareTargets explicitly
    // OFF. Two sets are needed to persist turning OFF a default-ON target (and vice versa).
    val enabledShareTargets: Set<String> = emptySet(),
    val disabledShareTargets: Set<String> = emptySet(),
    val enableOcr: Boolean = false, // S0386: default OFF - OCR engines delivered on demand
    val cameraOcrTranslationEnabled: Boolean = false, // Opt-in quick Photo-OCR-Translation flow
    val cameraOcrOnly: Boolean = false, // Under camera-ocr-translation: only perform OCR, no translation
    val ocrDefaultFontSize: String = "AUTO", // Default font size for OCR results (AUTO, MINIMUM, SMALL, MEDIUM, LARGE, HUGE)
    val ocrDefaultFontFamily: String = "DEFAULT", // Default font family for OCR results (DEFAULT, SERIF, MONOSPACE)
    val ocrEngineType: String = "TESSERACT",
    val paddleOcrModel: String = "CYRILLIC",
    
    // Playback and Sorting settings
    val defaultSortMode: SortMode = SortMode.NAME_ASC,
    val slideshowInterval: Int = 10, // seconds (default 10, range 1-3600)
    val slideshowMusicUri: String? = null, // URI of background music file for slideshow (legacy - unused)
    val enableSlideshowBackgroundMusic: Boolean = false, // Enable background music during image/GIF slideshows
    val slideshowMusicResourceId: Long? = null, // ID of resource containing music files for slideshow background
    val playToEndInSlideshow: Boolean = true,
    val allowRename: Boolean = true,
    val allowDelete: Boolean = true,
    val useTrash: Boolean = false, // Move deleted files to trash instead of permanent delete
    val confirmDelete: Boolean = true, // Confirm before deleting files (used by Safe Mode)
    val confirmMove: Boolean = false, // Confirm before moving files (used by Safe Mode)
    val defaultGridMode: Boolean = false,
    val hideGridActionButtons: Boolean = true, // Hide quick action buttons (copy/move/rename/delete) on grid thumbnails
    val fileOpsInOverflowMenu: Boolean = true, // Collapse file op buttons into a single ⋮ overflow menu per row
    val fileOpsOverflowMenuHintShown: Boolean = false, // True after the one-time "ops moved to menu" Toast was shown
    val hideSystemUiInFullscreen: Boolean = true, // Hide OS system UI (status bar, navigation bar) in fullscreen/slideshow mode
    val defaultIconSize: Int = 96, // dp (must be 32 + 8*N for slider validation)
    val defaultShowCommandPanel: Boolean = true, // Play media with command panel visible by default
    // S0820: video files opened from Browse enter fullscreen immediately when this is on;
    // per-resource showCommandPanel override still wins.
    val openVideoInFullscreen: Boolean = true,
    val showDetailedErrors: Boolean = false,
    val showPlayerHintOnFirstRun: Boolean = true, // Show touch zones hint overlay on first PlayerActivity launch
    val alwaysShowTouchZonesOverlay: Boolean = false, // Always show semi-transparent touch zones overlay in fullscreen mode
    val nineZoneGridEnabled: Boolean = true, // When false, the fullscreen player uses the simpler 3-zone tap layout instead of the 9-zone grid (S0620)
    val showVideoThumbnails: Boolean = true, // Extract and show first frame for video thumbnails (may be slow for network files)
    val enablePlayerWarmup: Boolean = false, // Optional Browse-side player infrastructure warm-up (no media preload)
    val rendererMigrationEnabled: Boolean = false, // Migration boundary flag for the static image renderer pipeline
    
    // Safe Mode settings (Phase 2.1) - Master toggle for confirmations
    val enableSafeMode: Boolean = true, // When ON: show confirmDelete/confirmMove dialogs. When OFF: skip confirmations
    
    // Scheduled operations
    val enableScheduledOperations: Boolean = true,
    val scheduledOperationsPaused: Boolean = false, // S0353: durable Pause All state for scheduled operations

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
    val cameraCaptureOpenForEditing: Boolean = false, // Open the captured photo in the drawing editor after saving
    val cameraCaptureCopyToClipboard: Boolean = false, // Also place a captured photo on the system clipboard (S0469)
    val cameraGeotagEnabled: Boolean = false, // S0766: opt-in GPS geotag of in-app camera photos (default off)
    // S1066: remembered in-app camera aspect ratio (CameraX AspectRatio: 0 = 4:3 default, 1 = 16:9).
    val cameraAspectRatio: Int = 0,
    // S0371: video recording to resource. disableVideoCapture mirrors disableCameraCapture's inverted
    // persistence (master toggle stored as a negative flag); videoCaptureOpenInPlayer is opt-in
    // (default OFF) - after a recording is saved it optionally opens in the player, never the editor.
    val disableVideoCapture: Boolean = false,    // Hide video-capture command in Browse globally
    val videoCaptureOpenInPlayer: Boolean = false, // Open the recorded video in the player after saving
    // S0375: default destination for video recordings when the current resource is not a usable
    // target; null = fallback to the public Movies folder.
    val videoRecordingDestinationResourceId: String? = null,
    val micRecordingEnabled: Boolean = false,      // S0100: Show mic record button in Browse
    val micRecordingAskFilename: Boolean = true,   // S0100: Show rename dialog before saving recording
    // S0367: default destination for microphone recordings; null = fallback to public Downloads.
    // Resolved by CaptureDestinationPolicy.resolveMicDestination.
    val micRecordingDestinationResourceId: String? = null,
    // S0367: default destination for camera photos; null = fallback to device camera folder (DCIM/Camera).
    // Resolved by CaptureDestinationPolicy.resolveCameraDestination.
    val cameraPhotosDestinationResourceId: String? = null,
    val gestureOverlayEnabled: Boolean = false,
    // S0847: four independently-toggleable edge bands (2 left, 2 right at 10-40% / 60-90% of safe height),
    // each with the DOWN/RIGHT/UP triple - up to 12 gestures. LEFT_TOP enabled by default and seeded from
    // the pre-S0847 single-strip bindings; the other three bands are opt-in (disabled, DO_NOT_USE).
    val screenshotGestureZoneLeftTopEnabled: Boolean = true,
    val screenshotGestureZoneLeftBottomEnabled: Boolean = false,
    val screenshotGestureZoneRightTopEnabled: Boolean = false,
    val screenshotGestureZoneRightBottomEnabled: Boolean = false,
    // S1008: per-zone visibility of the semi-transparent grey guide on the first 4 px of the band's edge
    // (S0724 generalised from the single left strip to all four bands). Effective only while the band is
    // enabled and the gesture overlay is on. LEFT_TOP migrates the pre-S1008 single strip-visible flag.
    val screenshotGestureZoneLeftTopStripVisible: Boolean = false,
    val screenshotGestureZoneLeftBottomStripVisible: Boolean = false,
    val screenshotGestureZoneRightTopStripVisible: Boolean = false,
    val screenshotGestureZoneRightBottomStripVisible: Boolean = false,
    val screenshotGestureLeftTopDown: ScreenshotGestureAction = ScreenshotGestureAction.SILENT_SCREENSHOT,
    val screenshotGestureLeftTopRight: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val screenshotGestureLeftTopUp: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val screenshotGestureLeftBottomDown: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val screenshotGestureLeftBottomRight: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val screenshotGestureLeftBottomUp: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val screenshotGestureRightTopDown: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val screenshotGestureRightTopRight: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val screenshotGestureRightTopUp: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val screenshotGestureRightBottomDown: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val screenshotGestureRightBottomRight: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    val screenshotGestureRightBottomUp: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
    // S1038: generic per-slot string payload, one per zone x direction slot (mirrors the 12 action
    // fields above). Value-agnostic and shared per ADR-3: it holds the target URL for the S1038
    // "open URL" action and the app package for the S1036 app-selection action. Empty string = unset.
    val screenshotGesturePayloadLeftTopDown: String = "",
    val screenshotGesturePayloadLeftTopRight: String = "",
    val screenshotGesturePayloadLeftTopUp: String = "",
    val screenshotGesturePayloadLeftBottomDown: String = "",
    val screenshotGesturePayloadLeftBottomRight: String = "",
    val screenshotGesturePayloadLeftBottomUp: String = "",
    val screenshotGesturePayloadRightTopDown: String = "",
    val screenshotGesturePayloadRightTopRight: String = "",
    val screenshotGesturePayloadRightTopUp: String = "",
    val screenshotGesturePayloadRightBottomDown: String = "",
    val screenshotGesturePayloadRightBottomRight: String = "",
    val screenshotGesturePayloadRightBottomUp: String = "",
    val screenshotDestinationResourceId: String? = null,
    // S0468: also place each gesture screenshot on the system clipboard, ready to paste elsewhere.
    val copyScreenshotToClipboard: Boolean = false,
    val screenCaptureDisclosureAccepted: Boolean = false,
    // S0774: screen video recording scenario (programs block). Direct toggle (like micRecordingEnabled).
    val screenRecordingEnabled: Boolean = false,
    // S0774: default destination for screen recordings; null = fallback to public Downloads.
    // Resolved by CaptureDestinationPolicy.resolveScreenRecordingDestination.
    val screenRecordingDestinationResourceId: String? = null,
    // S0774: user accepted the continuous-recording disclosure (separate from the one-shot screenshot one).
    val screenRecordingDisclosureAccepted: Boolean = false,

    // Player UI settings
    val copyPanelCollapsed: Boolean = false,
    val movePanelCollapsed: Boolean = false,
    // S0781: main-window resource-type filter strip collapsed state (mirror of copy/movePanelCollapsed).
    val resourceTypeTabCollapsed: Boolean = false,
    // S0807: main-window programs panel collapsed into its labelled strip (mirror of resourceTypeTabCollapsed).
    val programsPanelCollapsed: Boolean = false,
    // S0808: main-window streams panel collapsed into its labelled strip (mirror of programsPanelCollapsed).
    val streamsPanelCollapsed: Boolean = false,
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
    // S0133: default ON - fresh installs register in OS share-sheet on first app launch (via bootstrap).
    val acceptSharedFiles: Boolean = true,

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

    // S0470: also place each extracted video frame on the system clipboard (default off - no upgrade behaviour change).
    val videoFrameCopyToClipboard: Boolean = false,

    // Link auto-download (S0003): when an incoming Share-sheet payload is a plain http(s) URL
    // and `linkAutoDownloadEnabled` is on, the receiver tries to fetch the referenced file
    // (direct media or HTML-embedded media candidate) into the resource referenced by
    // `linkAutoDownloadResourceId` (or Downloads when null/unavailable).
    // `linkAutoDownloadOpenInPlayer` controls whether the resulting file opens automatically.
    // S0981: default OFF - owner call after device-test surfaced that a background-triggered
    // player launch can fail to visually snap to foreground; opt-in avoids surprising the user
    // with a launch they did not just initiate. Pre-existing installs are force-reset once by
    // S0981OpenInPlayerDefaultOff (data/migration) since this default is fully persisted on save.
    val linkAutoDownloadEnabled: Boolean = true,
    val linkAutoDownloadResourceId: Long? = null,
    val linkAutoDownloadOpenInPlayer: Boolean = false,

    // S0116 §5.1 pillar J: quality preference applied to streaming variant selection
    // and direct-file candidates with declared resolution.
    // `linkDownloadMaxResolution` allowed values: "480p", "720p", "1080p", "best".
    // `linkDownloadAudioOnly` requests audio-only rendition when the manifest exposes one.
    val linkDownloadMaxResolution: String = "1080p",
    val linkDownloadAudioOnly: Boolean = false,
    val linkDownloadLoginWallHeuristicEnabled: Boolean = true,

    // VR settings (spec §5.7/§8 - visible only when SUPPORT_VR_PLAYER == true).
    val vrRenderingMode: String = "CINEMA",         // Cinema (flat screen in VR) / FULL_SBS / FULL_OU
    val vrAutoImmersive: Boolean = true,             // Auto-enter immersive on stereo content; off → stay in Cinema/2D, manual entry only
    val vrPlayerEntryPromptDismissed: Boolean = false, // One-time player prompt for XR-capable devices with master toggle OFF
    // Global VR kill-switch: when true, bypasses all 3D/VR classification; all content plays as plain 2D
    val disable3dVr: Boolean = false,
    // S0264: panel-mode crop of SBS/OU stereo content to a single eye; default ON for every flavor.
    // Immersive VR rendering overrides this flag at runtime via VideoPlayerManager.vrImmersiveActive,
    // so a true default is safe on VR builds (no double crop while immersive).
    val panelStereoSingleEye: Boolean = true,
    val vrShowFps: Boolean = false,                  // Display diagnostic FPS counter in immersive HUD
    val playerShowFps: Boolean = false,              // S0021: Display diagnostic FPS counter over the flat (non-immersive) player

    // S0326: Global 3D/VR default settings (Settings → Media → 3D/VR).
    // Detection-source flags below are flavor-independent (flat stereo exists on every flavor);
    // they configure StereoDetector and feed the global-default fallback slot in
    // PlayerStereoModeCoordinator. Per-file override and a positive detection always win over these.
    val stereoAutoDetectEnabled: Boolean = true,     // Master switch for automatic 3D/VR format recognition
    val stereoTrustFilename: Boolean = true,         // Trust filename markers (_SBS, _TB, 180, 360, ..)
    val stereoTrustMetadata: Boolean = true,         // Trust embedded metadata (MP4 st3d/sv3d, Matroska StereoMode, GPano/PhotoSphere XMP)
    val stereoTrustAspectRatio: Boolean = false,     // Aspect-ratio heuristic - off by default (the false-positive source)
    val stereoAmbiguityBestGuess: Boolean = false,   // On ambiguity: false → open as 2D, true → apply best guess
    // Global default applied by the coordinator ONLY when detection returned UNKNOWN; MONO == plain 2D.
    val stereoDefaultLayout: StereoMode = StereoMode.MONO,      // Flat-stereo default: MONO / SBS_FULL / OU
    val stereoDefaultProjection: StereoMode = StereoMode.MONO,  // Projection default: MONO (flat) / EQUIRECT_180_MONO / EQUIRECT_360_MONO / CYLINDER_180

    // Playback resume on next launch: if true, app reopens last played file on cold start
    val resumeOnNextLaunch: Boolean = true,

    // Adaptive pre-cache strategy (spec §5)
    val prefetchCacheMultiplier: PrefetchCacheMultiplier = PrefetchCacheMultiplier.AUTO,
    val streamingCacheCleanupMode: StreamingCacheCleanupMode = StreamingCacheCleanupMode.ASK,
    val streamingCacheTtlDays: Int = 7,          // 0 = off, 1, 3, 7, 30

    // S0050: Black Screen mode - show/hide the black-screen toolbar button in audio/video players
    val showBlackScreenButton: Boolean = false,

    // S0028: Multi-window mode - allow opening Browse/Player in a separate window
    val allowSeparateWindow: Boolean = false,

    // S0162 / S0439: Screen rotation control
    // Program-scope follow-OS flag. true = every app window EXCEPT the player follows OS auto-rotate.
    val programFollowSystemRotation: Boolean = true,
    // Player-scope follow-OS flag, independent of the program-scope flag: governs the player family only.
    val playerFollowSystemRotation: Boolean = false,
    // Per-session sensor state (persisted; restored on next player launch).
    // Active only when the player is not following the OS.
    // true = screen follows physical rotation; false = screen locked to current orientation
    val playerRotationSensorEnabled: Boolean = true,

    // S0656: local usage statistics, ON by default. Data is collected only on-device and is
    // anonymous; nothing is transmitted without an explicit user action (S0473 introduced the flag
    // OFF, S0656 flips the default ON now that the privacy concern is resolved). Gates the detailed
    // StatsSink write path; the always-on baseline launch record is independent of this flag. Never
    // applied by a device profile (empty CSV row), so a profile cannot silently change collection.
    val enableStatistics: Boolean = true,

    // S1045: secure by default - block screenshots/Recents preview on screens exposing credentials.
    // User-disableable so power users on trusted devices can screen-record credential screens.
    val secureSensitiveScreens: Boolean = true,

    // S0404: launcher-mode desktop tuning. Grid geometry is computed from the screen; this factor is
    // the user's manual nudge on top of it (higher factor = smaller cells = more columns), needed
    // because head units and TV boxes report unreliable densities. Desktop content itself lives in
    // Room, not here - a device profile seeds it once and never re-applies (ADR-4).
    val launcherDensityFactor: Float = 1.0f,
    val launcherTaskbarShowRecents: Boolean = true,
    val launcherTaskbarShowPinned: Boolean = true,
    val launcherTaskbarShowTray: Boolean = true,
    val launcherReplaceSystemStatusArea: Boolean = false,
    // S0404: one-shot - true once the first-rotation hint has been shown, so it never repeats. No UI row
    // (invisible to the settings-doc gate); it is a remembered event, not a user-facing toggle.
    val launcherRotationHintShown: Boolean = false
) {
    companion object {
        /** S0404: selectable launcher grid densities (see [launcherDensityFactor]). */
        val LAUNCHER_DENSITY_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f)
    }

    /**
     * Returns set of MediaTypes that are globally enabled in app settings.
     * Resource-level mediaTypes should be intersected with this set.
     */
    fun getGloballyEnabledMediaTypes(): Set<MediaType> {
        // If 'All Files' mode is ON, return all known types regardless of individual settings
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
        if (supportOfficeDocuments) types.add(MediaType.OFFICE_DOCUMENT)
        return types
    }

    /** S0847: whether the given edge band is enabled for gesture detection. */
    fun screenshotGestureZoneEnabled(zone: ScreenshotGestureZone): Boolean = when (zone) {
        ScreenshotGestureZone.LEFT_TOP -> screenshotGestureZoneLeftTopEnabled
        ScreenshotGestureZone.LEFT_BOTTOM -> screenshotGestureZoneLeftBottomEnabled
        ScreenshotGestureZone.RIGHT_TOP -> screenshotGestureZoneRightTopEnabled
        ScreenshotGestureZone.RIGHT_BOTTOM -> screenshotGestureZoneRightBottomEnabled
    }

    /** S1008: whether the given edge band shows the grey strip guide (visible only while the band is enabled). */
    fun screenshotGestureZoneStripVisible(zone: ScreenshotGestureZone): Boolean = when (zone) {
        ScreenshotGestureZone.LEFT_TOP -> screenshotGestureZoneLeftTopStripVisible
        ScreenshotGestureZone.LEFT_BOTTOM -> screenshotGestureZoneLeftBottomStripVisible
        ScreenshotGestureZone.RIGHT_TOP -> screenshotGestureZoneRightTopStripVisible
        ScreenshotGestureZone.RIGHT_BOTTOM -> screenshotGestureZoneRightBottomStripVisible
    }

    /** S0847: resolves the action bound to a specific edge band + drag direction (one of 12 slots). */
    fun screenshotGestureAction(
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection
    ): ScreenshotGestureAction = when (zone) {
        ScreenshotGestureZone.LEFT_TOP -> when (direction) {
            ScreenshotGestureDirection.DOWN -> screenshotGestureLeftTopDown
            ScreenshotGestureDirection.RIGHT -> screenshotGestureLeftTopRight
            ScreenshotGestureDirection.UP -> screenshotGestureLeftTopUp
        }
        ScreenshotGestureZone.LEFT_BOTTOM -> when (direction) {
            ScreenshotGestureDirection.DOWN -> screenshotGestureLeftBottomDown
            ScreenshotGestureDirection.RIGHT -> screenshotGestureLeftBottomRight
            ScreenshotGestureDirection.UP -> screenshotGestureLeftBottomUp
        }
        ScreenshotGestureZone.RIGHT_TOP -> when (direction) {
            ScreenshotGestureDirection.DOWN -> screenshotGestureRightTopDown
            ScreenshotGestureDirection.RIGHT -> screenshotGestureRightTopRight
            ScreenshotGestureDirection.UP -> screenshotGestureRightTopUp
        }
        ScreenshotGestureZone.RIGHT_BOTTOM -> when (direction) {
            ScreenshotGestureDirection.DOWN -> screenshotGestureRightBottomDown
            ScreenshotGestureDirection.RIGHT -> screenshotGestureRightBottomRight
            ScreenshotGestureDirection.UP -> screenshotGestureRightBottomUp
        }
    }

    /**
     * S1038: resolves the generic per-slot payload for a specific edge band + drag direction (one of
     * 12 slots). Value-agnostic and shared with S1036 per ADR-3 - the URL for the "open URL" action
     * here, the app package for the S1036 app-selection action. Empty string means no payload set.
     */
    fun screenshotGesturePayload(
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection
    ): String = when (zone) {
        ScreenshotGestureZone.LEFT_TOP -> when (direction) {
            ScreenshotGestureDirection.DOWN -> screenshotGesturePayloadLeftTopDown
            ScreenshotGestureDirection.RIGHT -> screenshotGesturePayloadLeftTopRight
            ScreenshotGestureDirection.UP -> screenshotGesturePayloadLeftTopUp
        }
        ScreenshotGestureZone.LEFT_BOTTOM -> when (direction) {
            ScreenshotGestureDirection.DOWN -> screenshotGesturePayloadLeftBottomDown
            ScreenshotGestureDirection.RIGHT -> screenshotGesturePayloadLeftBottomRight
            ScreenshotGestureDirection.UP -> screenshotGesturePayloadLeftBottomUp
        }
        ScreenshotGestureZone.RIGHT_TOP -> when (direction) {
            ScreenshotGestureDirection.DOWN -> screenshotGesturePayloadRightTopDown
            ScreenshotGestureDirection.RIGHT -> screenshotGesturePayloadRightTopRight
            ScreenshotGestureDirection.UP -> screenshotGesturePayloadRightTopUp
        }
        ScreenshotGestureZone.RIGHT_BOTTOM -> when (direction) {
            ScreenshotGestureDirection.DOWN -> screenshotGesturePayloadRightBottomDown
            ScreenshotGestureDirection.RIGHT -> screenshotGesturePayloadRightBottomRight
            ScreenshotGestureDirection.UP -> screenshotGesturePayloadRightBottomUp
        }
    }
}
