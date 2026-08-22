package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.TimeFilter

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
    val videoSizeMin: Long = 1048576L,
    val videoSizeMax: Long = 107374182400L,
    val supportAudio: Boolean = true,
    val audioSizeMin: Long = 0L,
    val audioSizeMax: Long = 1073741824L,
    val searchAudioCoversOnline: Boolean = false,
    val searchAudioCoversOnlyOnWifi: Boolean = true,
    val saveAudioMetadataLocally: Boolean = true,
    val enablePhotosDuringAudio: Boolean = false,
    val audioBackgroundPhotosResourceId: String? = null,
    val enablePersistentAudioPlayback: Boolean = false,
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
    val enableTranslation: Boolean = true,
    val translationSourceLanguage: String = "auto",
    val translationTargetLanguage: String = "ru",
    val translationLensStyle: Boolean = true,
    val enableOcr: Boolean = true,
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
    val useTrash: Boolean = true,
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
    val enablePictureInPicture: Boolean = false,
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
    // S1740: Launcher settings
    val launcherDensityFactor: Float = 1.0f,
    val launcherTaskbarPlacement: String = "BOTTOM",
    val launcherTaskbarShowRecents: Boolean = true,
    val launcherTaskbarShowPinned: Boolean = true,
    val launcherTaskbarShowTray: Boolean = true,
    val launcherReplaceSystemStatusArea: Boolean = false,
    val launcherTopStatusStripMode: Boolean = false,
    val launcherForeignNotificationsEnabled: Boolean = false,
    val launcherTrayShowClock: Boolean = true,
    val launcherTrayShowBluetooth: Boolean = true,
    val launcherTrayShowSim1: Boolean = true,
    val launcherTrayShowSim2: Boolean = true,
    val launcherTrayShowNetwork: Boolean = true,
    val launcherTrayShowBattery: Boolean = true,
    val launcherRotationHintShown: Boolean = false,
    val launcherDesktopLocked: Boolean = false,
    val launcherWallpaperMode: String = "BRANDED",
    val launcherWallpaperImagePath: String = "",
    val allAppsSortOrder: String = "LABEL",
    val allAppsSortDescending: Boolean = false,
    val launcherScreenBlackoutTimeoutSeconds: Int = 0
)

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
    val addedAt: Long = 0L
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
