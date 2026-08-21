package com.sza.fastmediasorter.data.link.cookie

import io.mockk.every
import io.mockk.mockk
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpCookie

/**
 * Unit tests for [LinkDownloadCookieJar.loadForRequest]: session-context precedence over the
 * persistent store, HttpCookie→okhttp3.Cookie mapping (host-only vs explicit domain), the empty
 * fallback, and the read-only saveFromResponse no-op. Store + session context are mocked - no
 * CryptoHelper, no disk.
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
        every { store.loadForHostAccountOrBest("example.com", null) } returns listOf(httpCookie("auth", "fromStore", domain = ".example.com"))

        val cookies = jar.loadForRequest("https://example.com/x".toHttpUrl())

        assertEquals(1, cookies.size)
        assertEquals("auth", cookies[0].name)
        // explicit domain is trimmed of leading dot
        assertEquals("example.com", cookies[0].domain)
    }

    @Test
    fun `returns empty when neither source has cookies`() {
        every { context.cookiesFor(any()) } returns null
        every { store.loadForHostAccountOrBest(any(), any()) } returns emptyList()
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
    fun `saveFromResponse is a no-op`() {
        // Must not touch the store - persistence only happens via the explicit WebView flow.
        jar.saveFromResponse("https://x.com/".toHttpUrl(), emptyList())
        io.mockk.verify(exactly = 0) { store.loadForHostAccountOrBest(any(), any()) }
    }
}
