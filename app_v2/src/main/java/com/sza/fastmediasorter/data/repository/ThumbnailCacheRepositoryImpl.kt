package com.sza.fastmediasorter.data.repository

import android.content.Context
import com.sza.fastmediasorter.data.local.db.ThumbnailCacheDao
import com.sza.fastmediasorter.data.local.db.ThumbnailCacheEntity
import com.sza.fastmediasorter.domain.repository.CacheStats
import com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ThumbnailCacheRepository.
 * Stores thumbnails in app files directory with Room database index.
 * 
 * Cache directory: context.filesDir/thumbnails/
 * Database: thumbnail_cache table
 * 
 * NOTE: Using filesDir instead of cacheDir to ensure persistence between app restarts.
 * Android can clear cacheDir at any time, but filesDir is preserved unless manually cleared.
 */
@Singleton
class ThumbnailCacheRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val thumbnailCacheDao: ThumbnailCacheDao
) : ThumbnailCacheRepository {
    
    private val cacheDir: File by lazy {
        File(context.filesDir, "thumbnails").apply {
            if (!exists()) {
                mkdirs()
                Timber.d("ThumbnailCache: Created cache directory: $absolutePath")
            }
        }
    }
    
    override suspend fun getCachedThumbnail(filePath: String): File? {
        return try {
            val cacheEntry = thumbnailCacheDao.getThumbnail(filePath) ?: return null
            val thumbnailFile = File(cacheEntry.thumbnailPath)
            
            if (!thumbnailFile.exists()) {
                // File deleted but database entry exists - clean up
                Timber.w("ThumbnailCache: Cached file missing, deleting entry: $filePath")
                thumbnailCacheDao.deleteThumbnail(filePath)
                return null
            }
            
            thumbnailCacheDao.updateAccessTime(filePath, System.currentTimeMillis())
            
            Timber.v("ThumbnailCache: Cache HIT for $filePath")
            thumbnailFile
        } catch (e: Exception) {
            Timber.e(e, "ThumbnailCache: Error getting cached thumbnail for $filePath")
            null
        }
    }
    
    override suspend fun saveThumbnail(filePath: String, thumbnailFile: File) {
        try {
            if (!thumbnailFile.exists()) {
                Timber.w("ThumbnailCache: Cannot save non-existent file: ${thumbnailFile.absolutePath}")
                return
            }
            
            val currentTime = System.currentTimeMillis()
            val cacheEntry = ThumbnailCacheEntity(
                filePath = filePath,
                thumbnailPath = thumbnailFile.absolutePath,
                fileSize = thumbnailFile.length(),
                createdAt = currentTime,
                lastAccessedAt = currentTime
            )
            
            thumbnailCacheDao.insertThumbnail(cacheEntry)
            Timber.v("ThumbnailCache: Saved thumbnail for $filePath (${thumbnailFile.length()} bytes)")
        } catch (e: Exception) {
            Timber.e(e, "ThumbnailCache: Error saving thumbnail for $filePath")
        }
    }
    
    override suspend fun deleteThumbnail(filePath: String) {
        try {
            val cacheEntry = thumbnailCacheDao.getThumbnail(filePath)
            if (cacheEntry != null) {
                // Delete physical file
                val thumbnailFile = File(cacheEntry.thumbnailPath)
                if (thumbnailFile.exists()) {
                    thumbnailFile.delete()
                    Timber.d("ThumbnailCache: Deleted physical file: ${thumbnailFile.absolutePath}")
                }
                
                // Delete database entry
                thumbnailCacheDao.deleteThumbnail(filePath)
                Timber.d("ThumbnailCache: Deleted cache entry for $filePath")
            }
        } catch (e: Exception) {
            Timber.e(e, "ThumbnailCache: Error deleting thumbnail for $filePath")
        }
    }
    
    override suspend fun cleanupOldThumbnails(days: Int): Int {
        return try {
            val cutoffTime = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
            
            // Get old entries to delete physical files
            val oldEntries = thumbnailCacheDao.getThumbnailsOlderThan(cutoffTime)
            
            // Delete physical files
            var deletedFiles = 0
            oldEntries.forEach { entry ->
                val file = File(entry.thumbnailPath)
                if (file.exists() && file.delete()) {
                    deletedFiles++
                }
            }
            
            // Delete database entries
            val deletedEntries = thumbnailCacheDao.deleteOldThumbnails(cutoffTime)
            
            Timber.i("ThumbnailCache: Cleanup completed - deleted $deletedEntries entries, $deletedFiles files (older than $days days)")
            deletedEntries
        } catch (e: Exception) {
            Timber.e(e, "ThumbnailCache: Error during cleanup")
            0
        }
    }
    
    override suspend fun getCacheStats(): CacheStats {
        return try {
            val count = thumbnailCacheDao.getCacheCount()
            val size = thumbnailCacheDao.getTotalCacheSize()
            CacheStats(count, size)
        } catch (e: Exception) {
            Timber.e(e, "ThumbnailCache: Error getting cache stats")
            CacheStats(0, 0)
        }
    }
    
    override suspend fun enforceSizeLimit(maxBytes: Long): Int {
        return try {
            val totalSize = thumbnailCacheDao.getTotalCacheSize()
            if (totalSize <= maxBytes) {
                Timber.d("ThumbnailCache: size ${totalSize / 1024 / 1024}MB is within limit ${maxBytes / 1024 / 1024}MB - no eviction needed")
                return 0
            }

            // Evict LRU entries (oldest lastAccessedAt first) until under limit
            val entries = thumbnailCacheDao.getAllByLruOrder()
            var freed = 0L
            val toDelete = mutableListOf<String>()

            for (entry in entries) {
                if (totalSize - freed <= maxBytes) break
                val file = File(entry.thumbnailPath)
                if (file.exists()) {
                    freed += file.length()
                    file.delete()
                }
                toDelete.add(entry.filePath)
            }

            val deleted = if (toDelete.isNotEmpty()) thumbnailCacheDao.deleteByPaths(toDelete) else 0
            Timber.i("ThumbnailCache: evicted $deleted entries (${freed / 1024 / 1024}MB freed) to enforce ${maxBytes / 1024 / 1024}MB limit")
            deleted
        } catch (e: Exception) {
            Timber.e(e, "ThumbnailCache: error enforcing size limit")
            0
        }
    }

    /**
     * Migrate thumbnails from old cache directory (cacheDir) to new persistent directory (filesDir).
     * Should be called once during app startup.
     */
    suspend fun migrateLegacyCache() {
        try {
            val oldCacheDir = File(context.cacheDir, "thumbnails")
            if (!oldCacheDir.exists()) {
                Timber.d("ThumbnailCache: No legacy cache to migrate")
                return
            }
            
            val oldFiles = oldCacheDir.listFiles() ?: emptyArray()
            if (oldFiles.isEmpty()) {
                Timber.d("ThumbnailCache: Legacy cache directory is empty")
                oldCacheDir.delete()
                return
            }
            
            var migratedCount = 0
            var errorCount = 0
            
            oldFiles.forEach { oldFile ->
                try {
                    if (oldFile.isFile) {
                        // Find database entry for this file
                        val entries = thumbnailCacheDao.getAllThumbnails()
                        val matchingEntry = entries.find { File(it.thumbnailPath) == oldFile }
                        
                        if (matchingEntry != null) {
                            // Move file to new location
                            val newFile = File(cacheDir, oldFile.name)
                            if (oldFile.renameTo(newFile)) {
                                // Update database with new path
                                thumbnailCacheDao.updateThumbnailPath(matchingEntry.filePath, newFile.absolutePath)
                                migratedCount++
                                Timber.d("ThumbnailCache: Migrated ${oldFile.name}")
                            } else {
                                errorCount++
                                Timber.w("ThumbnailCache: Failed to move ${oldFile.name}")
                            }
                        } else {
                            // Orphaned file without database entry - delete it
                            oldFile.delete()
                            Timber.d("ThumbnailCache: Deleted orphaned file ${oldFile.name}")
                        }
                    }
                } catch (e: Exception) {
                    errorCount++
                    Timber.e(e, "ThumbnailCache: Error migrating ${oldFile.name}")
                }
            }
            
            // Clean up old directory if empty
            val remainingFiles = oldCacheDir.listFiles() ?: emptyArray()
            if (remainingFiles.isEmpty()) {
                oldCacheDir.delete()
                Timber.i("ThumbnailCache: Migration complete - moved $migratedCount files, $errorCount errors. Old directory removed.")
            } else {
                Timber.w("ThumbnailCache: Migration complete - moved $migratedCount files, $errorCount errors. ${remainingFiles.size} files remain in old directory.")
            }
        } catch (e: Exception) {
            Timber.e(e, "ThumbnailCache: Failed to migrate legacy cache")
        }
    }}