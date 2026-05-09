package com.sza.fastmediasorter.core.log

import android.net.Uri
import com.sza.fastmediasorter.BuildConfig
import okhttp3.Cookie
import timber.log.Timber

/**
 * S0116 §5.5: privacy-safe debug tracing helper for the URL download pipeline.
 *
 * - [tag] is a deliberate no-op after the spec transitioned to `Verified`
 *   (CLAUDE.md rule on temporary `Timber.d("Sxxxx:` markers). The 6 call sites
 *   remain as documented verification points (one per flow) but emit nothing.
 *   When a future spec is in active development, a new `LinkDownloadTrace.tag`
 *   helper can re-introduce the runtime emission.
 * - [verbose] emits `Timber.v("[link-dl] ..")` only when [BuildConfig.LOG_LINK_DOWNLOAD]
 *   is true (debug builds). The flag exists in production code as permanent diagnostic
 *   infrastructure for an evolving feature.
 * - [truncateUrl] strips query string, fragment, and any path segment beyond index 2
 *   to keep short-lived signed-tokens and tracking parameters out of logcat.
 * - [truncateCookies] formats a cookie list as `count=N names=[a,b,c]` — never logs
 *   cookie values.
 */
object LinkDownloadTrace {

    @Suppress("UNUSED_PARAMETER")
    fun tag(message: String) {
        // Retired on /spec-check transition to Verified — see kdoc.
    }

    fun verbose(message: String) {
        if (BuildConfig.LOG_LINK_DOWNLOAD) {
            Timber.v("[link-dl] %s", message)
        }
    }

    fun truncateUrl(url: String): String {
        if (url.isBlank()) return "<invalid_url>"
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return "<invalid_url>"
        val scheme = parsed.scheme ?: return "<invalid_url>"
        val host = parsed.host ?: return "<invalid_url>"
        val pathSegments = parsed.pathSegments.orEmpty().take(2)
        val pathPart = if (pathSegments.isEmpty()) "" else "/" + pathSegments.joinToString("/")
        return "$scheme://$host$pathPart"
    }

    fun truncateCookies(cookies: List<Cookie>): String {
        val names = cookies.joinToString(",") { it.name }
        return "count=${cookies.size} names=[$names]"
    }
}
