package com.sza.fastmediasorter.core.cache

import android.util.LruCache
import com.sza.fastmediasorter.domain.model.MediaFile
import timber.log.Timber

/**
 * Singleton cache manager for sharing media files list between BrowseActivity and PlayerActivity.
 * Eliminates need for re-scanning when navigating between screens.
 * 
 * Uses LruCache with 128MB limit to prevent memory leaks while keeping data across configuration changes.
 * LruCache automatically evicts least recently used entries when memory limit is reached,
 * but preserves data when app goes to background (unlike simple HashMap which is cleared by GC).
 */
object MediaFilesCacheManager {
    
    // Calculate cache size: 128MB = 128 * 1024 * 1024 bytes
    // Average MediaFile size ~500 bytes, so this can hold ~260,000 files across all resources
    private const val CACHE_SIZE_BYTES = 128 * 1024 * 1024
    
    // Cache key = resourceId, value = list of MediaFiles
    // LruCache requires size calculation via sizeOf() override
    private val cache = object : LruCache<Long, MutableList<MediaFile>>(CACHE_SIZE_BYTES) {
        override fun sizeOf(key: Long, value: MutableList<MediaFile>): Int {
            // Estimate: each MediaFile ~500 bytes (path 200 + metadata 300)
            return value.size * 500
        }
        
        override fun entryRemoved(
            evicted: Boolean,
            key: Long,
            oldValue: MutableList<MediaFile>,
            newValue: MutableList<MediaFile>?
        ) {
            if (evicted) {
                Timber.w("MediaFilesCache: Evicted resource $key with ${oldValue.size} files due to memory pressure")
            }
        }
    }
    
    /**
     * Stores cached list for a resource. Creates defensive copy to prevent external modifications.
     * Thread-safe: LruCache handles synchronization internally.
     * Auto-fixes cloud paths if needed (cloud:/ → cloud://).
     */
    fun setCachedList(resourceId: Long, files: List<MediaFile>) {
        val fixedFiles = files.map { file ->
            if (file.path.startsWith("cloud:/") && !file.path.startsWith("cloud://")) {
                Timber.w("MediaFilesCache: Auto-fixing cloud path: ${file.path}")
                file.copy(path = file.path.replaceFirst("cloud:/", "cloud://"))
            } else {
                file
            }
        }
        cache.put(resourceId, fixedFiles.toMutableList())
        Timber.d("MediaFilesCache: Cached ${fixedFiles.size} files for resource $resourceId")
    }
    
    /**
     * Retrieves cached list for a resource. Returns defensive copy.
     * Thread-safe: LruCache handles synchronization internally.
     */
    fun getCachedList(resourceId: Long): List<MediaFile>? {
        val files = cache.get(resourceId)?.toList()
        Timber.d("MediaFilesCache: Retrieved ${files?.size ?: 0} files for resource $resourceId")
        return files
    }
    
    /**
     * Updates a file in the cached list (after rename operation).
     * @return true if file was found and updated, false otherwise
     */
    fun updateFile(resourceId: Long, oldPath: String, newFile: MediaFile): Boolean {
        val list = cache.get(resourceId) ?: return false
        val index = list.indexOfFirst { it.path == oldPath }
        if (index >= 0) {
            list[index] = newFile
            Timber.d("MediaFilesCache: Updated file at index $index (${oldPath} → ${newFile.path})")
            return true
        }
        Timber.w("MediaFilesCache: File not found for update: $oldPath")
        return false
    }
    
    /**
     * Removes a file from the cached list (after delete/move operation).
     * Normalizes URIs by decoding before comparison to handle both encoded and decoded paths.
     * @return true if file was found and removed, false otherwise
     */
    fun removeFile(resourceId: Long, filePath: String): Boolean {
        val list = cache.get(resourceId) ?: return false
        
        // Normalize path for comparison (decode URI if it's encoded)
        val normalizedPath = try {
            if (filePath.startsWith("content://")) {
                android.net.Uri.decode(filePath)
            } else {
                filePath
            }
        } catch (e: Exception) {
            Timber.w(e, "MediaFilesCache: Failed to decode path, using as-is: $filePath")
            filePath
        }
        
        val removed = list.removeAll { cachedFile ->
            // Normalize cached file path for comparison
            val cachedPath = try {
                if (cachedFile.path.startsWith("content://")) {
                    android.net.Uri.decode(cachedFile.path)
                } else {
                    cachedFile.path
                }
            } catch (e: Exception) {
                Timber.w(e, "MediaFilesCache: Failed to decode cached path, using as-is: ${cachedFile.path}")
                cachedFile.path
            }
            
            cachedPath == normalizedPath
        }
        
        if (removed) {
            Timber.d("MediaFilesCache: Removed file $filePath from resource $resourceId (${list.size} files remaining)")
        } else {
            Timber.w("MediaFilesCache: File not found for removal: $filePath (normalized: $normalizedPath)")
        }
        return removed
    }
    
    /**
     * Adds a file to the cached list (after move-in operation from another resource).
     * Inserts in correct position based on current sort order (caller's responsibility to sort).
     */
    fun addFile(resourceId: Long, file: MediaFile) {
        val list = cache.get(resourceId) ?: mutableListOf<MediaFile>().also { cache.put(resourceId, it) }
        list.add(file)
        Timber.d("MediaFilesCache: Added file ${file.path} to resource $resourceId (${list.size} files total)")
    }
    
    /**
     * Clears cache for a specific resource (e.g., on explicit refresh).
     */
    fun clearCache(resourceId: Long) {
        cache.remove(resourceId)
        Timber.d("MediaFilesCache: Cleared cache for resource $resourceId")
    }
    
    /**
     * Clears all cached lists (e.g., on app logout or memory pressure).
     */
    fun clearAllCaches() {
        cache.evictAll()
        Timber.d("MediaFilesCache: Cleared all caches")
    }
    
    /**
     * Checks if a resource has cached data.
     */
    fun isCached(resourceId: Long): Boolean {
        return cache.get(resourceId) != null
    }
    
    /**
     * Gets current size of cached list without retrieving it.
     */
    fun getCacheSize(resourceId: Long): Int {
        return cache.get(resourceId)?.size ?: 0
    }
    
    /**
     * Fixes cloud paths in cached lists: cloud:/google_drive/ → cloud://google_drive/
     * Called once on app startup to migrate old path format.
     * Returns number of fixed paths.
     */
    fun fixCloudPaths(): Int {
        var fixedCount = 0
        val snapshot = cache.snapshot()
        
        snapshot.forEach { (resourceId, files) ->
            val updatedFiles = files.map { file ->
                if (file.path.startsWith("cloud:/") && !file.path.startsWith("cloud://")) {
                    fixedCount++
                    file.copy(path = file.path.replaceFirst("cloud:/", "cloud://"))
                } else {
                    file
                }
            }.toMutableList()
            
            if (fixedCount > 0) {
                cache.put(resourceId, updatedFiles)
            }
        }
        
        if (fixedCount > 0) {
            Timber.i("MediaFilesCache: Fixed $fixedCount cloud paths (cloud:/ → cloud://)")
        }
        
        return fixedCount
    }
}
