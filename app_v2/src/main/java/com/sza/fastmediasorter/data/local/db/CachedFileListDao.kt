package com.sza.fastmediasorter.data.local.db

import androidx.room.*

@Dao
interface CachedFileListDao {
    
    @Query("SELECT * FROM cached_file_lists WHERE resourceId = :resourceId ORDER BY id ASC")
    suspend fun getCachedFiles(resourceId: Long): List<CachedFileListEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<CachedFileListEntity>)
    
    @Query("DELETE FROM cached_file_lists WHERE resourceId = :resourceId")
    suspend fun deleteByResourceId(resourceId: Long)
    
    @Query("DELETE FROM cached_file_lists")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM cached_file_lists WHERE resourceId = :resourceId")
    suspend fun getCount(resourceId: Long): Int
    
    @Query("UPDATE cached_file_lists SET media_file_json = :newMediaFileJson WHERE resourceId = :resourceId AND media_file_json LIKE '%' || :oldPath || '%'")
    suspend fun updateFile(resourceId: Long, oldPath: String, newMediaFileJson: String): Int
    
    @Query("DELETE FROM cached_file_lists WHERE resourceId = :resourceId AND media_file_json LIKE '%' || :filePath || '%'")
    suspend fun deleteFile(resourceId: Long, filePath: String): Int
}
