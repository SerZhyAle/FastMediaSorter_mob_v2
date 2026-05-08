package com.sza.fastmediasorter.core.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Protocol-agnostic download deduplicator (S0113 Phase 02).
 *
 * Collapses concurrent download requests for the same URL into a single in-flight coroutine.
 * A second caller that arrives while the first is downloading the same URL awaits the existing
 * [Deferred] instead of starting a parallel download. The map entry is removed automatically
 * when the download completes or is cancelled.
 *
 * Implemented as an object singleton so it works across manually-constructed
 * [NetworkFileDownloader] instances without requiring Hilt injection.
 * Reusable for any network protocol (SFTP, SMB, FTP).
 */
object NetworkDownloadDeduplicator {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val inFlight = ConcurrentHashMap<String, Deferred<Any?>>()

    /**
     * Executes [block] for [key], or awaits an existing in-flight call for the same key.
     * Thread-safe; concurrent callers for identical keys share one coroutine result.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> deduplicate(key: String, block: suspend () -> T?): T? {
        val deferred: Deferred<Any?> = synchronized(inFlight) {
            val existing = inFlight[key]
            if (existing != null && existing.isActive) {
                Timber.d("NetworkDownloadDeduplicator: reusing in-flight download for $key")
                existing
            } else {
                val new = scope.async {
                    try {
                        block()
                    } finally {
                        synchronized(inFlight) { inFlight.remove(key) }
                    }
                }
                inFlight[key] = new
                new
            }
        }
        return deferred.await() as T?
    }
}
