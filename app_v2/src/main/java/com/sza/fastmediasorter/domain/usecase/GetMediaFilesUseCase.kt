package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.BuildConfig
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
import javax.inject.Inject
import com.sza.fastmediasorter.domain.repository.FavoritesRepository

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
    private val credentialsRepository: com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
) {
    companion object {
        private const val LARGE_FOLDER_THRESHOLD = 1000
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
        isSubfolderMode: Boolean = false
    ): Flow<List<MediaFile>> = flow {
        timber.log.Timber.d("GetMediaFilesUseCase: START invoke - resource='${resource.name}' (id=${resource.id}), type=${resource.type}, showHiddenFiles=$showHiddenFiles, currentPath=$currentPath, isSubfolderMode=$isSubfolderMode")
        timber.log.Timber.d("GetMediaFilesUseCase: path='${resource.path}', useChunked=$useChunkedLoading, sortMode=$sortMode")
        
        // Handle virtual Favorites resource
        if (resource.id == -100L) {
            timber.log.Timber.d("GetMediaFilesUseCase: Loading Favorites from repository")
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
            // Apply sorting
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
                timber.log.Timber.d("GetMediaFilesUseCase: Using existing SMB URL: $cleanPath")
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
                    timber.log.Timber.d("GetMediaFilesUseCase: Built full SMB path: $builtPath (from ${resource.path}, relativePath='$relativePath')")
                    builtPath
                } else {
                    timber.log.Timber.w("GetMediaFilesUseCase: Credentials not found or invalid for credentialsId=${resource.credentialsId}, using raw path")
                    resource.path
                }
            }
        } else {
            resource.path
        }
        
        // Determine effective path: use currentPath if in subfolder mode, otherwise use resource path
        val effectivePath = if (isSubfolderMode && currentPath != null) {
            timber.log.Timber.d("GetMediaFilesUseCase: Using currentPath for subfolder navigation: $currentPath")
            currentPath
        } else {
            fullPath
        }
        
        val scanner = mediaScannerFactory.getScanner(resource.type)
        
        timber.log.Timber.d("GetMediaFilesUseCase: Got scanner type=${scanner.javaClass.simpleName}")
        
        // Apply flavor-specific media type restrictions
        val flavorFilteredTypes = applyFlavorMediaTypeRestrictions(resource.supportedMediaTypes)
        timber.log.Timber.d("GetMediaFilesUseCase: Calling scanner with supportedTypes=${flavorFilteredTypes.map { it.name }} (original: ${resource.supportedMediaTypes.map { it.name }})")
        
        // In subfolder mode: list directory contents (files + folders in current directory only)
        // Otherwise: scan recursively (traditional behavior)
        val files = if (isSubfolderMode) {
            timber.log.Timber.d("GetMediaFilesUseCase: Using listDirectoryContents for subfolder mode, path=$effectivePath")
            scanner.listDirectoryContents(
                path = effectivePath,
                supportedTypes = flavorFilteredTypes,
                sizeFilter = sizeFilter,
                credentialsId = resource.credentialsId,
                showHiddenFiles = showHiddenFiles
            )
        } else if (useChunkedLoading && scanner is com.sza.fastmediasorter.data.network.SmbMediaScanner) {
            timber.log.Timber.d("GetMediaFilesUseCase: Using chunked loading, maxFiles=$maxFiles")
            // Use chunked loading for SMB to quickly show first files
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
            timber.log.Timber.d("GetMediaFilesUseCase: Using standard loading")
            // Standard full scan for other types with progress reporting
            val scanStartTime = System.currentTimeMillis()
            val result = scanner.scanFolder(
                path = effectivePath,
                supportedTypes = flavorFilteredTypes,
                sizeFilter = sizeFilter,
                credentialsId = resource.credentialsId,
                scanSubdirectories = resource.scanSubdirectories,
                showHiddenFiles = showHiddenFiles,
                onProgress = onProgress
            )
            val scanDuration = System.currentTimeMillis() - scanStartTime
            timber.log.Timber.d("GetMediaFilesUseCase: scanFolder completed in ${scanDuration}ms")
            result
        }
        
        timber.log.Timber.d("GetMediaFilesUseCase: Scanner returned ${files.size} files")
        
        // Fetch favorites to mark files
        val favoriteUris = try {
            favoritesRepository.getAllFavorites().first().map { it.uri }.toSet()
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to fetch favorites")
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
        // Without enrichment those fields are null, making metadata-based sorts
        // indistinguishable from NAME_ASC.
        val metadataReadyFiles = if (resource.rememberFileList || needsMetadataForSort(sortMode)) {
            filesWithFavorites.map { file ->
                CachedMediaMetadataExtractor.enrichForCache(file)
            }.also { CachedMediaMetadataExtractor.logSessionDiagnostics("scan resource=${resource.id}") }
        } else {
            filesWithFavorites
        }
        
        // Skip sorting for large folders (> 1000 files) - improves performance
        val sortedFiles = if (metadataReadyFiles.size > LARGE_FOLDER_THRESHOLD) {
            timber.log.Timber.d("GetMediaFilesUseCase: Large folder (${metadataReadyFiles.size} files), skipping sort for better performance")
            metadataReadyFiles  // Return unsorted for large folders
        } else {
            timber.log.Timber.d("GetMediaFilesUseCase: Small folder (${metadataReadyFiles.size} files), sorting by $sortMode")
            sortFiles(metadataReadyFiles, sortMode)
        }
        
        timber.log.Timber.d("GetMediaFilesUseCase: Emitting ${sortedFiles.size} files to flow")
        emit(sortedFiles)
        
        timber.log.Timber.d("GetMediaFilesUseCase: COMPLETE - flow emission done")
    }.flowOn(Dispatchers.IO) // Execute scanning and sorting on IO thread

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
     * Removes media types that are not supported by the current flavor (BuildConfig).
     * 
     * For example:
     * - photos flavor: SUPPORT_VIDEO=false, SUPPORT_AUDIO=false → removes VIDEO and AUDIO
     * - lite flavor: SUPPORT_DOCUMENTS=false, ENABLE_EPUB=false → removes TEXT, PDF, EPUB
     */
    private fun applyFlavorMediaTypeRestrictions(supportedTypes: Set<MediaType>): Set<MediaType> {
        return supportedTypes.filter { mediaType ->
            when (mediaType) {
                MediaType.VIDEO -> BuildConfig.SUPPORT_VIDEO
                MediaType.AUDIO -> BuildConfig.SUPPORT_AUDIO
                MediaType.IMAGE -> BuildConfig.SUPPORT_IMAGES
                MediaType.GIF -> BuildConfig.SUPPORT_IMAGES // GIF is considered an image type
                MediaType.TEXT -> BuildConfig.SUPPORT_DOCUMENTS
                MediaType.PDF -> BuildConfig.SUPPORT_DOCUMENTS
                MediaType.EPUB -> BuildConfig.ENABLE_EPUB
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> false
            }
        }.toSet()
    }
}
