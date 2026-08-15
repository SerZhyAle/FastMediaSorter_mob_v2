package com.sza.fastmediasorter.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S1511 - Phase 01 Step 01.1.
 *
 * One test per normalization rule, plus the two cases the step names explicitly: scheme case must fold
 * together, path case must not. Pure JVM - the normalizer holds no Android type.
 */
class StreamUrlNormalizerTest {

    @Test
    fun `addresses differing only in scheme case normalize equal`() {
        assertEquals(
            StreamUrlNormalizer.normalize("http://example.com/live.m3u8"),
            StreamUrlNormalizer.normalize("HTTP://example.com/live.m3u8"),
        )
    }

    @Test
    fun `addresses differing only in host case normalize equal`() {
        assertEquals(
            StreamUrlNormalizer.normalize("http://example.com/live.m3u8"),
            StreamUrlNormalizer.normalize("http://Example.COM/live.m3u8"),
        )
    }

    @Test
    fun `addresses differing only in path case do not normalize equal`() {
        assertNotEquals(
            StreamUrlNormalizer.normalize("http://example.com/Live.m3u8"),
            StreamUrlNormalizer.normalize("http://example.com/live.m3u8"),
        )
    }

    @Test
    fun `default port is dropped`() {
        assertEquals("http://example.com/live", StreamUrlNormalizer.normalize("http://example.com:80/live"))
        assertEquals("https://example.com/live", StreamUrlNormalizer.normalize("https://example.com:443/live"))
    }

    @Test
    fun `non-default port is kept`() {
        assertEquals("http://example.com:8080/live", StreamUrlNormalizer.normalize("http://example.com:8080/live"))
    }

    @Test
    fun `trailing slash is dropped`() {
        assertEquals("http://example.com/live", StreamUrlNormalizer.normalize("http://example.com/live/"))
        assertEquals("http://example.com", StreamUrlNormalizer.normalize("http://example.com/"))
    }

    @Test
    fun `query is preserved verbatim`() {
        val url = "http://example.com/live?Token=AbC&x=1"
        assertEquals(url, StreamUrlNormalizer.normalize(url))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("http://example.com/live", StreamUrlNormalizer.normalize("  http://example.com/live  "))
    }

    @Test
    fun `unparsable address falls back to the trimmed original`() {
        assertEquals("not an address", StreamUrlNormalizer.normalize(" not an address/ "))
    }
}
