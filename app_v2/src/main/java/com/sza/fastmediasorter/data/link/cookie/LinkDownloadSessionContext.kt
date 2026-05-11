package com.sza.fastmediasorter.data.link.cookie

import java.net.HttpCookie
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0155: holds pre-loaded cookies for the account selected for the currently
 * executing link-download pipeline run. Set by
 * [com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator]
 * before the pipeline starts; cleared in the finally block.
 *
 * Thread-safety: access is single-threaded within a coroutine pipeline run;
 * @Volatile ensures visibility across coroutine context switches.
 */
@Singleton
class LinkDownloadSessionContext @Inject constructor() {

    @Volatile private var activeCookies: Pair<String, List<HttpCookie>>? = null

    /** Set before the pipeline run. [host] is the canonical KnownAuthResources host. */
    fun set(host: String, cookies: List<HttpCookie>) {
        activeCookies = host to cookies
    }

    /**
     * Returns pre-loaded cookies if [requestHost] matches the active host or its parent
     * domain; returns null if no context is active or the host does not match.
     */
    fun cookiesFor(requestHost: String): List<HttpCookie>? {
        val (activeHost, cookies) = activeCookies ?: return null
        val normalized = requestHost.lowercase().removePrefix("www.")
        val activeNorm = activeHost.lowercase().removePrefix("www.")
        return if (normalized == activeNorm || normalized.endsWith(".$activeNorm")) cookies else null
    }

    /** Clear after the pipeline run. */
    fun clear() {
        activeCookies = null
    }
}
