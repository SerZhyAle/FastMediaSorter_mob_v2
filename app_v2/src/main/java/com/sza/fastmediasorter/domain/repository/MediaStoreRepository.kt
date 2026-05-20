package com.sza.fastmediasorter.domain.repository


import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.usecase.SizeFilter

interface MediaStoreRepository {
    
    suspend fun getFoldersWithMedia(allowedTypes: Set<MediaType>): List<FolderInfo>
    
    data class FolderInfo(
        val path: String,
        val name: String,
        val fileCount: Int,
        val containedTypes: Set<MediaType>
    )

    suspend fun getFilesInFolder(
        folderPath: String, 
        allowedTypes: Set<MediaType>, 
        recursive: Boolean = false,
        showHiddenFiles: Boolean = false
    ): List<MediaFile>

    suspend fun getRecentFiles(
        limit: Int,
        allowedTypes: Set<MediaType>
    ): List<MediaFile>

    /**
     * Query MediaStore for all files matching the given types across all volumes.
     * No sorting guarantee - files returned in MediaStore's natural order.
     *
     * @param allowedTypes  Set of media types to include
     * @param limit         Maximum number of files to return
     * @param showHiddenFiles  Whether to include files starting with '.'
     * @return Flat list of MediaFile, up to [limit] entries
     */
    suspend fun getAllFilesByTypes(
        allowedTypes: Set<MediaType>,
        limit: Int,
        showHiddenFiles: Boolean = false
    ): List<MediaFile>

    /**
     * Count files matching the given types across all volumes without materializing full MediaFile objects.
     * The returned count respects the same limit and filtering rules as [getAllFilesByTypes].
     */
    suspend fun countAllFilesByTypes(
        allowedTypes: Set<MediaType>,
        limit: Int,
        showHiddenFiles: Boolean = false,
        sizeFilter: SizeFilter? = null
    ): Int
    
    suspend fun getStandardFolders(): List<FolderInfo>

    suspend fun findCameraFolderPath(): String?
}
