package com.sza.fastmediasorter.data.youtube

import com.sza.fastmediasorter.domain.model.youtube.YouTubeChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2032: the parsers and the target codec, which are the whole of this ticket that a machine can judge
 * (strategic §7, last risk row). A truncated body is the shape a dead network actually takes.
 */
class YouTubeFeedParsingTest {

    @Test
    fun `channel page yields its canonical id`() {
        val html = """{"header":{},"metadata":{"channelId":"UC1234567890abcdefghijkl"}}"""
        assertEquals("UC1234567890abcdefghijkl", parseChannelId(html))
    }

    @Test
    fun `channel page falls back to the external id spelling`() {
        val html = """{"microformat":{"externalId":"UCabcdefghijkl1234567890"}}"""
        assertEquals("UCabcdefghijkl1234567890", parseChannelId(html))
    }

    @Test
    fun `channel page without either spelling yields nothing`() {
        assertNull(parseChannelId("<html><body>not a channel</body></html>"))
    }

    @Test
    fun `feed yields the channel title`() {
        assertEquals("Sample Channel", parseFeedChannelTitle(FEED))
    }

    @Test
    fun `feed title is unescaped`() {
        val feed = """<feed><title>Rock &amp; Roll</title><entry></entry></feed>"""
        assertEquals("Rock & Roll", parseFeedChannelTitle(feed))
    }

    @Test
    fun `feed yields the first entry as the latest video`() {
        val video = parseFeedLatestVideo(FEED)
        assertEquals("dQw4w9WgXcQ", video?.videoId)
        assertEquals("First Video", video?.title)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", video?.thumbnailUrl)
    }

    @Test
    fun `entry without a thumbnail falls back to the standard cover`() {
        val feed = """<feed><entry><yt:videoId>abc12345678</yt:videoId><title>T</title></entry></feed>"""
        assertEquals("https://i.ytimg.com/vi/abc12345678/hqdefault.jpg", parseFeedLatestVideo(feed)?.thumbnailUrl)
    }

    @Test
    fun `truncated feed yields no video`() {
        assertNull(parseFeedLatestVideo("<feed><title>Sample</title>"))
    }

    @Test
    fun `empty feed yields no title and no video`() {
        assertNull(parseFeedChannelTitle(""))
        assertNull(parseFeedLatestVideo(""))
    }

    @Test
    fun `a bare channel id costs no page fetch`() {
        assertEquals("UC1234567890abcdefghijkl", directChannelId("UC1234567890abcdefghijkl"))
        assertNull(directChannelId("@somehandle"))
    }

    @Test
    fun `a typed address reduces to its last segment`() {
        assertEquals("https://www.youtube.com/@handle", channelPageUrl("https://www.youtube.com/@handle?si=x"))
        assertEquals("https://www.youtube.com/@handle", channelPageUrl("@handle"))
        assertEquals("https://www.youtube.com/@handle", channelPageUrl("youtube.com/@handle/"))
    }

    @Test
    fun `channel encodes and decodes round trip`() {
        val channel = YouTubeChannel(channelId = "UC1234567890abcdefghijkl", title = "News | Daily")
        val decoded = YouTubeChannel.decode(channel.encode())
        assertEquals(channel.channelId, decoded?.channelId)
        assertEquals(channel.title, decoded?.title)
    }

    @Test
    fun `a target holding only an id decodes to a titleless channel`() {
        val decoded = YouTubeChannel.decode("UC1234567890abcdefghijkl")
        assertEquals("UC1234567890abcdefghijkl", decoded?.channelId)
        assertEquals("", decoded?.title)
    }

    @Test
    fun `a blank target decodes to nothing`() {
        assertNull(YouTubeChannel.decode(null))
        assertNull(YouTubeChannel.decode("   "))
        assertNull(YouTubeChannel.decode("|only a title"))
    }

    private companion object {
        const val FEED = """<?xml version="1.0"?>
<feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/">
  <title>Sample Channel</title>
  <entry>
    <yt:videoId>dQw4w9WgXcQ</yt:videoId>
    <title>First Video</title>
    <media:group>
      <media:thumbnail url="https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg" width="480" height="360"/>
    </media:group>
  </entry>
  <entry>
    <yt:videoId>olderVideo1</yt:videoId>
    <title>Older Video</title>
  </entry>
</feed>"""
    }
}
