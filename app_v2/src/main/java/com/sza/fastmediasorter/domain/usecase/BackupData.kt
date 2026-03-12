package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode

/**
 * Backup payload serialized to/from JSON for Google Drive backup.
 * Version field enables future schema migrations.
 */
data class BackupPayload(
    val version: Int = CURRENT_VERSION,
    val appVersionCode: Long = 0,
    val appVersionName: String = "",
    val createdAt: String = "",
    val deviceModel: String = "",
    val androidVersion: Int = 0,
    val settings: BackupSettings = BackupSettings(),
    val resources: List<BackupResource> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Subset of AppSettings safe for backup (excludes credentials).
 */
data class BackupSettings(
    val isResourceGridMode: Boolean = false,
    val language: String = "en",
    val preventSleep: Boolean = true,
    val showSmallControls: Boolean = false,
    val networkParallelism: Int = 4,
    val cacheSizeMb: Int = 2048,
    val isCacheSizeUserModified: Boolean = false,
    val enableBackgroundSync: Boolean = false,
    val backgroundSyncIntervalHours: Int = 4,
    val allFiles: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val showSubfoldersAsItems: Boolean = false,
    // Media
    val supportImages: Boolean = true,
    val imageSizeMin: Long = 1024L,
    val imageSizeMax: Long = 10485760L,
    val loadFullSizeImages: Boolean = true,
    val cropImagesToFullscreen: Boolean = true,
    val supportGifs: Boolean = true,
    val supportVideos: Boolean = true,
    val videoSizeMin: Long = 1048576L,
    val videoSizeMax: Long = 107374182400L,
    val supportAudio: Boolean = true,
    val audioSizeMin: Long = 0L,
    val audioSizeMax: Long = 1073741824L,
    val searchAudioCoversOnline: Boolean = false,
    val searchAudioCoversOnlyOnWifi: Boolean = true,
    val enablePhotosDuringAudio: Boolean = false,
    val audioBackgroundPhotosResourceId: String? = null,
    val enableBackgroundAudio: Boolean = false,
    val audioEmptyStateMode: String = "VISUALIZATION",
    val supportText: Boolean = true,
    val supportPdf: Boolean = true,
    val supportEpub: Boolean = true,
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
    val enableGoogleLens: Boolean = false,
    val enableOcr: Boolean = true,
    val ocrDefaultFontSize: String = "AUTO",
    val ocrDefaultFontFamily: String = "DEFAULT",
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
    val hideSystemUiInFullscreen: Boolean = true,
    val defaultIconSize: Int = 96,
    val defaultShowCommandPanel: Boolean = true,
    val showDetailedErrors: Boolean = false,
    val showPlayerHintOnFirstRun: Boolean = true,
    val alwaysShowTouchZonesOverlay: Boolean = false,
    val showVideoThumbnails: Boolean = true,
    val enablePlayerWarmup: Boolean = false,
    val rendererMigrationEnabled: Boolean = false,
    val enableSafeMode: Boolean = true,
    // Destinations
    val enableCopying: Boolean = true,
    val goToNextAfterCopy: Boolean = true,
    val overwriteOnCopy: Boolean = false,
    val enableMoving: Boolean = true,
    val overwriteOnMove: Boolean = false,
    val enableUndo: Boolean = true,
    val maxRecipients: Int = 10,
    val enableFavorites: Boolean = true,
    // Player UI
    val copyPanelCollapsed: Boolean = false,
    val movePanelCollapsed: Boolean = false,
    val enablePictureInPicture: Boolean = false,
    // File list caching
    val defaultRememberFileList: Boolean = false,
    // Dynamic background
    val dynamicBackgroundExtension: Boolean = false
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
