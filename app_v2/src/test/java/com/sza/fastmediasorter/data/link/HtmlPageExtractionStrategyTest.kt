package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class HtmlPageExtractionStrategyTest {

    private val direct: DirectFileExtractionStrategy = mockk()
    private val structuredMediaSniffer: StructuredMediaSniffer = mockk()
    private val settingsRepository: SettingsRepository = mockk()

    @Before
    fun setUp() {
        every { settingsRepository.getSettings() } returns flowOf(AppSettings())
        coEvery { structuredMediaSniffer.sniff(any<Document>()) } returns emptyList()
        every { structuredMediaSniffer.sniffEmbeddedJson(any<Document>()) } returns emptyList()
    }

    @Test
    fun `instagram single-image post uses ig api fallback before direct download`() = runTest {
        val pageUrl = "https://www.instagram.com/p/ABC123/"
        val pageHtml = """
            <html>
              <head>
                <meta property="og:image" content="https://preview.example.com/preview.jpg" />
              </head>
            </html>
        """.trimIndent()
        val apiJson = """
            {
              "items": [
                {
                  "image_versions2": {
                    "candidates": [
                      { "url": "https://cdninstagram.com/full.jpg" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val requests = mutableListOf<Request>()
        val expected = OpenResult.Stream(
            body = ByteArrayInputStream("img".toByteArray()),
            contentLength = 3,
            mime = "image/jpeg",
            fileName = "full.jpg",
            close = {},
        )
        every { structuredMediaSniffer.sniffInstagramApiResponse(apiJson, pageUrl) } returns listOf(
            embeddedCandidate("https://cdninstagram.com/full.jpg"),
        )
        coEvery { direct.open("https://cdninstagram.com/full.jpg", any()) } returns expected

        val strategy = HtmlPageExtractionStrategy(
            httpClient = fakeHttpClient(
                pageUrl = pageUrl,
                pageHtml = pageHtml,
                apiJson = apiJson,
                requests = requests,
            ),
            direct = direct,
            streamingSniffer = StreamingManifestSniffer(),
            structuredMediaSniffer = structuredMediaSniffer,
            settingsRepository = settingsRepository,
        )

        val out = strategy.open(pageUrl) { _, _ -> }

        assertTrue(out is OpenResult.Stream)
        assertEquals(1, requests.count { it.url.encodedPath.startsWith("/api/v1/media/") })
        assertEquals(
            "936619743392459",
            requests.single { it.url.encodedPath.startsWith("/api/v1/media/") }.header("x-ig-app-id"),
        )
        val apiRequest = requests.single { it.url.encodedPath.startsWith("/api/v1/media/") }
        assertEquals("www.instagram.com", apiRequest.url.host)
        assertEquals("XMLHttpRequest", apiRequest.header("x-requested-with"))
        assertEquals(pageUrl, apiRequest.header("Referer"))
        verify(exactly = 1) { structuredMediaSniffer.sniffInstagramApiResponse(apiJson, pageUrl) }
        coVerify(exactly = 1) { direct.open("https://cdninstagram.com/full.jpg", any()) }
    }

    @Test
    fun `instagram carousel post returns batch from ig api fallback`() = runTest {
        val pageUrl = "https://www.instagram.com/p/CAROUSEL42/?img_index=2"
        val pageHtml = """
            <html>
              <head>
                <meta property="og:image" content="https://preview.example.com/carousel-preview.jpg" />
              </head>
            </html>
        """.trimIndent()
        val apiJson = """
            {
              "items": [
                {
                  "carousel_media": [
                    { "image_versions2": { "candidates": [{ "url": "https://cdninstagram.com/slide-1.jpg" }] } },
                    { "image_versions2": { "candidates": [{ "url": "https://cdninstagram.com/slide-2.jpg" }] } }
                  ]
                }
              ]
            }
        """.trimIndent()
        every { structuredMediaSniffer.sniffInstagramApiResponse(apiJson, pageUrl) } returns listOf(
            embeddedCandidate("https://cdninstagram.com/slide-1.jpg"),
            embeddedCandidate("https://cdninstagram.com/slide-2.jpg"),
        )

        val strategy = HtmlPageExtractionStrategy(
            httpClient = fakeHttpClient(
                pageUrl = pageUrl,
                pageHtml = pageHtml,
                apiJson = apiJson,
                requests = mutableListOf(),
            ),
            direct = direct,
            streamingSniffer = StreamingManifestSniffer(),
            structuredMediaSniffer = structuredMediaSniffer,
            settingsRepository = settingsRepository,
        )

        val out = strategy.open(pageUrl) { _, _ -> }

        assertTrue(out is OpenResult.Batch)
        assertEquals(
            listOf(
                "https://cdninstagram.com/slide-1.jpg",
                "https://cdninstagram.com/slide-2.jpg",
            ),
            (out as OpenResult.Batch).items.map { it.url },
        )
        verify(exactly = 1) { structuredMediaSniffer.sniffInstagramApiResponse(apiJson, pageUrl) }
        coVerify(exactly = 0) { direct.open(any(), any()) }
    }

    @Test
    fun `instagram login page from media info falls through to the graphql query`() = runTest {
        val pageUrl = "https://www.instagram.com/p/ABC123/"
        val pageHtml = """
            <html><head><meta property="og:image" content="https://preview.example.com/p.jpg" /></head></html>
        """.trimIndent()
        val graphqlJson = """{"data":{"xdt_shortcode_media":{"display_url":"https://cdninstagram.com/full.jpg"}}}"""
        val requests = mutableListOf<Request>()
        val expected = OpenResult.Stream(
            body = ByteArrayInputStream("img".toByteArray()),
            contentLength = 3,
            mime = "image/jpeg",
            fileName = "full.jpg",
            close = {},
        )
        every { structuredMediaSniffer.sniffInstagramGraphqlResponse(graphqlJson, pageUrl) } returns listOf(
            embeddedCandidate("https://cdninstagram.com/full.jpg"),
        )
        coEvery { direct.open("https://cdninstagram.com/full.jpg", any()) } returns expected

        val strategy = HtmlPageExtractionStrategy(
            httpClient = fakeHttpClient(
                pageUrl = pageUrl,
                pageHtml = pageHtml,
                apiJson = "<!DOCTYPE html><html><title>Login</title></html>",
                requests = requests,
                graphqlJson = graphqlJson,
            ),
            direct = direct,
            streamingSniffer = StreamingManifestSniffer(),
            structuredMediaSniffer = structuredMediaSniffer,
            settingsRepository = settingsRepository,
        )

        val out = strategy.open(pageUrl) { _, _ -> }

        assertTrue(out is OpenResult.Stream)
        val graphqlRequest = requests.single { it.url.encodedPath == "/graphql/query/" }
        assertEquals("www.instagram.com", graphqlRequest.url.host)
        assertEquals("XMLHttpRequest", graphqlRequest.header("x-requested-with"))
        assertTrue(graphqlRequest.url.queryParameter("variables").orEmpty().contains("\"shortcode\":\"ABC123\""))
        verify(exactly = 0) { structuredMediaSniffer.sniffInstagramApiResponse(any(), any()) }
    }

    @Test
    fun `instagram revoked session repeats the post fetch once without cookies`() = runTest {
        val pageUrl = "https://www.instagram.com/p/ABC123/"
        val pageHtml = """
            <html><head><meta property="og:image" content="https://preview.example.com/p.jpg" /></head></html>
        """.trimIndent()
        val apiJson = """{"items":[{"image_versions2":{"candidates":[{"url":"https://cdninstagram.com/full.jpg"}]}}]}"""
        val requests = mutableListOf<Request>()
        val expected = OpenResult.Stream(
            body = ByteArrayInputStream("img".toByteArray()),
            contentLength = 3,
            mime = "image/jpeg",
            fileName = "full.jpg",
            close = {},
        )
        every { structuredMediaSniffer.sniffInstagramApiResponse(apiJson, pageUrl) } returns listOf(
            embeddedCandidate("https://cdninstagram.com/full.jpg"),
        )
        coEvery { direct.open("https://cdninstagram.com/full.jpg", any()) } returns expected

        val strategy = HtmlPageExtractionStrategy(
            httpClient = fakeHttpClient(
                pageUrl = pageUrl,
                pageHtml = pageHtml,
                apiJson = apiJson,
                requests = requests,
                cookieJar = storedSessionJar,
                loginRequired = { it.header("x-csrftoken") != null },
            ),
            direct = direct,
            streamingSniffer = StreamingManifestSniffer(),
            structuredMediaSniffer = structuredMediaSniffer,
            settingsRepository = settingsRepository,
        )

        val out = strategy.open(pageUrl) { _, _ -> }

        assertTrue(out is OpenResult.Stream)
        val apiRequests = requests.filter { it.url.encodedPath.startsWith("/api/v1/media/") }
        assertEquals(listOf("stored-csrf", null), apiRequests.map { it.header("x-csrftoken") })
        verify(exactly = 1) { structuredMediaSniffer.sniffInstagramApiResponse(apiJson, pageUrl) }
    }

    @Test
    fun `instagram post refused without cookies too is retried only once`() = runTest {
        val pageUrl = "https://www.instagram.com/p/ABC123/"
        val pageHtml = """
            <html><head><meta property="og:image" content="https://preview.example.com/p.jpg" /></head></html>
        """.trimIndent()
        val requests = mutableListOf<Request>()

        val strategy = HtmlPageExtractionStrategy(
            httpClient = fakeHttpClient(
                pageUrl = pageUrl,
                pageHtml = pageHtml,
                apiJson = "{}",
                requests = requests,
                cookieJar = storedSessionJar,
                loginRequired = { true },
            ),
            direct = direct,
            streamingSniffer = StreamingManifestSniffer(),
            structuredMediaSniffer = structuredMediaSniffer,
            settingsRepository = settingsRepository,
        )

        val out = strategy.open(pageUrl) { _, _ -> }

        assertTrue(out is OpenResult.SocialPreviewOnly)
        assertEquals(2, requests.count { it.url.encodedPath.startsWith("/api/v1/media/") })
        assertEquals(2, requests.count { it.url.encodedPath == "/graphql/query/" })
        verify(exactly = 0) { structuredMediaSniffer.sniffInstagramApiResponse(any(), any()) }
        coVerify(exactly = 0) { direct.open(any(), any()) }
    }

    private val storedSessionJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

        override fun loadForRequest(url: HttpUrl): List<Cookie> = listOf(
            Cookie.Builder().domain("instagram.com").name("csrftoken").value("stored-csrf").build(),
            Cookie.Builder().domain("instagram.com").name("sessionid").value("revoked").build(),
        )
    }

    private fun embeddedCandidate(url: String): HtmlMediaCandidate = HtmlMediaCandidate(
        url = url,
        source = HtmlMediaCandidate.Source.EMBEDDED_JSON,
        tentativeMime = null,
        tentativeSizeBytes = null,
    )

    @Test
    fun `video tag with a relative hls playlist returns streaming`() = runTest {
        val pageUrl = "http://127.0.0.1/page.html"
        val pageHtml = """<html><body><video src="angel/hls.m3u8" controls></video></body></html>"""
        val strategy = HtmlPageExtractionStrategy(
            httpClient = fakeHttpClient(
                pageUrl = pageUrl,
                pageHtml = pageHtml,
                apiJson = "{}",
                requests = mutableListOf(),
            ),
            direct = direct,
            streamingSniffer = StreamingManifestSniffer(),
            structuredMediaSniffer = structuredMediaSniffer,
            settingsRepository = settingsRepository,
        )

        val out = strategy.open(pageUrl) { _, _ -> }

        assertTrue(out is OpenResult.Streaming)
        assertEquals(
            StreamingManifest.Hls("http://127.0.0.1/angel/hls.m3u8"),
            (out as OpenResult.Streaming).manifest,
        )
        assertEquals("hls.mp4", out.tentativeFileName)
        coVerify(exactly = 0) { direct.open(any(), any()) }
    }

    @Test
    fun `source tag with an absolute hls playlist returns streaming`() = runTest {
        val pageUrl = "http://127.0.0.1/page2.html"
        val manifestUrl = "https://storage.example.com/angel-one-hls/hls.m3u8"
        val pageHtml = """
            <html><body><video controls>
              <source src="$manifestUrl" type="application/x-mpegURL">
            </video></body></html>
        """.trimIndent()
        val strategy = HtmlPageExtractionStrategy(
            httpClient = fakeHttpClient(
                pageUrl = pageUrl,
                pageHtml = pageHtml,
                apiJson = "{}",
                requests = mutableListOf(),
            ),
            direct = direct,
            streamingSniffer = StreamingManifestSniffer(),
            structuredMediaSniffer = structuredMediaSniffer,
            settingsRepository = settingsRepository,
        )

        val out = strategy.open(pageUrl) { _, _ -> }

        assertEquals(StreamingManifest.Hls(manifestUrl), (out as OpenResult.Streaming).manifest)
    }

    private fun fakeHttpClient(
        pageUrl: String,
        pageHtml: String,
        apiJson: String,
        requests: MutableList<Request>,
        graphqlJson: String = "{}",
        cookieJar: CookieJar = CookieJar.NO_COOKIES,
        loginRequired: (Request) -> Boolean = { false },
    ): OkHttpClient {
        val htmlType = "text/html".toMediaType()
        val jsonType = "application/json".toMediaType()
        val imageType = "image/jpeg".toMediaType()
        val loginWall = """{"message":"Please wait a few minutes before you try again.","require_login":true}"""
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                when {
                    request.method == "GET" && request.url.toString() == pageUrl ->
                        response(request, 200, pageHtml.toResponseBody(htmlType))

                    request.url.host == "www.instagram.com" && request.url.encodedPath.startsWith("/api/v1/media/") -> {
                        val hasAppId = request.header("x-ig-app-id") == "936619743392459"
                        val code = if (hasAppId && !loginRequired(request)) 200 else 401
                        val answer = if (code == 200) apiJson else loginWall
                        response(request, code, answer.toResponseBody(jsonType))
                    }

                    request.url.host == "www.instagram.com" && request.url.encodedPath == "/graphql/query/" ->
                        if (loginRequired(request)) {
                            response(request, 401, loginWall.toResponseBody(jsonType))
                        } else {
                            response(request, 200, graphqlJson.toResponseBody(jsonType))
                        }

                    request.method == "HEAD" && request.url.host.contains("cdninstagram.com") ->
                        response(request, 200, ByteArray(0).toResponseBody(imageType))

                    else -> response(request, 404, ByteArray(0).toResponseBody("text/plain".toMediaType()))
                }
            }
            .build()
    }

    private fun response(request: Request, code: Int, body: okhttp3.ResponseBody): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "ERROR")
            .body(body)
            .build()
}
