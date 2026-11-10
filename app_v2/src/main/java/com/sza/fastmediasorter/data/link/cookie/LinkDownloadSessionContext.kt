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
        val serverDeleted: Set<String> = emptySet(),
    )

    /** What one response's Set-Cookie list did to the running session; names only, never values. */
    data class CookieMerge(
        val added: List<String> = emptyList(),
        val replaced: List<String> = emptyList(),
        val deleted: List<String> = emptyList(),
    ) {
        val changed: Boolean get() = added.isNotEmpty() || replaced.isNotEmpty() || deleted.isNotEmpty()
    }

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

    /**
     * S4509: folds the Set-Cookie values of one redirect chain into the session that served the request,
     * so a server that rotates a cookie and redirects never gets the stale value back on the next hop.
     * The same session [matching] picks for the request is the one updated. The change is in-memory
     * only: the encrypted store is untouched and the merged copy dies with [clear]. A received cookie
     * that is already expired deletes the cookie it names, and the deletion is remembered for
     * [serverDeletedCookies]. The returned merge is empty when nothing changed or no session matched.
     */
    fun mergeResponseCookies(requestHost: String, received: List<HttpCookie>): CookieMerge {
        if (received.isEmpty()) return CookieMerge()
        return synchronized(lock) {
            val index = sessions.indexOfLast { hostMatches(requestHost, it.host) }
            val active = sessions.getOrNull(index) ?: return@synchronized CookieMerge()
            val merged = active.cookies.toMutableList()
            val outcome = applyReceived(merged, received)
            if (outcome.changed) {
                sessions[index] = active.copy(cookies = merged, serverDeleted = active.serverDeleted + outcome.deleted)
            }
            outcome
        }
    }

    /**
     * S4509: names of the session cookies a server deleted during [owner]'s run. A login wall that
     * revokes the session answers with an expired Set-Cookie and then serves only the preview, so
     * the caller needs this to tell a revoked session from a post that simply has no media.
     */
    fun serverDeletedCookies(owner: Any): Set<String> = synchronized(lock) {
        sessions.lastOrNull { it.owner === owner }?.serverDeleted.orEmpty()
    }

    private fun applyReceived(merged: MutableList<HttpCookie>, received: List<HttpCookie>): CookieMerge {
        val added = ArrayList<String>()
        val replaced = ArrayList<String>()
        val deleted = ArrayList<String>()
        for (cookie in received) {
            val existing = merged.indexOfFirst { it.name == cookie.name && pathOf(it) == pathOf(cookie) }
            when {
                cookie.hasExpired() -> if (existing >= 0) {
                    merged.removeAt(existing)
                    deleted += cookie.name
                }
                existing < 0 -> {
                    merged.add(cookie)
                    added += cookie.name
                }
                merged[existing].value != cookie.value -> {
                    merged[existing] = cookie
                    replaced += cookie.name
                }
            }
        }
        return CookieMerge(added, replaced, deleted)
    }

    private fun pathOf(cookie: HttpCookie): String = cookie.path ?: "/"

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
