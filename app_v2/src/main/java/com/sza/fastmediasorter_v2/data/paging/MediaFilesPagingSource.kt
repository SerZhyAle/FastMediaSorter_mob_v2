package com.sza.fastmediasorter_v2.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.SortMode
import com.sza.fastmediasorter_v2.domain.usecase.GetMediaFilesUseCase
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
    private val getMediaFilesUseCase: GetMediaFilesUseCase
) : PagingSource<Int, MediaFile>() {

    companion object {
        const val PAGE_SIZE = 50
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaFile> {
        return try {
            val page = params.key ?: 0
            
            // Get all files (we'll implement chunked loading in usecase later)
            val allFiles = getMediaFilesUseCase(resource, sortMode, sizeFilter).first()
            
            // Calculate pagination
            val startIndex = page * PAGE_SIZE
            val endIndex = minOf(startIndex + PAGE_SIZE, allFiles.size)
            
            val files = if (startIndex < allFiles.size) {
                allFiles.subList(startIndex, endIndex)
            } else {
                emptyList()
            }
            
            Timber.d("Loaded page $page with ${files.size} files (total: ${allFiles.size})")
            
            LoadResult.Page(
                data = files,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (endIndex >= allFiles.size) null else page + 1
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
}
