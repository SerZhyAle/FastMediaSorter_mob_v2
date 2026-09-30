package com.sza.fastmediasorter.core.cache

import timber.log.Timber
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Session-only playback failure cache.
 *
 * Keeps timeout failures only for the current process lifetime so the player can warn on the
 * next open without turning a transient playback issue into a long-lived persisted error.
 */
object VideoPlaybackFailureSessionCache {

    // newSetFromMap, not newKeySet(): KeySetView#clear and #size are API 24 and legacy ships to API 23.
    private val failedPaths: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

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
