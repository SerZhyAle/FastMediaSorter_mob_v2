package com.sza.fastmediasorter.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1832 - Phase 01 Step 01.2.
 *
 * One test per claim about the identity key: what folds, what must not, and the measured collision pair
 * that justified adding the protocol fold at all. Pure JVM - the derivation holds no Android type.
 */
class StreamChannelIdentityTest {

    @Test
    fun `http and https on the same host and path give one identity`() {
        assertEquals(
            StreamChannelIdentity.of("http://example.com/live.m3u8"),
            StreamChannelIdentity.of("https://example.com/live.m3u8"),
        )
    }

    @Test
    fun `the measured collision pair from the published bank folds together`() {
        assertEquals(
            StreamChannelIdentity.of("http://dispatcher.rndfnk.com/rbb/fritz/live/mp3/mid"),
            StreamChannelIdentity.of("https://dispatcher.rndfnk.com/rbb/fritz/live/mp3/mid"),
        )
    }

    @Test
    fun `rtsp keeps its own scheme and does not collide with http`() {
        assertNotEquals(
            StreamChannelIdentity.of("rtsp://example.com/live"),
            StreamChannelIdentity.of("http://example.com/live"),
        )
    }

    @Test
    fun `rtsp is not rewritten to the web token`() {
        assertTrue(StreamChannelIdentity.of("rtsp://example.com/live").startsWith("rtsp://"))
    }

    @Test
    fun `a trailing slash folds`() {
        assertEquals(
            StreamChannelIdentity.of("https://example.com/live"),
            StreamChannelIdentity.of("https://example.com/live/"),
        )
    }

    @Test
    fun `host case folds`() {
        assertEquals(
            StreamChannelIdentity.of("http://example.com/live.m3u8"),
            StreamChannelIdentity.of("http://Example.COM/live.m3u8"),
        )
    }

    @Test
    fun `an explicit default port folds against the same address without one`() {
        assertEquals(
            StreamChannelIdentity.of("http://example.com:80/live.m3u8"),
            StreamChannelIdentity.of("https://example.com/live.m3u8"),
        )
    }

    @Test
    fun `a non-default port is kept and separates two channels`() {
        assertNotEquals(
            StreamChannelIdentity.of("http://example.com:8000/live.m3u8"),
            StreamChannelIdentity.of("http://example.com/live.m3u8"),
        )
    }

    @Test
    fun `path case is preserved`() {
        assertNotEquals(
            StreamChannelIdentity.of("https://example.com/Live.m3u8"),
            StreamChannelIdentity.of("https://example.com/live.m3u8"),
        )
    }

    @Test
    fun `a query is preserved`() {
        assertNotEquals(
            StreamChannelIdentity.of("https://example.com/live?rung=high"),
            StreamChannelIdentity.of("https://example.com/live?rung=low"),
        )
    }

    @Test
    fun `an unparsable address still gets an identity of its own`() {
        val identity = StreamChannelIdentity.of("  not a url  ")
        assertEquals("not a url", identity)
        assertNotEquals(identity, StreamChannelIdentity.of("also not a url"))
    }
}
