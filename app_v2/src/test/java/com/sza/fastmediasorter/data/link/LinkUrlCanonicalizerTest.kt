package com.sza.fastmediasorter.data.link

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S0190 - verifies that URL canonicalization rewrites the well-known YouTube
 * equivalents (music.youtube.com, m.youtube.com, /shorts/) to the canonical
 * `www.youtube.com/watch?v=<id>` form, while leaving everything else untouched.
 */
class LinkUrlCanonicalizerTest {

    private val canonicalizer = LinkUrlCanonicalizer()

    @Test
    fun music_youtube_watch_normalised_to_www() {
        val rewritten = canonicalizer.canonicalize("https://music.youtube.com/watch?v=abc123")
        assertEquals("https://www.youtube.com/watch?v=abc123", rewritten.url)
    }

    @Test
    fun music_youtube_preserves_list_param() {
        val rewritten = canonicalizer.canonicalize("https://music.youtube.com/watch?v=abc123&list=RDxyz")
        // Query order is preserved by HttpUrl.Builder.
        assertEquals("https://www.youtube.com/watch?v=abc123&list=RDxyz", rewritten.url)
    }

    @Test
    fun mobile_youtube_normalised_to_www() {
        val rewritten = canonicalizer.canonicalize("https://m.youtube.com/watch?v=abc123")
        assertEquals("https://www.youtube.com/watch?v=abc123", rewritten.url)
    }

    @Test
    fun shorts_rewritten_to_watch_on_www() {
        val rewritten = canonicalizer.canonicalize("https://www.youtube.com/shorts/abc123")
        assertEquals("https://www.youtube.com/watch?v=abc123", rewritten.url)
    }

    @Test
    fun bare_youtube_shorts_rewritten_to_www_watch() {
        val rewritten = canonicalizer.canonicalize("https://youtube.com/shorts/abc123")
        assertEquals("https://www.youtube.com/watch?v=abc123", rewritten.url)
    }

    @Test
    fun shorts_preserves_timestamp_query() {
        val rewritten = canonicalizer.canonicalize("https://www.youtube.com/shorts/abc123?t=5")
        // The shorts/<id> path collapses to /watch, then v=<id> is set as a query parameter.
        // The pre-existing t=5 must survive the rebuild.
        assertEquals("https://www.youtube.com/watch?t=5&v=abc123", rewritten.url)
    }

    @Test
    fun canonical_url_passthrough() {
        val canonical = "https://www.youtube.com/watch?v=abc123"
        assertEquals(canonical, canonicalizer.canonicalize(canonical).url)
    }

    @Test
    fun non_youtube_url_unchanged() {
        val foreign = "https://example.com/foo?bar=1"
        assertEquals(foreign, canonicalizer.canonicalize(foreign).url)
    }

    @Test
    fun malformed_url_unchanged() {
        val nonsense = "not a url"
        assertEquals(nonsense, canonicalizer.canonicalize(nonsense).url)
    }

    @Test
    fun youtube_non_shorts_path_unchanged() {
        // /playlist?list=… stays under www.youtube.com but is not /shorts/ so passes through.
        val playlist = "https://www.youtube.com/playlist?list=PLxyz"
        assertEquals(playlist, canonicalizer.canonicalize(playlist).url)
    }

    @Test
    fun shorts_host_case_insensitive() {
        val rewritten = canonicalizer.canonicalize("https://WWW.YouTube.COM/shorts/abc123")
        // Host is case-insensitive - HttpUrl lowercases it automatically.
        assertEquals("https://www.youtube.com/watch?v=abc123", rewritten.url)
    }

    @Test
    fun music_youtube_case_insensitive() {
        val rewritten = canonicalizer.canonicalize("https://Music.YouTube.com/watch?v=abc123")
        assertEquals("https://www.youtube.com/watch?v=abc123", rewritten.url)
    }

    @Test
    fun music_youtube_marks_audio_only() {
        val rewritten = canonicalizer.canonicalize("https://music.youtube.com/watch?v=abc123")
        assertEquals(true, rewritten.audioOnly)
    }

    @Test
    fun shorts_does_not_mark_audio_only() {
        val rewritten = canonicalizer.canonicalize("https://www.youtube.com/shorts/abc123")
        assertEquals(false, rewritten.audioOnly)
    }

    @Test
    fun non_youtube_does_not_mark_audio_only() {
        val rewritten = canonicalizer.canonicalize("https://example.com/foo")
        assertEquals(false, rewritten.audioOnly)
    }
}
