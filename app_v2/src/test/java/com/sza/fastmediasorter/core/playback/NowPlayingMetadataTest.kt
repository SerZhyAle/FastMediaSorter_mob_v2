package com.sza.fastmediasorter.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S1142: best-effort ICY `StreamTitle` split into artist/title with graceful degradation (ADR-2). */
class NowPlayingMetadataTest {

    @Test
    fun `splits Artist - Title on the separator`() {
        val meta = NowPlayingMetadata.parse("Miles Davis - So What")
        assertEquals("Miles Davis", meta?.artist)
        assertEquals("So What", meta?.title)
    }

    @Test
    fun `no separator puts whole string in title`() {
        val meta = NowPlayingMetadata.parse("News bulletin")
        assertNull(meta?.artist)
        assertEquals("News bulletin", meta?.title)
    }

    @Test
    fun `blank string returns null`() {
        assertNull(NowPlayingMetadata.parse(""))
        assertNull(NowPlayingMetadata.parse("   "))
    }

    @Test
    fun `trims surrounding whitespace on both sides`() {
        val meta = NowPlayingMetadata.parse("  Artist   -   Title  ")
        assertEquals("Artist", meta?.artist)
        assertEquals("Title", meta?.title)
    }

    @Test
    fun `splits only on the first separator`() {
        val meta = NowPlayingMetadata.parse("Artist - Title - Remix")
        assertEquals("Artist", meta?.artist)
        assertEquals("Title - Remix", meta?.title)
    }

    @Test
    fun `empty side falls back to whole string as title`() {
        val meta = NowPlayingMetadata.parse(" - Title")
        assertNull(meta?.artist)
        assertEquals("- Title", meta?.title)
    }

    @Test
    fun `trackLine renders artist and title`() {
        assertEquals("Artist - Title", NowPlayingMetadata(artist = "Artist", title = "Title").trackLine())
    }

    @Test
    fun `trackLine falls back to bare title when artist null`() {
        assertEquals("Title", NowPlayingMetadata(artist = null, title = "Title").trackLine())
    }
}
