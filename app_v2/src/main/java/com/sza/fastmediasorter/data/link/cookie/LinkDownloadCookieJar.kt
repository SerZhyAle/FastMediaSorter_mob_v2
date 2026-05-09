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
 * `loadForRequest` translates the persisted `java.net.HttpCookie` records into
 * `okhttp3.Cookie` instances honoring domain/path/expiry/secure/httpOnly. The
 * pipeline does NOT absorb cookies from HTTP responses — `saveFromResponse` is
 * intentionally a no-op (per S0116 §5.1 pillar L cookies arrive exclusively
 * through the WebView authentication flow in Phase 05).
 */
@Singleton
class LinkDownloadCookieJar @Inject constructor(
    private val store: EncryptedCookieStore,
) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val raw = store.loadFor(host)
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
