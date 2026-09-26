package com.sza.fastmediasorter.core.cast

import android.content.Context
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class LocalCastProxyServerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private var server: LocalCastProxyServer? = null

    @Before
    fun setUp() {
        context = mockk<Context>(relaxed = true)
    }

    @After
    fun tearDown() {
        server?.stop()
    }

    @Test
    fun `castUrl returns formatted HTTP URL when LAN IP provider returns valid IP`() {
        val srv = LocalCastProxyServer(
            context = context,
            lanAddressProvider = { "192.168.1.50" },
        )
        server = srv
        srv.start()
        assertTrue(srv.isAlive)

        val url = srv.castUrl()
        assertTrue(url != null && url.startsWith("http://192.168.1.50:") && url.contains("/cast-media/"))
    }

    @Test
    fun `serveFile issues a new token so an earlier URL stops working`() {
        val srv = startServingSample()
        val oldUrl = requireNotNull(srv.castUrl())
        srv.serveFile(tempFolder.newFile("other.mp4").apply { writeBytes(ByteArray(4)) })

        assertTrue(oldUrl != srv.castUrl())
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, open(oldUrl).responseCode)
    }

    @Test
    fun `a path without the token is refused`() {
        val srv = startServingSample()
        val base = requireNotNull(srv.castUrl()).substringBeforeLast('/')

        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, open(base).responseCode)
    }

    @Test
    fun `no Range header returns the whole file and advertises byte ranges`() {
        val srv = startServingSample()
        val connection = open(requireNotNull(srv.castUrl()))

        assertEquals(HttpURLConnection.HTTP_OK, connection.responseCode)
        assertEquals("bytes", connection.getHeaderField("Accept-Ranges"))
        assertEquals(SAMPLE_SIZE, connection.inputStream.use { it.readBytes() }.size)
    }

    @Test
    fun `a byte range is answered with 206 and exactly those bytes`() {
        val srv = startServingSample()
        val connection = open(requireNotNull(srv.castUrl()), range = "bytes=10-19")

        assertEquals(HttpURLConnection.HTTP_PARTIAL, connection.responseCode)
        assertEquals("bytes 10-19/$SAMPLE_SIZE", connection.getHeaderField("Content-Range"))
        val body = connection.inputStream.use { it.readBytes() }
        assertEquals((10 until 20).map { it.toByte() }, body.toList())
    }

    @Test
    fun `a range past the end is answered with 416`() {
        val srv = startServingSample()
        val connection = open(requireNotNull(srv.castUrl()), range = "bytes=500-")

        assertEquals(RANGE_NOT_SATISFIABLE, connection.responseCode)
        assertEquals("bytes */$SAMPLE_SIZE", connection.getHeaderField("Content-Range"))
    }

    @Test
    fun `parseByteRange reads open, suffix and malformed ranges`() {
        assertEquals(90L..99L, LocalCastProxyServer.parseByteRange("bytes=90-", 100L))
        assertEquals(95L..99L, LocalCastProxyServer.parseByteRange("bytes=-5", 100L))
        assertEquals(0L..99L, LocalCastProxyServer.parseByteRange("bytes=0-500", 100L))
        assertNull(LocalCastProxyServer.parseByteRange("bytes=0-1,5-6", 100L))
        assertNull(LocalCastProxyServer.parseByteRange("items=0-1", 100L))
        assertTrue(LocalCastProxyServer.parseByteRange("bytes=-0", 100L)!!.isEmpty())
    }

    private fun startServingSample(): LocalCastProxyServer {
        val srv = LocalCastProxyServer(context = context, lanAddressProvider = { "127.0.0.1" })
        server = srv
        srv.start()
        val sample = tempFolder.newFile("sample.mp4").apply { writeBytes(ByteArray(SAMPLE_SIZE) { it.toByte() }) }
        srv.serveFile(sample)
        return srv
    }

    private fun open(url: String, range: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            range?.let { setRequestProperty("Range", it) }
        }

    @Test
    fun `castUrl returns null when LAN IP provider returns null`() {
        val srv = LocalCastProxyServer(
            context = context,
            lanAddressProvider = { null },
        )
        server = srv

        assertNull(srv.castUrl())
    }

    @Test
    fun `stop updates isAlive state to false`() {
        val srv = LocalCastProxyServer(
            context = context,
            lanAddressProvider = { "192.168.1.50" },
        )
        server = srv
        srv.start()
        assertTrue(srv.isAlive)

        srv.stop()
        assertFalse(srv.isAlive)
    }

    @Test
    fun `mimeType detects common extensions`() {
        assertEquals("video/mp4", LocalCastProxyServer.mimeType(File("video.mp4")))
        assertEquals("audio/mpeg", LocalCastProxyServer.mimeType(File("audio.mp3")))
        assertEquals("image/jpeg", LocalCastProxyServer.mimeType(File("photo.jpg")))
        assertEquals("application/octet-stream", LocalCastProxyServer.mimeType(File("unknown.xyz")))
    }

    private companion object {
        const val SAMPLE_SIZE = 100
        const val TIMEOUT_MS = 5_000
        const val RANGE_NOT_SATISFIABLE = 416
    }
}
