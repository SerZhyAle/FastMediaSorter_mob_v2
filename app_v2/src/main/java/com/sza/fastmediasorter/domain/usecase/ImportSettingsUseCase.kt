package com.sza.fastmediasorter.domain.usecase

import android.os.Environment
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

/**
 * UseCase for importing all app settings and resources from XML file
 * File location: Downloads/FastMediaSorter_export.xml
 */
class ImportSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val credentialsRepository: com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            // Check if file exists
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val importFile = File(downloadsDir, "FastMediaSorter_export.xml")
            
            if (!importFile.exists()) {
                return Result.failure(Exception("File not found"))
            }
            
            // Parse XML
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            
            // Use try-catch to handle potential close errors on deleted files
            try {
                FileInputStream(importFile).use { inputStream ->
                    parser.setInput(inputStream, "UTF-8")
                }
            } catch (e: java.io.IOException) {
                Timber.w(e, "IOException while reading import file (file may have been deleted)")
                return Result.failure(e)
            }
            
            var settings: AppSettings? = null
            val resources = mutableListOf<MediaResource>()
            val credentials = mutableListOf<com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity>()
            
            var eventType = parser.eventType
            var currentSection: String? = null
            var currentResource: MutableMap<String, String>? = null
            var currentTag: String? = null
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        when (tagName) {
                            "Settings" -> currentSection = "Settings"
                            "NetworkCredentials" -> currentSection = "NetworkCredentials"
                            "Credential" -> currentResource = mutableMapOf()
                            "Resources" -> currentSection = "Resources"
                            "Resource" -> currentResource = mutableMapOf()
                            else -> currentTag = tagName
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isNotEmpty() && currentTag != null) {
                            when (currentSection) {
                                "Settings" -> {
                                    // Store for later processing
                                    if (currentResource == null) {
                                        currentResource = mutableMapOf()
                                    }
                                    currentResource[currentTag] = text
                                }
                                "NetworkCredentials" -> {
                                    currentResource?.set(currentTag, text)
                                }
                                "Resources" -> {
                                    currentResource?.set(currentTag, text)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name
                        when (tagName) {
                            "Settings" -> {
                                // Build AppSettings from collected data
                                currentResource?.let { data ->
                                    settings = AppSettings(
                                        language = data["language"] ?: "en",
                                        preventSleep = data["preventSleep"]?.toBoolean() ?: true,
                                        showSmallControls = data["showSmallControls"]?.toBoolean() ?: false,
                                        defaultUser = data["defaultUser"] ?: "",
                                        defaultPassword = data["defaultPassword"] ?: "",
                                        enableBackgroundSync = data["enableBackgroundSync"]?.toBoolean() ?: true,
                                        backgroundSyncIntervalHours = data["backgroundSyncIntervalHours"]?.toInt() ?: 4,
                                        supportImages = data["supportImages"]?.toBoolean() ?: true,
                                        imageSizeMin = data["imageSizeMin"]?.toLong() ?: 1024L,
                                        imageSizeMax = data["imageSizeMax"]?.toLong() ?: 10485760L,
                                        loadFullSizeImages = data["loadFullSizeImages"]?.toBoolean() ?: false,
                                        supportGifs = data["supportGifs"]?.toBoolean() ?: true,
                                        supportVideos = data["supportVideos"]?.toBoolean() ?: true,
                                        videoSizeMin = data["videoSizeMin"]?.toLong() ?: 102400L,
                                        videoSizeMax = data["videoSizeMax"]?.toLong() ?: 107374182400L,
                                        supportAudio = data["supportAudio"]?.toBoolean() ?: true,
                                        audioSizeMin = data["audioSizeMin"]?.toLong() ?: 10240L,
                                        audioSizeMax = data["audioSizeMax"]?.toLong() ?: 1048576000L,
                                        defaultSortMode = SortMode.valueOf(data["defaultSortMode"] ?: "NAME_ASC"),
                                        slideshowInterval = data["slideshowInterval"]?.toInt() ?: 10,
                                        playToEndInSlideshow = data["playToEndInSlideshow"]?.toBoolean() ?: false,
                                        allowRename = data["allowRename"]?.toBoolean() ?: true,
                                        allowDelete = data["allowDelete"]?.toBoolean() ?: true,
                                        confirmDelete = data["confirmDelete"]?.toBoolean() ?: true,
                                        defaultGridMode = data["defaultGridMode"]?.toBoolean() ?: false,
                                        defaultIconSize = data["defaultIconSize"]?.toInt() ?: 96,
                                        defaultShowCommandPanel = data["defaultShowCommandPanel"]?.toBoolean() ?: true,
                                        showDetailedErrors = data["showDetailedErrors"]?.toBoolean() ?: false,
                                        showPlayerHintOnFirstRun = data["showPlayerHintOnFirstRun"]?.toBoolean() ?: true,
                                        showVideoThumbnails = data["showVideoThumbnails"]?.toBoolean() ?: false,
                                        enableCopying = data["enableCopying"]?.toBoolean() ?: true,
                                        goToNextAfterCopy = data["goToNextAfterCopy"]?.toBoolean() ?: true,
                                        overwriteOnCopy = data["overwriteOnCopy"]?.toBoolean() ?: false,
                                        enableMoving = data["enableMoving"]?.toBoolean() ?: true,
                                        overwriteOnMove = data["overwriteOnMove"]?.toBoolean() ?: false,
                                        enableUndo = data["enableUndo"]?.toBoolean() ?: true,
                                        copyPanelCollapsed = data["copyPanelCollapsed"]?.toBoolean() ?: false,
                                        movePanelCollapsed = data["movePanelCollapsed"]?.toBoolean() ?: false
                                    )
                                }
                                currentResource = null
                                currentSection = null
                            }
                            "Resource" -> {
                                currentResource?.let { data ->
                                    val resource = MediaResource(
                                        id = 0, // Will be auto-generated
                                        name = data["name"] ?: "",
                                        type = ResourceType.valueOf(data["type"] ?: "LOCAL"),
                                        path = data["path"] ?: "",
                                        isDestination = data["isDestination"]?.toBoolean() ?: false,
                                        destinationOrder = data["destinationOrder"]?.toInt() ?: 0,
                                        displayOrder = data["displayOrder"]?.toInt() ?: 0,
                                        fileCount = 0, // Will be updated on next scan
                                        createdDate = System.currentTimeMillis(),
                                        lastBrowseDate = null,
                                        sortMode = SortMode.valueOf(data["sortMode"] ?: "NAME_ASC"),
                                        displayMode = DisplayMode.valueOf(data["displayMode"] ?: "LIST"),
                                        credentialsId = data["credentialsId"], // Already String?
                                        cloudProvider = data["cloudProvider"]?.let { CloudProvider.valueOf(it) },
                                        cloudFolderId = data["cloudFolderId"], // Already String?
                                        comment = data["comment"]
                                    )
                                    resources.add(resource)
                                }
                                currentResource = null
                            }
                            "Credential" -> {
                                currentResource?.let { data ->
                                    val credential = com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity(
                                        id = 0, // Will be auto-generated
                                        credentialId = data["credentialId"] ?: java.util.UUID.randomUUID().toString(),
                                        type = data["type"] ?: "SMB",
                                        server = data["server"] ?: "",
                                        port = data["port"]?.toIntOrNull() ?: 445,
                                        username = data["username"] ?: "",
                                        encryptedPassword = "", // Password NOT imported - user must re-enter
                                        domain = data["domain"] ?: "",
                                        shareName = data["shareName"]
                                    )
                                    credentials.add(credential)
                                }
                                currentResource = null
                            }
                            "NetworkCredentials" -> {
                                currentSection = null
                            }
                            "Resources" -> {
                                currentSection = null
                            }
                            else -> currentTag = null
                        }
                    }
                }
                eventType = parser.next()
            }
            
            // Apply imported settings
            settings?.let {
                settingsRepository.updateSettings(it)
                Timber.d("Settings imported successfully")
            }
            
            // Import credentials first (resources reference them via credentialId)
            if (credentials.isNotEmpty()) {
                credentials.forEach { credential ->
                    credentialsRepository.insert(credential)
                }
                Timber.d("Imported ${credentials.size} network credentials (passwords must be re-entered)")
            }
            
            // Clear existing resources and add imported ones
            if (resources.isNotEmpty()) {
                resourceRepository.deleteAllResources()
                resources.forEach { resource ->
                    resourceRepository.addResource(resource)
                }
                Timber.d("Imported ${resources.size} resources")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to import settings")
            Result.failure(e)
        }
    }
}
