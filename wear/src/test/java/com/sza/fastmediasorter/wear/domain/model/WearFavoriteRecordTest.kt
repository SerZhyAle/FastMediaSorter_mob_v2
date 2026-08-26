package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1846: a favourite made before this ticket must survive it.
 *
 * The store keeps two shapes at once - records written from now on, and the `sourceId:filePath` strings
 * written before - and the user cannot tell which of their favourites is which. Every case here stands for
 * a way one of those marks could silently disappear or turn into a different file.
 */
class WearFavoriteRecordTest {

    @Test
    fun `a legacy key keeps its path and is named by the last segment`() {
        val record = WearFavoriteRecord.fromLegacyKey("local:/storage/emulated/0/Music/Song.mp3")

        assertEquals("local", record?.sourceId)
        assertEquals("/storage/emulated/0/Music/Song.mp3", record?.filePath)
        assertEquals("Song.mp3", record?.displayName)
        assertNull("a legacy key never knew the kind", record?.mimeType)
    }

    @Test
    fun `a path holding colons of its own is not cut at the wrong one`() {
        val record = WearFavoriteRecord.fromLegacyKey("nas-1:smb://host/share/Album: Live/track.flac")

        assertEquals("nas-1", record?.sourceId)
        assertEquals("smb://host/share/Album: Live/track.flac", record?.filePath)
        assertEquals("track.flac", record?.displayName)
    }

    @Test
    fun `a key with no separator or no payload is not a favourite`() {
        assertNull(WearFavoriteRecord.fromLegacyKey("no-separator-here"))
        assertNull(WearFavoriteRecord.fromLegacyKey(":/leading/colon"))
        assertNull(WearFavoriteRecord.fromLegacyKey("trailing:"))
    }

    @Test
    fun `merge lists records first and legacy entries after them`() {
        val record = WearFavoriteRecord("local", "/a/new.mp3", "new.mp3", "audio")

        val merged = mergeFavorites(listOf(record), setOf("local:/a/old.mp3"))

        assertEquals(listOf("new.mp3", "old.mp3"), merged.map { it.displayName })
    }

    @Test
    fun `a legacy key the records already describe is not listed twice`() {
        val record = WearFavoriteRecord("local", "/a/song.mp3", "Song", "audio")

        val merged = mergeFavorites(listOf(record), setOf("local:/a/song.mp3"))

        assertEquals(1, merged.size)
        assertEquals("Song", merged.single().displayName)
        assertEquals("the record wins - it is the one that carries the kind", "audio", merged.single().mimeType)
    }

    @Test
    fun `an empty store merges to an empty list rather than failing`() {
        assertEquals(emptyList<WearFavoriteRecord>(), mergeFavorites(emptyList(), emptySet()))
    }

    @Test
    fun `the source id rule agrees for both writers`() {
        assertEquals("local", favoriteSourceId(isNetworkSource = false, networkSourceId = "ignored"))
        assertEquals("nas-1", favoriteSourceId(isNetworkSource = true, networkSourceId = "nas-1"))
        assertEquals("network", favoriteSourceId(isNetworkSource = true, networkSourceId = null))
        assertEquals("network", favoriteSourceId(isNetworkSource = true, networkSourceId = " "))
    }

    @Test
    fun `stream identity normalizes host default port and trailing slash`() {
        assertEquals(
            "https://radio.example/Live?quality=HD#Now",
            normalizeWearStreamUrl(" HTTPS://RADIO.EXAMPLE:443/Live/?quality=HD#Now ")
        )
    }

    @Test
    fun `stream identity preserves path query and fragment case`() {
        assertEquals(
            "rtsp://camera.example/Feed/HD?Token=AbC#View",
            normalizeWearStreamUrl("RTSP://CAMERA.EXAMPLE:554/Feed/HD?Token=AbC#View")
        )
    }

    @Test
    fun `unparseable stream identities remain separate trimmed values`() {
        assertEquals("bad stream one", normalizeWearStreamUrl(" bad stream one/ "))
        assertEquals("bad stream two", normalizeWearStreamUrl(" bad stream two "))
    }
}
