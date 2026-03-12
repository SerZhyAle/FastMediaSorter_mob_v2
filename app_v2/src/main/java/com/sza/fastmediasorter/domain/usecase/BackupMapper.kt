package com.sza.fastmediasorter.domain.usecase

import android.os.Build
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Converts domain models to backup-safe DTOs and back.
 * Keeps credential fields out of the backup payload.
 */
object BackupMapper {

    fun toBackupPayload(
        settings: AppSettings,
        resources: List<MediaResource>,
        appVersionCode: Long,
        appVersionName: String
    ): BackupPayload {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return BackupPayload(
            version = BackupPayload.CURRENT_VERSION,
            appVersionCode = appVersionCode,
            appVersionName = appVersionName,
            createdAt = isoFormat.format(Date()),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.SDK_INT,
            settings = toBackupSettings(settings),
            resources = resources.map { toBackupResource(it) }
        )
    }

    fun toBackupSettings(settings: AppSettings): BackupSettings {
        return BackupSettings(
            isResourceGridMode = settings.isResourceGridMode,
            language = settings.language,
            preventSleep = settings.preventSleep,
            showSmallControls = settings.showSmallControls,
            networkParallelism = settings.networkParallelism,
            cacheSizeMb = settings.cacheSizeMb,
            isCacheSizeUserModified = settings.isCacheSizeUserModified,
            enableBackgroundSync = settings.enableBackgroundSync,
            backgroundSyncIntervalHours = settings.backgroundSyncIntervalHours,
            allFiles = settings.allFiles,
            showHiddenFiles = settings.showHiddenFiles,
            showSubfoldersAsItems = settings.showSubfoldersAsItems,
            supportImages = settings.supportImages,
            imageSizeMin = settings.imageSizeMin,
            imageSizeMax = settings.imageSizeMax,
            loadFullSizeImages = settings.loadFullSizeImages,
            cropImagesToFullscreen = settings.cropImagesToFullscreen,
            supportGifs = settings.supportGifs,
            supportVideos = settings.supportVideos,
            videoSizeMin = settings.videoSizeMin,
            videoSizeMax = settings.videoSizeMax,
            supportAudio = settings.supportAudio,
            audioSizeMin = settings.audioSizeMin,
            audioSizeMax = settings.audioSizeMax,
            searchAudioCoversOnline = settings.searchAudioCoversOnline,
            searchAudioCoversOnlyOnWifi = settings.searchAudioCoversOnlyOnWifi,
            enablePhotosDuringAudio = settings.enablePhotosDuringAudio,
            audioBackgroundPhotosResourceId = settings.audioBackgroundPhotosResourceId,
            enableBackgroundAudio = settings.enableBackgroundAudio,
            audioEmptyStateMode = settings.audioEmptyStateMode,
            supportText = settings.supportText,
            supportPdf = settings.supportPdf,
            supportEpub = settings.supportEpub,
            showPdfThumbnails = settings.showPdfThumbnails,
            textSizeMax = settings.textSizeMax,
            showTextLineNumbers = settings.showTextLineNumbers,
            textReaderTheme = settings.textReaderTheme,
            markdownRendered = settings.markdownRendered,
            syntaxHighlighting = settings.syntaxHighlighting,
            pdfScrollMode = settings.pdfScrollMode,
            pdfColorMode = settings.pdfColorMode,
            epubLineHeight = settings.epubLineHeight,
            epubHorizontalMargin = settings.epubHorizontalMargin,
            enableTranslation = settings.enableTranslation,
            translationSourceLanguage = settings.translationSourceLanguage,
            translationTargetLanguage = settings.translationTargetLanguage,
            translationLensStyle = settings.translationLensStyle,
            enableGoogleLens = settings.enableGoogleLens,
            enableOcr = settings.enableOcr,
            ocrDefaultFontSize = settings.ocrDefaultFontSize,
            ocrDefaultFontFamily = settings.ocrDefaultFontFamily,
            defaultSortMode = settings.defaultSortMode.name,
            slideshowInterval = settings.slideshowInterval,
            enableSlideshowBackgroundMusic = settings.enableSlideshowBackgroundMusic,
            playToEndInSlideshow = settings.playToEndInSlideshow,
            allowRename = settings.allowRename,
            allowDelete = settings.allowDelete,
            useTrash = settings.useTrash,
            confirmDelete = settings.confirmDelete,
            confirmMove = settings.confirmMove,
            defaultGridMode = settings.defaultGridMode,
            hideGridActionButtons = settings.hideGridActionButtons,
            hideSystemUiInFullscreen = settings.hideSystemUiInFullscreen,
            defaultIconSize = settings.defaultIconSize,
            defaultShowCommandPanel = settings.defaultShowCommandPanel,
            showDetailedErrors = settings.showDetailedErrors,
            showPlayerHintOnFirstRun = settings.showPlayerHintOnFirstRun,
            alwaysShowTouchZonesOverlay = settings.alwaysShowTouchZonesOverlay,
            showVideoThumbnails = settings.showVideoThumbnails,
            enablePlayerWarmup = settings.enablePlayerWarmup,
            rendererMigrationEnabled = settings.rendererMigrationEnabled,
            enableSafeMode = settings.enableSafeMode,
            enableCopying = settings.enableCopying,
            goToNextAfterCopy = settings.goToNextAfterCopy,
            overwriteOnCopy = settings.overwriteOnCopy,
            enableMoving = settings.enableMoving,
            overwriteOnMove = settings.overwriteOnMove,
            enableUndo = settings.enableUndo,
            maxRecipients = settings.maxRecipients,
            enableFavorites = settings.enableFavorites,
            copyPanelCollapsed = settings.copyPanelCollapsed,
            movePanelCollapsed = settings.movePanelCollapsed,
            enablePictureInPicture = settings.enablePictureInPicture,
            defaultRememberFileList = settings.defaultRememberFileList,
            dynamicBackgroundExtension = settings.dynamicBackgroundExtension
        )
    }

    fun toBackupResource(resource: MediaResource): BackupResource {
        return BackupResource(
            name = resource.name,
            path = resource.path,
            type = resource.type.name,
            cloudProvider = resource.cloudProvider?.name,
            cloudFolderId = resource.cloudFolderId,
            accountId = resource.accountId,
            displayMode = resource.displayMode.name,
            sortMode = resource.sortMode.name,
            displayOrder = resource.displayOrder,
            isDestination = resource.isDestination,
            destinationColor = resource.destinationColor,
            destinationOrder = resource.destinationOrder ?: -1,
            isWritable = resource.isWritable,
            isReadOnly = resource.isReadOnly,
            scanSubdirectories = resource.scanSubdirectories,
            disableThumbnails = resource.disableThumbnails,
            allFiles = resource.allFiles,
            showHiddenFiles = resource.showHiddenFiles,
            showSubfoldersAsItems = resource.showSubfoldersAsItems,
            supportedMediaTypes = resource.supportedMediaTypes.map { it.name },
            profile = resource.profile.name,
            accessPin = resource.accessPin,
            readSpeedMbps = resource.readSpeedMbps,
            writeSpeedMbps = resource.writeSpeedMbps,
            recommendedThreads = resource.recommendedThreads,
            slideshowInterval = resource.slideshowInterval,
            rememberFileList = resource.rememberFileList,
            comment = resource.comment,
            showCommandPanel = resource.showCommandPanel
        )
    }

    fun toAppSettings(backup: BackupSettings, current: AppSettings): AppSettings {
        return current.copy(
            isResourceGridMode = backup.isResourceGridMode,
            language = backup.language,
            preventSleep = backup.preventSleep,
            showSmallControls = backup.showSmallControls,
            // Keep credentials from current: defaultUser, defaultPassword
            networkParallelism = backup.networkParallelism,
            cacheSizeMb = backup.cacheSizeMb,
            isCacheSizeUserModified = backup.isCacheSizeUserModified,
            enableBackgroundSync = backup.enableBackgroundSync,
            backgroundSyncIntervalHours = backup.backgroundSyncIntervalHours,
            allFiles = backup.allFiles,
            showHiddenFiles = backup.showHiddenFiles,
            showSubfoldersAsItems = backup.showSubfoldersAsItems,
            supportImages = backup.supportImages,
            imageSizeMin = backup.imageSizeMin,
            imageSizeMax = backup.imageSizeMax,
            loadFullSizeImages = backup.loadFullSizeImages,
            cropImagesToFullscreen = backup.cropImagesToFullscreen,
            supportGifs = backup.supportGifs,
            supportVideos = backup.supportVideos,
            videoSizeMin = backup.videoSizeMin,
            videoSizeMax = backup.videoSizeMax,
            supportAudio = backup.supportAudio,
            audioSizeMin = backup.audioSizeMin,
            audioSizeMax = backup.audioSizeMax,
            searchAudioCoversOnline = backup.searchAudioCoversOnline,
            searchAudioCoversOnlyOnWifi = backup.searchAudioCoversOnlyOnWifi,
            enablePhotosDuringAudio = backup.enablePhotosDuringAudio,
            audioBackgroundPhotosResourceId = backup.audioBackgroundPhotosResourceId,
            enableBackgroundAudio = backup.enableBackgroundAudio,
            audioEmptyStateMode = backup.audioEmptyStateMode,
            supportText = backup.supportText,
            supportPdf = backup.supportPdf,
            supportEpub = backup.supportEpub,
            showPdfThumbnails = backup.showPdfThumbnails,
            textSizeMax = backup.textSizeMax,
            showTextLineNumbers = backup.showTextLineNumbers,
            textReaderTheme = backup.textReaderTheme,
            markdownRendered = backup.markdownRendered,
            syntaxHighlighting = backup.syntaxHighlighting,
            pdfScrollMode = backup.pdfScrollMode,
            pdfColorMode = backup.pdfColorMode,
            epubLineHeight = backup.epubLineHeight,
            epubHorizontalMargin = backup.epubHorizontalMargin,
            enableTranslation = backup.enableTranslation,
            translationSourceLanguage = backup.translationSourceLanguage,
            translationTargetLanguage = backup.translationTargetLanguage,
            translationLensStyle = backup.translationLensStyle,
            enableGoogleLens = backup.enableGoogleLens,
            enableOcr = backup.enableOcr,
            ocrDefaultFontSize = backup.ocrDefaultFontSize,
            ocrDefaultFontFamily = backup.ocrDefaultFontFamily,
            defaultSortMode = safeParseSortMode(backup.defaultSortMode),
            slideshowInterval = backup.slideshowInterval,
            enableSlideshowBackgroundMusic = backup.enableSlideshowBackgroundMusic,
            playToEndInSlideshow = backup.playToEndInSlideshow,
            allowRename = backup.allowRename,
            allowDelete = backup.allowDelete,
            useTrash = backup.useTrash,
            confirmDelete = backup.confirmDelete,
            confirmMove = backup.confirmMove,
            defaultGridMode = backup.defaultGridMode,
            hideGridActionButtons = backup.hideGridActionButtons,
            hideSystemUiInFullscreen = backup.hideSystemUiInFullscreen,
            defaultIconSize = backup.defaultIconSize,
            defaultShowCommandPanel = backup.defaultShowCommandPanel,
            showDetailedErrors = backup.showDetailedErrors,
            showPlayerHintOnFirstRun = backup.showPlayerHintOnFirstRun,
            alwaysShowTouchZonesOverlay = backup.alwaysShowTouchZonesOverlay,
            showVideoThumbnails = backup.showVideoThumbnails,
            enablePlayerWarmup = backup.enablePlayerWarmup,
            rendererMigrationEnabled = backup.rendererMigrationEnabled,
            enableSafeMode = backup.enableSafeMode,
            enableCopying = backup.enableCopying,
            goToNextAfterCopy = backup.goToNextAfterCopy,
            overwriteOnCopy = backup.overwriteOnCopy,
            enableMoving = backup.enableMoving,
            overwriteOnMove = backup.overwriteOnMove,
            enableUndo = backup.enableUndo,
            maxRecipients = backup.maxRecipients,
            enableFavorites = backup.enableFavorites,
            copyPanelCollapsed = backup.copyPanelCollapsed,
            movePanelCollapsed = backup.movePanelCollapsed,
            enablePictureInPicture = backup.enablePictureInPicture,
            defaultRememberFileList = backup.defaultRememberFileList,
            dynamicBackgroundExtension = backup.dynamicBackgroundExtension
        )
    }

    fun toMediaResource(backup: BackupResource): MediaResource {
        return MediaResource(
            name = backup.name,
            path = backup.path,
            type = safeParseResourceType(backup.type),
            cloudProvider = backup.cloudProvider?.let { safeParseCloudProvider(it) },
            cloudFolderId = backup.cloudFolderId,
            accountId = backup.accountId,
            displayMode = safeParseDisplayMode(backup.displayMode),
            sortMode = safeParseSortMode(backup.sortMode),
            displayOrder = backup.displayOrder,
            isDestination = backup.isDestination,
            destinationColor = backup.destinationColor,
            destinationOrder = backup.destinationOrder,
            isWritable = backup.isWritable,
            isReadOnly = backup.isReadOnly,
            isAvailable = false, // Mark as unavailable until verified
            scanSubdirectories = backup.scanSubdirectories,
            disableThumbnails = backup.disableThumbnails,
            allFiles = backup.allFiles,
            showHiddenFiles = backup.showHiddenFiles,
            showSubfoldersAsItems = backup.showSubfoldersAsItems,
            supportedMediaTypes = backup.supportedMediaTypes.mapNotNull { safeParseMediaType(it) }.toSet(),
            profile = safeParseResourceProfile(backup.profile),
            accessPin = backup.accessPin,
            readSpeedMbps = backup.readSpeedMbps,
            writeSpeedMbps = backup.writeSpeedMbps,
            recommendedThreads = backup.recommendedThreads,
            slideshowInterval = backup.slideshowInterval,
            rememberFileList = backup.rememberFileList,
            comment = backup.comment,
            showCommandPanel = backup.showCommandPanel
        )
    }

    // Safe enum parsers — fall back to defaults for forward compatibility
    private fun safeParseSortMode(value: String): com.sza.fastmediasorter.domain.model.SortMode {
        return try { com.sza.fastmediasorter.domain.model.SortMode.valueOf(value) }
        catch (_: Exception) { com.sza.fastmediasorter.domain.model.SortMode.NAME_ASC }
    }
    private fun safeParseResourceType(value: String): com.sza.fastmediasorter.domain.model.ResourceType {
        return try { com.sza.fastmediasorter.domain.model.ResourceType.valueOf(value) }
        catch (_: Exception) { com.sza.fastmediasorter.domain.model.ResourceType.LOCAL }
    }
    private fun safeParseCloudProvider(value: String): com.sza.fastmediasorter.data.cloud.CloudProvider {
        return try { com.sza.fastmediasorter.data.cloud.CloudProvider.valueOf(value) }
        catch (_: Exception) { com.sza.fastmediasorter.data.cloud.CloudProvider.GOOGLE_DRIVE }
    }
    private fun safeParseDisplayMode(value: String): com.sza.fastmediasorter.domain.model.DisplayMode {
        return try { com.sza.fastmediasorter.domain.model.DisplayMode.valueOf(value) }
        catch (_: Exception) { com.sza.fastmediasorter.domain.model.DisplayMode.LIST }
    }
    private fun safeParseMediaType(value: String): MediaType? {
        return try { MediaType.valueOf(value) }
        catch (_: Exception) { null }
    }
    private fun safeParseResourceProfile(value: String): com.sza.fastmediasorter.domain.model.ResourceProfile {
        return try { com.sza.fastmediasorter.domain.model.ResourceProfile.valueOf(value) }
        catch (_: Exception) { com.sza.fastmediasorter.domain.model.ResourceProfile.NONE }
    }
}
