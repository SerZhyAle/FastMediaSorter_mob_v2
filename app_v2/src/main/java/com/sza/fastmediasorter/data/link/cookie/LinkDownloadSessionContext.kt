package com.sza.fastmediasorter.data.link.cookie

import java.net.HttpCookie
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the cookies + UA selected for each running link-download pipeline.
 * This lets one download use the exact chosen account without mutating global state.
 *
 * S0182: also carries the User-Agent captured at login time so yt-dlp/OkHttp can
 * replay it on every downstream request, keeping the same sessionid + UA pair
 * the way the server originally saw it.
 *
 * S3843: WorkManager runs several link downloads at once, so a single shared slot let
 * one run overwrite or clear another run's cookies mid-download. Each run registers its
 * session under its own owner key and clears only that entry; a lookup picks the most
 * recently registered session whose host matches the request.
 */
@Singleton
class LinkDownloadSessionContext @Inject constructor() {

    private data class Active(
        val owner: Any,
        val host: String,
        val cookies: List<HttpCookie>,
        val userAgent: String?,
        val audioOnly: Boolean = false,
    )

    private val lock = Any()
    private val sessions = ArrayList<Active>()

    @Deprecated("Use set(host, cookies, userAgent)", level = DeprecationLevel.WARNING)
    fun set(host: String, cookies: List<HttpCookie>) {
        set(host, cookies, null, audioOnly = false, owner = SHARED_OWNER)
    }

    fun set(host: String, cookies: List<HttpCookie>, userAgent: String?) {
        set(host, cookies, userAgent, audioOnly = false, owner = SHARED_OWNER)
    }

    fun set(host: String, cookies: List<HttpCookie>, userAgent: String?, audioOnly: Boolean, owner: Any) {
        synchronized(lock) {
            sessions.removeAll { it.owner === owner }
            sessions.add(Active(owner, host, cookies, userAgent, audioOnly))
        }
    }

    fun cookiesFor(requestHost: String): List<HttpCookie>? = matching(requestHost)?.cookies

    /** S0182: returns the pinned User-Agent for the matching session, or null if unset. */
    fun userAgentFor(requestHost: String): String? = matching(requestHost)?.userAgent

    /** S0190: returns `true` when the matching session is bound to an audio-only request (e.g. YouTube Music). */
    fun audioOnlyFor(requestHost: String): Boolean = matching(requestHost)?.audioOnly == true

    fun clear(owner: Any) {
        synchronized(lock) {
            sessions.removeAll { it.owner === owner }
        }
    }

    private fun matching(requestHost: String): Active? = synchronized(lock) {
        sessions.lastOrNull { hostMatches(requestHost, it.host) }
    }

    private fun hostMatches(requestHost: String, activeHost: String): Boolean {
        val normalized = requestHost.lowercase().removePrefix("www.")
        val activeNormalized = activeHost.lowercase().removePrefix("www.")
        return normalized == activeNormalized || normalized.endsWith(".$activeNormalized")
    }

    private companion object {
        val SHARED_OWNER = Any()
    }
}
