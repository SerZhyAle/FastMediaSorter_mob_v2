package com.sza.fastmediasorter.data.link

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S0140: pure-JVM coverage for structured data harvesting. OkHttp is stubbed with
 * an interceptor so JSON-LD and oEmbed stay testable without Android runtime.
 *
 * S0223: @RunWith(RobolectricTestRunner::class) added so that org.json.JSONObject is
 * backed by the real Android implementation (not the JVM stub that returns null from
 * all methods under isReturnDefaultValues=true). Required for any test that exercises
 * JSON parsing in StructuredMediaSniffer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.11.1 maxSdkVersion=34; targetSdkVersion=35 would fail without this.
class StructuredMediaSnifferTest {

    @Test
    fun `harvests JSON-LD VideoObject contentUrl`() = runBlocking {
        val sniffer = StructuredMediaSniffer(fakeHttpClient("{}"))
        val html = """
            <html><head>
              <script type="application/ld+json">
                { "@type":"VideoObject", "contentUrl":"https://cdn.example.com/video.mp4" }
              </script>
            </head></html>
        """.trimIndent()

        val out = sniffer.sniff(html, "https://example.com/post")

        assertTrue(out.any {
            it.source == HtmlMediaCandidate.Source.JSON_LD &&
                it.url == "https://cdn.example.com/video.mp4"
        })
    }

    @Test
    fun `harvests JSON-LD ImageObject contentUrl`() = runBlocking {
        val sniffer = StructuredMediaSniffer(fakeHttpClient("{}"))
        val html = """
            <html><head>
              <script type="application/ld+json">
                { "@type":"ImageObject", "contentUrl":"https://cdn.example.com/photo.jpg" }
              </script>
            </head></html>
        """.trimIndent()

        val out = sniffer.sniff(html, "https://example.com/post")

        assertTrue(out.any {
            it.source == HtmlMediaCandidate.Source.JSON_LD &&
                it.url == "https://cdn.example.com/photo.jpg"
        })
    }

    @Test
    fun `harvests oEmbed direct URL for photo responses`() = runBlocking {
        val sniffer = StructuredMediaSniffer(
            fakeHttpClient(
                """
                { "type":"photo", "url":"https://cdn.example.com/oembed-photo.jpg" }
                """.trimIndent(),
            ),
        )
        val html = """
            <html><head>
              <link rel="alternate" type="application/json+oembed" href="https://provider.example.com/oembed?url=123" />
            </head></html>
        """.trimIndent()

        val out = sniffer.sniff(html, "https://example.com/post")

        assertTrue(out.any {
            it.source == HtmlMediaCandidate.Source.OEMBED &&
                it.url == "https://cdn.example.com/oembed-photo.jpg"
        })
    }

    @Test
    fun `harvests video source from oEmbed html snippet`() = runBlocking {
        val sniffer = StructuredMediaSniffer(
            fakeHttpClient(
                """
                {
                  "type":"video",
                  "html":"<video><source src='https://cdn.example.com/embed.mp4' /></video>"
                }
                """.trimIndent(),
            ),
        )
        val html = """
            <html><head>
              <link rel="alternate" type="application/json+oembed" href="https://provider.example.com/oembed?url=123" />
            </head></html>
        """.trimIndent()

        val out = sniffer.sniff(html, "https://example.com/post")

        assertTrue(out.any {
            it.source == HtmlMediaCandidate.Source.OEMBED &&
                it.url == "https://cdn.example.com/embed.mp4"
        })
    }

    @Test
    fun `dedups repeated structured URLs`() = runBlocking {
        val sniffer = StructuredMediaSniffer(
            fakeHttpClient(
                """
                { "type":"photo", "url":"https://cdn.example.com/shared.jpg" }
                """.trimIndent(),
            ),
        )
        val html = """
            <html><head>
              <script type="application/ld+json">
                { "@type":"ImageObject", "contentUrl":"https://cdn.example.com/shared.jpg" }
              </script>
              <link rel="alternate" type="application/json+oembed" href="https://provider.example.com/oembed?url=123" />
            </head></html>
        """.trimIndent()

        val out = sniffer.sniff(html, "https://example.com/post")

        assertEquals(1, out.count { it.url == "https://cdn.example.com/shared.jpg" })
    }

        @Test
        fun `sniffEmbeddedJson harvests Threads single image`() {
                val sniffer = StructuredMediaSniffer(fakeHttpClient("{}"))
                val html = """
                        <html><head>
                            <script type="application/json" data-sjs>
                                {
                                    "thread_items": [
                                        {
                                            "post": {
                                                "image_versions2": {
                                                    "candidates": [
                                                        { "url": "https://cdn.example.com/thread-full.jpg" },
                                                        { "url": "https://cdn.example.com/thread-fallback.jpg" }
                                                    ]
                                                }
                                            }
                                        }
                                    ]
                                }
                            </script>
                        </head></html>
                """.trimIndent()

                val out = sniffer.sniffEmbeddedJson(html, "https://www.threads.com/@user/post/1")

                assertTrue(out.any {
                        it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON &&
                                it.url == "https://cdn.example.com/thread-full.jpg"
                })
        }

        @Test
        fun `sniffEmbeddedJson keeps one canonical URL per Threads carousel slide`() {
                val sniffer = StructuredMediaSniffer(fakeHttpClient("{}"))
                val html = """
                        <html><head>
                            <script type="application/json" data-sjs>
                                {
                                    "thread_items": [
                                        {
                                            "post": {
                                                "carousel_media": [
                                                    {
                                                        "image_versions2": {
                                                            "candidates": [
                                                                { "url": "https://cdn.example.com/slide-1-full.jpg" },
                                                                { "url": "https://cdn.example.com/slide-1-fallback.jpg" }
                                                            ]
                                                        }
                                                    },
                                                    {
                                                        "image_versions2": {
                                                            "candidates": [
                                                                { "url": "https://cdn.example.com/slide-2-full.jpg" },
                                                                { "url": "https://cdn.example.com/slide-2-fallback.jpg" }
                                                            ]
                                                        }
                                                    }
                                                ]
                                            }
                                        }
                                    ]
                                }
                            </script>
                        </head></html>
                """.trimIndent()

                val out = sniffer.sniffEmbeddedJson(html, "https://www.threads.com/@user/post/2")

                assertEquals(
                        listOf(
                                "https://cdn.example.com/slide-1-full.jpg",
                                "https://cdn.example.com/slide-2-full.jpg",
                        ),
                        out.map { it.url },
                )
                assertTrue(out.all { it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON })
        }

    // --- S0223: sniffInstagramApiResponse tests ---

    @Test
    fun `sniffInstagramApiResponse single image post returns EMBEDDED_JSON candidate`() {
        val sniffer = StructuredMediaSniffer(fakeHttpClient("{}"))
        val json = """
            {
              "items": [
                {
                  "image_versions2": {
                    "candidates": [
                      { "url": "https://cdninstagram.com/img_full.jpg", "width": 1080, "height": 1080 },
                      { "url": "https://cdninstagram.com/img_thumb.jpg", "width": 150, "height": 150 }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val out = sniffer.sniffInstagramApiResponse(json, "https://www.instagram.com/p/ABC/")

        // First candidate is taken (highest-res, first in array)
        assertEquals(1, out.size)
        assertEquals("https://cdninstagram.com/img_full.jpg", out[0].url)
        assertEquals(HtmlMediaCandidate.Source.EMBEDDED_JSON, out[0].source)
    }

    @Test
    fun `sniffInstagramApiResponse carousel returns all slide candidates`() {
        val sniffer = StructuredMediaSniffer(fakeHttpClient("{}"))
        val json = """
            {
              "items": [
                {
                  "carousel_media": [
                    { "image_versions2": { "candidates": [{ "url": "https://cdninstagram.com/slide1.jpg" }] } },
                    { "image_versions2": { "candidates": [{ "url": "https://cdninstagram.com/slide2.jpg" }] } },
                    { "image_versions2": { "candidates": [{ "url": "https://cdninstagram.com/slide3.jpg" }] } }
                  ]
                }
              ]
            }
        """.trimIndent()

        val out = sniffer.sniffInstagramApiResponse(json, "https://www.instagram.com/p/ABC/")

        assertEquals(3, out.size)
        assertTrue(out.all { it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON })
        assertEquals(
            listOf(
                "https://cdninstagram.com/slide1.jpg",
                "https://cdninstagram.com/slide2.jpg",
                "https://cdninstagram.com/slide3.jpg",
            ),
            out.map { it.url },
        )
    }

    @Test
    fun `sniffInstagramApiResponse empty items returns empty list`() {
        val sniffer = StructuredMediaSniffer(fakeHttpClient("{}"))
        val json = """{"items":[]}"""

        val out = sniffer.sniffInstagramApiResponse(json, "https://www.instagram.com/p/ABC/")

        assertTrue(out.isEmpty())
    }

    @Test
    fun `sniffInstagramApiResponse malformed JSON returns empty list without crash`() {
        val sniffer = StructuredMediaSniffer(fakeHttpClient("{}"))

        val out = sniffer.sniffInstagramApiResponse("not-json!!!", "https://www.instagram.com/p/ABC/")

        assertTrue(out.isEmpty())
    }

    @Test
    fun `sniffInstagramApiResponse deduplicates slides with same CDN asset key`() {
        val sniffer = StructuredMediaSniffer(fakeHttpClient("{}"))
        // Two URLs for the same asset differ only in CDN edge node and signing params;
        // last path segment (the asset key) is identical.
        val json = """
            {
              "items": [
                {
                  "carousel_media": [
                    { "image_versions2": { "candidates": [{ "url": "https://edge1.cdninstagram.com/v/img_abc123_n.jpg?token=A" }] } },
                    { "image_versions2": { "candidates": [{ "url": "https://edge2.cdninstagram.com/v/img_abc123_n.jpg?token=B" }] } }
                  ]
                }
              ]
            }
        """.trimIndent()

        val out = sniffer.sniffInstagramApiResponse(json, "https://www.instagram.com/p/ABC/")

        // Same asset key → deduplicated to 1
        assertEquals(1, out.size)
    }

    private fun fakeHttpClient(jsonBody: String): OkHttpClient {
        val mediaType = "application/json".toMediaType()
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonBody.toResponseBody(mediaType))
                    .build()
            }
            .build()
    }
}