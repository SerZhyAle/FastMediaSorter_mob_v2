package com.sza.fastmediasorter.core.cache

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified file cache for all network files.
 * Eliminates duplicate downloads by providing a single cache for:
 * - Image/GIF/Video playback (Glide/ExoPlayer)
 * - PDF/TEXT/EPUB viewing (NetworkFileManager)
 * - Metadata extraction (MediaMetadataHelper)
 * - Thumbnail generation (NetworkPdfThumbnailLoader)
 * 
 * Cache key format: {path.hashCode()}_{fileSize}
 * - Ensures same file from different contexts reuses cache
 * - Size validation prevents stale cache hits
 * 
 * Cache location: {cacheDir}/unified_network_cache/
 */
@Singleton
class UnifiedFileCache @Inject constructor(
    private val context: Context
) {
    private val cacheDir = File(context.cacheDir, "unified_network_cache")
    
    companion object {
        private const val MAX_CACHE_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    /**
     * Get cached file if exists and valid.
     * 
     * @param path Original network path (smb://, sftp://, ftp://)
     * @param size Expected file size in bytes
     * @return Cached file or null if not found/invalid
     */
    fun getCachedFile(path: String, size: Long): File? {
        val cacheKey = generateCacheKey(path, size)
        val cachedFile = File(cacheDir, cacheKey)
        
        // Validate: file exists, size matches, not too old
        if (cachedFile.exists()) {
            if (cachedFile.length() != size) {
                Timber.w("UnifiedFileCache: Size mismatch for $path - expected $size, got ${cachedFile.length()}. Deleting stale cache.")
                cachedFile.delete()
                return null
            }
            
            val age = System.currentTimeMillis() - cachedFile.lastModified()
            if (age > MAX_CACHE_AGE_MS) {
                Timber.d("UnifiedFileCache: Cache expired for $path (age: ${age / 1000 / 60} minutes)")
                cachedFile.delete()
                return null
            }
            
            Timber.d("UnifiedFileCache: Cache HIT - $path (${size / 1024} KB, age: ${age / 1000} sec)")
            return cachedFile
        }
        
        Timber.d("UnifiedFileCache: Cache MISS - $path")
        return null
    }
    
    /**
     * Store file in cache.
     * 
     * @param path Original network path
     * @param size File size in bytes
     * @param sourceFile File to cache (will be copied)
     * @return Cached file reference
     */
    fun putFile(path: String, size: Long, sourceFile: File): File {
        val cacheKey = generateCacheKey(path, size)
        val cachedFile = File(cacheDir, cacheKey)
        
        try {
            if (sourceFile.absolutePath == cachedFile.absolutePath) {
                // Source is already in cache location
                Timber.d("UnifiedFileCache: File already in cache - $path")
                return cachedFile
            }
            
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            sourceFile.copyTo(cachedFile, overwrite = true)
            Timber.d("UnifiedFileCache: Stored file - $path (${size / 1024} KB)")
            return cachedFile
        } catch (e: IOException) {
            Timber.e(e, "UnifiedFileCache: Failed to cache file - $path")
            throw e
        }
    }
    
    /**
     * Get or create file in cache directory directly.
     * Used when downloading directly to cache location.
     * 
     * @param path Original network path
     * @param size File size in bytes
     * @return File reference in cache (may not exist yet)
     */
    fun getCacheFile(path: String, size: Long): File {
        // Ensure cache directory exists before returning file reference
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        val cacheKey = generateCacheKey(path, size)
        return File(cacheDir, cacheKey)
    }
    
    /**
     * Check if file exists in cache and is valid.
     */
    fun isCached(path: String, size: Long): Boolean {
        return getCachedFile(path, size) != null
    }
    
    /**
     * Clear all cached files.
     */
    fun clearAll() {
        try {
            val deletedCount = cacheDir.listFiles()?.count { it.delete() } ?: 0
            Timber.i("UnifiedFileCache: Cleared cache - deleted $deletedCount files")
        } catch (e: Exception) {
            Timber.e(e, "UnifiedFileCache: Failed to clear cache")
        }
    }
    
    /**
     * Clear all cached files (alias for clearAll).
     * @deprecated Use clearAll() instead
     */
    @Deprecated("Use clearAll() instead", ReplaceWith("clearAll()"))
    fun clearCache() {
        clearAll()
    }
    
    /**
     * Get cache statistics.
     */
    fun getCacheStats(): CacheStats {
        val files = cacheDir.listFiles() ?: emptyArray()
        val totalSize = files.sumOf { it.length() }
        val fileCount = files.size
        
        return CacheStats(
            fileCount = fileCount,
            totalSizeBytes = totalSize,
            totalSizeMB = totalSize / 1024 / 1024
        )
    }
    
    /**
     * Generate cache key from path and size.
     */
    private fun generateCacheKey(path: String, size: Long): String {
        return "${path.hashCode()}_$size"
    }
    
    data class CacheStats(
        val fileCount: Int,
        val totalSizeBytes: Long,
        val totalSizeMB: Long
    )
}
