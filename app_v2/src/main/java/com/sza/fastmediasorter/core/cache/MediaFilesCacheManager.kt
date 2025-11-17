package com.sza.fastmediasorter.core.cache

import com.sza.fastmediasorter.domain.model.MediaFile
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton cache manager for sharing media files list between BrowseActivity and PlayerActivity.
 * Eliminates need for re-scanning when navigating between screens.
 * Thread-safe implementation using ConcurrentHashMap.
 */
object MediaFilesCacheManager {
    
    // Cache key = resourceId
    private val cache = ConcurrentHashMap<Long, MutableList<MediaFile>>()
    
    /**
     * Stores cached list for a resource. Creates defensive copy to prevent external modifications.
     */
    fun setCachedList(resourceId: Long, files: List<MediaFile>) {
        cache[resourceId] = files.toMutableList()
        Timber.d("MediaFilesCache: Cached ${files.size} files for resource $resourceId")
    }
    
    /**
     * Retrieves cached list for a resource. Returns defensive copy.
     */
    fun getCachedList(resourceId: Long): List<MediaFile>? {
        val files = cache[resourceId]?.toList()
        Timber.d("MediaFilesCache: Retrieved ${files?.size ?: 0} files for resource $resourceId")
        return files
    }
    
    /**
     * Updates a file in the cached list (after rename operation).
     * @return true if file was found and updated, false otherwise
     */
    fun updateFile(resourceId: Long, oldPath: String, newFile: MediaFile): Boolean {
        val list = cache[resourceId] ?: return false
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
     * @return true if file was found and removed, false otherwise
     */
    fun removeFile(resourceId: Long, filePath: String): Boolean {
        val list = cache[resourceId] ?: return false
        val removed = list.removeAll { it.path == filePath }
        if (removed) {
            Timber.d("MediaFilesCache: Removed file $filePath from resource $resourceId (${list.size} files remaining)")
        } else {
            Timber.w("MediaFilesCache: File not found for removal: $filePath")
        }
        return removed
    }
    
    /**
     * Adds a file to the cached list (after move-in operation from another resource).
     * Inserts in correct position based on current sort order (caller's responsibility to sort).
     */
    fun addFile(resourceId: Long, file: MediaFile) {
        val list = cache[resourceId] ?: mutableListOf<MediaFile>().also { cache[resourceId] = it }
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
        cache.clear()
        Timber.d("MediaFilesCache: Cleared all caches")
    }
    
    /**
     * Checks if a resource has cached data.
     */
    fun isCached(resourceId: Long): Boolean {
        return cache.containsKey(resourceId)
    }
    
    /**
     * Gets current size of cached list without retrieving it.
     */
    fun getCacheSize(resourceId: Long): Int {
        return cache[resourceId]?.size ?: 0
    }
}
