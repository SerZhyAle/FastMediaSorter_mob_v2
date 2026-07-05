package com.sza.fastmediasorter.wear.util

import timber.log.Timber
import java.io.File

/**
 * Bounds the SMB playback temp-file cache (S0902) - Wear players download each SMB file to a
 * per-type cache dir before ExoPlayer can stream it (no direct InputStream playback), and the
 * dir previously had no size cap or eviction, growing without bound on watch storage.
 */
object SmbCacheEvictor {

    /**
     * Deletes the oldest files (by [File.lastModified]) in [cacheDir] until its total size is
     * at or under [capBytes], never deleting [keep] (the file just written for the current
     * playback). No-op if the dir is already under the cap.
     */
    fun evictOldestUntilUnderCap(cacheDir: File, keep: File, capBytes: Long) {
        val files = cacheDir.listFiles() ?: return
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= capBytes) return
        Timber.d("S0902: evicting ${cacheDir.name} cache, $totalBytes bytes over $capBytes cap")

        val oldestFirst = files
            .filter { it != keep }
            .sortedBy { it.lastModified() }

        for (file in oldestFirst) {
            if (totalBytes <= capBytes) break
            val size = file.length()
            if (file.delete()) {
                totalBytes -= size
            } else {
                Timber.w("SmbCacheEvictor: failed to delete ${file.name}")
            }
        }
    }
}
