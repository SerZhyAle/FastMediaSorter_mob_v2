package com.sza.fastmediasorter.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * PagingSource for paged loading of media files.
 * Loads files in chunks to improve performance for large lists.
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
                credentialsId = resource.credentialsId,
                scanSubdirectories = resource.scanSubdirectories
            )
            
            // IMPORTANT: Do NOT sort pages here for NAME_ASC mode
            // scanFolderPaged() already returns files in sorted order (by name, case-insensitive)
            // Sorting individual pages would break global sort order
            // For other sort modes (DATE, SIZE), full scan is needed anyway
            val files = when (sortMode) {
                SortMode.NAME_ASC -> result.files // Already sorted by scanner
                SortMode.NAME_DESC -> result.files.reversed() // Reverse already sorted list
                else -> {
                    // For DATE/SIZE/TYPE sorting, we need all files - pagination breaks these modes
                    // This is a known limitation: pagination only works correctly with NAME sorting
                    Timber.w("Pagination with sort mode $sortMode may produce incorrect order. Use NAME_ASC for best results.")
                    sortFiles(result.files, sortMode)
                }
            }
            
            Timber.d("Loaded page $page: offset=$offset, returned=${files.size}, hasMore=${result.hasMore}")
            
            LoadResult.Page(
                data = files,
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
