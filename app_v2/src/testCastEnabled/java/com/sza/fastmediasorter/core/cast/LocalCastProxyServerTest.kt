package com.sza.fastmediasorter.core.cast

import android.content.Context
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class LocalCastProxyServerTest {

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
        assertTrue(url != null && url.startsWith("http://192.168.1.50:") && url.endsWith("/cast-media"))
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
}
