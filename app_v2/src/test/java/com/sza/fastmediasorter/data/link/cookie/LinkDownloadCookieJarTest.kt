package com.sza.fastmediasorter.data.link.cookie

import io.mockk.every
import io.mockk.mockk
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpCookie

/**
 * Unit tests for [LinkDownloadCookieJar.loadForRequest]: session-context precedence over the
 * persistent store, HttpCookie→okhttp3.Cookie mapping (host-only vs explicit domain), the empty
 * fallback, and saveFromResponse folding redirect-hop cookies into the running session only (S4509).
 * Store is mocked - no CryptoHelper, no disk.
 */
class LinkDownloadCookieJarTest {

    private val store = mockk<EncryptedCookieStore>(relaxed = true)
    private val context = mockk<LinkDownloadSessionContext>(relaxed = true)
    private val jar = LinkDownloadCookieJar(store, context)

    private fun httpCookie(name: String, value: String, domain: String? = null) =
        HttpCookie(name, value).apply {
            path = "/"
            if (domain != null) this.domain = domain
        }

    @Test
    fun `prefers session-context cookies over store`() {
        every { context.cookiesFor("example.com") } returns listOf(httpCookie("sid", "ctx"))

        val cookies = jar.loadForRequest("https://example.com/file".toHttpUrl())

        assertEquals(1, cookies.size)
        assertEquals("sid", cookies[0].name)
        assertEquals("ctx", cookies[0].value)
        // host-only domain when source cookie had no explicit domain
        assertEquals("example.com", cookies[0].domain)
    }

    @Test
    fun `falls back to store when session has none`() {
        every { context.cookiesFor("example.com") } returns null
        every { store.bestAccountIdFor("example.com") } returns "acc1"
        every {
            store.loadForAccount("example.com", "acc1")
        } returns listOf(httpCookie("auth", "fromStore", domain = ".example.com"))

        val cookies = jar.loadForRequest("https://example.com/x".toHttpUrl())

        assertEquals(1, cookies.size)
        assertEquals("auth", cookies[0].name)
        // explicit domain is trimmed of leading dot
        assertEquals("example.com", cookies[0].domain)
    }

    @Test
    fun `returns empty when neither source has cookies`() {
        every { context.cookiesFor(any()) } returns null
        every { store.bestAccountIdFor(any()) } returns null
        every { store.listAllAccounts() } returns emptyList()

        assertTrue(jar.loadForRequest("https://nowhere.test/x".toHttpUrl()).isEmpty())
    }

    @Test
    fun `maps multiple cookies preserving names`() {
        every { context.cookiesFor("site.com") } returns listOf(
            httpCookie("a", "1"),
            httpCookie("b", "2")
        )

        val cookies = jar.loadForRequest("https://site.com/".toHttpUrl())
        assertEquals(setOf("a", "b"), cookies.map { it.name }.toSet())
    }

    @Test
    fun `saveFromResponse never touches the store`() {
        // Persistence only happens via the explicit WebView flow.
        jar.saveFromResponse("https://x.com/".toHttpUrl(), emptyList())
        io.mockk.verify(exactly = 0) { store.bestAccountIdFor(any()) }
    }

    private val sessionOwner = Any()

    private fun responseCookie(name: String, value: String, expiresAtMillis: Long? = null): Cookie {
        val builder = Cookie.Builder().name(name).value(value).domain("instagram.com").path("/")
        if (expiresAtMillis != null) builder.expiresAt(expiresAtMillis)
        return builder.build()
    }

    /** A jar over a real session context holding a signed-in Instagram session, as one run registers it. */
    private fun jarWithInstagramSession(): LinkDownloadCookieJar {
        val realContext = LinkDownloadSessionContext()
        realContext.set(
            "www.instagram.com",
            listOf(httpCookie("sessionid", "s1"), httpCookie("csrftoken", "old")),
            null,
            audioOnly = false,
            owner = sessionOwner,
        )
        return LinkDownloadCookieJar(store, realContext)
    }

    private fun valuesAfterSave(jar: LinkDownloadCookieJar, vararg received: Cookie): Map<String, String> {
        val url = "https://www.instagram.com/p/abc/".toHttpUrl()
        jar.saveFromResponse(url, received.toList())
        return jar.loadForRequest(url).associate { it.name to it.value }
    }

    @Test
    fun `rotated response cookie replaces the stale value on the next hop`() {
        val values = valuesAfterSave(jarWithInstagramSession(), responseCookie("csrftoken", "new"))

        assertEquals(mapOf("sessionid" to "s1", "csrftoken" to "new"), values)
    }

    @Test
    fun `new response cookie is added to the running session`() {
        val values = valuesAfterSave(jarWithInstagramSession(), responseCookie("rur", "r1"))

        assertEquals(mapOf("sessionid" to "s1", "csrftoken" to "old", "rur" to "r1"), values)
    }

    @Test
    fun `expired response cookie deletes the cookie it names`() {
        val values = valuesAfterSave(jarWithInstagramSession(), responseCookie("sessionid", "", expiresAtMillis = 1L))

        assertEquals(mapOf("csrftoken" to "old"), values)
    }

    @Test
    fun `merge classifies each received cookie and remembers only deletions`() {
        val realContext = LinkDownloadSessionContext()
        realContext.set(
            "www.instagram.com",
            listOf(httpCookie("sessionid", "s1"), httpCookie("csrftoken", "old")),
            null,
            audioOnly = false,
            owner = sessionOwner,
        )
        val expired = HttpCookie("sessionid", "").apply {
            path = "/"
            maxAge = 0L
        }

        val rotation = realContext.mergeResponseCookies("www.instagram.com", listOf(httpCookie("csrftoken", "new")))
        assertEquals(listOf("csrftoken"), rotation.replaced)
        assertTrue(realContext.serverDeletedCookies(sessionOwner).isEmpty())

        val revoke = realContext.mergeResponseCookies("i.instagram.com", listOf(expired, httpCookie("rur", "r1")))
        assertEquals(listOf("sessionid"), revoke.deleted)
        assertEquals(listOf("rur"), revoke.added)
        assertEquals(setOf("sessionid"), realContext.serverDeletedCookies(sessionOwner))
        assertTrue(realContext.serverDeletedCookies(Any()).isEmpty())
    }

    @Test
    fun `response cookie without a running session is dropped`() {
        every { store.bestAccountIdFor(any()) } returns null
        every { store.listAllAccounts() } returns emptyList()
        val bare = LinkDownloadCookieJar(store, LinkDownloadSessionContext())

        assertTrue(valuesAfterSave(bare, responseCookie("csrftoken", "new")).isEmpty())
    }

    @Test
    fun `best account pick is cached until the store generation moves`() {
        var generation = 1L
        every { context.cookiesFor("cdn.example.com") } returns null
        every { store.writeGeneration } answers { generation }
        every { store.bestAccountIdFor("cdn.example.com") } returns "acc1"
        every { store.loadForAccount("cdn.example.com", "acc1") } returns listOf(httpCookie("sid", "v"))
        val url = "https://cdn.example.com/seg.ts".toHttpUrl()

        repeat(3) { assertEquals(1, jar.loadForRequest(url).size) }
        io.mockk.verify(exactly = 1) { store.bestAccountIdFor("cdn.example.com") }
        io.mockk.verify(exactly = 3) { store.loadForAccount("cdn.example.com", "acc1") }

        generation = 2L
        jar.loadForRequest(url)
        io.mockk.verify(exactly = 2) { store.bestAccountIdFor("cdn.example.com") }
    }
}
