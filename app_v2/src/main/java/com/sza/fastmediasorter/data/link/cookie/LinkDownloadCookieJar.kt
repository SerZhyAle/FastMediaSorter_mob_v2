package com.sza.fastmediasorter.data.link.cookie

import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkDownloadCookieJar @Inject constructor(
    private val store: EncryptedCookieStore,
    private val context: LinkDownloadSessionContext,
) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        @Suppress("DEPRECATION")
        val raw = context.cookiesFor(host)
            ?: store.loadFor(host).ifEmpty { null }
            // S0171: eTLD+1 wildcard — forward registered-domain cookies to CDN subdomains.
            // Ensures www.tiktok.com session reaches v16-webapp-prime.tiktok.com on re-fetches.
            ?: registrableDomain(host)?.let { reg ->
                store.listAllAccounts()
                    .firstOrNull { (h, _) -> registrableDomain(h) == reg }
                    ?.let { (h, e) -> store.loadForAccount(h, e.accountId) }
            }
            ?: emptyList()
        if (raw.isEmpty()) return emptyList()

        val out = raw.mapNotNull { cookie ->
            try {
                val builder = Cookie.Builder()
                    .name(cookie.name)
                    .value(cookie.value ?: "")
                    .path(cookie.path ?: "/")
                if (cookie.domain.isNullOrBlank()) {
                    builder.hostOnlyDomain(host)
                } else {
                    builder.domain(cookie.domain.trimStart('.'))
                }
                if (cookie.maxAge >= 0L) {
                    builder.expiresAt(System.currentTimeMillis() + cookie.maxAge * 1000L)
                }
                if (cookie.secure) builder.secure()
                if (cookie.isHttpOnly) builder.httpOnly()
                builder.build()
            } catch (throwable: Throwable) {
                if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                null
            }
        }

        LinkDownloadTrace.verbose(
            "link-download-cookie-jar inject host=$host ${LinkDownloadTrace.truncateCookies(out)}",
        )
        return out
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Cookies are persisted only via the explicit WebView auth flow.
    }

    // S0171: returns the registrable domain (eTLD+1) for host, e.g. "tiktok.com" for
    // "v16-webapp-prime.tiktok.com". Naive split is sufficient for the platforms we support.
    private fun registrableDomain(host: String): String? {
        val parts = host.split('.')
        return if (parts.size >= 2) "${parts[parts.size - 2]}.${parts.last()}" else null
    }
}