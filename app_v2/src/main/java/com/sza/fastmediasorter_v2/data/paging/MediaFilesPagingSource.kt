package com.sza.fastmediasorter_v2.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.SortMode
import com.sza.fastmediasorter_v2.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter_v2.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter_v2.domain.usecase.SizeFilter
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * PagingSource для постраничной загрузки медиафайлов.
 * Загружает файлы порциями для улучшения производительности при больших списках.
 */
class MediaFilesPagingSource(
    private val resource: MediaResource,
    private val sortMode: SortMode,
    private val sizeFilter: SizeFilter,
    private val mediaScannerFactory: MediaScannerFactory
) : PagingSource<Int, MediaFile>() {

    companion object {
        const val PAGE_SIZE = 50
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaFile> {
        return try {
            val page = params.key ?: 0
            val offset = page * PAGE_SIZE
            
            // Get scanner for resource type
            val scanner = mediaScannerFactory.getScanner(resource.type)
            
            // Scan folder with pagination support
            val result = scanner.scanFolderPaged(
                path = resource.path,
                supportedTypes = resource.supportedMediaTypes,
                sizeFilter = sizeFilter,
                offset = offset,
                limit = PAGE_SIZE,
                credentialsId = resource.credentialsId
            )
            
            // Apply sorting to page
            val sortedFiles = sortFiles(result.files, sortMode)
            
            Timber.d("Loaded page $page with ${sortedFiles.size} files, hasMore=${result.hasMore}")
            
            LoadResult.Page(
                data = sortedFiles,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (result.hasMore) page + 1 else null
            )
        } catch (e: Exception) {
            Timber.e(e, "Error loading media files page")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MediaFile>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
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
