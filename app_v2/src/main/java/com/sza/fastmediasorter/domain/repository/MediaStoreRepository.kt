package com.sza.fastmediasorter.domain.repository


import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.MediaFile

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
    
    suspend fun getStandardFolders(): List<FolderInfo>
}
