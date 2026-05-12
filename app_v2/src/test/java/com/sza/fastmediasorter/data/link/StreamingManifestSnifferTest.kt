package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0116 Phase 02 step 5: pure-JVM unit suite for [StreamingManifestSniffer].
 * No Robolectric required — Jsoup parses HTML without Android framework.
 */
class StreamingManifestSnifferTest {

    private val sniffer = StreamingManifestSniffer()

    @Test
    fun `harvests HLS from video-source element`() {
        val html = """
            <html><body>
              <video><source src="https://cdn.example.com/video/index.m3u8" type="application/vnd.apple.mpegurl"/></video>
            </body></html>
        """.trimIndent()
        val out = sniffer.sniff(html, "https://example.com/")
        assertTrue(out.any { it.source == HtmlMediaCandidate.Source.HLS_MANIFEST })
        assertTrue(out.any { it.manifest is StreamingManifest.Hls })
    }

    @Test
    fun `harvests DASH from JSON-LD VideoObject contentUrl`() {
        val html = """
            <html><head>
              <script type="application/ld+json">
              { "@context":"http://schema.org", "@type":"VideoObject",
                "contentUrl":"https://x.example.com/d.mpd" }
              </script>
            </head></html>
        """.trimIndent()
        val out = sniffer.sniff(html, "https://example.com/")
        assertTrue(out.any { it.source == HtmlMediaCandidate.Source.DASH_MANIFEST })
        assertTrue(out.any { it.manifest is StreamingManifest.Dash })
    }

    @Test
    fun `harvests m3u8 URL from inline script body via plain-text regex`() {
        val html = """
            <html><body>
              <script>var src = 'https://stream.example.com/live/master.m3u8?token=abc';</script>
            </body></html>
        """.trimIndent()
        val out = sniffer.sniff(html, "https://example.com/")
        assertTrue(out.any { it.source == HtmlMediaCandidate.Source.HLS_MANIFEST })
    }

    @Test
    fun `harvests from data-hls-src attribute`() {
        val html = """
            <html><body>
              <div data-hls-src="https://cdn.example.com/playlist.m3u8"></div>
            </body></html>
        """.trimIndent()
        val out = sniffer.sniff(html, "https://example.com/")
        assertTrue(out.any { it.source == HtmlMediaCandidate.Source.HLS_MANIFEST })
    }

    @Test
    fun `returns empty list when HTML has no manifest signals`() {
        val html = """
            <html><body>
              <p>Hello world</p>
              <img src="https://example.com/cat.jpg"/>
            </body></html>
        """.trimIndent()
        val out = sniffer.sniff(html, "https://example.com/")
        // Sniffer surfaces only manifest candidates; the cat.jpg is harvested elsewhere.
        assertEquals(0, out.size)
    }

    @Test
    fun `dedups same m3u8 URL appearing in multiple sources`() {
        val url = "https://cdn.example.com/v/playlist.m3u8"
        val html = """
            <html><body>
              <source src="$url"/>
              <script>var x = "$url";</script>
              <div data-hls-src="$url"></div>
            </body></html>
        """.trimIndent()
        val out = sniffer.sniff(html, "https://example.com/")
        assertEquals(1, out.size)
    }
}
