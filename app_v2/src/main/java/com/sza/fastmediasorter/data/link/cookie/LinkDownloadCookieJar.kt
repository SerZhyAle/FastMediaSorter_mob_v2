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
            // S0171/S0176: eTLD+1 wildcard - forward registered-domain cookies to CDN subdomains.
            // Uses the shared PSL-aware resolver (S0176) so co.uk / com.au are handled correctly.
            ?: registrableDomainOrNull(host)?.let { reg ->
                store.listAllAccounts()
                    .firstOrNull { (h, _) -> registrableDomainOrNull(h) == reg }
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

}