package com.sza.fastmediasorter.core.cache

import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber

/**
 * Session-only playback failure cache.
 *
 * Keeps timeout failures only for the current process lifetime so the player can warn on the
 * next open without turning a transient playback issue into a long-lived persisted error.
 */
object VideoPlaybackFailureSessionCache {

    private val failedPaths = ConcurrentHashMap.newKeySet<String>()

    fun hasFailure(path: String): Boolean = failedPaths.contains(path)

    fun markFailed(path: String) {
        if (failedPaths.add(path)) {
            Timber.i("VideoPlaybackFailureSessionCache: marked failed %s", path.substringAfterLast('/'))
        }
    }

    fun clear(path: String) {
        if (failedPaths.remove(path)) {
            Timber.d("VideoPlaybackFailureSessionCache: cleared %s", path.substringAfterLast('/'))
        }
    }

    fun clearAll() {
        val cleared = failedPaths.size
        failedPaths.clear()
        Timber.i("VideoPlaybackFailureSessionCache: cleared %d entries", cleared)
    }
}