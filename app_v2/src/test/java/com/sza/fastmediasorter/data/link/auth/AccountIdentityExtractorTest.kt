package com.sza.fastmediasorter.data.link.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpCookie

class AccountIdentityExtractorTest {

    @Test
    fun `instagram main host returns ds_user_id`() {
        val out = AccountIdentityExtractor.extract(
            host = "instagram.com",
            cookies = listOf(HttpCookie("ds_user_id", "112233")),
        )

        assertEquals("112233", out)
    }

    @Test
    fun `instagram subdomain returns ds_user_id`() {
        val out = AccountIdentityExtractor.extract(
            host = "www.instagram.com",
            cookies = listOf(HttpCookie("ds_user_id", "445566")),
        )

        assertEquals("445566", out)
    }

    @Test
    fun `threads returns ds_user_id`() {
        val out = AccountIdentityExtractor.extract(
            host = "threads.net",
            cookies = listOf(HttpCookie("ds_user_id", "778899")),
        )

        assertEquals("778899", out)
    }

    @Test
    fun `facebook returns c_user`() {
        val out = AccountIdentityExtractor.extract(
            host = "www.facebook.com",
            cookies = listOf(HttpCookie("c_user", "100000123")),
        )

        assertEquals("100000123", out)
    }

    @Test
    fun `x dot com strips twid u prefix`() {
        val out = AccountIdentityExtractor.extract(
            host = "x.com",
            cookies = listOf(HttpCookie("twid", "u=987654")),
        )

        assertEquals("987654", out)
    }

    @Test
    fun `twitter dot com returns twid without prefix`() {
        val out = AccountIdentityExtractor.extract(
            host = "twitter.com",
            cookies = listOf(HttpCookie("twid", "555444")),
        )

        assertEquals("555444", out)
    }

    @Test
    fun `unknown host returns null even with identity-shaped cookies`() {
        val out = AccountIdentityExtractor.extract(
            host = "vk.com",
            cookies = listOf(HttpCookie("ds_user_id", "112233")),
        )

        assertNull(out)
    }

    @Test
    fun `known host with missing identity cookie returns null`() {
        val out = AccountIdentityExtractor.extract(
            host = "instagram.com",
            cookies = listOf(HttpCookie("csrftoken", "abc123")),
        )

        assertNull(out)
    }

    @Test
    fun `blank identity cookie value returns null`() {
        val out = AccountIdentityExtractor.extract(
            host = "facebook.com",
            cookies = listOf(HttpCookie("c_user", "")),
        )

        assertNull(out)
    }

    @Test
    fun `twid value containing only u prefix returns null after strip`() {
        val out = AccountIdentityExtractor.extract(
            host = "x.com",
            cookies = listOf(HttpCookie("twid", "u=")),
        )

        assertNull(out)
    }

    @Test
    fun `mixed-case host is matched`() {
        val out = AccountIdentityExtractor.extract(
            host = "WWW.Instagram.COM",
            cookies = listOf(HttpCookie("ds_user_id", "112233")),
        )

        assertEquals("112233", out)
    }

    @Test
    fun `instagram anonymous cookies are not a signed-in session`() {
        val signedIn = AccountIdentityExtractor.hasSignedInCookies(
            host = "www.instagram.com",
            cookies = listOf(HttpCookie("csrftoken", "abc"), HttpCookie("mid", "def")),
        )

        assertFalse(signedIn)
    }

    @Test
    fun `instagram sessionid makes a signed-in session`() {
        val signedIn = AccountIdentityExtractor.hasSignedInCookies(
            host = "www.instagram.com",
            cookies = listOf(HttpCookie("csrftoken", "abc"), HttpCookie("SessionID", "42%3Atoken")),
        )

        assertTrue(signedIn)
    }

    @Test
    fun `blank sessionid value is not a signed-in session`() {
        val signedIn = AccountIdentityExtractor.hasSignedInCookies(
            host = "instagram.com",
            cookies = listOf(HttpCookie("sessionid", "")),
        )

        assertFalse(signedIn)
    }

    @Test
    fun `threads needs sessionid too`() {
        assertFalse(
            AccountIdentityExtractor.hasSignedInCookies("threads.net", listOf(HttpCookie("csrftoken", "abc"))),
        )
        assertTrue(
            AccountIdentityExtractor.hasSignedInCookies("threads.net", listOf(HttpCookie("sessionid", "42"))),
        )
    }

    @Test
    fun `facebook and x need their identity cookie`() {
        assertFalse(AccountIdentityExtractor.hasSignedInCookies("www.facebook.com", listOf(HttpCookie("fr", "1"))))
        assertTrue(AccountIdentityExtractor.hasSignedInCookies("www.facebook.com", listOf(HttpCookie("c_user", "1"))))
        assertFalse(AccountIdentityExtractor.hasSignedInCookies("x.com", listOf(HttpCookie("guest_id", "1"))))
        assertTrue(AccountIdentityExtractor.hasSignedInCookies("x.com", listOf(HttpCookie("twid", "u=1"))))
    }

    @Test
    fun `unknown host passes on any non-empty cookie set`() {
        assertTrue(AccountIdentityExtractor.hasSignedInCookies("vk.com", listOf(HttpCookie("remixsid", "1"))))
        assertFalse(AccountIdentityExtractor.hasSignedInCookies("vk.com", emptyList()))
    }

    @Test
    fun `blank host is never signed in`() {
        assertFalse(AccountIdentityExtractor.hasSignedInCookies("", listOf(HttpCookie("sessionid", "1"))))
    }
}
