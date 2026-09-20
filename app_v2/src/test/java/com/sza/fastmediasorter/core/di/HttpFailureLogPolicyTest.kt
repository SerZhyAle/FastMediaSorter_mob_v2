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
    fun `providers that log their own refusal use the expected fallback path`() {
        val urls = listOf(
            "https://api.open-meteo.com/v1/forecast?latitude=35.9&longitude=14.5",
            "https://geocoding-api.open-meteo.com/v1/search?name=Valletta",
            "https://www.youtube.com/feeds/videos.xml?channel_id=UC123",
            "https://www.youtube.com/@handle",
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

    // An unclassified host keeps the interceptor's own line, which is a warning - it is the duplicate
    // that this list suppresses, not the log entry itself.
    @Test
    fun `unrelated HTTP failures keep the interceptor line`() {
        val url = "https://example.com/api/files".toHttpUrl()

        assertFalse(isExpectedHttpFallback(url))
    }
}
