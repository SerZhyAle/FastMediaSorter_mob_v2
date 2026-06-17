package com.sza.fastmediasorter.domain.usecase

import android.os.Build
import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.model.TimeFilter
import com.sza.fastmediasorter.domain.repository.RawAuthSession
import java.net.HttpCookie
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
        favorites: List<BackupFavorite>,
        appVersionCode: Long,
        appVersionName: String,
        scheduledOperations: List<BackupScheduledOperation> = emptyList()
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
            resources = resources.map { toBackupResource(it) },
            favorites = favorites,
            scheduledOperations = scheduledOperations
        )
    }

    fun toBackupScheduledOperation(
        op: ScheduledOperation,
        resourceLookup: Map<Long, MediaResource>
    ): BackupScheduledOperation? {
        val src = resourceLookup[op.sourceResourceId] ?: return null
        val dst = if (op.targetResourceId != null) resourceLookup[op.targetResourceId] else null
        return BackupScheduledOperation(
            isEnabled = op.isEnabled,
            sourceResourcePath = src.path,
            sourceResourceType = src.type.name,
            operationType = op.operationType.name,
            targetResourcePath = dst?.path,
            targetResourceType = dst?.type?.name,
            fileTypeMask = op.fileTypeMask,
            fileTypeFilter = null,
            timeFilter = op.timeFilter.name,
            startTimeHour = op.startTimeHour,
            startTimeMinute = op.startTimeMinute,
            intervalHours = op.intervalHours,
            intervalMinutes = op.intervalMinutes,
            overwrite = op.overwrite,
            silentMode = op.silentMode
        )
    }

    fun toScheduledOperation(
        backup: BackupScheduledOperation,
        resourcesByPath: Map<String, MediaResource>
    ): ScheduledOperation? {
        val srcKey = "${backup.sourceResourcePath}|${backup.sourceResourceType}"
        val src = resourcesByPath.values.firstOrNull {
            it.path == backup.sourceResourcePath && it.type.name == backup.sourceResourceType
        } ?: return null

        val opType = runCatching { ScheduledOpType.valueOf(backup.operationType) }
            .getOrElse { ScheduledOpType.COPY }

        val dst = if (backup.targetResourcePath != null && backup.targetResourceType != null) {
            resourcesByPath.values.firstOrNull {
                it.path == backup.targetResourcePath && it.type.name == backup.targetResourceType
            }
        } else null

        if (opType != ScheduledOpType.DELETE && dst == null) return null

        return ScheduledOperation(
            id = 0,
            isEnabled = backup.isEnabled,
            sourceResourceId = src.id,
            operationType = opType,
            targetResourceId = dst?.id,
            fileTypeMask = backup.fileTypeMask
                ?: FileTypeFlags.fromLegacyName(backup.fileTypeFilter ?: ""),
            timeFilter = runCatching { TimeFilter.valueOf(backup.timeFilter) }
                .getOrElse { TimeFilter.ALL },
            startTimeHour = backup.startTimeHour,
            startTimeMinute = backup.startTimeMinute,
            intervalHours = backup.intervalHours,
            intervalMinutes = backup.intervalMinutes,
            overwrite = backup.overwrite,
            silentMode = backup.silentMode
        )
    }

    fun toBackupSettings(settings: AppSettings): BackupSettings {
        return BackupSettings(
            isResourceGridMode = settings.isResourceGridMode,
            language = settings.language,
            defaultUser = settings.defaultUser,
            defaultPassword = settings.defaultPassword,
            preventSleep = settings.preventSleep,
            keepScreenOnPlayer = settings.keepScreenOnPlayer,
            showSmallControls = settings.showSmallControls,
            embeddedGameEnabled = settings.embeddedGameEnabled,
            networkParallelism = settings.networkParallelism,
            cacheSizeMb = settings.cacheSizeMb,
            isCacheSizeUserModified = settings.isCacheSizeUserModified,
            enableBackgroundSync = settings.enableBackgroundSync,
            backgroundSyncIntervalHours = settings.backgroundSyncIntervalHours,
            smbEnabled = settings.smbEnabled,
            sftpEnabled = settings.sftpEnabled,
            ftpEnabled = settings.ftpEnabled,
            googleDriveEnabled = settings.googleDriveEnabled,
            oneDriveEnabled = settings.oneDriveEnabled,
            dropboxEnabled = settings.dropboxEnabled,
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
            saveAudioMetadataLocally = settings.saveAudioMetadataLocally,
            enablePhotosDuringAudio = settings.enablePhotosDuringAudio,
            audioBackgroundPhotosResourceId = settings.audioBackgroundPhotosResourceId,
            enablePersistentAudioPlayback = settings.enablePersistentAudioPlayback,
            audioEmptyStateMode = settings.audioEmptyStateMode,
            supportText = settings.supportText,
            supportPdf = settings.supportPdf,
            supportEpub = settings.supportEpub,
            supportOfficeDocuments = settings.supportOfficeDocuments,
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
            enableOcr = settings.enableOcr,
            ocrDefaultFontSize = settings.ocrDefaultFontSize,
            ocrDefaultFontFamily = settings.ocrDefaultFontFamily,
            ocrEngineType = settings.ocrEngineType,
            paddleOcrModel = settings.paddleOcrModel,
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
            fileOpsInOverflowMenu = settings.fileOpsInOverflowMenu,
            fileOpsOverflowMenuHintShown = settings.fileOpsOverflowMenuHintShown,
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
            enableScheduledOperations = settings.enableScheduledOperations,
            enableCopying = settings.enableCopying,
            goToNextAfterCopy = settings.goToNextAfterCopy,
            overwriteOnCopy = settings.overwriteOnCopy,
            enableMoving = settings.enableMoving,
            overwriteOnMove = settings.overwriteOnMove,
            enableUndo = settings.enableUndo,
            maxRecipients = settings.maxRecipients,
            enableFavorites = settings.enableFavorites,
            disableCameraCapture = settings.disableCameraCapture,
            skipCameraFilenameDialog = settings.skipCameraFilenameDialog,
            cameraCaptureOpenForEditing = settings.cameraCaptureOpenForEditing,
            cameraCaptureCopyToClipboard = settings.cameraCaptureCopyToClipboard,
            copyPanelCollapsed = settings.copyPanelCollapsed,
            movePanelCollapsed = settings.movePanelCollapsed,
            enablePictureInPicture = settings.enablePictureInPicture,
            defaultRememberFileList = settings.defaultRememberFileList,
            dynamicBackgroundExtension = settings.dynamicBackgroundExtension,
            enableThumbnailPreload = settings.enableThumbnailPreload,
            thumbnailPreloadWifiOnly = settings.thumbnailPreloadWifiOnly,
            videoSnapshotResourceId = settings.videoSnapshotResourceId,
            videoSnapshotFormat = settings.videoSnapshotFormat,
            videoFrameCopyToClipboard = settings.videoFrameCopyToClipboard,
            linkAutoDownloadEnabled = settings.linkAutoDownloadEnabled,
            linkAutoDownloadResourceId = settings.linkAutoDownloadResourceId,
            linkAutoDownloadOpenInPlayer = settings.linkAutoDownloadOpenInPlayer,
            linkDownloadMaxResolution = settings.linkDownloadMaxResolution,
            linkDownloadAudioOnly = settings.linkDownloadAudioOnly,
            linkDownloadLoginWallHeuristicEnabled = settings.linkDownloadLoginWallHeuristicEnabled,
            vrRenderingMode = settings.vrRenderingMode,
            vrAutoImmersive = settings.vrAutoImmersive,
            disable3dVr = settings.disable3dVr,
            // S0326: global 3D/VR default settings
            stereoAutoDetectEnabled = settings.stereoAutoDetectEnabled,
            stereoTrustFilename = settings.stereoTrustFilename,
            stereoTrustMetadata = settings.stereoTrustMetadata,
            stereoTrustAspectRatio = settings.stereoTrustAspectRatio,
            stereoAmbiguityBestGuess = settings.stereoAmbiguityBestGuess,
            stereoDefaultLayout = settings.stereoDefaultLayout.name,
            stereoDefaultProjection = settings.stereoDefaultProjection.name,
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
            credentialsId = resource.credentialsId,
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
            language = backup.language.gsonSafe(current.language),
            preventSleep = backup.preventSleep,
            keepScreenOnPlayer = backup.keepScreenOnPlayer,
            showSmallControls = backup.showSmallControls,
            embeddedGameEnabled = backup.embeddedGameEnabled,
            // S0406: restore global default network login from backup (was kept-from-current before).
            defaultUser = backup.defaultUser,
            defaultPassword = backup.defaultPassword,
            networkParallelism = backup.networkParallelism,
            cacheSizeMb = backup.cacheSizeMb,
            isCacheSizeUserModified = backup.isCacheSizeUserModified,
            enableBackgroundSync = backup.enableBackgroundSync,
            backgroundSyncIntervalHours = backup.backgroundSyncIntervalHours,
            smbEnabled = backup.smbEnabled,
            sftpEnabled = backup.sftpEnabled,
            ftpEnabled = backup.ftpEnabled,
            googleDriveEnabled = backup.googleDriveEnabled,
            oneDriveEnabled = backup.oneDriveEnabled,
            dropboxEnabled = backup.dropboxEnabled,
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
            saveAudioMetadataLocally = backup.saveAudioMetadataLocally,
            enablePhotosDuringAudio = backup.enablePhotosDuringAudio,
            audioBackgroundPhotosResourceId = backup.audioBackgroundPhotosResourceId,
            enablePersistentAudioPlayback = backup.enablePersistentAudioPlayback,
            audioEmptyStateMode = backup.audioEmptyStateMode.gsonSafe(current.audioEmptyStateMode),
            supportText = backup.supportText,
            supportPdf = backup.supportPdf,
            supportEpub = backup.supportEpub,
            supportOfficeDocuments = backup.supportOfficeDocuments,
            showPdfThumbnails = backup.showPdfThumbnails,
            textSizeMax = backup.textSizeMax,
            showTextLineNumbers = backup.showTextLineNumbers,
            textReaderTheme = backup.textReaderTheme.gsonSafe(current.textReaderTheme),
            markdownRendered = backup.markdownRendered,
            syntaxHighlighting = backup.syntaxHighlighting,
            pdfScrollMode = backup.pdfScrollMode,
            pdfColorMode = backup.pdfColorMode.gsonSafe(current.pdfColorMode),
            epubLineHeight = backup.epubLineHeight,
            epubHorizontalMargin = backup.epubHorizontalMargin,
            enableTranslation = backup.enableTranslation,
            translationSourceLanguage = backup.translationSourceLanguage,
            translationTargetLanguage = backup.translationTargetLanguage,
            translationLensStyle = backup.translationLensStyle,
            enableOcr = backup.enableOcr,
            ocrDefaultFontSize = backup.ocrDefaultFontSize.gsonSafe(current.ocrDefaultFontSize),
            ocrDefaultFontFamily = backup.ocrDefaultFontFamily.gsonSafe(current.ocrDefaultFontFamily),
            ocrEngineType = backup.ocrEngineType.gsonSafe(current.ocrEngineType),
            paddleOcrModel = backup.paddleOcrModel.gsonSafe(current.paddleOcrModel),
            defaultSortMode = safeParseSortMode(backup.defaultSortMode.gsonSafe(current.defaultSortMode.name)),
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
            fileOpsInOverflowMenu = backup.fileOpsInOverflowMenu,
            fileOpsOverflowMenuHintShown = backup.fileOpsOverflowMenuHintShown,
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
            enableScheduledOperations = backup.enableScheduledOperations,
            enableCopying = backup.enableCopying,
            goToNextAfterCopy = backup.goToNextAfterCopy,
            overwriteOnCopy = backup.overwriteOnCopy,
            enableMoving = backup.enableMoving,
            overwriteOnMove = backup.overwriteOnMove,
            enableUndo = backup.enableUndo,
            maxRecipients = backup.maxRecipients,
            enableFavorites = backup.enableFavorites,
            disableCameraCapture = backup.disableCameraCapture,
            skipCameraFilenameDialog = backup.skipCameraFilenameDialog,
            cameraCaptureOpenForEditing = backup.cameraCaptureOpenForEditing,
            cameraCaptureCopyToClipboard = backup.cameraCaptureCopyToClipboard,
            copyPanelCollapsed = backup.copyPanelCollapsed,
            movePanelCollapsed = backup.movePanelCollapsed,
            enablePictureInPicture = backup.enablePictureInPicture,
            defaultRememberFileList = backup.defaultRememberFileList,
            dynamicBackgroundExtension = backup.dynamicBackgroundExtension,
            enableThumbnailPreload = backup.enableThumbnailPreload,
            thumbnailPreloadWifiOnly = backup.thumbnailPreloadWifiOnly,
            videoSnapshotResourceId = backup.videoSnapshotResourceId,
            videoSnapshotFormat = backup.videoSnapshotFormat,
            videoFrameCopyToClipboard = backup.videoFrameCopyToClipboard,
            // S0003: null in older backups → keep current setting (don't reset master toggle)
            linkAutoDownloadEnabled = backup.linkAutoDownloadEnabled ?: current.linkAutoDownloadEnabled,
            linkAutoDownloadResourceId = backup.linkAutoDownloadResourceId,
            linkAutoDownloadOpenInPlayer = backup.linkAutoDownloadOpenInPlayer ?: current.linkAutoDownloadOpenInPlayer,
            // S0116: null in older backups → preserve current setting
            linkDownloadMaxResolution = backup.linkDownloadMaxResolution ?: current.linkDownloadMaxResolution,
            linkDownloadAudioOnly = backup.linkDownloadAudioOnly ?: current.linkDownloadAudioOnly,
            linkDownloadLoginWallHeuristicEnabled = backup.linkDownloadLoginWallHeuristicEnabled
                ?: current.linkDownloadLoginWallHeuristicEnabled,
            vrRenderingMode = backup.vrRenderingMode.gsonSafe(current.vrRenderingMode),
            // null in older backup files → preserve current setting
            vrAutoImmersive = backup.vrAutoImmersive ?: current.vrAutoImmersive,
            // null in older backup files → preserve current setting (don't force to false on restore)
            disable3dVr = backup.disable3dVr ?: current.disable3dVr,
            // S0326: null in older backup files → preserve current setting
            stereoAutoDetectEnabled = backup.stereoAutoDetectEnabled ?: current.stereoAutoDetectEnabled,
            stereoTrustFilename = backup.stereoTrustFilename ?: current.stereoTrustFilename,
            stereoTrustMetadata = backup.stereoTrustMetadata ?: current.stereoTrustMetadata,
            stereoTrustAspectRatio = backup.stereoTrustAspectRatio ?: current.stereoTrustAspectRatio,
            stereoAmbiguityBestGuess = backup.stereoAmbiguityBestGuess ?: current.stereoAmbiguityBestGuess,
            stereoDefaultLayout = backup.stereoDefaultLayout
                ?.let { StereoMode.fromKey(it) }
                ?.takeIf { it != StereoMode.AUTO && it != StereoMode.UNKNOWN }
                ?: current.stereoDefaultLayout,
            stereoDefaultProjection = backup.stereoDefaultProjection
                ?.let { StereoMode.fromKey(it) }
                ?.takeIf { it != StereoMode.AUTO && it != StereoMode.UNKNOWN }
                ?: current.stereoDefaultProjection,
        )
    }

    fun toMediaResource(backup: BackupResource): MediaResource {
        return MediaResource(
            name = backup.name.gsonSafe(""),
            path = backup.path.gsonSafe(""),
            type = safeParseResourceType(backup.type.gsonSafe("LOCAL")),
            cloudProvider = backup.cloudProvider?.let { safeParseCloudProvider(it) },
            cloudFolderId = backup.cloudFolderId,
            accountId = backup.accountId,
            credentialsId = backup.credentialsId,
            displayMode = safeParseDisplayMode(backup.displayMode.gsonSafe("LIST")),
            sortMode = safeParseSortMode(backup.sortMode.gsonSafe("NAME_ASC")),
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
            supportedMediaTypes = backup.supportedMediaTypes.gsonSafeList().mapNotNull { safeParseMediaType(it) }.toSet(),
            profile = safeParseResourceProfile(backup.profile.gsonSafe("NONE")),
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

    // Guards against Gson bypassing Kotlin non-null guarantees when fields are absent
    // in older backup JSON. Gson uses reflection and can set non-nullable fields to null.
    // Using `as?` triggers a null-returning cast rather than a Kotlin smart-cast no-op.
    private fun String.gsonSafe(fallback: String): String = (this as? String) ?: fallback
    private fun <T> List<T>.gsonSafeList(): List<T> = (this as? List<T>) ?: emptyList()

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

    // S0406: network credential mapping. Export reads the decrypted password/SSH key;
    // import re-encrypts via the Keystore-backed factory so secrets land in the right format.
    fun toBackupNetworkCredential(entity: NetworkCredentialsEntity): BackupNetworkCredential {
        return BackupNetworkCredential(
            credentialId = entity.credentialId,
            type = entity.type,
            server = entity.server,
            port = entity.port,
            username = entity.username,
            domain = entity.domain,
            shareName = entity.shareName,
            sshPrivateKey = entity.decryptedSshPrivateKey,
            accountId = entity.accountId,
            password = entity.password
        )
    }

    fun toNetworkCredentialsEntity(backup: BackupNetworkCredential): NetworkCredentialsEntity {
        return NetworkCredentialsEntity.create(
            credentialId = backup.credentialId.gsonSafe("").ifBlank { java.util.UUID.randomUUID().toString() },
            type = backup.type.gsonSafe("SMB"),
            server = backup.server.gsonSafe(""),
            port = backup.port,
            username = backup.username.gsonSafe(""),
            plaintextPassword = backup.password.gsonSafe(""),
            domain = backup.domain.gsonSafe(""),
            shareName = backup.shareName?.takeIf { it.isNotBlank() },
            sshPrivateKey = backup.sshPrivateKey?.takeIf { it.isNotBlank() },
            accountId = backup.accountId.gsonSafe("")
        )
    }

    // S0406: web auth session mapping (cookies for link downloads).
    fun toBackupWebAuthSession(raw: RawAuthSession): BackupWebAuthSession {
        val now = System.currentTimeMillis()
        return BackupWebAuthSession(
            host = raw.host,
            accountId = raw.accountId,
            displayName = raw.displayName,
            userAgent = raw.userAgent,
            savedAtEpochMillis = raw.savedAtEpochMillis,
            lastUsedAtEpochMillis = raw.lastUsedAtEpochMillis,
            cookies = raw.cookies.map { cookie ->
                BackupCookie(
                    name = cookie.name,
                    value = cookie.value ?: "",
                    domain = (cookie.domain ?: raw.host).ifBlank { raw.host },
                    path = (cookie.path ?: "/").ifBlank { "/" },
                    secure = cookie.secure,
                    httpOnly = cookie.isHttpOnly,
                    // maxAge >= 0 → persistent cookie; convert relative TTL to absolute epoch.
                    expiresAtEpochMillis = if (cookie.maxAge >= 0L) now + cookie.maxAge * 1000L else null
                )
            }
        )
    }

    fun toRawAuthSession(backup: BackupWebAuthSession): RawAuthSession {
        val now = System.currentTimeMillis()
        return RawAuthSession(
            host = backup.host.gsonSafe(""),
            accountId = backup.accountId.gsonSafe(""),
            displayName = backup.displayName.gsonSafe(""),
            userAgent = backup.userAgent,
            savedAtEpochMillis = backup.savedAtEpochMillis,
            lastUsedAtEpochMillis = backup.lastUsedAtEpochMillis,
            cookies = backup.cookies.gsonSafeList().mapNotNull { c ->
                if (c.name.isBlank()) return@mapNotNull null
                HttpCookie(c.name, c.value.gsonSafe("")).apply {
                    domain = c.domain.gsonSafe("").ifBlank { backup.host }
                    path = c.path.gsonSafe("").ifBlank { "/" }
                    secure = c.secure
                    isHttpOnly = c.httpOnly
                    val expires = c.expiresAtEpochMillis
                    maxAge = if (expires != null) ((expires - now) / 1000L).coerceAtLeast(1L) else -1L
                }
            }
        )
    }

    fun toBackupFavorites(
        favorites: List<FavoritesEntity>,
        resourceLookup: Map<Long, MediaResource>
    ): List<BackupFavorite> {
        return favorites.map { entity ->
            val resource = resourceLookup[entity.resourceId]
            BackupFavorite(
                uri = entity.uri,
                resourceName = resource?.name ?: "",
                resourcePath = resource?.path ?: "",
                displayName = entity.displayName,
                mediaType = entity.mediaType,
                size = entity.size,
                lastKnownPath = entity.lastKnownPath,
                dateModified = entity.dateModified,
                addedTimestamp = entity.addedTimestamp
            )
        }
    }

    fun toFavoritesEntity(
        backup: BackupFavorite,
        resolvedResourceId: Long
    ): FavoritesEntity {
        return FavoritesEntity(
            uri = backup.uri,
            resourceId = resolvedResourceId,
            displayName = backup.displayName,
            mediaType = backup.mediaType,
            size = backup.size,
            lastKnownPath = backup.lastKnownPath,
            dateModified = backup.dateModified,
            addedTimestamp = backup.addedTimestamp
        )
    }
}
