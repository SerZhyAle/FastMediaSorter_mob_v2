package com.sza.fastmediasorter.ui.launcher.gadget

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2031: the stream window cell addresses its channel by an identity that may contain a colon, and its
 * footprint is decided by the channel's kind. Both rules are read by code that never sees the other, so
 * nothing but this test ties them to the values the cell is stored and drawn with.
 */
@Suppress("FunctionNaming") // backtick test names, project convention (cf. LauncherStarterSetsParityTest)
class StreamWindowTargetTest {

    // Only the codec is exercised, and it reads no gadget: the registry's collaborators exist to answer
    // byKey/available, which this test never calls.
    private val registry = LauncherGadgetRegistry(
        clock = mockk(),
        weather = mockk(),
        playlist = mockk(),
        streams = mockk(),
        folderPreview = mockk(),
        search = mockk(),
        aggregated = emptyList(),
    )

    @Test
    fun `an identity carrying a colon survives the target round trip`() {
        val identity = "https://stream.example.org:8443/live"

        val target = registry.encodeTarget(LauncherGadgetRegistry.KEY_STREAM_WINDOW, identity)
        val decoded = registry.decodeTarget(target)

        assertEquals(LauncherGadgetRegistry.KEY_STREAM_WINDOW, decoded?.first)
        assertEquals(identity, decoded?.second)
    }

    @Test
    fun `a stream window cell with no channel decodes to a null param`() {
        val decoded = registry.decodeTarget(LauncherGadgetRegistry.KEY_STREAM_WINDOW)

        assertEquals(LauncherGadgetRegistry.KEY_STREAM_WINDOW, decoded?.first)
        assertNull(decoded?.second)
    }

    @Test
    fun `a video channel is placed three by two and a radio channel two by two`() {
        assertEquals(StreamWindow.VIDEO_SPAN_W to StreamWindow.VIDEO_SPAN_H, StreamWindow.spanFor("VIDEO"))
        assertEquals(StreamWindow.VIDEO_SPAN_W to StreamWindow.VIDEO_SPAN_H, StreamWindow.spanFor("RTSP"))
        assertEquals(StreamWindow.AUDIO_SPAN to StreamWindow.AUDIO_SPAN, StreamWindow.spanFor("AUDIO"))
        assertEquals(3, StreamWindow.VIDEO_SPAN_W)
        assertEquals(2, StreamWindow.VIDEO_SPAN_H)
    }

    @Test
    fun `video and rtsp channels are shown as a player, audio channels are not`() {
        assertTrue(StreamWindow.isVideoKind("VIDEO"))
        assertTrue(StreamWindow.isVideoKind("RTSP"))
        assertTrue(StreamWindow.isVideoKind("video"))
        assertFalse(StreamWindow.isVideoKind("AUDIO"))
    }
}
