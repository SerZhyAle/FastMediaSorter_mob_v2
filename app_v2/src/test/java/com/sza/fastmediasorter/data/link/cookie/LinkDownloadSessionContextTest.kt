package com.sza.fastmediasorter.data.link.cookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpCookie

/**
 * S0176/S0182: Contract tests for the session-context host matching rules. Sticky-UA
 * piggybacks on the same exact-host and sibling-subdomain resolution as cookies.
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

    @Test
    fun exact_host_returns_user_agent() {
        context.set("instagram.com", emptyList(), "mobile-ua")
        assertEquals("mobile-ua", context.userAgentFor("instagram.com"))
    }

    @Test
    fun www_user_agent_matches_sibling_subdomain_requests() {
        context.set("www.instagram.com", emptyList(), "mobile-ua")
        assertEquals("mobile-ua", context.userAgentFor("m.instagram.com"))
    }

    @Test
    fun user_agent_returns_null_for_unrelated_domain() {
        context.set("instagram.com", emptyList(), "mobile-ua")
        assertNull(context.userAgentFor("tiktok.com"))
    }

    @Test
    fun overlapping_runs_on_different_hosts_keep_each_others_cookies() {
        val runA = Any()
        val runB = Any()
        val cookiesA = listOf(HttpCookie("sessionid", "insta"))
        val cookiesB = listOf(HttpCookie("SID", "yt"))
        context.set("instagram.com", cookiesA, "ua-a", audioOnly = false, owner = runA)
        context.set("youtube.com", cookiesB, "ua-b", audioOnly = true, owner = runB)

        assertEquals(cookiesA, context.cookiesFor("instagram.com"))
        assertEquals(cookiesB, context.cookiesFor("youtube.com"))

        context.clear(runA)

        assertNull(context.cookiesFor("instagram.com"))
        assertEquals(cookiesB, context.cookiesFor("youtube.com"))
        assertEquals("ua-b", context.userAgentFor("youtube.com"))
        assertTrue(context.audioOnlyFor("youtube.com"))
    }

    @Test
    fun clear_removes_only_the_owning_run_on_the_same_host() {
        val runA = Any()
        val runB = Any()
        val cookiesA = listOf(HttpCookie("sessionid", "account-x"))
        val cookiesB = listOf(HttpCookie("sessionid", "account-y"))
        context.set("instagram.com", cookiesA, null, audioOnly = false, owner = runA)
        context.set("instagram.com", cookiesB, null, audioOnly = false, owner = runB)

        context.clear(runB)

        assertEquals(cookiesA, context.cookiesFor("instagram.com"))
        assertFalse(context.audioOnlyFor("instagram.com"))
    }
}
