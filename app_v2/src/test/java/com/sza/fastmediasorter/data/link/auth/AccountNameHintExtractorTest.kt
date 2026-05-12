package com.sza.fastmediasorter.data.link.auth

import java.net.HttpCookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountNameHintExtractorTest {

    @Test
    fun `prefers explicit username cookie`() {
        val out = AccountNameHintExtractor.extract(
            listOf(HttpCookie("username", "artist_account")),
        )

        assertEquals("artist_account", out)
    }

    @Test
    fun `falls back to ds_user and twid formats`() {
        val fromDsUser = AccountNameHintExtractor.extract(
            listOf(HttpCookie("ds_user", "creator")),
        )
        val fromTwid = AccountNameHintExtractor.extract(
            listOf(HttpCookie("twid", "u=987654")),
        )

        assertEquals("creator", fromDsUser)
        assertEquals("987654", fromTwid)
    }

    @Test
    fun `returns null when cookies do not carry account hints`() {
        val out = AccountNameHintExtractor.extract(
            listOf(HttpCookie("csrftoken", "abc123")),
        )

        assertNull(out)
    }
}