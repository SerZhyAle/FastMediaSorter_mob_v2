package com.sza.fastmediasorter.wear.data.network.itunes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ITunesTrackTest {

    @Test
    fun `getHighResArtworkUrl upgrades 100x100 artwork url`() {
        val track = ITunesTrack(
            artistName = "Artist",
            collectionName = "Album",
            artworkUrl100 = "https://example.com/artwork/100x100bb.jpg",
            artworkUrl60 = "https://example.com/artwork/60x60bb.jpg"
        )

        assertEquals(
            "https://example.com/artwork/600x600bb.jpg",
            track.getHighResArtworkUrl()
        )
    }

    @Test
    fun `getHighResArtworkUrl returns null when source url is absent`() {
        val track = ITunesTrack(
            artistName = null,
            collectionName = null,
            artworkUrl100 = null,
            artworkUrl60 = null
        )

        assertNull(track.getHighResArtworkUrl())
    }
}