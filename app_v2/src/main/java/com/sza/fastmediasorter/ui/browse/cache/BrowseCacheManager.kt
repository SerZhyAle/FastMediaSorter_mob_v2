package com.sza.fastmediasorter.ui.browse.cache

import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaFile
import timber.log.Timber

/**
 * Manages file cache operations for BrowseViewModel.
 * Handles cache checking, filtering, and cache invalidation logic.
 */
class BrowseCacheManager(
    private val resourceId: Long,
    private val paginationThreshold: Int
) {
    companion object {
        /**
         * Cache TTL (Time-to-Live) for small resources.
         * If resource was browsed within this time, cache is considered valid.
         * Default: 5 minutes (300,000 ms)
         */
        private const val SMALL_RESOURCE_CACHE_TTL_MS = 5 * 60 * 1000L
    }
    /**
     * Result of cache check operation.
     */
    sealed class CacheCheckResult {
        /**
         * Cache is valid and should be used.
         * @param files The cached files to display
         */
        data class UseCache(val files: List<MediaFile>) : CacheCheckResult()
        
        /**
         * Cache should not be used, need to perform fresh scan.
         * @param reason The reason why cache was rejected
         */
        data class Rescan(val reason: String) : CacheCheckResult()
    }
    
    /**
     * Checks if cached files should be used based on cache status, size, and freshness.
     * 
     * Strategy:
     * - Large resources (>= paginationThreshold): Always use cache if available
     * - Small resources with recent browse (< 5 min): Use cache for fast reopening
     * - Small resources with stale cache: Rescan to detect external changes
     * 
     * @param filter Optional filter to apply to cached files before returning
     * @param lastBrowseDate Timestamp of last successful browse (null for first browse)
     * @param forceRescan If true, always rescan regardless of cache state
     * @return CacheCheckResult indicating whether to use cache or rescan
     */
    fun checkCache(
        filter: FileFilter?,
        lastBrowseDate: Long? = null,
        forceRescan: Boolean = false
    ): CacheCheckResult {
        Timber.d("BrowseCacheManager: Checking cache for resource $resourceId (lastBrowse=${lastBrowseDate}, forceRescan=$forceRescan)")
        
        if (forceRescan) {
            Timber.d("BrowseCacheManager: Force rescan requested, clearing cache")
            MediaFilesCacheManager.clearCache(resourceId)
            return CacheCheckResult.Rescan("Force rescan requested")
        }
        
        val cachedFiles = MediaFilesCacheManager.getCachedList(resourceId)
        Timber.d("BrowseCacheManager: Cache check result - cachedFiles=${cachedFiles?.size ?: "null"}")
        
        return when {
            // No cache found - need to scan
            cachedFiles == null -> {
                Timber.d("BrowseCacheManager: No cache found, will perform fresh scan")
                CacheCheckResult.Rescan("No cache found")
            }
            
            // Cache exists but is empty (process killed and restored empty cache) - clear and rescan
            cachedFiles.isEmpty() -> {
                Timber.w("BrowseCacheManager: Found empty cache for resource $resourceId, clearing and rescanning")
                MediaFilesCacheManager.clearCache(resourceId)
                CacheCheckResult.Rescan("Empty cache")
            }
            
            // Large resource - always use cache
            cachedFiles.size >= paginationThreshold -> {
                Timber.d("BrowseCacheManager: Using cached list (${cachedFiles.size} files) - large resource, skipping scan")
                val filteredFiles = if (filter != null) {
                    Timber.i("╔═══ APPLYING CACHE FILTER ═══")
                    Timber.i("║ Filter mediaTypes: ${filter.mediaTypes?.map { it.name } ?: "ALL"}")
                    Timber.i("║ Files BEFORE filter: ${cachedFiles.size}")
                    val result = applyFilter(cachedFiles, filter)
                    Timber.i("║ Files AFTER filter: ${result.size}")
                    if (result.size < cachedFiles.size) {
                        val removed = cachedFiles.size - result.size
                        Timber.i("║ Filtered OUT: $removed files")
                    }
                    Timber.i("╚══════════════════════════════")
                    result
                } else {
                    cachedFiles
                }
                Timber.d("BrowseCacheManager: Filtered cached list ${cachedFiles.size} → ${filteredFiles.size} files")
                CacheCheckResult.UseCache(filteredFiles)
            }
            
            // Small resource - check if cache is fresh (browsed within TTL)
            else -> {
                val now = System.currentTimeMillis()
                val cacheAge = if (lastBrowseDate != null) now - lastBrowseDate else Long.MAX_VALUE
                val isCacheFresh = cacheAge < SMALL_RESOURCE_CACHE_TTL_MS
                
                if (isCacheFresh) {
                    Timber.d("BrowseCacheManager: Using fresh cache (${cachedFiles.size} files) - age=${cacheAge}ms < TTL=${SMALL_RESOURCE_CACHE_TTL_MS}ms")
                    val filteredFiles = if (filter != null) {
                        applyFilter(cachedFiles, filter)
                    } else {
                        cachedFiles
                    }
                    CacheCheckResult.UseCache(filteredFiles)
                } else {
                    Timber.d("BrowseCacheManager: Cache stale (${cachedFiles.size} files) - age=${cacheAge}ms >= TTL=${SMALL_RESOURCE_CACHE_TTL_MS}ms, rescanning")
                    MediaFilesCacheManager.clearCache(resourceId)
                    CacheCheckResult.Rescan("Stale cache (age=${cacheAge / 1000}s)")
                }
            }
        }
    }
    
    /**
     * Applies FileFilter to a list of MediaFiles.
     * Filters by name, date range, size range, and media types.
     * 
     * @param files The files to filter
     * @param filter The filter criteria
     * @return Filtered list of files
     */
    fun applyFilter(files: List<MediaFile>, filter: FileFilter): List<MediaFile> {
        return files.filter { file ->
            val matchesName = filter.nameContains == null || 
                file.name.contains(filter.nameContains, ignoreCase = true)
            
            val matchesMinDate = filter.minDate == null || 
                file.createdDate >= filter.minDate
            
            val matchesMaxDate = filter.maxDate == null || 
                file.createdDate <= filter.maxDate
            
            val fileSizeMb = file.size / (1024f * 1024f)
            val matchesMinSize = filter.minSizeMb == null || 
                fileSizeMb >= filter.minSizeMb
            
            val matchesMaxSize = filter.maxSizeMb == null || 
                fileSizeMb <= filter.maxSizeMb
            
            val matchesType = filter.mediaTypes == null || 
                filter.mediaTypes.contains(file.type)
            
            matchesName && matchesMinDate && matchesMaxDate && 
                matchesMinSize && matchesMaxSize && matchesType
        }
    }
}
