package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

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
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<MediaFile>
    
    /**
     * Scan folder with pagination support.
     * @param offset Starting position (0-based)
     * @param limit Maximum number of files to return
     * @return MediaFilePage with files and hasMore flag
     */
    suspend fun scanFolderPaged(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter? = null,
        offset: Int,
        limit: Int,
        credentialsId: String? = null
    ): MediaFilePage
    
    suspend fun getFileCount(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter? = null,
        credentialsId: String? = null
    ): Int
    
    suspend fun isWritable(path: String, credentialsId: String? = null): Boolean
}

class GetMediaFilesUseCase @Inject constructor(
    private val mediaScannerFactory: MediaScannerFactory
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
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Flow<List<MediaFile>> = flow {
        timber.log.Timber.d("GetMediaFilesUseCase: Starting scan for ${resource.name}, useChunked=$useChunkedLoading")
        
        val scanner = mediaScannerFactory.getScanner(resource.type)
        
        timber.log.Timber.d("GetMediaFilesUseCase: Got scanner type=${scanner.javaClass.simpleName}")
        
        val files = if (useChunkedLoading && scanner is com.sza.fastmediasorter.data.network.SmbMediaScanner) {
            timber.log.Timber.d("GetMediaFilesUseCase: Using chunked loading, maxFiles=$maxFiles")
            // Use chunked loading for SMB to quickly show first files
            scanner.scanFolderChunked(
                path = resource.path,
                supportedTypes = resource.supportedMediaTypes,
                sizeFilter = sizeFilter,
                maxFiles = maxFiles,
                credentialsId = resource.credentialsId
            )
        } else {
            timber.log.Timber.d("GetMediaFilesUseCase: Using standard loading")
            // Standard full scan for other types with progress reporting
            scanner.scanFolder(
                path = resource.path,
                supportedTypes = resource.supportedMediaTypes,
                sizeFilter = sizeFilter,
                credentialsId = resource.credentialsId,
                onProgress = onProgress
            )
        }
        
        timber.log.Timber.d("GetMediaFilesUseCase: Scanned ${files.size} files, sorting by $sortMode")
        
        // Skip sorting for large folders (> 1000 files) - improves performance
        val sortedFiles = if (files.size > LARGE_FOLDER_THRESHOLD) {
            timber.log.Timber.d("GetMediaFilesUseCase: Large folder (${files.size} files), skipping sort for better performance")
            files  // Return unsorted for large folders
        } else {
            sortFiles(files, sortMode)
        }
        
        emit(sortedFiles)
        
        timber.log.Timber.d("GetMediaFilesUseCase: Emitted sorted files")
    }.flowOn(Dispatchers.IO) // Execute scanning and sorting on IO thread

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
            SortMode.RANDOM -> files.shuffled() // Random order for slideshows
        }
    }
}
