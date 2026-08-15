package com.sza.fastmediasorter.wear.util

import timber.log.Timber
import java.io.File

/**
 * Bounds the network playback temp-file cache (S0902) - Wear players download each remote file to a
 * per-type cache dir before ExoPlayer can stream it (no direct InputStream playback), and the
 * dir previously had no size cap or eviction, growing without bound on watch storage.
 *
 * S1687 widened it from SMB to every supported protocol, which is what the name now says.
 */
object MediaCacheEvictor {

    /**
     * Deletes the oldest files (by [File.lastModified]) in [cacheDir] until its total size is
     * at or under [capBytes], never deleting [keep] (the file just written for the current
     * playback). No-op if the dir is already under the cap.
     */
    fun evictOldestUntilUnderCap(cacheDir: File, keep: File, capBytes: Long) {
        val files = cacheDir.listFiles() ?: return
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= capBytes) return

        val oldestFirst = files
            .filter { it != keep }
            .sortedBy { it.lastModified() }

        for (file in oldestFirst) {
            if (totalBytes <= capBytes) break
            val size = file.length()
            if (file.delete()) {
                totalBytes -= size
            } else {
                Timber.w("MediaCacheEvictor: failed to delete ${file.name}")
            }
        }
    }
}
