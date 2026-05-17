package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
        coEvery { structuredMediaSniffer.sniff(any(), any()) } returns emptyList()
        every { structuredMediaSniffer.sniffEmbeddedJson(any(), any()) } returns emptyList()
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
        assertEquals(1, requests.count { it.url.host == "i.instagram.com" })
        assertEquals(
            "936619743392459",
            requests.single { it.url.host == "i.instagram.com" }.header("x-ig-app-id"),
        )
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

    private fun embeddedCandidate(url: String): HtmlMediaCandidate = HtmlMediaCandidate(
        url = url,
        source = HtmlMediaCandidate.Source.EMBEDDED_JSON,
        tentativeMime = null,
        tentativeSizeBytes = null,
    )

    private fun fakeHttpClient(
        pageUrl: String,
        pageHtml: String,
        apiJson: String,
        requests: MutableList<Request>,
    ): OkHttpClient {
        val htmlType = "text/html".toMediaType()
        val jsonType = "application/json".toMediaType()
        val imageType = "image/jpeg".toMediaType()
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                when {
                    request.method == "GET" && request.url.toString() == pageUrl ->
                        response(request, 200, pageHtml.toResponseBody(htmlType))

                    request.url.host == "i.instagram.com" -> {
                        val hasAppId = request.header("x-ig-app-id") == "936619743392459"
                        val code = if (hasAppId) 200 else 401
                        val body = if (code == 200) apiJson.toResponseBody(jsonType) else "{}".toResponseBody(jsonType)
                        response(request, code, body)
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