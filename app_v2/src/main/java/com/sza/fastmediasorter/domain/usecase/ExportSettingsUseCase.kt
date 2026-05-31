package com.sza.fastmediasorter.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * UseCase for exporting all app settings and resources to XML file
 * File location: Downloads/FastMediaSorter_export.xml
 */
class ExportSettingsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val scheduledOperationRepository: ScheduledOperationRepository
) {
    suspend operator fun invoke(): Result<String> {
        return try {
            val settings = settingsRepository.getSettings().first()
            val resources = resourceRepository.getAllResources().first()
            val credentials = credentialsRepository.getAllCredentials().first()
            val scheduledOps = scheduledOperationRepository.getAll().first()
            val resourceMap = resources.associateBy { it.id }
            
            // Build XML content
            val xml = buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                appendLine("<FastMediaSorterBackup version=\"3.0\">")
                
                // Settings section
                appendLine("  <Settings>")
                appendLine("    <language>${settings.language.escapeXml()}</language>")
                appendLine("    <preventSleep>${settings.preventSleep}</preventSleep>")
                appendLine("    <showSmallControls>${settings.showSmallControls}</showSmallControls>")
                appendLine("    <defaultUser>${settings.defaultUser.escapeXml()}</defaultUser>")
                appendLine("    <defaultPassword>${settings.defaultPassword.escapeXml()}</defaultPassword>")
                appendLine("    <networkParallelism>${settings.networkParallelism}</networkParallelism>")
                appendLine("    <cacheSizeMb>${settings.cacheSizeMb}</cacheSizeMb>")
                appendLine("    <isCacheSizeUserModified>${settings.isCacheSizeUserModified}</isCacheSizeUserModified>")
                appendLine("    <enableBackgroundSync>${settings.enableBackgroundSync}</enableBackgroundSync>")
                appendLine("    <backgroundSyncIntervalHours>${settings.backgroundSyncIntervalHours}</backgroundSyncIntervalHours>")
                appendLine("    <supportImages>${settings.supportImages}</supportImages>")
                appendLine("    <imageSizeMin>${settings.imageSizeMin}</imageSizeMin>")
                appendLine("    <imageSizeMax>${settings.imageSizeMax}</imageSizeMax>")
                appendLine("    <loadFullSizeImages>${settings.loadFullSizeImages}</loadFullSizeImages>")
                appendLine("    <supportGifs>${settings.supportGifs}</supportGifs>")
                appendLine("    <supportVideos>${settings.supportVideos}</supportVideos>")
                appendLine("    <videoSizeMin>${settings.videoSizeMin}</videoSizeMin>")
                appendLine("    <videoSizeMax>${settings.videoSizeMax}</videoSizeMax>")
                appendLine("    <supportAudio>${settings.supportAudio}</supportAudio>")
                appendLine("    <audioSizeMin>${settings.audioSizeMin}</audioSizeMin>")
                appendLine("    <audioSizeMax>${settings.audioSizeMax}</audioSizeMax>")
                appendLine("    <searchAudioCoversOnline>${settings.searchAudioCoversOnline}</searchAudioCoversOnline>")
                appendLine("    <searchAudioCoversOnlyOnWifi>${settings.searchAudioCoversOnlyOnWifi}</searchAudioCoversOnlyOnWifi>")
                appendLine("    <saveAudioMetadataLocally>${settings.saveAudioMetadataLocally}</saveAudioMetadataLocally>")
                appendLine("    <enablePhotosDuringAudio>${settings.enablePhotosDuringAudio}</enablePhotosDuringAudio>")
                if (settings.audioBackgroundPhotosResourceId != null) {
                    appendLine("    <audioBackgroundPhotosResourceId>${settings.audioBackgroundPhotosResourceId.escapeXml()}</audioBackgroundPhotosResourceId>")
                }
                
                // Document settings
                appendLine("    <supportText>${settings.supportText}</supportText>")
                appendLine("    <supportPdf>${settings.supportPdf}</supportPdf>")
                appendLine("    <supportEpub>${settings.supportEpub}</supportEpub>")
                appendLine("    <supportOfficeDocuments>${settings.supportOfficeDocuments}</supportOfficeDocuments>")
                appendLine("    <showPdfThumbnails>${settings.showPdfThumbnails}</showPdfThumbnails>")
                appendLine("    <textSizeMax>${settings.textSizeMax}</textSizeMax>")
                appendLine("    <showTextLineNumbers>${settings.showTextLineNumbers}</showTextLineNumbers>")
                
                // Translation settings
                appendLine("    <enableTranslation>${settings.enableTranslation}</enableTranslation>")
                appendLine("    <translationSourceLanguage>${settings.translationSourceLanguage.escapeXml()}</translationSourceLanguage>")
                appendLine("    <translationTargetLanguage>${settings.translationTargetLanguage.escapeXml()}</translationTargetLanguage>")
                appendLine("    <translationLensStyle>${settings.translationLensStyle}</translationLensStyle>")
                appendLine("    <enableGoogleLens>${settings.enableGoogleLens}</enableGoogleLens>")
                appendLine("    <enableOcr>${settings.enableOcr}</enableOcr>")
                appendLine("    <ocrDefaultFontSize>${settings.ocrDefaultFontSize.escapeXml()}</ocrDefaultFontSize>")
                appendLine("    <ocrDefaultFontFamily>${settings.ocrDefaultFontFamily.escapeXml()}</ocrDefaultFontFamily>")
                appendLine("    <ocrEngineType>${settings.ocrEngineType.escapeXml()}</ocrEngineType>")
                appendLine("    <paddleOcrModel>${settings.paddleOcrModel.escapeXml()}</paddleOcrModel>")

                // Playback and Sorting settings
                appendLine("    <defaultSortMode>${settings.defaultSortMode.name}</defaultSortMode>")
                appendLine("    <slideshowInterval>${settings.slideshowInterval}</slideshowInterval>")
                appendLine("    <embeddedGameEnabled>${settings.embeddedGameEnabled}</embeddedGameEnabled>")
                appendLine("    <playToEndInSlideshow>${settings.playToEndInSlideshow}</playToEndInSlideshow>")
                
                // Slideshow Music
                appendLine("    <enableSlideshowBackgroundMusic>${settings.enableSlideshowBackgroundMusic}</enableSlideshowBackgroundMusic>")
                if (settings.slideshowMusicResourceId != null) {
                    appendLine("    <slideshowMusicResourceId>${settings.slideshowMusicResourceId}</slideshowMusicResourceId>")
                }

                appendLine("    <allowRename>${settings.allowRename}</allowRename>")
                appendLine("    <allowDelete>${settings.allowDelete}</allowDelete>")
                appendLine("    <useTrash>${settings.useTrash}</useTrash>")
                appendLine("    <confirmDelete>${settings.confirmDelete}</confirmDelete>")
                
                appendLine("    <defaultGridMode>${settings.defaultGridMode}</defaultGridMode>")
                appendLine("    <isResourceGridMode>${settings.isResourceGridMode}</isResourceGridMode>")
                appendLine("    <showSubfoldersAsItems>${settings.showSubfoldersAsItems}</showSubfoldersAsItems>")
                
                appendLine("    <hideGridActionButtons>${settings.hideGridActionButtons}</hideGridActionButtons>")
                appendLine("    <fileOpsInOverflowMenu>${settings.fileOpsInOverflowMenu}</fileOpsInOverflowMenu>")
                appendLine("    <fileOpsOverflowMenuHintShown>${settings.fileOpsOverflowMenuHintShown}</fileOpsOverflowMenuHintShown>")
                appendLine("    <defaultIconSize>${settings.defaultIconSize}</defaultIconSize>")
                appendLine("    <defaultShowCommandPanel>${settings.defaultShowCommandPanel}</defaultShowCommandPanel>")
                appendLine("    <showDetailedErrors>${settings.showDetailedErrors}</showDetailedErrors>")
                appendLine("    <showPlayerHintOnFirstRun>${settings.showPlayerHintOnFirstRun}</showPlayerHintOnFirstRun>")
                appendLine("    <alwaysShowTouchZonesOverlay>${settings.alwaysShowTouchZonesOverlay}</alwaysShowTouchZonesOverlay>")
                appendLine("    <showVideoThumbnails>${settings.showVideoThumbnails}</showVideoThumbnails>")
                appendLine("    <enablePlayerWarmup>${settings.enablePlayerWarmup}</enablePlayerWarmup>")
                appendLine("    <rendererMigrationEnabled>${settings.rendererMigrationEnabled}</rendererMigrationEnabled>")
                appendLine("    <enableSafeMode>${settings.enableSafeMode}</enableSafeMode>")
                appendLine("    <enableFavorites>${settings.enableFavorites}</enableFavorites>")
                appendLine("    <disableCameraCapture>${settings.disableCameraCapture}</disableCameraCapture>")
                appendLine("    <skipCameraFilenameDialog>${settings.skipCameraFilenameDialog}</skipCameraFilenameDialog>")
                appendLine("    <enableScheduledOperations>${settings.enableScheduledOperations}</enableScheduledOperations>")
                appendLine("    <enableCopying>${settings.enableCopying}</enableCopying>")
                appendLine("    <goToNextAfterCopy>${settings.goToNextAfterCopy}</goToNextAfterCopy>")
                appendLine("    <overwriteOnCopy>${settings.overwriteOnCopy}</overwriteOnCopy>")
                appendLine("    <enableMoving>${settings.enableMoving}</enableMoving>")
                appendLine("    <overwriteOnMove>${settings.overwriteOnMove}</overwriteOnMove>")
                appendLine("    <confirmMove>${settings.confirmMove}</confirmMove>")
                appendLine("    <enableUndo>${settings.enableUndo}</enableUndo>")
                appendLine("    <maxRecipients>${settings.maxRecipients}</maxRecipients>")
                appendLine("    <copyPanelCollapsed>${settings.copyPanelCollapsed}</copyPanelCollapsed>")
                appendLine("    <movePanelCollapsed>${settings.movePanelCollapsed}</movePanelCollapsed>")
                appendLine("    <allFiles>${settings.allFiles}</allFiles>")
                appendLine("    <showHiddenFiles>${settings.showHiddenFiles}</showHiddenFiles>")
                
                appendLine("    <lastUsedResourceId>${settings.lastUsedResourceId}</lastUsedResourceId>")
                appendLine("    <enableThumbnailPreload>${settings.enableThumbnailPreload}</enableThumbnailPreload>")
                appendLine("    <thumbnailPreloadWifiOnly>${settings.thumbnailPreloadWifiOnly}</thumbnailPreloadWifiOnly>")
                if (settings.videoSnapshotResourceId != null) {
                    appendLine("    <videoSnapshotResourceId>${settings.videoSnapshotResourceId}</videoSnapshotResourceId>")
                }
                appendLine("    <videoSnapshotFormat>${settings.videoSnapshotFormat}</videoSnapshotFormat>")
                appendLine("  </Settings>")
                
                // Network Credentials section (without passwords!)
                appendLine("  <NetworkCredentials>")
                for (cred in credentials) {
                    appendLine("    <Credential>")
                    appendLine("      <credentialId>${cred.credentialId.escapeXml()}</credentialId>")
                    appendLine("      <type>${cred.type.escapeXml()}</type>")
                    appendLine("      <server>${cred.server.escapeXml()}</server>")
                    appendLine("      <port>${cred.port}</port>")
                    appendLine("      <username>${cred.username.escapeXml()}</username>")
                    appendLine("      <domain>${cred.domain.escapeXml()}</domain>")
                    if (cred.shareName != null) {
                        appendLine("      <shareName>${cred.shareName.escapeXml()}</shareName>")
                    }
                    appendLine("    </Credential>")
                }
                appendLine("  </NetworkCredentials>")
                
                // Resources section
                appendLine("  <Resources>")
                for (resource in resources) {
                    appendLine("    <Resource>")
                    appendLine("      <name>${resource.name.escapeXml()}</name>")
                    appendLine("      <type>${resource.type.name}</type>")
                    appendLine("      <path>${resource.path.escapeXml()}</path>")
                    appendLine("      <isDestination>${resource.isDestination}</isDestination>")
                    appendLine("      <destinationOrder>${resource.destinationOrder}</destinationOrder>")
                    appendLine("      <destinationColor>${resource.destinationColor}</destinationColor>")
                    appendLine("      <isReadOnly>${resource.isReadOnly}</isReadOnly>")
                    appendLine("      <displayOrder>${resource.displayOrder}</displayOrder>")
                    appendLine("      <sortMode>${resource.sortMode.name}</sortMode>")
                    appendLine("      <displayMode>${resource.displayMode.name}</displayMode>")
                    
                    val supportedTypes = resource.supportedMediaTypes.joinToString(",") { it.name }
                    appendLine("      <supportedMediaTypes>${supportedTypes}</supportedMediaTypes>")
                    
                    appendLine("      <scanSubdirectories>${resource.scanSubdirectories}</scanSubdirectories>")
                    appendLine("      <disableThumbnails>${resource.disableThumbnails}</disableThumbnails>")
                    appendLine("      <allFiles>${resource.allFiles}</allFiles>")
                    appendLine("      <showHiddenFiles>${resource.showHiddenFiles}</showHiddenFiles>")
                    
                    if (resource.accessPin != null) {
                        appendLine("      <accessPin>${resource.accessPin.escapeXml()}</accessPin>")
                    }
                    
                    if (resource.showCommandPanel != null) {
                        appendLine("      <showCommandPanel>${resource.showCommandPanel}</showCommandPanel>")
                    }
                    
                    if (resource.readSpeedMbps != null) {
                        appendLine("      <readSpeedMbps>${resource.readSpeedMbps}</readSpeedMbps>")
                    }
                    if (resource.writeSpeedMbps != null) {
                        appendLine("      <writeSpeedMbps>${resource.writeSpeedMbps}</writeSpeedMbps>")
                    }
                    if (resource.recommendedThreads != null) {
                        appendLine("      <recommendedThreads>${resource.recommendedThreads}</recommendedThreads>")
                    }
                    if (resource.lastSpeedTestDate != null) {
                        appendLine("      <lastSpeedTestDate>${resource.lastSpeedTestDate}</lastSpeedTestDate>")
                    }

                    if (resource.credentialsId != null) {
                        appendLine("      <credentialsId>${resource.credentialsId}</credentialsId>")
                    }
                    if (resource.cloudProvider != null) {
                        appendLine("      <cloudProvider>${resource.cloudProvider.name}</cloudProvider>")
                    }
                    if (resource.cloudFolderId != null) {
                        appendLine("      <cloudFolderId>${resource.cloudFolderId.escapeXml()}</cloudFolderId>")
                    }
                    if (resource.comment != null) {
                        appendLine("      <comment>${resource.comment.escapeXml()}</comment>")
                    }
                    appendLine("    </Resource>")
                }
                appendLine("  </Resources>")

                // Scheduled Operations section (version 3.0+)
                appendLine("  <ScheduledOperations>")
                for (op in scheduledOps) {
                    val src = resourceMap[op.sourceResourceId]
                    val dst = if (op.targetResourceId != null) resourceMap[op.targetResourceId] else null
                    if (src == null) continue // skip if source resource gone
                    appendLine("    <ScheduledOperation>")
                    appendLine("      <isEnabled>${op.isEnabled}</isEnabled>")
                    appendLine("      <sourceResourcePath>${src.path.escapeXml()}</sourceResourcePath>")
                    appendLine("      <sourceResourceType>${src.type.name}</sourceResourceType>")
                    appendLine("      <operationType>${op.operationType.name}</operationType>")
                    if (dst != null) {
                        appendLine("      <targetResourcePath>${dst.path.escapeXml()}</targetResourcePath>")
                        appendLine("      <targetResourceType>${dst.type.name}</targetResourceType>")
                    }
                    appendLine("      <fileTypeMask>${op.fileTypeMask}</fileTypeMask>")
                    appendLine("      <timeFilter>${op.timeFilter.name}</timeFilter>")
                    appendLine("      <startTimeHour>${op.startTimeHour}</startTimeHour>")
                    appendLine("      <startTimeMinute>${op.startTimeMinute}</startTimeMinute>")
                    appendLine("      <intervalHours>${op.intervalHours}</intervalHours>")
                    appendLine("      <intervalMinutes>${op.intervalMinutes}</intervalMinutes>")
                    appendLine("      <overwrite>${op.overwrite}</overwrite>")
                    appendLine("      <silentMode>${op.silentMode}</silentMode>")
                    appendLine("    </ScheduledOperation>")
                }
                appendLine("  </ScheduledOperations>")

                appendLine("</FastMediaSorterBackup>")
            }
            
            // Write to Downloads folder using MediaStore for Android 10+
            val exportPath = writeToDownloads(xml, "FastMediaSorter_export.xml")
            
            Timber.d("Settings exported to: $exportPath")
            Result.success(exportPath)
        } catch (e: Exception) {
            Timber.e(e, "Failed to export settings")
            Result.failure(e)
        }
    }
    
    /**
     * Write content to Downloads folder using appropriate API for the Android version.
     * Uses MediaStore for Android 10+ (API 29+), direct file access for older versions.
     * Returns the content URI (for Android 10+) or file path (for older versions).
     */
    private fun writeToDownloads(content: String, fileName: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Use MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/xml")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            
            // Delete existing file with same name (for overwrite)
            resolver.delete(
                collection,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(fileName)
            )
            
            val uri = resolver.insert(collection, contentValues)
                ?: throw Exception("Failed to create file in Downloads")
            
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            } ?: throw Exception("Failed to open output stream")
            
            // Mark as complete
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            
            // Return the content URI for verification
            Timber.i("Settings exported successfully to URI: $uri")
            uri.toString()
        } else {
            // Android 9 and below: Direct file access
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportFile = File(downloadsDir, fileName)
            exportFile.writeText(content)
            MediaStoreNotifier.notifyFile(context, exportFile.absolutePath, "settings-export")
            exportFile.absolutePath
        }
    }
    
    private fun String.escapeXml(): String {
        return this.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
