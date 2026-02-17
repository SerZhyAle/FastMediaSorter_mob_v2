package com.sza.fastmediasorter.data.repository

import com.google.gson.Gson
import com.sza.fastmediasorter.data.local.db.CachedFileListDao
import com.sza.fastmediasorter.data.local.db.CachedFileListEntity
import com.sza.fastmediasorter.domain.model.MediaFile
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedFileListRepository @Inject constructor(
    private val cachedFileListDao: CachedFileListDao
) {
    private val gson = Gson()
    
    /**
     * Save file list for a resource in database
     */
    suspend fun saveCachedFiles(resourceId: Long, files: List<MediaFile>) {
        // Check file limit
        if (files.size > 1_000_000) {
            Timber.w("CachedFileList: File count (${files.size}) exceeds limit (1,000,000) for resource $resourceId. Skipping cache save.")
            return
        }
        
        try {
            // Delete old entries first
            cachedFileListDao.deleteByResourceId(resourceId)
            
            // Convert MediaFile to Entity
            val entities = files.map { mediaFile ->
                CachedFileListEntity(
                    resourceId = resourceId,
                    mediaFileJson = gson.toJson(mediaFile)
                )
            }
            
            // Save to database
            cachedFileListDao.insertAll(entities)
            Timber.d("CachedFileList: Saved ${files.size} files for resource $resourceId")
        } catch (e: Exception) {
            Timber.e(e, "CachedFileList: Failed to save files for resource $resourceId")
            throw e
        }
    }
    
    /**
     * Load file list for a resource from database
     */
    suspend fun getCachedFiles(resourceId: Long): List<MediaFile>? {
        return try {
            val entities = cachedFileListDao.getCachedFiles(resourceId)
            if (entities.isEmpty()) {
                Timber.d("CachedFileList: No cached files found for resource $resourceId")
                return null
            }
            
            val files = entities.mapNotNull { entity ->
                try {
                    gson.fromJson(entity.mediaFileJson, MediaFile::class.java)
                } catch (e: Exception) {
                    Timber.e(e, "CachedFileList: Failed to parse MediaFile JSON")
                    null
                }
            }
            
            Timber.d("CachedFileList: Loaded ${files.size} files for resource $resourceId")
            files
        } catch (e: Exception) {
            Timber.e(e, "CachedFileList: Failed to load files for resource $resourceId")
            null
        }
    }
    
    /**
     * Delete cached files for a resource
     */
    suspend fun deleteCachedFiles(resourceId: Long) {
        cachedFileListDao.deleteByResourceId(resourceId)
        Timber.d("CachedFileList: Deleted cached files for resource $resourceId")
    }
    
    /**
     * Delete ALL cached files (called when clearing cache)
     */
    suspend fun deleteAllCachedFiles() {
        cachedFileListDao.deleteAll()
        Timber.d("CachedFileList: Deleted ALL cached files")
    }
    
    /**
     * Check if cached files exist for a resource
     */
    suspend fun hasCachedFiles(resourceId: Long): Boolean {
        return cachedFileListDao.getCount(resourceId) > 0
    }
    
    /**
     * Update file after rename
     */
    suspend fun updateFile(resourceId: Long, oldPath: String, newFile: MediaFile) {
        try {
            val newJson = gson.toJson(newFile)
            cachedFileListDao.updateFile(resourceId, oldPath, newJson)
            Timber.d("CachedFileList: Updated file $oldPath -> ${newFile.path} in resource $resourceId")
        } catch (e: Exception) {
            Timber.e(e, "CachedFileList: Failed to update file in resource $resourceId")
        }
    }
    
    /**
     * Delete file from cache (after delete/move)
     */
    suspend fun deleteFile(resourceId: Long, filePath: String) {
        try {
            cachedFileListDao.deleteFile(resourceId, filePath)
            Timber.d("CachedFileList: Deleted file $filePath from resource $resourceId")
        } catch (e: Exception) {
            Timber.e(e, "CachedFileList: Failed to delete file from resource $resourceId")
        }
    }
}

