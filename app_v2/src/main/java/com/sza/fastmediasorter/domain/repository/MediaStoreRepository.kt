package com.sza.fastmediasorter.domain.repository


import com.sza.fastmediasorter.domain.model.MediaType

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
    ): List<com.sza.fastmediasorter.domain.model.MediaFile>
    
    suspend fun getStandardFolders(): List<FolderInfo>
}
