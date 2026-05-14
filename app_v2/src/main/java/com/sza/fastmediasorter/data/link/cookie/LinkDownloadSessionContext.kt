package com.sza.fastmediasorter.data.link.cookie

import java.net.HttpCookie
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the cookies + UA selected for the currently running link-download pipeline.
 * This lets one download use the exact chosen account without mutating global state.
 *
 * S0182: also carries the User-Agent captured at login time so yt-dlp/OkHttp can
 * replay it on every downstream request, keeping the same sessionid + UA pair
 * the way the server originally saw it.
 */
@Singleton
class LinkDownloadSessionContext @Inject constructor() {

    private data class Active(
        val host: String,
        val cookies: List<HttpCookie>,
        val userAgent: String?,
    )

    @Volatile
    private var active: Active? = null

    @Deprecated("Use set(host, cookies, userAgent)", level = DeprecationLevel.WARNING)
    fun set(host: String, cookies: List<HttpCookie>) {
        active = Active(host, cookies, null)
    }

    fun set(host: String, cookies: List<HttpCookie>, userAgent: String?) {
        active = Active(host, cookies, userAgent)
    }

    fun cookiesFor(requestHost: String): List<HttpCookie>? {
        val a = active ?: return null
        return if (hostMatches(requestHost, a.host)) a.cookies else null
    }

    /** S0182: returns the pinned User-Agent for the active session, or null if unset. */
    fun userAgentFor(requestHost: String): String? {
        val a = active ?: return null
        return if (hostMatches(requestHost, a.host)) a.userAgent else null
    }

    fun clear() {
        active = null
    }

    private fun hostMatches(requestHost: String, activeHost: String): Boolean {
        val normalized = requestHost.lowercase().removePrefix("www.")
        val activeNormalized = activeHost.lowercase().removePrefix("www.")
        return normalized == activeNormalized || normalized.endsWith(".$activeNormalized")
    }
}