package com.sza.fastmediasorter.ui.launcher.gadget

import com.sza.fastmediasorter.domain.model.youtube.YouTubeChannel
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2032: what a placed YouTube window cell stores, how big it is, and which of its two behaviours the
 * device selects. Each rule is read by code that never sees the others, so nothing but this test ties
 * them to the values the cell is stored, drawn and driven with (strategic §7).
 */
@Suppress("FunctionNaming") // backtick test names, project convention (cf. StreamWindowTargetTest)
class YouTubeChannelWindowTargetTest {

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
    fun `an encoded channel survives the target round trip`() {
        val channel = YouTubeChannel(channelId = "UC1234567890abcdefghijkl", title = "News: Daily")

        val target = registry.encodeTarget(LauncherGadgetRegistry.KEY_YOUTUBE_CHANNEL_WINDOW, channel.encode())
        val decoded = registry.decodeTarget(target)

        assertEquals(LauncherGadgetRegistry.KEY_YOUTUBE_CHANNEL_WINDOW, decoded?.first)
        assertEquals(channel.channelId, YouTubeChannel.decode(decoded?.second)?.channelId)
        assertEquals(channel.title, YouTubeChannel.decode(decoded?.second)?.title)
    }

    @Test
    fun `a window cell with no channel decodes to a null param`() {
        val decoded = registry.decodeTarget(LauncherGadgetRegistry.KEY_YOUTUBE_CHANNEL_WINDOW)

        assertEquals(LauncherGadgetRegistry.KEY_YOUTUBE_CHANNEL_WINDOW, decoded?.first)
        assertNull(decoded?.second)
        assertNull(YouTubeChannel.decode(decoded?.second))
    }

    @Test
    fun `the cell is placed three wide and two tall`() {
        assertEquals(3, YouTubeChannelWindow.SPAN_W)
        assertEquals(2, YouTubeChannelWindow.SPAN_H)
    }

    @Test
    fun `an unreadable web view version selects the channel shortcut`() {
        assertFalse(YouTubeEmbedAvailability.isEmbedUsable(null))
        assertFalse(YouTubeEmbedAvailability.isEmbedUsable(""))
        assertFalse(YouTubeEmbedAvailability.isEmbedUsable("not a version"))
    }

    @Test
    fun `an old web view selects the channel shortcut and a current one selects the embed`() {
        assertFalse(YouTubeEmbedAvailability.isEmbedUsable("66.0.3359.158"))
        assertTrue(YouTubeEmbedAvailability.isEmbedUsable("80.0.3987.99"))
        assertTrue(YouTubeEmbedAvailability.isEmbedUsable("124.0.6367.113"))
    }
}
