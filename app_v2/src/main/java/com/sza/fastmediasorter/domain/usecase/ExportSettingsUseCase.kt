package com.sza.fastmediasorter.domain.usecase

import android.os.Environment
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * UseCase for exporting all app settings and resources to XML file
 * File location: Downloads/FastMediaSorter_export.xml
 */
class ExportSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val credentialsRepository: NetworkCredentialsRepository
) {
    suspend operator fun invoke(): Result<String> {
        return try {
            val settings = settingsRepository.getSettings().first()
            val resources = resourceRepository.getAllResources().first()
            val credentials = credentialsRepository.getAllCredentials().first()
            
            // Build XML content
            val xml = buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                appendLine("<FastMediaSorterBackup version=\"2.0\">")
                
                // Settings section
                appendLine("  <Settings>")
                appendLine("    <language>${settings.language.escapeXml()}</language>")
                appendLine("    <preventSleep>${settings.preventSleep}</preventSleep>")
                appendLine("    <showSmallControls>${settings.showSmallControls}</showSmallControls>")
                appendLine("    <defaultUser>${settings.defaultUser.escapeXml()}</defaultUser>")
                appendLine("    <defaultPassword>${settings.defaultPassword.escapeXml()}</defaultPassword>")
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
                appendLine("    <defaultSortMode>${settings.defaultSortMode.name}</defaultSortMode>")
                appendLine("    <slideshowInterval>${settings.slideshowInterval}</slideshowInterval>")
                appendLine("    <playToEndInSlideshow>${settings.playToEndInSlideshow}</playToEndInSlideshow>")
                appendLine("    <allowRename>${settings.allowRename}</allowRename>")
                appendLine("    <allowDelete>${settings.allowDelete}</allowDelete>")
                appendLine("    <confirmDelete>${settings.confirmDelete}</confirmDelete>")
                appendLine("    <defaultGridMode>${settings.defaultGridMode}</defaultGridMode>")
                appendLine("    <defaultIconSize>${settings.defaultIconSize}</defaultIconSize>")
                appendLine("    <defaultShowCommandPanel>${settings.defaultShowCommandPanel}</defaultShowCommandPanel>")
                appendLine("    <showDetailedErrors>${settings.showDetailedErrors}</showDetailedErrors>")
                appendLine("    <showPlayerHintOnFirstRun>${settings.showPlayerHintOnFirstRun}</showPlayerHintOnFirstRun>")
                appendLine("    <showVideoThumbnails>${settings.showVideoThumbnails}</showVideoThumbnails>")
                appendLine("    <enableCopying>${settings.enableCopying}</enableCopying>")
                appendLine("    <goToNextAfterCopy>${settings.goToNextAfterCopy}</goToNextAfterCopy>")
                appendLine("    <overwriteOnCopy>${settings.overwriteOnCopy}</overwriteOnCopy>")
                appendLine("    <enableMoving>${settings.enableMoving}</enableMoving>")
                appendLine("    <overwriteOnMove>${settings.overwriteOnMove}</overwriteOnMove>")
                appendLine("    <enableUndo>${settings.enableUndo}</enableUndo>")
                appendLine("    <copyPanelCollapsed>${settings.copyPanelCollapsed}</copyPanelCollapsed>")
                appendLine("    <movePanelCollapsed>${settings.movePanelCollapsed}</movePanelCollapsed>")
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
                    appendLine("      <displayOrder>${resource.displayOrder}</displayOrder>")
                    appendLine("      <sortMode>${resource.sortMode.name}</sortMode>")
                    appendLine("      <displayMode>${resource.displayMode.name}</displayMode>")
                    if (resource.credentialsId != null) {
                        appendLine("      <credentialsId>${resource.credentialsId}</credentialsId>")
                    }
                    if (resource.cloudProvider != null) {
                        appendLine("      <cloudProvider>${resource.cloudProvider.name}</cloudProvider>")
                    }
                    if (resource.cloudFolderId != null) {
                        appendLine("      <cloudFolderId>${resource.cloudFolderId.escapeXml()}</cloudFolderId>")
                    }
                    appendLine("    </Resource>")
                }
                appendLine("  </Resources>")
                
                appendLine("</FastMediaSorterBackup>")
            }
            
            // Write to Downloads folder
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportFile = File(downloadsDir, "FastMediaSorter_export.xml")
            exportFile.writeText(xml)
            
            Timber.d("Settings exported to: ${exportFile.absolutePath}")
            Result.success(exportFile.absolutePath)
        } catch (e: Exception) {
            Timber.e(e, "Failed to export settings")
            Result.failure(e)
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
