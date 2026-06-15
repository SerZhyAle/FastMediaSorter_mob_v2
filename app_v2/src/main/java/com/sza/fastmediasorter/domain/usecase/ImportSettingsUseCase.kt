package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.TimeFilter
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject

/**
 * UseCase for importing all app settings and resources from XML file
 * File location: Downloads/FastMediaSorter_export.xml
 */
class ImportSettingsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val credentialsRepository: com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository,
    private val scheduledOperationRepository: ScheduledOperationRepository,
    private val workManagerScheduler: WorkManagerScheduler,
    private val applyBackupPayloadUseCase: ApplyBackupPayloadUseCase
) {
    private companion object {
        const val LEGACY_XML_FILE_NAME = "FastMediaSorter_export.xml"
    }
    /**
     * Import settings from XML file.
     * @param exportUri Optional content URI from a previous export. If provided, uses it directly.
     *                  If null, attempts to find file by name in Downloads folder.
     */
    suspend operator fun invoke(exportUri: String? = null): Result<Unit> {
        return try {
            // Get input stream for the file
            val inputStream = if (exportUri != null && exportUri.startsWith("content://")) {
                // Use provided URI directly (from recent export)
                try {
                    val uri = android.net.Uri.parse(exportUri)
                    Timber.d("ImportSettings: Using provided URI: $exportUri")
                    context.contentResolver.openInputStream(uri)
                } catch (e: Exception) {
                    Timber.e(e, "ImportSettings: Failed to open provided URI: $exportUri")
                    null
                }
            } else {
                // Auto lookup: prefer the unified JSON backup, fall back to legacy XML.
                getImportFileInputStream()
            } ?: run {
                val downloadsPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "MediaStore Downloads collection"
                } else {
                    @Suppress("DEPRECATION")
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                }
                val errorMsg = """File not found: FastMediaSorter_backup.json (or legacy FastMediaSorter_export.xml)
                    |Searched in: $downloadsPath
                    |Android version: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})
                    |Please ensure the file exists in your Downloads folder""".trimMargin()
                Timber.e("ImportSettings: $errorMsg")
                return Result.failure(Exception(errorMsg))
            }
            
            // S0406: read once, then dispatch by format. JSON → unified applier; XML → legacy parser.
            val importText = inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val trimmedText = importText.trimStart('﻿', ' ', '\n', '\r', '\t', ' ')
            if (trimmedText.startsWith("{")) {
                return importFromJson(trimmedText)
            }

            // Legacy XML path (back-compat) - parse from the text already read.
            java.io.ByteArrayInputStream(importText.toByteArray(Charsets.UTF_8)).use { stream ->
                // Parse XML
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(stream, "UTF-8")

                var settings: AppSettings? = null
                val resources = mutableListOf<MediaResource>()
                val credentials = mutableListOf<com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity>()
                val scheduledOps = mutableListOf<MutableMap<String, String>>()
                var backupVersion = 0 // parsed from root element attribute

                var eventType = parser.eventType
                var currentSection: String? = null
                var currentResource: MutableMap<String, String>? = null
                var currentTag: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        when (tagName) {
                            "FastMediaSorterBackup" -> {
                                val versionAttr = parser.getAttributeValue(null, "version")
                                backupVersion = versionAttr?.substringBefore(".")?.toIntOrNull() ?: 0
                            }
                            "Settings" -> currentSection = "Settings"
                            "NetworkCredentials" -> currentSection = "NetworkCredentials"
                            "Credential" -> currentResource = mutableMapOf()
                            "Resources" -> currentSection = "Resources"
                            "Resource" -> currentResource = mutableMapOf()
                            "ScheduledOperations" -> {
                                if (backupVersion >= 3) currentSection = "ScheduledOperations"
                            }
                            "ScheduledOperation" -> {
                                if (currentSection == "ScheduledOperations") currentResource = mutableMapOf()
                            }
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
                                "ScheduledOperations" -> {
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
                                        isResourceGridMode = data["isResourceGridMode"]?.toBoolean() ?: false,
                                        
                                        language = data["language"] ?: "en",
                                        preventSleep = data["preventSleep"]?.toBoolean() ?: true,
                                        showSmallControls = data["showSmallControls"]?.toBoolean() ?: false,
                                        defaultUser = data["defaultUser"] ?: "",
                                        defaultPassword = data["defaultPassword"] ?: "",
                                        networkParallelism = data["networkParallelism"]?.toInt() ?: 4,
                                        cacheSizeMb = data["cacheSizeMb"]?.toInt() ?: 2048,
                                        isCacheSizeUserModified = data["isCacheSizeUserModified"]?.toBoolean() ?: false,
                                        enableBackgroundSync = data["enableBackgroundSync"]?.toBoolean() ?: true,
                                        backgroundSyncIntervalHours = data["backgroundSyncIntervalHours"]?.toInt() ?: 4,
                                        
                                        allFiles = data["allFiles"]?.toBoolean() ?: false,
                                        showHiddenFiles = data["showHiddenFiles"]?.toBoolean() ?: false,
                                        showSubfoldersAsItems = data["showSubfoldersAsItems"]?.toBoolean() ?: false,
                                        
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
                                        searchAudioCoversOnline = data["searchAudioCoversOnline"]?.toBoolean() ?: false,
                                        searchAudioCoversOnlyOnWifi = data["searchAudioCoversOnlyOnWifi"]?.toBoolean() ?: true,
                                        saveAudioMetadataLocally = data["saveAudioMetadataLocally"]?.toBoolean() ?: true,
                                        enablePhotosDuringAudio = data["enablePhotosDuringAudio"]?.toBoolean() ?: false,
                                        audioBackgroundPhotosResourceId = data["audioBackgroundPhotosResourceId"],
                                        
                                        // Document support
                                        supportText = data["supportText"]?.toBoolean() ?: false,
                                        supportPdf = data["supportPdf"]?.toBoolean() ?: false,
                                        supportEpub = data["supportEpub"]?.toBoolean() ?: false,
                                        supportOfficeDocuments = data["supportOfficeDocuments"]?.toBoolean()
                                            ?: (data["supportText"]?.toBoolean() == true ||
                                                data["supportPdf"]?.toBoolean() == true ||
                                                data["supportEpub"]?.toBoolean() == true),
                                        showPdfThumbnails = data["showPdfThumbnails"]?.toBoolean() ?: false,
                                        textSizeMax = data["textSizeMax"]?.toLong() ?: 104857600L,
                                        showTextLineNumbers = data["showTextLineNumbers"]?.toBoolean() ?: false,
                                        
                                        // Translation
                                        enableTranslation = data["enableTranslation"]?.toBoolean() ?: false,
                                        translationSourceLanguage = data["translationSourceLanguage"] ?: "auto",
                                        translationTargetLanguage = data["translationTargetLanguage"] ?: "ru",
                                        translationLensStyle = data["translationLensStyle"]?.toBoolean() ?: false,
                                        enableGoogleLens = data["enableGoogleLens"]?.toBoolean() ?: false,
                                        enableOcr = data["enableOcr"]?.toBoolean() ?: false,
                                        ocrDefaultFontSize = data["ocrDefaultFontSize"] ?: "AUTO",
                                        ocrDefaultFontFamily = data["ocrDefaultFontFamily"] ?: "DEFAULT",
                                        ocrEngineType = data["ocrEngineType"] ?: "TESSERACT",
                                        paddleOcrModel = data["paddleOcrModel"] ?: "CYRILLIC",
                                        
                                        defaultSortMode = SortMode.valueOf(data["defaultSortMode"] ?: "NAME_ASC"),
                                        slideshowInterval = data["slideshowInterval"]?.toInt() ?: 10,
                                        embeddedGameEnabled = data["embeddedGameEnabled"]?.toBoolean() ?: false,
                                        enableSlideshowBackgroundMusic = data["enableSlideshowBackgroundMusic"]?.toBoolean() ?: false,
                                        slideshowMusicResourceId = data["slideshowMusicResourceId"]?.toLongOrNull(),
                                        
                                        playToEndInSlideshow = data["playToEndInSlideshow"]?.toBoolean() ?: false,
                                        allowRename = data["allowRename"]?.toBoolean() ?: true,
                                        allowDelete = data["allowDelete"]?.toBoolean() ?: true,
                                        useTrash = data["useTrash"]?.toBoolean() ?: true,
                                        confirmDelete = data["confirmDelete"]?.toBoolean() ?: true,
                                        defaultGridMode = data["defaultGridMode"]?.toBoolean() ?: false,
                                        hideGridActionButtons = data["hideGridActionButtons"]?.toBoolean() ?: false,
                                        fileOpsInOverflowMenu = data["fileOpsInOverflowMenu"]?.toBoolean() ?: false,
                                        fileOpsOverflowMenuHintShown = data["fileOpsOverflowMenuHintShown"]?.toBoolean() ?: false,
                                        defaultIconSize = data["defaultIconSize"]?.toInt() ?: 96,
                                        defaultShowCommandPanel = data["defaultShowCommandPanel"]?.toBoolean() ?: true,
                                        showDetailedErrors = data["showDetailedErrors"]?.toBoolean() ?: false,
                                        showPlayerHintOnFirstRun = data["showPlayerHintOnFirstRun"]?.toBoolean() ?: true,
                                        alwaysShowTouchZonesOverlay = data["alwaysShowTouchZonesOverlay"]?.toBoolean() ?: false,
                                        showVideoThumbnails = data["showVideoThumbnails"]?.toBoolean() ?: false,
                                        enablePlayerWarmup = data["enablePlayerWarmup"]?.toBoolean() ?: false,
                                        rendererMigrationEnabled = data["rendererMigrationEnabled"]?.toBoolean() ?: false,
                                        enableSafeMode = data["enableSafeMode"]?.toBoolean() ?: true,
                                        enableFavorites = data["enableFavorites"]?.toBoolean() ?: false,
                                        disableCameraCapture = data["disableCameraCapture"]?.toBoolean() ?: false,
                                        skipCameraFilenameDialog = data["skipCameraFilenameDialog"]?.toBoolean() ?: false,
                                        cameraCaptureOpenForEditing = data["cameraCaptureOpenForEditing"]?.toBoolean() ?: false,
                                        enableScheduledOperations = data["enableScheduledOperations"]?.toBoolean() ?: true,
                                        enableCopying = data["enableCopying"]?.toBoolean() ?: true,
                                        goToNextAfterCopy = data["goToNextAfterCopy"]?.toBoolean() ?: true,
                                        overwriteOnCopy = data["overwriteOnCopy"]?.toBoolean() ?: false,
                                        enableMoving = data["enableMoving"]?.toBoolean() ?: true,
                                        overwriteOnMove = data["overwriteOnMove"]?.toBoolean() ?: false,
                                        confirmMove = data["confirmMove"]?.toBoolean() ?: false,
                                        enableUndo = data["enableUndo"]?.toBoolean() ?: true,
                                        maxRecipients = data["maxRecipients"]?.toInt() ?: 10,
                                        copyPanelCollapsed = data["copyPanelCollapsed"]?.toBoolean() ?: false,
                                        movePanelCollapsed = data["movePanelCollapsed"]?.toBoolean() ?: false,
                                        
                                        lastUsedResourceId = data["lastUsedResourceId"]?.toLong() ?: -1L,
                                        enableThumbnailPreload = data["enableThumbnailPreload"]?.toBoolean() ?: false,
                                        thumbnailPreloadWifiOnly = data["thumbnailPreloadWifiOnly"]?.toBoolean() ?: true,
                                        videoSnapshotResourceId = data["videoSnapshotResourceId"]?.toLongOrNull(),
                                            videoSnapshotFormat = data["videoSnapshotFormat"]
                                                ?.takeIf { it == "JPG" }
                                                ?: "PNG"
                                    )
                                }
                                currentResource = null
                                currentSection = null
                            }
                            "Resource" -> {
                                currentResource?.let { data ->
                                    // Parse supported media types
                                    val supportedTypesString = data["supportedMediaTypes"]
                                    val supportedTypes = if (!supportedTypesString.isNullOrBlank()) {
                                        supportedTypesString.split(",")
                                            .mapNotNull { 
                                                try {
                                                    com.sza.fastmediasorter.domain.model.MediaType.valueOf(it) 
                                                } catch (e: Exception) { 
                                                    null 
                                                }
                                            }
                                            .toSet()
                                    } else {
                                        setOf(com.sza.fastmediasorter.domain.model.MediaType.IMAGE, com.sza.fastmediasorter.domain.model.MediaType.VIDEO)
                                    }
                                
                                    val resource = MediaResource(
                                        id = 0, // Will be set to existing ID if found, or 0 (auto-gen) for new
                                        name = data["name"] ?: "",
                                        type = ResourceType.valueOf(data["type"] ?: "LOCAL"),
                                        path = data["path"] ?: "",
                                        isDestination = data["isDestination"]?.toBoolean() ?: false,
                                        destinationOrder = data["destinationOrder"]?.toIntOrNull(),
                                        destinationColor = data["destinationColor"]?.toIntOrNull() ?: 0xFF4CAF50.toInt(),
                                        isReadOnly = data["isReadOnly"]?.toBoolean() ?: false,
                                        displayOrder = data["displayOrder"]?.toInt() ?: 0,
                                        fileCount = 0, // Will be updated on next scan
                                        createdDate = System.currentTimeMillis(),
                                        lastBrowseDate = null,
                                        sortMode = SortMode.valueOf(data["sortMode"] ?: "NAME_ASC"),
                                        displayMode = DisplayMode.valueOf(data["displayMode"] ?: "LIST"),
                                        
                                        supportedMediaTypes = supportedTypes,
                                        scanSubdirectories = data["scanSubdirectories"]?.toBoolean() ?: false,
                                        disableThumbnails = data["disableThumbnails"]?.toBoolean() ?: false,
                                        allFiles = data["allFiles"]?.toBoolean() ?: false,
                                        showHiddenFiles = data["showHiddenFiles"]?.toBoolean() ?: false,
                                        accessPin = data["accessPin"],
                                        showCommandPanel = data["showCommandPanel"]?.toBoolean(),
                                        
                                        readSpeedMbps = data["readSpeedMbps"]?.toDoubleOrNull(),
                                        writeSpeedMbps = data["writeSpeedMbps"]?.toDoubleOrNull(),
                                        recommendedThreads = data["recommendedThreads"]?.toIntOrNull(),
                                        lastSpeedTestDate = data["lastSpeedTestDate"]?.toLongOrNull(),
                                        
                                        credentialsId = data["credentialsId"],
                                        cloudProvider = data["cloudProvider"]?.let { CloudProvider.valueOf(it) },
                                        cloudFolderId = data["cloudFolderId"],
                                        comment = data["comment"]
                                    )
                                    resources.add(resource)
                                }
                                currentResource = null
                            }
                            "Credential" -> {
                                currentResource?.let { data ->
                                    val credential = com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity(
                                        id = 0, // Will be auto-generated or merged? For now, we always append/overwrite credentials since they are small
                                        credentialId = data["credentialId"] ?: java.util.UUID.randomUUID().toString(),
                                        type = data["type"] ?: "SMB",
                                        server = data["server"] ?: "",
                                        port = data["port"]?.toIntOrNull() ?: 445,
                                        username = data["username"] ?: "",
                                        encryptedPassword = "", // Password NOT imported - user must re-enter
                                        domain = data["domain"] ?: "",
                                        shareName = data["shareName"]?.takeIf { it.isNotBlank() }
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
                            "ScheduledOperation" -> {
                                currentResource?.let { scheduledOps.add(it) }
                                currentResource = null
                            }
                            "ScheduledOperations" -> {
                                currentSection = null
                            }
                            else -> currentTag = null
                        }
                    }
                }
                eventType = parser.next()
            }
            
            settings?.let {
                settingsRepository.updateSettings(it)
                Timber.d("Settings imported successfully")
            }
            
            // Import credentials 
            // Strategy: Insert/Update. Since password is not there, we just update host/user info.
            // Actually, for simplicity and safety, we just insert. 
            // In a real append scenario, checking existence would be better, but credential ID usually unique.
            if (credentials.isNotEmpty()) {
                // Legacy exports may omit shareName on the credential node while the linked SMB resource still has it in path.
                val importedSmbShareNames = resources
                    .asSequence()
                    .filter { it.type == ResourceType.SMB && !it.credentialsId.isNullOrBlank() }
                    .mapNotNull { resource ->
                        val credentialId = resource.credentialsId ?: return@mapNotNull null
                        val shareName = com.sza.fastmediasorter.utils.SmbPathUtils.extractShare(resource.path)
                            ?.takeIf { it.isNotBlank() }
                        shareName?.let { credentialId to it }
                    }
                    .toMap()

                val existingCredentials = credentialsRepository.getAllCredentials().first()
                val existingCredMap = existingCredentials.associateBy { it.credentialId }
                
                credentials.forEach { credential ->
                    val normalizedCredential = if (credential.type.equals("SMB", ignoreCase = true)) {
                        credential.copy(
                            shareName = credential.shareName?.takeIf { it.isNotBlank() }
                                ?: importedSmbShareNames[credential.credentialId]
                        )
                    } else {
                        credential
                    }

                    val existing = existingCredMap[normalizedCredential.credentialId]
                    if (existing != null) {
                        // Preserve existing password: import XML never contains passwords,
                        // so overwriting with empty encryptedPassword would destroy working credentials.
                        // Merge: take metadata from import, keep password (and ID) from DB.
                        val merged = normalizedCredential.copy(
                            id = existing.id,
                            encryptedPassword = existing.encryptedPassword,
                            shareName = normalizedCredential.shareName?.takeIf { it.isNotBlank() }
                                ?: existing.shareName
                        )
                        credentialsRepository.update(merged)
                    } else {
                        credentialsRepository.insert(normalizedCredential)
                    }
                }
                Timber.d("Imported ${credentials.size} network credentials")
            }
            
            // MERGE RESOURCES LOGIC
            // Strategy: 
            // 1. Get existing resources
            // 2. For each imported resource:
            //    - Look for match by (Path + Type)
            //    - If found: Update existing resource (keep ID) with imported values
            //    - If not found: Insert as new
            if (resources.isNotEmpty()) {
                val existingResources = resourceRepository.getAllResources().first()
                // Key: Path + Type. Using a composite key for matching.
                val existingMap = existingResources.associateBy { "${it.path}|${it.type}" }
                
                resources.forEach { importedResource ->
                    val key = "${importedResource.path}|${importedResource.type}"
                    val existing = existingMap[key]
                    
                    if (existing != null) {
                        // Update existing: Copy ID and other non-imported internal stats if we wanted to preserve them 
                        // (but we usually want to restore backup state). 
                        // So we explicitly take the ID from existing to ensure Update, not Insert.
                        val mergedResource = importedResource.copy(id = existing.id)
                        resourceRepository.updateResource(mergedResource)
                        Timber.d("Updated existing resource: ${importedResource.name}")
                    } else {
                        resourceRepository.addResource(importedResource)
                        Timber.d("Added new resource: ${importedResource.name}")
                    }
                }
                Timber.d("Processed import of ${resources.size} resources (Merge Mode)")
            }

            // Import scheduled operations (version 3+ only)
            if (backupVersion >= 3 && scheduledOps.isNotEmpty()) {
                // Build path+type → id lookup from current DB state (after resource merge above)
                val allResources = resourceRepository.getAllResources().first()
                val resourceLookup = allResources.associateBy { "${it.path}|${it.type.name}" }

                scheduledOps.forEach { data ->
                    val srcKey = "${data["sourceResourcePath"]}|${data["sourceResourceType"]}"
                    val srcResource = resourceLookup[srcKey]
                    if (srcResource == null) {
                        Timber.w("ImportSettings: ScheduledOp skipped - source not found: $srcKey")
                        return@forEach
                    }

                    val dstPath = data["targetResourcePath"]
                    val dstType = data["targetResourceType"]
                    val dstResource = if (dstPath != null && dstType != null) {
                        resourceLookup["$dstPath|$dstType"]
                    } else null

                    val opType = runCatching { ScheduledOpType.valueOf(data["operationType"] ?: "") }
                        .getOrElse { ScheduledOpType.COPY }
                    if (opType != ScheduledOpType.DELETE && dstResource == null) {
                        Timber.w("ImportSettings: ScheduledOp skipped - target not found for $opType")
                        return@forEach
                    }

                    val op = ScheduledOperation(
                        id = 0,
                        isEnabled = data["isEnabled"]?.toBoolean() ?: true,
                        sourceResourceId = srcResource.id,
                        operationType = opType,
                        targetResourceId = dstResource?.id,
                        fileTypeMask = data["fileTypeMask"]?.toIntOrNull()
                            ?: FileTypeFlags.fromLegacyName(data["fileTypeFilter"] ?: ""),
                        timeFilter = runCatching { TimeFilter.valueOf(data["timeFilter"] ?: "") }
                            .getOrElse { TimeFilter.ALL },
                        startTimeHour = data["startTimeHour"]?.toIntOrNull() ?: 0,
                        startTimeMinute = data["startTimeMinute"]?.toIntOrNull() ?: 0,
                        intervalHours = data["intervalHours"]?.toIntOrNull() ?: 1,
                        intervalMinutes = data["intervalMinutes"]?.toIntOrNull() ?: 0,
                        overwrite = data["overwrite"]?.toBoolean() ?: false,
                        silentMode = data["silentMode"]?.toBoolean() ?: false
                    )
                    val newId = scheduledOperationRepository.upsert(op)
                    if (op.isEnabled) {
                        val savedOp = scheduledOperationRepository.getById(newId)
                        if (savedOp != null) workManagerScheduler.scheduleOperation(savedOp)
                    }
                }
                Timber.d("Imported ${scheduledOps.size} scheduled operations")
            }

            Result.success(Unit)
            } // End of inputStream.use block
        } catch (e: Exception) {
            Timber.e(e, "Failed to import settings")
            Result.failure(e)
        }
    }
    
    /**
     * S0406: parse the unified JSON payload and delegate to the shared applier.
     */
    private suspend fun importFromJson(json: String): Result<Unit> {
        return try {
            val payload = com.google.gson.GsonBuilder().setLenient().create()
                .fromJson(json, BackupPayload::class.java)
                ?: return Result.failure(IllegalStateException("Empty backup payload"))
            applyBackupPayloadUseCase(payload)
            Timber.i("Settings imported from JSON backup v%d", payload.version)
            Result.success(Unit)
        } catch (e: com.google.gson.JsonSyntaxException) {
            Timber.e(e, "Backup JSON is corrupted")
            Result.failure(Exception("Backup file is damaged. Cannot restore."))
        } catch (e: Exception) {
            Timber.e(e, "Failed to import JSON backup")
            Result.failure(e)
        }
    }

    /**
     * S0406: auto lookup - prefer the unified JSON backup, fall back to the legacy XML file.
     * For each candidate name try the MediaStore query first, then a direct read from the
     * public Downloads directory. The direct read covers backups not owned by the current
     * install (export -> uninstall -> reinstall, or a different package variant), which the
     * MediaStore Downloads query never returns under scoped storage.
     */
    private fun getImportFileInputStream(): InputStream? =
        openImportCandidate(ExportSettingsUseCase.EXPORT_FILE_NAME)
            ?: openImportCandidate(LEGACY_XML_FILE_NAME)

    private fun openImportCandidate(fileName: String): InputStream? =
        getFileInputStream(fileName)
            ?: getFileInputStreamFromPublicDownloads(fileName)

    /**
     * S0406: direct-File fallback for the auto lookup. The MediaStore Downloads query only
     * returns files owned by the current install, so a backup made by a previous install or
     * a different package variant is invisible to it even though it sits in Downloads. With
     * all-files access granted the file is readable directly regardless of owner.
     */
    private fun getFileInputStreamFromPublicDownloads(fileName: String): InputStream? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !PermissionHelper.hasAllFilesAccessPermission(context)
        ) {
            // Without all-files access scoped storage blocks reading non-owned Downloads files.
            return null
        }
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.isDirectory) return null

        val baseName = fileName.substringBeforeLast(".")
        val extension = fileName.substringAfterLast(".")
        // Match "<base>.ext" and Android's "<base> (1).ext" duplicates; pick the most recent.
        val match = downloadsDir.listFiles { f ->
            f.isFile &&
                f.name.startsWith(baseName, ignoreCase = true) &&
                f.name.endsWith(".$extension", ignoreCase = true)
        }?.maxByOrNull { it.lastModified() } ?: return null

        return try {
            FileInputStream(match)
        } catch (e: Exception) {
            Timber.e(e, "ImportSettings: failed to open fallback file ${match.absolutePath}")
            null
        }
    }

    /**
     * Get input stream for file in Downloads folder using appropriate API for the Android version.
     * Uses MediaStore for Android 10+ (API 29+), direct file access for older versions.
     */
    private fun getFileInputStream(fileName: String): InputStream? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Use MediaStore
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            
            val projection = arrayOf(
                MediaStore.Downloads._ID, 
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.OWNER_PACKAGE_NAME
            )
                // Use LIKE query to find files starting with the base name (handling " (1)", " (2)", etc.)
                // This is crucial because Android appends numbers if file exists and we can't overwrite
                val baseName = fileName.substringBeforeLast(".")
                val extension = fileName.substringAfterLast(".")
                
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                // Match "FastMediaSorter_export%" to catch "FastMediaSorter_export.xml", "FastMediaSorter_export (1).xml", etc.
                val selectionArgs = arrayOf("$baseName%.$extension")
                
                // Sort by date added descending to get the most recent file
                val sortOrder = "${MediaStore.Downloads.DATE_ADDED} DESC"
                
                Timber.d("ImportSettings: Querying MediaStore for files like: $baseName%.$extension")
                
                resolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                    val resultCount = cursor.count
                    Timber.d("ImportSettings: MediaStore query returned $resultCount results")
                    
                    if (resultCount == 0) {
                        Timber.e("ImportSettings: No files found in MediaStore Downloads matching pattern")
                        Timber.e("ImportSettings: Ensure file is in Downloads folder and visible to MediaStore")
                    }
                    
                    // Log top results for debugging
                    var loggedCount = 0
                    while (cursor.moveToNext() && loggedCount < 5) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME))
                        // Only verify it ends with correct extension to be safe (though LIKE query should handle it)
                        if (name.endsWith(".$extension", ignoreCase = true)) {
                            Timber.d("ImportSettings: Found candidate - ID=$id, name=$name")
                        }
                        loggedCount++
                    }
                    
                    // Go back to first result
                    if (cursor.moveToFirst()) {
                        do {
                            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME))
                            if (name.endsWith(".$extension", ignoreCase = true)) {
                                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                                val uri = android.content.ContentUris.withAppendedId(collection, id)
                                Timber.d("ImportSettings: Selecting most recent match: $name (ID=$id)")
                                
                                return try {
                                    resolver.openInputStream(uri)
                                } catch (e: Exception) {
                                    Timber.e(e, "ImportSettings: Failed to open input stream for URI: $uri")
                                    null
                                }
                            }
                        } while (cursor.moveToNext())
                        
                        Timber.e("ImportSettings: No file with correct extension found in results")
                        null
                    } else {
                        Timber.e("ImportSettings: Cursor empty after query")
                        null
                    }
                } ?: run {
                    Timber.e("ImportSettings: MediaStore query failed - cursor is null")
                    null
                }
        } else {
            // Android 9 and below: Direct file access
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            
            Timber.d("ImportSettings: Checking direct file access (Android ${Build.VERSION.SDK_INT})")
            Timber.d("ImportSettings: Downloads path: ${downloadsDir.absolutePath}")
            Timber.d("ImportSettings: Looking for file: ${file.absolutePath}")
            Timber.d("ImportSettings: File exists: ${file.exists()}")
            
            if (file.exists()) {
                Timber.d("ImportSettings: File found, size: ${file.length()} bytes")
                try {
                    FileInputStream(file)
                } catch (e: Exception) {
                    Timber.e(e, "ImportSettings: Failed to open file: ${file.absolutePath}")
                    null
                }
            } else {
                Timber.e("ImportSettings: File not found at: ${file.absolutePath}")
                Timber.e("ImportSettings: Downloads directory exists: ${downloadsDir.exists()}")
                Timber.e("ImportSettings: Downloads directory readable: ${downloadsDir.canRead()}")
                null
            }
        }
    }
}
