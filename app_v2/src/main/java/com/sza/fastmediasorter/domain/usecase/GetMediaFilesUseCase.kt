package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.metrics.ScanMetricsRecorder
import com.sza.fastmediasorter.core.util.CachedMediaMetadataExtractor
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.sza.fastmediasorter.core.logging.CorrelationContext
import com.sza.fastmediasorter.core.logging.StructuredLogger
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import com.sza.fastmediasorter.domain.usecase.scan.IncrementalScanStrategy
import com.sza.fastmediasorter.domain.usecase.scan.ScanDispatcher
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import timber.log.Timber

data class SizeFilter(
    val imageSizeMin: Long,
    val imageSizeMax: Long,
    val videoSizeMin: Long,
    val videoSizeMax: Long,
    val audioSizeMin: Long,
    val audioSizeMax: Long
)

data class MediaFilePage(
    val files: List<MediaFile>,
    val hasMore: Boolean
)

interface MediaScanner {
    suspend fun scanFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter? = null,
        credentialsId: String? = null,
        scanSubdirectories: Boolean = true,
        showHiddenFiles: Boolean = false,
        onProgress: ScanProgressCallback? = null
    ): List<MediaFile>
    
    /**
     * List directory contents (files + folders) for subfolder navigation.
     * Unlike scanFolder, this returns only immediate children, with folders represented as MediaFile with isDirectory=true.
     * @param path Directory path to list
     * @param supportedTypes Media types to include for files (folders are always included)
     * @param sizeFilter Size filter for files (not applied to folders)
     * @param credentialsId Credentials for network resources
     * @param showHiddenFiles Whether to include hidden files and folders
     * @return List of MediaFile items (files and folders mixed)
     */
    suspend fun listDirectoryContents(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter? = null,
        credentialsId: String? = null,
        showHiddenFiles: Boolean = false
    ): List<MediaFile> {
        // Default implementation: use scanFolder with scanSubdirectories=false (for backwards compatibility)
        return scanFolder(path, supportedTypes, sizeFilter, credentialsId, scanSubdirectories = false, showHiddenFiles)
    }
    
    /**
     * Scan folder with pagination support.
     * @param offset Starting position (0-based)
     * @param limit Maximum number of files to return
     * @param scanSubdirectories Whether to scan subdirectories recursively
     * @param showHiddenFiles Whether to include hidden files and folders
     * @return MediaFilePage with files and hasMore flag
     */
    suspend fun scanFolderPaged(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter? = null,
        offset: Int,
        limit: Int,
        credentialsId: String? = null,
        scanSubdirectories: Boolean = true,
        showHiddenFiles: Boolean = false
    ): MediaFilePage
    
    suspend fun getFileCount(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter? = null,
        credentialsId: String? = null,
        scanSubdirectories: Boolean = true,
        showHiddenFiles: Boolean = false
    ): Int
    
    suspend fun isWritable(path: String, credentialsId: String? = null): Boolean
}



class GetMediaFilesUseCase @Inject constructor(
    private val mediaScannerFactory: MediaScannerFactory,
    private val favoritesRepository: FavoritesRepository,
    private val credentialsRepository: com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository,
    private val cachedFileListRepository: CachedFileListRepository,
    private val scanDispatcher: ScanDispatcher,
    private val metadataExtractor: CachedMediaMetadataExtractor,
    private val mediaCapabilities: MediaCapabilities
) {
    companion object {
        private const val LARGE_FOLDER_THRESHOLD = 1000
        /** Minimum file count from chunked scan to trigger progressive emission */
        private const val PROGRESSIVE_BATCH_SIZE = 1000
    }
    
    operator fun invoke(
        resource: MediaResource,
        sortMode: SortMode = SortMode.NAME_ASC,
        sizeFilter: SizeFilter? = null,
        useChunkedLoading: Boolean = false,
        maxFiles: Int = 100,
        showHiddenFiles: Boolean = false,
        onProgress: ScanProgressCallback? = null,
        currentPath: String? = null,
        isSubfolderMode: Boolean = false,
        /**
         * When true, bypasses any in-memory cache and forces a fresh full scan
         * of the resource folder (A5-T4 API surface).
         * Default is false (normal cache-aware behaviour).
         */
        forceFullScan: Boolean = false,
        /**
         * When true, emits an early partial batch (first ~1000 files without
         * metadata extraction) for SMB resources before the complete scan finishes.
         * This lets the UI display files within seconds while the full scan
         * continues in the background. Default is false for backward compatibility
         * (.first() callers receive only the complete result).
         */
        progressiveLoading: Boolean = false
    ): Flow<List<MediaFile>> {
        val contextElement = CorrelationContext.asContextElement(
            operation = "get-media-files",
            extras = mapOf("resourceId" to resource.id.toString(), "type" to resource.type.name)
        )
        return flow {
        StructuredLogger.d("START invoke", "resource" to resource.name, "showHiddenFiles" to showHiddenFiles, "forceFullScan" to forceFullScan)

        // If forced, evict any cached data for this resource before scanning.
        if (forceFullScan) {
            MediaFilesCacheManager.clearCache(resource.id)
            StructuredLogger.i("forceFullScan=true - cleared in-memory cache")
        }
        
        if (resource.id == -100L) {
            StructuredLogger.d("Loading Favorites from repository")
            val favorites = favoritesRepository.getAllFavorites().first()
            val favoriteFiles = favorites.map { entity ->
                MediaFile(
                    path = entity.uri,
                    name = entity.displayName,
                    type = MediaType.entries.getOrElse(entity.mediaType) { MediaType.IMAGE },
                    size = entity.size,
                    createdDate = entity.dateModified,
                    resourceId = entity.resourceId,
                    isFavorite = true,
                    // Default values for other fields
                    width = 0, height = 0, duration = 0
                )
            }
            // Apply flavor-specific media type restrictions to favorites
            val flavorAllowedTypes = applyFlavorMediaTypeRestrictions(MediaType.entries.toSet())
            val filteredFavorites = favoriteFiles.filter { it.type in flavorAllowedTypes }
            val sortedFavorites = sortFiles(filteredFavorites, sortMode)
            emit(sortedFavorites)
            return@flow
        }
        
        // Build full path for SMB resources
        val fullPath = if (resource.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB && resource.credentialsId != null) {
            // Check if resource.path already contains full SMB URL
            val cleanPath = resource.path.trim()
            if (cleanPath.startsWith("smb://", ignoreCase = true)) {
                // Path is already a full SMB URL, use it as-is
                StructuredLogger.d("Using existing SMB URL", "path" to cleanPath)
                cleanPath
            } else {
                // Build full smb://server:port/share/path from components
                val credentials = credentialsRepository.getByCredentialId(resource.credentialsId)
                if (credentials != null && !credentials.shareName.isNullOrEmpty()) {
                    val port = credentials.port.takeIf { it > 0 } ?: 445
                    // Ensure we don't have a partial smb:// prefix or similar junk
                    val pathWithoutSchema = if (cleanPath.startsWith("smb:", ignoreCase = true)) {
                         cleanPath.substring(4).trimStart('/')
                    } else {
                        cleanPath.trimStart('/')
                    }
                    
                    // If resource.path is just the share name, use empty path to avoid duplication
                    // e.g., if path="/test_media" and share="test_media", use empty path
                    val relativePath = if (pathWithoutSchema == credentials.shareName) {
                        ""
                    } else if (pathWithoutSchema.startsWith(credentials.shareName + "/")) {
                        // If path="/test_media/subfolder", extract "subfolder"
                        pathWithoutSchema.substring(credentials.shareName.length + 1)
                    } else {
                        // Otherwise use the path as-is
                        pathWithoutSchema
                    }
                    
                    val builtPath = com.sza.fastmediasorter.utils.SmbPathUtils.buildSmbPath(
                        server = credentials.server,
                        share = credentials.shareName,
                        path = relativePath,
                        port = port
                    )
                    StructuredLogger.d("Built full SMB path", "path" to builtPath, "relativePath" to relativePath)
                    builtPath
                } else {
                    StructuredLogger.w("Credentials not found or invalid", "credentialsId" to resource.credentialsId)
                    resource.path
                }
            }
        } else {
            resource.path
        }
        
        // Determine effective path: use currentPath if in subfolder mode, otherwise use resource path
        val effectivePath = if (isSubfolderMode && currentPath != null) {
            StructuredLogger.d("Using currentPath for subfolder navigation", "path" to currentPath)
            currentPath
        } else {
            fullPath
        }
        
        val scanner = mediaScannerFactory.getScanner(resource.type)
        
        StructuredLogger.d("Got scanner", "scannerType" to scanner.javaClass.simpleName)
        
        // Apply flavor-specific media type restrictions
        val flavorFilteredTypes = applyFlavorMediaTypeRestrictions(resource.supportedMediaTypes)
        StructuredLogger.d("Calling scanner", "types" to flavorFilteredTypes.map { it.name })
        
        // Check if we can skip the scan (A5-T2, A5-T3)
        val cachedEntity = cachedFileListRepository.getEntityByResourceId(resource.id)
        val canSkip = !forceFullScan && !isSubfolderMode && resource.rememberFileList && 
            IncrementalScanStrategy.canSkipScan(cachedEntity, effectivePath, resource.type)

        val files = if (canSkip) {
            StructuredLogger.i("IncrementalScan: cache is valid, skipping physical scan", "resource" to resource.name)
            cachedFileListRepository.getCachedFiles(resource.id) ?: emptyList()
        } else {
            // Progressive loading: emit a fast partial batch for large SMB folders
            // before the full (metadata-heavy) scan starts. This gives the UI files
            // within seconds while the complete scan continues in the background.
            //
            // S0248: this is the documented two-phase listing pattern. The chunked early-emit
            // (`scanFolderChunked` with PROGRESSIVE_BATCH_SIZE) and the full follow-up scan
            // (`scanFolder` below) issue DIFFERENT coalesce keys at SmbMediaScanCoordinator
            // because they carry different `maxFiles` budgets - so the defensive in-flight
            // coalescer does not merge them. The redundant root-listing observed in S0246
            // §4.2 traces back to this intentional split. Future regressions where the SAME
            // listing parameters are emitted twice will be folded by the coalescer in
            // SmbMediaScanCoordinator and logged as `dedup hit` at DEBUG level.
            if (progressiveLoading && !isSubfolderMode && !useChunkedLoading
                && scanner is com.sza.fastmediasorter.data.network.SmbMediaScanner
            ) {
                try {
                    val quickFiles = scanner.scanFolderChunked(
                        path = effectivePath,
                        supportedTypes = flavorFilteredTypes,
                        sizeFilter = sizeFilter,
                        maxFiles = PROGRESSIVE_BATCH_SIZE,
                        credentialsId = resource.credentialsId,
                        scanSubdirectories = resource.scanSubdirectories,
                        showHiddenFiles = showHiddenFiles
                    )
                    // S0248 Phase 5: emit the listing-only PENDING batch unconditionally -
                    // do not gate on size. Even a small SMB folder benefits from the UI
                    // seeing names/sizes/types immediately while the full enrichment pass
                    // continues in the background. Each row carries `metadataState = PARTIAL`
                    // so consumers know enrichment-derived fields may still be missing.
                    if (quickFiles.isNotEmpty()) {
                        val tagged = quickFiles.map {
                            it.copy(
                                resourceId = resource.id,
                                metadataState = com.sza.fastmediasorter.domain.model.MetadataState.PARTIAL
                            )
                        }
                        StructuredLogger.d("Progressive: emitting listing-only PENDING batch", "count" to tagged.size)
                        StructuredLogger.i("listing_complete", "resource" to resource.id, "count" to tagged.size)
                        emit(tagged)
                    } else {
                        StructuredLogger.d("Progressive: folder empty, skipping early emit")
                    }
                } catch (e: Exception) {
                    StructuredLogger.w(e, "Progressive partial scan failed, continuing with full scan")
                }
            }

            // Physical scan (with parallelism control A5-T10)
            scanDispatcher.withScanPermit(resource.type) {
                if (isSubfolderMode) {
                    StructuredLogger.d("Using listDirectoryContents for subfolder mode", "path" to effectivePath)
                    scanner.listDirectoryContents(
                        path = effectivePath,
                        supportedTypes = flavorFilteredTypes,
                        sizeFilter = sizeFilter,
                        credentialsId = resource.credentialsId,
                        showHiddenFiles = showHiddenFiles
                    )
                } else if (useChunkedLoading && scanner is com.sza.fastmediasorter.data.network.SmbMediaScanner) {
                    StructuredLogger.d("Using chunked loading", "maxFiles" to maxFiles)
                    scanner.scanFolderChunked(
                        path = effectivePath,
                        supportedTypes = flavorFilteredTypes,
                        sizeFilter = sizeFilter,
                        maxFiles = maxFiles,
                        credentialsId = resource.credentialsId,
                        scanSubdirectories = resource.scanSubdirectories,
                        showHiddenFiles = showHiddenFiles
                    )
                } else {
                    StructuredLogger.d("Using standard loading")
                    val metricsToken = ScanMetricsRecorder.beginScan(resource.id, resource.type, expectedFileCount = resource.fileCount)
                    val result = scanner.scanFolder(
                        path = effectivePath,
                        supportedTypes = flavorFilteredTypes,
                        sizeFilter = sizeFilter,
                        credentialsId = resource.credentialsId,
                        scanSubdirectories = resource.scanSubdirectories,
                        showHiddenFiles = showHiddenFiles,
                        onProgress = onProgress
                    )
                    ScanMetricsRecorder.endScan(metricsToken, result.size)
                    
                    // Update persistent cache if enabled (A5-T1, A5-T6)
                    if (resource.rememberFileList) {
                        val now = System.currentTimeMillis()
                        val folderMtime = IncrementalScanStrategy.currentFolderMtime(effectivePath)
                        cachedFileListRepository.saveCachedFiles(
                            resourceId = resource.id,
                            files = result,
                            lastScanTimestamp = now,
                            lastModifiedFolder = folderMtime
                        )
                    }
                    result
                }
            }
        }
        
        StructuredLogger.d("Scanner returned files", "count" to files.size)
        
        // Fetch favorites to mark files
        val favoriteUris = try {
            favoritesRepository.getAllFavorites().first().map { it.uri }.toSet()
        } catch (e: Exception) {
            StructuredLogger.e(e, "Failed to fetch favorites")
            emptySet<String>()
        }
        
        val filesWithFavorites = files.map { file ->
            // Populate resourceId if missing (it should be missing from scanner)
            val fileWithResource = if (file.resourceId == null) {
                file.copy(resourceId = resource.id)
            } else {
                file
            }
            
            if (favoriteUris.contains(fileWithResource.path)) {
                fileWithResource.copy(isFavorite = true)
            } else {
                fileWithResource
            }
        }

        // Enrich metadata when rememberFileList is enabled OR when sort mode requires
        // audio/image metadata fields (artist, title, duration, exifDateTime).
        val metadataReadyFiles = if (resource.rememberFileList || needsMetadataForSort(sortMode)) {
            metadataExtractor.enrichBatch(
                resourceId = resource.id,
                resourceType = resource.type,
                credentialsId = resource.credentialsId,
                files = filesWithFavorites,
                // S0248 Phase 3: a user-triggered refresh (forceFullScan == true) also asks
                // the cache to retry rows previously marked BROKEN. PARTIAL is always retried.
                forceRefresh = forceFullScan
            ).also { metadataExtractor.logSessionDiagnostics("scan resource=${resource.id}") }
        } else {
            filesWithFavorites
        }
        
        // Skip sorting for large folders (> 1000 files) - improves performance
        val sortedFiles = if (metadataReadyFiles.size > LARGE_FOLDER_THRESHOLD) {
            StructuredLogger.d("Large folder, skipping sort for performance", "count" to metadataReadyFiles.size)
            metadataReadyFiles  // Return unsorted for large folders
        } else {
            StructuredLogger.d("Small folder, sorting", "count" to metadataReadyFiles.size, "sortMode" to sortMode)
            sortFiles(metadataReadyFiles, sortMode)
        }
        
        StructuredLogger.d("Emitting files", "count" to sortedFiles.size)
        emit(sortedFiles)
        
        StructuredLogger.d("COMPLETE - flow emission done")
    }.flowOn(Dispatchers.IO + contextElement) // Execute scanning and sorting on IO thread
    }

    /**
     * Returns true when the sort mode relies on metadata fields that require
     * [CachedMediaMetadataExtractor.enrichForCache] to be populated:
     * artist, title, duration (audio) or exifDateTime (image).
     */
    private fun needsMetadataForSort(mode: SortMode): Boolean = when (mode) {
        SortMode.ARTIST_ASC, SortMode.ARTIST_DESC,
        SortMode.TITLE_ASC, SortMode.TITLE_DESC,
        SortMode.DURATION_ASC, SortMode.DURATION_DESC,
        SortMode.DATE_TAKEN_ASC, SortMode.DATE_TAKEN_DESC -> true
        else -> false
    }

    private fun sortFiles(files: List<MediaFile>, mode: SortMode): List<MediaFile> {
        return when (mode) {
            SortMode.MANUAL -> files // Keep original order for manual mode
            SortMode.NAME_ASC -> files.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
            SortMode.DATE_ASC -> files.sortedBy { it.createdDate }
            SortMode.DATE_DESC -> files.sortedByDescending { it.createdDate }
            SortMode.SIZE_ASC -> files.sortedBy { it.size }
            SortMode.SIZE_DESC -> files.sortedByDescending { it.size }
            SortMode.TYPE_ASC -> files.sortedBy { it.type.ordinal }
            SortMode.TYPE_DESC -> files.sortedByDescending { it.type.ordinal }
            SortMode.ARTIST_ASC -> files.sortedWith(compareBy<MediaFile> { it.artist?.lowercase() ?: "" }.thenBy { it.name.lowercase() })
            SortMode.ARTIST_DESC -> files.sortedWith(compareByDescending<MediaFile> { it.artist?.lowercase() ?: "" }.thenBy { it.name.lowercase() })
            SortMode.TITLE_ASC -> files.sortedWith(compareBy<MediaFile> { it.title?.lowercase() ?: "" }.thenBy { it.name.lowercase() })
            SortMode.TITLE_DESC -> files.sortedWith(compareByDescending<MediaFile> { it.title?.lowercase() ?: "" }.thenBy { it.name.lowercase() })
            SortMode.DURATION_ASC -> files.sortedWith(compareBy<MediaFile> { it.duration ?: Long.MAX_VALUE }.thenBy { it.name.lowercase() })
            SortMode.DURATION_DESC -> files.sortedWith(compareByDescending<MediaFile> { it.duration ?: Long.MIN_VALUE }.thenBy { it.name.lowercase() })
            SortMode.DATE_TAKEN_ASC -> files.sortedWith(compareBy<MediaFile> { it.exifDateTime ?: Long.MAX_VALUE }.thenBy { it.name.lowercase() })
            SortMode.DATE_TAKEN_DESC -> files.sortedWith(compareByDescending<MediaFile> { it.exifDateTime ?: Long.MIN_VALUE }.thenBy { it.name.lowercase() })
            SortMode.RANDOM -> files.shuffled() // Random order for slideshows
        }
    }
    
    /**
     * Apply product flavor-specific media type restrictions.
     * Removes media types that are not supported by the current flavor (injected MediaCapabilities).
     * 
     * For example:
     * - photos flavor: SUPPORT_VIDEO=false, SUPPORT_AUDIO=false → removes VIDEO and AUDIO
     * - lite flavor: SUPPORT_DOCUMENTS=false, ENABLE_EPUB=false -> removes TEXT, PDF, EPUB, OFFICE_DOCUMENT
     */
    private fun applyFlavorMediaTypeRestrictions(supportedTypes: Set<MediaType>): Set<MediaType> {
        return supportedTypes.filter { mediaType ->
            when (mediaType) {
                MediaType.VIDEO -> mediaCapabilities.supportsVideo
                MediaType.AUDIO -> mediaCapabilities.supportsAudio
                MediaType.IMAGE -> mediaCapabilities.supportsImages
                MediaType.GIF -> mediaCapabilities.supportsImages // GIF is considered an image type
                MediaType.TEXT -> mediaCapabilities.supportsDocuments
                MediaType.PDF -> mediaCapabilities.supportsDocuments
                MediaType.EPUB -> mediaCapabilities.supportsEpub
                MediaType.OFFICE_DOCUMENT -> mediaCapabilities.supportsDocuments
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> true
            }
        }.toSet()
    }
}
