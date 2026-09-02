package com.sza.fastmediasorter.core.di

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpFailureLogPolicyTest {

    @Test
    fun `optional artwork requests use the expected fallback path`() {
        val urls = listOf(
            "https://itunes.apple.com/search?term=track",
            "https://api.deezer.com/search?q=track",
            "https://musicbrainz.org/ws/2/recording/?query=track",
            "https://coverartarchive.org/release/release-id/front-500",
        )

        urls.forEach { url ->
            assertTrue(url, isExpectedHttpFallback(url.toHttpUrl()))
        }
    }

    @Test
    fun `delivery manifest keeps its existing fallback classification`() {
        val url = "https://example.com/delivery-manifest.json".toHttpUrl()

        assertTrue(isExpectedHttpFallback(url))
    }

    @Test
    fun `unrelated HTTP failures remain errors`() {
        val url = "https://example.com/api/files".toHttpUrl()

        assertFalse(isExpectedHttpFallback(url))
    }
}
