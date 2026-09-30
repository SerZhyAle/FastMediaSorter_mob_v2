package com.sza.fastmediasorter.data.link.cookie

import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import com.sza.fastmediasorter.core.util.httpOnlyCompat
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.net.HttpCookie
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkDownloadCookieJar @Inject constructor(
    private val store: EncryptedCookieStore,
    private val context: LinkDownloadSessionContext,
) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val raw = context.cookiesFor(host)
            ?: hostBestAccountCookies(host)
            // S0171/S0176: eTLD+1 wildcard - forward registered-domain cookies to CDN subdomains.
            // Uses the shared PSL-aware resolver (S0176) so co.uk / com.au are handled correctly.
            ?: registrableDomainOrNull(host)?.let(::registrableDomainCookies)
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
                if (cookie.httpOnlyCompat) builder.httpOnly()
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

    /**
     * Picking a host's best account decrypts every stored record, and a segmented download asks
     * once per segment, so the pick is remembered per host until the store's next write, exactly
     * like the registrable-domain match below. A miss is remembered too.
     */
    private fun hostBestAccountCookies(host: String): List<HttpCookie>? {
        val generation = store.writeGeneration
        val match = bestAccounts[host]?.takeIf { it.generation == generation }
            ?: DomainMatch(generation, host, store.bestAccountIdFor(host)).also { bestAccounts[host] = it }
        val accountId = match.accountId ?: return null
        return store.loadForAccount(host, accountId).ifEmpty { null }
    }

    private val bestAccounts = ConcurrentHashMap<String, DomainMatch>()

    /**
     * listAllAccounts decrypts and parses every stored record, and a segmented HLS/DASH download
     * asks once per segment, so the account matched to a registrable domain is remembered until the
     * store's next write. A miss is remembered too - it is the common case for a host with no login.
     */
    private fun registrableDomainCookies(registrableDomain: String): List<HttpCookie>? {
        val generation = store.writeGeneration
        val match = domainMatches[registrableDomain]?.takeIf { it.generation == generation }
            ?: resolveDomainMatch(registrableDomain, generation).also { domainMatches[registrableDomain] = it }
        val accountId = match.accountId ?: return null
        return store.loadForAccount(match.host, accountId)
    }

    private fun resolveDomainMatch(registrableDomain: String, generation: Long): DomainMatch {
        val found = store.listAllAccounts().firstOrNull { (h, _) -> registrableDomainOrNull(h) == registrableDomain }
        return DomainMatch(generation, found?.first.orEmpty(), found?.second?.accountId)
    }

    private class DomainMatch(val generation: Long, val host: String, val accountId: String?)

    private val domainMatches = ConcurrentHashMap<String, DomainMatch>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Cookies are persisted only via the explicit WebView auth flow.
    }

}
