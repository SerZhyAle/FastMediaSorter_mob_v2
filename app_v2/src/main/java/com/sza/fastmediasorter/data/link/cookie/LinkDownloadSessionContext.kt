package com.sza.fastmediasorter.data.link.cookie

import java.net.HttpCookie
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the cookies selected for the currently running link-download pipeline.
 * This lets one download use the exact chosen account without mutating global state.
 */
@Singleton
class LinkDownloadSessionContext @Inject constructor() {

    @Volatile
    private var activeCookies: Pair<String, List<HttpCookie>>? = null

    fun set(host: String, cookies: List<HttpCookie>) {
        activeCookies = host to cookies
    }

    fun cookiesFor(requestHost: String): List<HttpCookie>? {
        val (activeHost, cookies) = activeCookies ?: return null
        val normalized = requestHost.lowercase().removePrefix("www.")
        val activeNormalized = activeHost.lowercase().removePrefix("www.")
        return if (normalized == activeNormalized || normalized.endsWith(".$activeNormalized")) {
            cookies
        } else {
            null
        }
    }

    fun clear() {
        activeCookies = null
    }
}