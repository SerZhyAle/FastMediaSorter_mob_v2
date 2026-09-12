package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Backup payload serialized to/from JSON for Google Drive backup.
 * Version field enables future schema migrations.
 */
data class BackupPayload(
    val version: Int = CURRENT_VERSION,
    val appVersionCode: Long = 0,
    val appVersionName: String? = null,
    val createdAt: String? = null,
    val deviceModel: String? = null,
    val androidVersion: Int = 0,
    // Nullable so that Gson correctly reflects a missing field rather than silently
    // leaving a non-null default that Kotlin cannot distinguish from a real value.
    val settings: BackupSettings? = null,
    val resources: List<BackupResource>? = null,
    val favorites: List<BackupFavorite>? = null,
    val scheduledOperations: List<BackupScheduledOperation>? = null,
    // S0406: secret-bearing sections, nullable so older (v4) backups still deserialize.
    val networkCredentials: List<BackupNetworkCredential>? = null,
    val webAuthSessions: List<BackupWebAuthSession>? = null,
    // S1740: Launcher desktop items (shortcuts, gadgets, sections)
    val launcherCells: List<BackupLauncherCell>? = null
) {
    companion object {
        // S1346: v5->v6 - not a payload-shape change but a trust marker for
        // linkAutoDownloadOpenInPlayer. Pre-v6 backups always persisted `true` (the pre-S0981
        // default), so BackupMapper must not trust that field from a payload below this version.
        const val CURRENT_VERSION = 6
    }
}

/**
 * Subset of AppSettings safe for backup (excludes credentials).
 */
data class BackupSettings(
    val isResourceGridMode: Boolean = false,
    val resourceGridCellSize: String = "MEDIUM",
    val language: String = "en",
    // S0406: global default network login carried for max portability (plaintext per ADR-2).
    val defaultUser: String = "",
    val defaultPassword: String = "",
    val preventSleep: Boolean = true,
    val keepScreenOnPlayer: Boolean = true,
    val showSmallControls: Boolean = false,
    val embeddedGameEnabled: Boolean = false,
    val frontFlashlightEnabled: Boolean = false,
    val frontFlashlightColor: Int = AppSettings.FRONT_FLASHLIGHT_DEFAULT_COLOR,
    val waterFlashlightEnabled: Boolean = false,
    val networkParallelism: Int = 4,
    val cacheSizeMb: Int = 2048,
    val isCacheSizeUserModified: Boolean = false,
    val enableBackgroundSync: Boolean = false,
    val backgroundSyncIntervalHours: Int = 4,
    val smbEnabled: Boolean = true,
    val sftpEnabled: Boolean = true,
    val ftpEnabled: Boolean = true,
    val googleDriveEnabled: Boolean = true,
    val oneDriveEnabled: Boolean = true,
    val dropboxEnabled: Boolean = true,
    val allFiles: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val showSubfoldersAsItems: Boolean = false,
    // Media
    val supportImages: Boolean = true,
    val imageSizeMin: Long = 1024L,
    val imageSizeMax: Long = 10485760L,
    val loadFullSizeImages: Boolean = true,
    val cropImagesToFullscreen: Boolean = false,
    val supportGifs: Boolean = true,
    val supportVideos: Boolean = true,
    val videoSizeMin: Long = AppSettings.DEFAULT_VIDEO_SIZE_MIN,
    val videoSizeMax: Long = 107374182400L,
    val supportAudio: Boolean = true,
    val audioSizeMin: Long = 0L,
    val audioSizeMax: Long = 1073741824L,
    val searchAudioCoversOnline: Boolean = false,
    val searchAudioCoversOnlyOnWifi: Boolean = true,
    val saveAudioMetadataLocally: Boolean = true,
    val enablePhotosDuringAudio: Boolean = false,
    val audioBackgroundPhotosResourceId: String? = null,
    // S2247: mirrors the AppSettings default - a backup written before this field existed must not
    // restore as disabled, since the setting is the user's opt-out rather than an opt-in.
    val enablePersistentAudioPlayback: Boolean = true,
    val audioEmptyStateMode: String = "CANVAS_WAVES",
    val supportText: Boolean = true,
    val supportPdf: Boolean = true,
    val supportEpub: Boolean = true,
    val supportOfficeDocuments: Boolean = true,
    val showPdfThumbnails: Boolean = false,
    val textSizeMax: Long = 104857600L,
    val showTextLineNumbers: Boolean = false,
    val textReaderTheme: String = "SYSTEM",
    val markdownRendered: Boolean = true,
    val syntaxHighlighting: Boolean = true,
    val pdfScrollMode: Boolean = false,
    val pdfColorMode: String = "NORMAL",
    val epubLineHeight: Float = 1.6f,
    val epubHorizontalMargin: Int = 16,
    // Translation & OCR
    // S2631: both engines are delivered on demand and default OFF since S0386; this copy still carried
    // the pre-S0386 `true`, so a backup without the key restored a translation stack a fresh install
    // does not enable.
    val enableTranslation: Boolean = AppSettings.DEFAULTS.enableTranslation,
    val translationSourceLanguage: String = "auto",
    val translationTargetLanguage: String = "ru",
    val translationLensStyle: Boolean = true,
    val enableOcr: Boolean = AppSettings.DEFAULTS.enableOcr,
    val ocrDefaultFontSize: String = "AUTO",
    val ocrDefaultFontFamily: String = "DEFAULT",
    val ocrEngineType: String = "TESSERACT",
    val paddleOcrModel: String = "CYRILLIC",
    // Playback
    val defaultSortMode: String = "NAME_ASC",
    val slideshowInterval: Int = 10,
    val enableSlideshowBackgroundMusic: Boolean = false,
    val playToEndInSlideshow: Boolean = true,
    val allowRename: Boolean = true,
    val allowDelete: Boolean = true,
    // S2631: deleting straight through is the shipped default; restoring a backup must not silently
    // turn the trash folder on for a user whose fresh install would not have it.
    val useTrash: Boolean = AppSettings.DEFAULTS.useTrash,
    val confirmDelete: Boolean = true,
    val confirmMove: Boolean = false,
    val defaultGridMode: Boolean = false,
    val hideGridActionButtons: Boolean = true,
    val fileOpsInOverflowMenu: Boolean = true,
    val fileOpsOverflowMenuHintShown: Boolean = false,
    val hideSystemUiInFullscreen: Boolean = true,
    val defaultIconSize: Int = 96,
    val defaultShowCommandPanel: Boolean = true,
    val openVideoInFullscreen: Boolean = true,
    val showDetailedErrors: Boolean = false,
    val showPlayerHintOnFirstRun: Boolean = true,
    val alwaysShowTouchZonesOverlay: Boolean = false,
    val nineZoneGridEnabled: Boolean = true,
    val showVideoThumbnails: Boolean = true,
    val enablePlayerWarmup: Boolean = false,
    val rendererMigrationEnabled: Boolean = false,
    val enableSafeMode: Boolean = true,
    // Scheduled operations
    val enableScheduledOperations: Boolean = true,
    // Destinations
    val enableCopying: Boolean = true,
    val goToNextAfterCopy: Boolean = true,
    val overwriteOnCopy: Boolean = false,
    val enableMoving: Boolean = true,
    val overwriteOnMove: Boolean = false,
    val enableUndo: Boolean = true,
    val maxRecipients: Int = 10,
    val enableFavorites: Boolean = true,
    val disableCameraCapture: Boolean = false,
    val skipCameraFilenameDialog: Boolean = false,
    val cameraCaptureOpenForEditing: Boolean = false,
    val cameraCaptureCopyToClipboard: Boolean = false,
    // Player UI
    val copyPanelCollapsed: Boolean = false,
    val movePanelCollapsed: Boolean = false,
    val resourceTypeTabCollapsed: Boolean = false,
    // S2631: the copy withheld a feature the constructor ships enabled, so a restore turned
    // picture-in-picture off for a user who had never switched it off.
    val enablePictureInPicture: Boolean = AppSettings.DEFAULTS.enablePictureInPicture,
    // File list caching
    val defaultRememberFileList: Boolean = false,
    // Dynamic background
    val dynamicBackgroundExtension: Boolean = false,
    // X.11: Background thumbnail pre-generation
    val enableThumbnailPreload: Boolean = false,
    val thumbnailPreloadWifiOnly: Boolean = true,
    // Video frame snapshot destination resource ID
    val videoSnapshotResourceId: Long? = null,
    // Video frame snapshot format: "PNG" (default) or "JPG"
    val videoSnapshotFormat: String = "JPG",
    // S0470: copy extracted video frame to clipboard
    val videoFrameCopyToClipboard: Boolean = false,
    // Link auto-download (S0003) - nullable for forward-compat with older backups
    val linkAutoDownloadEnabled: Boolean? = null,
    val linkAutoDownloadResourceId: Long? = null,
    val linkAutoDownloadOpenInPlayer: Boolean? = null,
    // S0116 §5.1 pillar J: streaming/quality preference (nullable for forward-compat).
    val linkDownloadMaxResolution: String? = null,
    val linkDownloadAudioOnly: Boolean? = null,
    val linkDownloadLoginWallHeuristicEnabled: Boolean? = null,
    // VR settings (spec §5.7 / Phase 8)
    val vrRenderingMode: String = "CINEMA",
    // Auto-enter immersive on stereo content; nullable for forward-compat with older backup files
    val vrAutoImmersive: Boolean? = null,
    // Global VR kill-switch (spec §3.0.2); nullable for forward-compat with older backup files
    val disable3dVr: Boolean? = null,
    // S0326: global 3D/VR default settings; nullable for forward-compat with older backup files
    val stereoAutoDetectEnabled: Boolean? = null,
    val stereoTrustFilename: Boolean? = null,
    val stereoTrustMetadata: Boolean? = null,
    val stereoTrustAspectRatio: Boolean? = null,
    val stereoAmbiguityBestGuess: Boolean? = null,
    val stereoDefaultLayout: String? = null,
    val stereoDefaultProjection: String? = null,
    // Deprecated since S0251 - kept only so old JSON backups still deserialize.
    val vrForcedFormat: String? = null,
    // S1740: Launcher settings.
    // S2631: the four fields below read the launcher group's own defaults. Each was frozen at the
    // value in force when S1740 wrote this block, and S2017/S2320 later moved the source of truth
    // without moving the copy.
    val launcherDensityFactor: Float = AppSettings.DEFAULTS.launcher.densityFactor,
    val launcherTaskbarPlacement: String = "BOTTOM",
    val launcherTaskbarShowRecents: Boolean = true,
    val launcherTaskbarShowPinned: Boolean = true,
    val launcherTaskbarShowTray: Boolean = true,
    val launcherReplaceSystemStatusArea: Boolean = AppSettings.DEFAULTS.launcher.replaceSystemStatusArea,
    val launcherTopStatusStripMode: Boolean = false,
    val launcherForeignNotificationsEnabled: Boolean =
        AppSettings.DEFAULTS.launcher.foreignNotificationsEnabled,
    val launcherTrayShowClock: Boolean = AppSettings.DEFAULTS.launcher.trayShowClock,
    val launcherTrayShowBluetooth: Boolean = true,
    val launcherTrayShowTethering: Boolean = true,
    val launcherTrayShowSim1: Boolean = true,
    val launcherTrayShowSim2: Boolean = true,
    val launcherTrayShowNetwork: Boolean = true,
    val launcherTrayShowBattery: Boolean = true,
    val launcherRotationHintShown: Boolean = false,
    val launcherDesktopLocked: Boolean = false,
    // Nullable so a field absent from an older backup restores as "keep current": a non-null Boolean
    // could only say true or false, and neither can mean "the writer had no opinion".
    // S2631 corrected the reason recorded here. It used to read "plain Gson skips Kotlin defaults, so a
    // non-null Boolean here would silently read as false", which is not what happens - every parameter
    // of this class has a default, so Kotlin emits a synthetic no-argument constructor and Gson prefers
    // it over unsafe allocation, which is exactly why the declared defaults above are reachable at all.
    // The wrong reason mattered: it made a diverged default look harmless.
    val launcherDesktopDoubleTapLockEnabled: Boolean? = null,
    val launcherWallpaperMode: String = "BRANDED",
    val launcherWallpaperImagePath: String = "",
    val launcherWallpaperCameraId: String = "",
    // S2736: matches the model default, so a backup written before the field existed restores the
    // current default rather than the one it replaced.
    val allAppsSortOrder: String = "LAUNCH_FREQUENCY",
    val allAppsSortDescending: Boolean = false,
    // S2384: a backup written before this field existed restores to the current default, not to Off.
    val launcherScreenBlackoutTimeoutSeconds: Int = AppSettings.DEFAULT_LAUNCHER_SCREEN_TIMEOUT_SECONDS,
    // S2632: both fields are introduced now, so NO already-written backup file carries them. A non-null
    // default would therefore reset the user's real setting on every restore from an existing file -
    // the same silent loss this ticket fixes, moved one step later. Nullable means "the writer had no
    // opinion", which BackupMapper restores as "keep current".
    val launcherTrayShowSpeed: Boolean? = null,
    val launcherAnimationPalette: String? = null,
    // S2648: the 124 settings this DTO never carried, grouped rather than listed flat. Two reasons, both
    // load-bearing. First the ceiling: a JVM method descriptor holds at most 255 slots, and Kotlin's
    // synthetic all-defaults constructor spends one per parameter (two per non-nullable Long) plus a
    // bitmask int per 32 plus a marker plus `this`; at 284 flat parameters that descriptor reaches roughly
    // 302 slots, which kotlinc emits silently and ART rejects at class verification - every
    // `BackupSettings()` in the process. That exact failure already created ScreenshotGestureSettings
    // (S1470) and LauncherSettings (S2300) in the model. Second the migration: a group absent from an
    // already-written file arrives as null, which restores as "keep current", so no backup written before
    // this block can reset a setting it never carried - which is why CURRENT_VERSION stays 6.
    // A field ADDED to one of these groups later is a different case and is declared nullable on its own,
    // the way S2632's two launcher fields above are.
    val screenshotGesture: ScreenshotGesture? = null,
    val launcherExtra: LauncherExtra? = null,
    val capture: Capture? = null,
    val programs: Programs? = null,
    val streams: Streams? = null,
    val appearance: Appearance? = null,
    val playerExtra: PlayerExtra? = null,
    val integration: Integration? = null
) {

    /**
     * S2648: every field of `ScreenshotGestureSettings` - the one settings group the backup omitted whole,
     * so a user who had configured twelve gestures across four zones restored to none of them.
     *
     * Actions travel as the enum name; a null action or payload means the key was absent and the current
     * value is kept, which also covers Gson writing null into a field declared non-null in Kotlin.
     */
    data class ScreenshotGesture(
        val zoneLeftTopEnabled: Boolean = true,
        val zoneLeftBottomEnabled: Boolean = false,
        val zoneRightTopEnabled: Boolean = false,
        val zoneRightBottomEnabled: Boolean = false,
        val zoneLeftTopStripVisible: Boolean = false,
        val zoneLeftBottomStripVisible: Boolean = false,
        val zoneRightTopStripVisible: Boolean = false,
        val zoneRightBottomStripVisible: Boolean = false,
        val leftTopDown: String? = null,
        val leftTopRight: String? = null,
        val leftTopUp: String? = null,
        val leftBottomDown: String? = null,
        val leftBottomRight: String? = null,
        val leftBottomUp: String? = null,
        val rightTopDown: String? = null,
        val rightTopRight: String? = null,
        val rightTopUp: String? = null,
        val rightBottomDown: String? = null,
        val rightBottomRight: String? = null,
        val rightBottomUp: String? = null,
        val payloadLeftTopDown: String? = null,
        val payloadLeftTopRight: String? = null,
        val payloadLeftTopUp: String? = null,
        val payloadLeftBottomDown: String? = null,
        val payloadLeftBottomRight: String? = null,
        val payloadLeftBottomUp: String? = null,
        val payloadRightTopDown: String? = null,
        val payloadRightTopRight: String? = null,
        val payloadRightTopUp: String? = null,
        val payloadRightBottomDown: String? = null,
        val payloadRightBottomRight: String? = null,
        val payloadRightBottomUp: String? = null
    )

    /**
     * S2648: the `LauncherSettings` fields the backup did not carry.
     *
     * The sixteen swipe slots travel as their `persistedName` token, which both action families already
     * define for DataStore, so the backup reuses the persistence name rather than inventing a second one.
     * The two step-counter fields are deliberately absent: they are a baseline against one device's own
     * sensor, and another device's baseline would render a wrong step count.
     */
    data class LauncherExtra(
        val desktopSwipeUpAction: String? = null,
        val desktopSwipeDownAction: String? = null,
        val desktopSwipeLeftAction: String? = null,
        val desktopSwipeRightAction: String? = null,
        val desktopSwipeUpPayload: String? = null,
        val desktopSwipeDownPayload: String? = null,
        val desktopSwipeLeftPayload: String? = null,
        val desktopSwipeRightPayload: String? = null,
        val allAppsSwipeUpAction: String? = null,
        val allAppsSwipeDownAction: String? = null,
        val allAppsSwipeLeftAction: String? = null,
        val allAppsSwipeRightAction: String? = null,
        val allAppsSwipeUpPayload: String? = null,
        val allAppsSwipeDownPayload: String? = null,
        val allAppsSwipeLeftPayload: String? = null,
        val allAppsSwipeRightPayload: String? = null,
        val screenCount: Int = 2,
        val weatherLastLocation: String? = null,
        val widgetBackdropAlpha: Float = AppSettings.DEFAULT_LAUNCHER_WIDGET_BACKDROP_ALPHA,
        // S2730: nullable for the S2632 reason - all three are introduced now, so no already-written
        // backup file carries them, and a non-null default would reset the user's real tuning on every
        // restore from an existing file. Null means "the writer had no opinion" = keep current.
        val wallpaperIntensity: Float? = null,
        val wallpaperAnimationSpeed: Float? = null,
        val wallpaperParticleDensity: Float? = null,
        // S2730: nullable for the same S2632 reason as the three above - an existing backup file predates
        // the switch, and a non-null default would turn the badge off for a user who had turned it on.
        val showScreenNumber: Boolean? = null
    )

    /**
     * S2648: camera, microphone, video and screen capture.
     *
     * The destination fields carry the raw resource id, matching the three the backup already carries
     * (`audioBackgroundPhotosResourceId`, `videoSnapshotResourceId`, `linkAutoDownloadResourceId`): an id
     * that no longer resolves falls back to the same default folder an absent value would.
     * `cameraLensSettings` is deliberately absent - lens ids belong to one device's hardware.
     */
    data class Capture(
        val cameraAspectRatio: Int = 1,
        val cameraGeotagEnabled: Boolean = false,
        val cameraGridEnabled: Boolean = false,
        val cameraOcrOnly: Boolean = false,
        val cameraOcrTranslationEnabled: Boolean = false,
        val cameraPhotosDestinationResourceId: String? = null,
        val disableVideoCapture: Boolean = false,
        val videoCaptureOpenInPlayer: Boolean = false,
        val videoRecordingDestinationResourceId: String? = null,
        val micRecordingEnabled: Boolean = false,
        val micRecordingAskFilename: Boolean = true,
        val micRecordingDestinationResourceId: String? = null,
        val screenRecordingEnabled: Boolean = false,
        val screenRecordingDestinationResourceId: String? = null,
        val screenshotDestinationResourceId: String? = null,
        val copyScreenshotToClipboard: Boolean = false,
        val gestureOverlayEnabled: Boolean = false
    )

    /**
     * S2648: the programs block - which tools are reachable and how they behave.
     *
     * Neither disclosure-accepted flag is here: a screen-capture consent is given on a device by the person
     * holding it, and a restored "already accepted" would suppress a warning that person never saw.
     */
    data class Programs(
        val enableCalculator: Boolean = false,
        val enableStopwatch: Boolean = false,
        val stopwatchParticipantCount: Int = AppSettings.STOPWATCH_DEFAULT_PARTICIPANTS,
        val stopwatchMusicEnabled: Boolean = false,
        val stopwatchMusicUri: String? = null,
        val stopwatchVolumeKeysControl: Boolean = true,
        val enableNetworkMonitor: Boolean = false,
        val enableTourist: Boolean = false,
        val recordGnssTrack: Boolean = false,
        val enableSystemInfo: Boolean = false,
        val enableWearCompanion: Boolean = false,
        val mirrorEnabled: Boolean = true,
        val mirrorZoomRatio: Float = AppSettings.MIRROR_DEFAULT_ZOOM_RATIO,
        val mirrorHorizontallyFlipped: Boolean = true,
        val mirrorBacklightOn: Boolean = true,
        val showProgramsPanelInMainWindow: Boolean = false,
        val programsPanelCollapsed: Boolean = false,
        val showBlackScreenButton: Boolean = false,
        // S2843: nullable for the S2730 reason - an older backup file carries no key here, and a
        // non-null default would silently turn the shade shortcut's notification back off.
        val flashlightShortcutNotificationEnabled: Boolean? = null
    )

    /** S2648: the streams feature and the streaming cache that serves it. */
    data class Streams(
        val enableStreams: Boolean = false,
        val streamsDefaultSort: String? = null,
        val streamsDefaultMediaFilter: String? = null,
        val streamsCatalogRefreshPolicy: String? = null,
        val streamsDefaultAudioLanguage: String? = null,
        val streamsDefaultSubtitleLanguage: String? = null,
        val streamsSmartBuffering: Boolean = false,
        val streamsPanelCollapsed: Boolean = false,
        val showStreamsPanelInMainWindow: Boolean = false,
        val streamingCacheCleanupMode: String? = null,
        val streamingCacheTtlDays: Int = 7,
        val prefetchCacheMultiplier: String? = null,
        // S2843: nullable for the S2730 reason - a backup file written before this ticket carries no
        // key here, and a non-null default would push the class default over a value the user chose.
        // The broadcast block below is the audio-broadcast session the user tuned; the source device
        // id is deliberately absent, because it addresses one phone (see BackupSettingsCoverageTest).
        val streamsVisualizeAsMusic: Boolean? = null,
        val broadcastStreamTitle: String? = null,
        val broadcastBitRateBps: Int? = null,
        val broadcastPort: Int? = null,
        val broadcastSampleRateHz: Int? = null,
        val broadcastChannelCount: Int? = null,
        val broadcastAutoOpenShare: Boolean? = null
    )

    /** S2648: appearance and the general interaction settings that shape every screen. */
    data class Appearance(
        val colorTheme: String? = null,
        val disableAnimations: Boolean = false,
        val useCompactElements: Boolean = false,
        val resourceOpsInOverflowMenu: Boolean = true,
        val browseSwipeLeftAction: String? = null,
        val browseSwipeRightAction: String? = null,
        val showNowPlayingPanel: Boolean = false,
        val secureSensitiveScreens: Boolean = true,
        val powerSavingTrigger: String? = null,
        // S2727: null means "the payload predates this field" - restore then keeps the value already on
        // the device instead of forcing METRIC over a user who had chosen IMPERIAL.
        val unitSystem: String? = null,
        val allowSeparateWindow: Boolean = false,
        val enableStatistics: Boolean = true,
        val backgroundAudioExitBehavior: String? = null
    )

    /** S2648: player-side settings, including both rotation policies and the two FPS counters. */
    data class PlayerExtra(
        val playerFollowSystemRotation: Boolean = false,
        val programFollowSystemRotation: Boolean = true,
        val playerRotationSensorEnabled: Boolean = true,
        val playerPanelAutoHideSeconds: Int = 10,
        val playerShowFps: Boolean = false,
        val vrShowFps: Boolean = false,
        val vrPlayerEntryPromptDismissed: Boolean = false,
        val panelStereoSingleEye: Boolean = true,
        val resumeOnNextLaunch: Boolean = true,
        val slideshowMusicResourceId: Long? = null
    )

    /**
     * S2648: how the app presents itself to the rest of the system.
     *
     * `acceptSharedFiles` and `isPrimaryMediaPlayer` decide which manifest aliases are enabled. The
     * restore writes only the setting: unlike a settings-screen toggle, which flips the component there
     * and then, a restored value reaches the OS at the next process start, when
     * `DefaultPlayerStateBootstrapper` reconciles component state against DataStore. That reconcile is
     * idempotent and unconditional, so the aliases end up correct without anything extra here.
     */
    data class Integration(
        val acceptSharedFiles: Boolean = true,
        val isPrimaryMediaPlayer: Boolean = false,
        val enabledShareTargets: Set<String>? = null,
        val disabledShareTargets: Set<String>? = null,
        val scheduledOperationsPaused: Boolean = false,
        // S2843: nullable for the S2730 reason - an older backup file carries no key here, and a
        // non-null default would hand the watch's media takeover back to a user who had refused it.
        val suppressWearMediaTakeover: Boolean? = null
    )
}

/**
 * S1740: Serializable launcher desktop cell (shortcut, gadget, section header).
 */
data class BackupLauncherCell(
    val orientation: String = "PORTRAIT",
    val rowIndex: Int = 0,
    val colIndex: Int = 0,
    val spanW: Int = 1,
    val spanH: Int = 1,
    val kind: String = "SHORTCUT",
    val target: String = "",
    val labelOverride: String? = null,
    val addedAt: Long = 0L,
    val origin: String = "USER",
)

/**
 * Serializable resource for backup (excludes id, credentialsId, ephemeral state).
 */
data class BackupResource(
    val name: String = "",
    val path: String = "",
    val type: String = "LOCAL",
    val cloudProvider: String? = null,
    val cloudFolderId: String? = null,
    val accountId: String? = null,
    // S0406: link to the network credential so restored SMB/SFTP resources reuse the password.
    val credentialsId: String? = null,
    val displayMode: String = "LIST",
    val sortMode: String = "NAME_ASC",
    val displayOrder: Int = 0,
    val isDestination: Boolean = false,
    val destinationColor: Int = 0,
    val destinationOrder: Int = -1,
    val isWritable: Boolean = false,
    val isReadOnly: Boolean = false,
    val scanSubdirectories: Boolean = false,
    val disableThumbnails: Boolean = false,
    val allFiles: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val showSubfoldersAsItems: Boolean = false,
    val supportedMediaTypes: List<String> = listOf("IMAGE", "VIDEO", "AUDIO", "GIF"),
    val profile: String = "NONE",
    val accessPin: String? = null,
    val readSpeedMbps: Double? = null,
    val writeSpeedMbps: Double? = null,
    val recommendedThreads: Int? = null,
    val slideshowInterval: Int = 10,
    val rememberFileList: Boolean = false,
    val comment: String? = null,
    val showCommandPanel: Boolean? = null
)

/**
 * Serializable scheduled operation for backup.
 * Resources are identified by path+type so they survive cross-device restore.
 */
data class BackupScheduledOperation(
    val isEnabled: Boolean = true,
    val sourceResourcePath: String = "",
    val sourceResourceType: String = "LOCAL",
    val operationType: String = "COPY",
    val targetResourcePath: String? = null,
    val targetResourceType: String? = null,
    val fileTypeFilter: String? = null,
    val fileTypeMask: Int? = null,
    val timeFilter: String = "ALL",
    val startTimeHour: Int = 0,
    val startTimeMinute: Int = 0,
    val intervalHours: Int = 1,
    val intervalMinutes: Int = 0,
    val overwrite: Boolean = false,
    val silentMode: Boolean = false
)

/**
 * Serializable favorite for backup (uses resource name+path for cross-device resolution).
 */
data class BackupFavorite(
    val uri: String = "",
    val resourceName: String = "",
    val resourcePath: String = "",
    val displayName: String = "",
    val mediaType: Int = 0,
    val size: Long = 0,
    val lastKnownPath: String = "",
    val dateModified: Long = 0,
    val addedTimestamp: Long = 0
)

/**
 * S0406: serializable network credential including the plaintext password and SSH key.
 * Secrets travel in clear text by owner decision (ADR-2) - the backup file lives in the
 * user's private space. Restored via re-encryption through the Keystore-backed CryptoHelper.
 */
data class BackupNetworkCredential(
    val credentialId: String = "",
    val type: String = "SMB",
    val server: String = "",
    val port: Int = 0,
    val username: String = "",
    val domain: String = "",
    val shareName: String? = null,
    val sshPrivateKey: String? = null,
    val accountId: String = "",
    val password: String = ""
)

/**
 * S0406: serializable saved site authorization (cookies) for link downloads.
 * Only active sessions with live cookies are exported; expired cookies are dropped on load.
 */
data class BackupWebAuthSession(
    val host: String = "",
    val accountId: String = "",
    val displayName: String = "",
    val userAgent: String? = null,
    val savedAtEpochMillis: Long = 0,
    val lastUsedAtEpochMillis: Long = 0,
    val cookies: List<BackupCookie> = emptyList()
)

/**
 * S0406: serializable HTTP cookie. `expiresAtEpochMillis` is null for session cookies.
 */
data class BackupCookie(
    val name: String = "",
    val value: String = "",
    val domain: String = "",
    val path: String = "/",
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val expiresAtEpochMillis: Long? = null
)
