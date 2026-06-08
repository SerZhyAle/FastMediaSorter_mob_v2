package com.sza.fastmediasorter.core.xr

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped cache that lets VR launch/return Intents carry only a primitive token instead of an
 * application Serializable payload.
 *
 * system_server snapshots a task's base Intent for the recents list and tries to unmarshal its extras
 * without the app classloader; any application type placed there throws BadParcelableException /
 * ClassNotFoundException in system_server (true for Serializable and Parcelable alike). Producers store
 * the payload here and put the returned token string into the Intent; consumers resolve it back in the
 * same process. system_server never sees an application type.
 *
 * Both the launch transition (caller -> immersive host) and the return transition (host -> caller) run
 * in-process, so a process-scoped holder is sufficient for the live case. After process death the holder
 * is empty and consumers fall back to their defaults (diagnostic-playlist input, best-effort snapshot
 * restore). The map is bounded so a token that is never consumed cannot grow it without limit.
 */
@Singleton
class VrLaunchPayloadHolder @Inject constructor() {

    private val lock = Any()
    private val counter = AtomicLong(0L)
    private val entries = LinkedHashMap<String, Any>()

    /** Store [payload] and return a fresh primitive token to carry in an Intent extra. */
    fun put(payload: Any): String {
        val token = TOKEN_PREFIX + counter.incrementAndGet()
        synchronized(lock) {
            entries[token] = payload
            while (entries.size > MAX_ENTRIES) {
                val oldest = entries.keys.iterator().next()
                entries.remove(oldest)
            }
        }
        return token
    }

    /** Read the payload for [token] without removing it (safe for re-entrant reads). */
    fun <T> peek(token: String?): T? {
        if (token == null) return null
        synchronized(lock) {
            @Suppress("UNCHECKED_CAST")
            return entries[token] as? T
        }
    }

    /** Read and remove the payload for [token]. Returns null on a second consume or unknown token. */
    fun <T> consume(token: String?): T? {
        if (token == null) return null
        synchronized(lock) {
            @Suppress("UNCHECKED_CAST")
            return entries.remove(token) as? T
        }
    }

    private companion object {
        const val TOKEN_PREFIX = "vrpl-"
        const val MAX_ENTRIES = 16
    }
}
