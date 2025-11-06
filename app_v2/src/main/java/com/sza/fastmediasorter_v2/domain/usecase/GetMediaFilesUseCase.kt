package com.sza.fastmediasorter_v2.domain.usecase

import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

interface MediaScanner {
    suspend fun scanFolder(path: String, supportedTypes: Set<MediaType>): List<MediaFile>
    suspend fun getFileCount(path: String, supportedTypes: Set<MediaType>): Int
    suspend fun isWritable(path: String): Boolean
}

class GetMediaFilesUseCase @Inject constructor(
    private val mediaScanner: MediaScanner
) {
    operator fun invoke(
        resource: MediaResource,
        sortMode: SortMode = SortMode.NAME_ASC
    ): Flow<List<MediaFile>> = flow {
        val files = mediaScanner.scanFolder(
            path = resource.path,
            supportedTypes = resource.supportedMediaTypes
        )
        emit(sortFiles(files, sortMode))
    }

    private fun sortFiles(files: List<MediaFile>, mode: SortMode): List<MediaFile> {
        return when (mode) {
            SortMode.NAME_ASC -> files.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
            SortMode.DATE_ASC -> files.sortedBy { it.createdDate }
            SortMode.DATE_DESC -> files.sortedByDescending { it.createdDate }
            SortMode.SIZE_ASC -> files.sortedBy { it.size }
            SortMode.SIZE_DESC -> files.sortedByDescending { it.size }
            SortMode.TYPE_ASC -> files.sortedBy { it.type.ordinal }
            SortMode.TYPE_DESC -> files.sortedByDescending { it.type.ordinal }
        }
    }
}
