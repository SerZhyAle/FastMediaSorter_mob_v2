package com.sza.fastmediasorter_v2.domain.usecase

import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
        credentialsId: String? = null
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
    operator fun invoke(
        resource: MediaResource,
        sortMode: SortMode = SortMode.NAME_ASC,
        sizeFilter: SizeFilter? = null,
        useChunkedLoading: Boolean = false,
        maxFiles: Int = 100
    ): Flow<List<MediaFile>> = flow {
        val scanner = mediaScannerFactory.getScanner(resource.type)
        
        val files = if (useChunkedLoading && scanner is com.sza.fastmediasorter_v2.data.network.SmbMediaScanner) {
            // Use chunked loading for SMB to quickly show first files
            scanner.scanFolderChunked(
                path = resource.path,
                supportedTypes = resource.supportedMediaTypes,
                sizeFilter = sizeFilter,
                maxFiles = maxFiles,
                credentialsId = resource.credentialsId
            )
        } else {
            // Standard full scan for other types
            scanner.scanFolder(
                path = resource.path,
                supportedTypes = resource.supportedMediaTypes,
                sizeFilter = sizeFilter,
                credentialsId = resource.credentialsId
            )
        }
        
        emit(sortFiles(files, sortMode))
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
            SortMode.RANDOM -> files.shuffled() // Random order for slideshows
        }
    }
}
