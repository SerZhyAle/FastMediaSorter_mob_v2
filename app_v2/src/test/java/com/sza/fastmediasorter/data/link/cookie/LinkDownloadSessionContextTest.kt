package com.sza.fastmediasorter.data.link.cookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.net.HttpCookie

/**
 * S0176: Contract tests for [LinkDownloadSessionContext.cookiesFor] — freezes the
 * current matching rules so Q3 research result (session context holder unchanged) stays
 * executable.
 */
class LinkDownloadSessionContextTest {

    private lateinit var context: LinkDownloadSessionContext

    @Before
    fun setUp() {
        context = LinkDownloadSessionContext()
    }

    @Test
    fun exact_host_returns_cookies() {
        val cookies = listOf(HttpCookie("sessionid", "abc123"))
        context.set("instagram.com", cookies)
        assertEquals(cookies, context.cookiesFor("instagram.com"))
    }

    @Test
    fun www_session_matches_sibling_subdomain_requests() {
        // Q3 research result: www.instagram.com active session serves m.instagram.com requests
        // because cookiesFor() strips the "www." prefix and then checks suffix match.
        val cookies = listOf(HttpCookie("sessionid", "abc123"))
        context.set("www.instagram.com", cookies)
        assertEquals(cookies, context.cookiesFor("m.instagram.com"))
    }

    @Test
    fun no_match_for_unrelated_domain() {
        val cookies = listOf(HttpCookie("sessionid", "abc123"))
        context.set("instagram.com", cookies)
        assertNull(context.cookiesFor("tiktok.com"))
    }
}
