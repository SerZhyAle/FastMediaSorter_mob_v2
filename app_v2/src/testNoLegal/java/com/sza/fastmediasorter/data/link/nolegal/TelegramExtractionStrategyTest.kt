package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S0303: JVM unit tests for [TelegramExtractionStrategy] - host probing, public-post classification
 * and reject reasons, single-photo delegation to direct, and album → Batch. OkHttp + direct
 * downloader mocked; no real network. Embed HTML is a canned fixture pinned against the documented
 * `tgme_widget_message_*` markup (research env had no live fetch).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TelegramExtractionStrategyTest {

    private lateinit var direct: DirectFileExtractionStrategy
    private val noProgress: (Long, Long?) -> Unit = { _, _ -> }

    @Before
    fun setUp() {
        direct = mockk()
    }

    private fun strategy(client: OkHttpTestSupport.MockedClient) =
        TelegramExtractionStrategy(client.client, direct)

    private fun anyClient() = OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, ""))

    @Test
    fun probe_telegramHost_applicable() = runTest {
        assertTrue(strategy(anyClient()).probe("https://t.me/durov/123") is ProbeResult.Applicable)
    }

    @Test
    fun probe_legacyTelegramMeHost_applicable() = runTest {
        assertTrue(strategy(anyClient()).probe("https://telegram.me/durov/123") is ProbeResult.Applicable)
    }

    @Test
    fun probe_foreignHost_notApplicable() = runTest {
        assertEquals(ProbeResult.NotApplicable, strategy(anyClient()).probe("https://example.com/a/1"))
    }

    @Test
    fun open_privateChannel_notFoundPrivate() = runTest {
        val result = strategy(anyClient()).open("https://t.me/c/1234567890/55", noProgress)
        assertEquals("telegram_private_channel", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_inviteLink_notFoundInvite() = runTest {
        val result = strategy(anyClient()).open("https://t.me/joinchat/AbCdEf", noProgress)
        assertEquals("telegram_invite_link", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_bareProfile_notFoundNoPostId() = runTest {
        val result = strategy(anyClient()).open("https://t.me/durov", noProgress)
        assertEquals("telegram_no_post_id", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_singlePhotoPost_delegatesToDirect() = runTest {
        val html = """
            <html><body>
            <a class="tgme_widget_message_photo_wrap"
               style="background-image:url('https://cdn4.cdn-telegram.org/file/photo1.jpg')"></a>
            </body></html>
        """.trimIndent()
        val client = OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, html))
        val urlSlot = slot<String>()
        coEvery { direct.open(capture(urlSlot), any(), any()) } returns OpenResult.NotFound("d")

        strategy(client).open("https://t.me/durov/123", noProgress)

        assertEquals("https://cdn4.cdn-telegram.org/file/photo1.jpg", urlSlot.captured)
    }

    @Test
    fun open_albumPost_returnsBatch() = runTest {
        val html = """
            <html><body>
            <a class="tgme_widget_message_photo_wrap"
               style="background-image:url('https://cdn4.cdn-telegram.org/file/a.jpg')"></a>
            <a class="tgme_widget_message_photo_wrap"
               style="background-image:url('https://cdn4.cdn-telegram.org/file/b.jpg')"></a>
            </body></html>
        """.trimIndent()
        val client = OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, html))

        val result = strategy(client).open("https://t.me/durov/123", noProgress)

        val batch = result as OpenResult.Batch
        assertEquals(2, batch.items.size)
        assertEquals("https://cdn4.cdn-telegram.org/file/a.jpg", batch.items[0].url)
        assertEquals("https://cdn4.cdn-telegram.org/file/b.jpg", batch.items[1].url)
    }

    @Test
    fun open_postWithNoMedia_notFoundNoMedia() = runTest {
        val client = OkHttpTestSupport.clientReturning(
            OkHttpTestSupport.response(200, "<html><body>text only</body></html>")
        )
        val result = strategy(client).open("https://t.me/durov/123", noProgress)
        assertEquals("telegram_no_media", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_webPreviewForm_canonicalisedAndExtracted() = runTest {
        val html = """
            <a class="tgme_widget_message_photo_wrap"
               style="background-image:url('https://cdn4.cdn-telegram.org/file/s.jpg')"></a>
        """.trimIndent()
        val client = OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, html))
        val urlSlot = slot<String>()
        coEvery { direct.open(capture(urlSlot), any(), any()) } returns OpenResult.NotFound("d")

        // /s/<channel>/<id> web-preview form must canonicalise to the same post.
        strategy(client).open("https://t.me/s/durov/123", noProgress)

        assertEquals("https://cdn4.cdn-telegram.org/file/s.jpg", urlSlot.captured)
    }

    @Test
    fun id_isTelegram() {
        assertEquals("telegram", strategy(anyClient()).id)
    }
}
