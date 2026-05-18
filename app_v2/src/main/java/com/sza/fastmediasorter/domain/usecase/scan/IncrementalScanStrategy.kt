package com.sza.fastmediasorter.domain.usecase.scan

import com.sza.fastmediasorter.core.logging.StructuredLogger
import com.sza.fastmediasorter.data.local.db.CachedFileListEntity
import com.sza.fastmediasorter.domain.model.ResourceType
import java.io.File

/**
 * Determines whether a full re-scan can be skipped in favour of the cached
 * file list (A5-T3 - delta path without full re-scan).
 *
 * ### Decision logic
 *
 * A scan can be skipped (i.e. the cache is still valid) when ALL of the
 * following hold:
 *
 * 1. A cached snapshot exists ([CachedFileListEntity] is non-null).
 * 2. The snapshot is younger than [maxCacheAgeMs] (default: 5 minutes).
 * 3. For **LOCAL** resources: the folder's `File.lastModified()` has not
 *    changed since the snapshot was made ([CachedFileListEntity.lastModifiedFolder]).
 * 4. For **NETWORK / CLOUD** resources: folder mtime cannot be determined
 *    cheaply without connecting, so the decision always returns `false`
 *    (full scan required). This is a conservative, safe default.
 *
 * ### Usage
 *
 * ```kotlin
 * val entity       = cachedFileListRepository.getEntityByResourceId(resource.id)
 * val canUseCached = IncrementalScanStrategy.canSkipScan(entity, resource.path, resource.type)
 *
 * if (canUseCached) {
 *     val cachedFiles = cachedFileListRepository.getCachedFiles(resource.id)
 *     return@flow cachedFiles ?: runFullScan()
 * } else {
 *     runFullScan()
 * }
 * ```
 *
 * [maxCacheAgeMs] and [enableForLocalOnly] are mutable for testing and
 * flavour-specific tuning.
 */
object IncrementalScanStrategy {

    /**
     * Maximum age of a cached snapshot that is considered "fresh" (ms).
     *
     * Default: 5 minutes - conservative enough for local sources.
     * Set to 0 to disable age-based cache invalidation.
     */
    var maxCacheAgeMs: Long = 5L * 60 * 1000

    /**
     * When `true` (default), the skip-scan optimisation is attempted only
     * for [ResourceType.LOCAL] sources where folder mtime is cheaply available.
     * Set to `false` if you want to experiment with caching other types too.
     */
    var enableForLocalOnly: Boolean = true

    /**
     * Decides whether a full re-scan can be skipped.
     *
     * @param entity         Cached entity from [CachedFileListDao], or `null` if absent.
     * @param resourcePath   Root path of the resource (folder path for LOCAL).
     * @param resourceType   Type of the resource.
     * @param nowMs          Current time in epoch ms (injectable for testing).
     * @return `true` if the existing cache can be reused; `false` if a full scan is needed.
     */
    fun canSkipScan(
        entity: CachedFileListEntity?,
        resourcePath: String,
        resourceType: ResourceType,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (entity == null) {
            StructuredLogger.d("IncrementalScan: no cache → full scan", "path" to resourcePath)
            return false
        }

        // Non-LOCAL resources: skip the optimisation - see KDoc for rationale.
        if (enableForLocalOnly && resourceType != ResourceType.LOCAL) {
            StructuredLogger.d("IncrementalScan: non-local → optimisation disabled", "type" to resourceType.name)
            return false
        }

        // Age check.
        val lastScan = entity.lastScanTimestamp
        if (lastScan == null || maxCacheAgeMs > 0 && (nowMs - lastScan) > maxCacheAgeMs) {
            StructuredLogger.d(
                "IncrementalScan: cache too old",
                "age" to "${nowMs - (lastScan ?: 0)}ms",
                "limit" to "${maxCacheAgeMs}ms"
            )
            return false
        }

        // For LOCAL: compare folder mtime.
        if (resourceType == ResourceType.LOCAL) {
            val cachedFolderMtime  = entity.lastModifiedFolder
            val currentFolderMtime = currentFolderMtime(resourcePath)

            if (cachedFolderMtime == null || currentFolderMtime == null) {
                StructuredLogger.d(
                    "IncrementalScan: folder mtime unavailable",
                    "cached" to cachedFolderMtime,
                    "current" to currentFolderMtime
                )
                return false
            }

            if (currentFolderMtime != cachedFolderMtime) {
                StructuredLogger.d(
                    "IncrementalScan: folder mtime changed",
                    "cached" to cachedFolderMtime,
                    "current" to currentFolderMtime
                )
                return false
            }

            StructuredLogger.d(
                "IncrementalScan: folder mtime unchanged → cache valid",
                "mtime" to cachedFolderMtime,
                "path" to resourcePath
            )
            return true
        }

        // Fallback for non-LOCAL when enableForLocalOnly=false: rely on age alone.
        StructuredLogger.d("IncrementalScan: age-only check passed → cache valid", "type" to resourceType.name)
        return true
    }

    /**
     * Reads the `lastModified` timestamp of a LOCAL folder (epoch ms).
     *
     * @return The folder's last-modified time, or `null` if the path does not
     *         exist or is not a directory.
     */
    fun currentFolderMtime(path: String): Long? {
        // Virtual resources have no folder - always return null to force full scan
        if (com.sza.fastmediasorter.util.VirtualPathUtils.isVirtualPath(path)) return null

        return try {
            val file = File(path)
            if (file.exists() && file.isDirectory) file.lastModified() else null
        } catch (e: SecurityException) {
            StructuredLogger.w(e, "IncrementalScan: cannot read mtime", "path" to path)
            null
        }
    }

    /** Resets all settings to defaults. Useful in tests. */
    fun reset() {
        maxCacheAgeMs      = 5L * 60 * 1000
        enableForLocalOnly = true
    }
}
