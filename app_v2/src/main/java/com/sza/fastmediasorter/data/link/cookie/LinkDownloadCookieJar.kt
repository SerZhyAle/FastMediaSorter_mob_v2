package com.sza.fastmediasorter.data.link.cookie

import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar K: OkHttp `CookieJar` adapter over [EncryptedCookieStore].
 *
 * S0155: Checks [LinkDownloadSessionContext] first — if the context has pre-loaded
 * cookies for the requested host (matching the account selected before the pipeline
 * started), those are used; otherwise falls back to the deprecated `store.loadFor(host)`
 * to preserve backward compat for callers that have not yet been updated.
 *
 * `saveFromResponse` is intentionally a no-op (per S0116 §5.1 pillar L cookies arrive
 * exclusively through the WebView authentication flow in Phase 05).
 */
@Singleton
class LinkDownloadCookieJar @Inject constructor(
    private val store: EncryptedCookieStore,
    private val context: LinkDownloadSessionContext,
) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        // S0155: prefer session-context cookies when an account was selected for this run.
        val raw = context.cookiesFor(host)
            ?: @Suppress("DEPRECATION") store.loadFor(host)
        if (raw.isEmpty()) return emptyList()
        val out = raw.mapNotNull { c ->
            try {
                val builder = Cookie.Builder()
                    .name(c.name)
                    .value(c.value ?: "")
                    .path(c.path ?: "/")
                if (c.domain.isNullOrBlank()) builder.hostOnlyDomain(host) else builder.domain(c.domain.trimStart('.'))
                if (c.maxAge >= 0L) {
                    val expiresAt = System.currentTimeMillis() + c.maxAge * 1000L
                    builder.expiresAt(expiresAt)
                }
                if (c.secure) builder.secure()
                if (c.isHttpOnly) builder.httpOnly()
                builder.build()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                null
            }
        }
        LinkDownloadTrace.verbose(
            "link-download-cookie-jar inject host=$host ${LinkDownloadTrace.truncateCookies(out)}",
        )
        return out
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Intentional no-op — see class kdoc.
    }
}
