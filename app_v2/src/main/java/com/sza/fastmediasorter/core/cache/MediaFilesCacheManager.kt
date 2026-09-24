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

    // Average MediaFile size ~500 bytes, so the 128MB ceiling holds ~260,000 files across resources.
    private const val MAX_CACHE_SIZE_BYTES = 128 * 1024 * 1024

    // S1299: the flat 128MB ceiling ignored the device. On a low-RAM phone (legacy flavor runs from
    // API 23) the whole Java heap can be ~96-192MB, so the "limit" allowed the cache to dominate the
    // heap and contribute to OOM in the player. Scale it to the actual heap and keep a sane floor.
    private const val HEAP_FRACTION = 8
    private const val MIN_CACHE_SIZE_BYTES = 8 * 1024 * 1024

    private val cacheSizeBytes: Int = run {
        val heapBudget = (Runtime.getRuntime().maxMemory() / HEAP_FRACTION)
            .coerceIn(MIN_CACHE_SIZE_BYTES.toLong(), MAX_CACHE_SIZE_BYTES.toLong())
        heapBudget.toInt()
    }

    // S0729: one lock serializes every read-modify-write of a resource's list against the snapshot read
    // on IO (RandomPhotoFrameWidgetRefresher), so an update is never lost to a concurrent replace.
    // S3470: a stored list is never mutated after put(). LruCache charges sizeOf() at put() and
    // subtracts sizeOf(previous) on replace/removal; an in-place resize made that subtraction read the
    // new length, the running total drifted, and evictAll() threw IllegalStateException
    // ("sizeOf() is reporting inconsistent results"). Every mutator builds a new list and put()s it.
    private val lock = Any()
    
    // Cache key = resourceId, value = list of MediaFiles
    // LruCache requires size calculation via sizeOf() override
    private val cache = object : LruCache<Long, MutableList<MediaFile>>(cacheSizeBytes) {
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
        synchronized(lock) { cache.put(resourceId, fixedFiles.toMutableList()) }
        Timber.d("MediaFilesCache: Cached ${fixedFiles.size} files for resource $resourceId")
    }
    
    /**
     * Retrieves cached list for a resource. Returns defensive copy.
     * Thread-safe: LruCache handles synchronization internally.
     */
    fun getCachedList(resourceId: Long): List<MediaFile>? {
        // S0729: snapshot under lock so the toList() copy cannot race an in-place mutation on Main.
        val files = synchronized(lock) { cache.get(resourceId)?.toList() }
        Timber.d("MediaFilesCache: Retrieved ${files?.size ?: 0} files for resource $resourceId")
        return files
    }
    
    /**
     * Updates a file in the cached list (after rename operation).
     * @return true if file was found and updated, false otherwise
     */
    fun updateFile(resourceId: Long, oldPath: String, newFile: MediaFile): Boolean = synchronized(lock) {
        val list = cache.get(resourceId) ?: return@synchronized false
        val index = list.indexOfFirst { it.path == oldPath }
        if (index >= 0) {
            cache.put(resourceId, list.toMutableList().also { it[index] = newFile })
            Timber.d("MediaFilesCache: Updated file at index $index (${oldPath} → ${newFile.path})")
            return@synchronized true
        }
        Timber.w("MediaFilesCache: File not found for update: $oldPath")
        false
    }
    
    /**
     * Removes a file from the cached list (after delete/move operation).
     * Normalizes URIs by decoding before comparison to handle both encoded and decoded paths.
     * @return true if file was found and removed, false otherwise
     */
    fun removeFile(resourceId: Long, filePath: String): Boolean = synchronized(lock) {
        val list = cache.get(resourceId) ?: return@synchronized false

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
        
        val remaining = list.filterNot { cachedFile ->
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

        val removed = remaining.size != list.size
        if (removed) {
            cache.put(resourceId, remaining.toMutableList())
            Timber.d(
                "MediaFilesCache: Removed file $filePath from resource $resourceId " +
                    "(${remaining.size} files remaining)"
            )
        } else {
            Timber.w("MediaFilesCache: File not found for removal: $filePath (normalized: $normalizedPath)")
        }
        removed
    }
    
    /**
     * Adds a file to the cached list (after move-in operation from another resource).
     * Inserts in correct position based on current sort order (caller's responsibility to sort).
     */
    fun addFile(resourceId: Long, file: MediaFile) = synchronized(lock) {
        // S1299: re-putting a NEW list charges the grown size against the budget; a list grown in
        // place was never charged and the "bounded" cache silently exceeded it.
        val updated = (cache.get(resourceId)?.toMutableList() ?: mutableListOf()).also { it.add(file) }
        cache.put(resourceId, updated)
        Timber.d("MediaFilesCache: Added file ${file.path} to resource $resourceId (${updated.size} files total)")
    }

    /**
     * Clears cache for a specific resource (e.g., on explicit refresh).
     */
    fun clearCache(resourceId: Long) {
        synchronized(lock) { cache.remove(resourceId) }
        Timber.d("MediaFilesCache: Cleared cache for resource $resourceId")
    }

    /**
     * Clears all cached lists (e.g., on app logout or memory pressure).
     */
    fun clearAllCaches() {
        synchronized(lock) { cache.evictAll() }
        Timber.d("MediaFilesCache: Cleared all caches")
    }

    /**
     * Checks if a resource has cached data.
     */
    fun isCached(resourceId: Long): Boolean = synchronized(lock) { cache.get(resourceId) != null }
    
    /**
     * Gets current size of cached list without retrieving it.
     */
    fun getCacheSize(resourceId: Long): Int = synchronized(lock) {
        cache.get(resourceId)?.size ?: 0
    }
    
    /**
     * Fixes cloud paths in cached lists: cloud:/google_drive/ → cloud://google_drive/
     * Called once on app startup to migrate old path format.
     * Returns number of fixed paths.
     */
    fun fixCloudPaths(): Int = synchronized(lock) {
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

        fixedCount
    }
}
