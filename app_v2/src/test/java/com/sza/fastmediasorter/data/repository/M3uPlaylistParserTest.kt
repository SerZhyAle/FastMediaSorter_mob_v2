package com.sza.fastmediasorter.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.security.MessageDigest

/**
 * USER-PLAYLIST conformance of [M3uPlaylistParser] on the catalog's Extended M3U8 vectors. The
 * provenance check is named to run first, so a stale vendored copy fails before any row count misleads.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class M3uPlaylistParserTest {

    private val parser = M3uPlaylistParser()

    @Test
    fun `a00 the vendored vectors match their provenance record`() {
        val recorded = String(resource(PROVENANCE).readBytes(), Charsets.UTF_8).lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size == PROVENANCE_ROW_FIELDS && it[0] == "sha256" }
            .associate { it[1] to it[2].lowercase() }
        assertEquals(VECTORS, recorded.keys)
        VECTORS.forEach { name ->
            val digest = MessageDigest.getInstance("SHA-256").digest(resource(name).readBytes())
            val actual = digest.joinToString("") { "%02x".format(it) }
            assertEquals("$name differs from PROVENANCE.txt - re-vendor it from the catalog", recorded[name], actual)
        }
    }

    @Test
    fun `b01 an HLS media manifest imports nothing`() {
        assertTrue(parser.parse(vector(HLS_MANIFEST)).isEmpty())
    }

    @Test
    fun `b02 m3u8 entry URLs and a title mentioning the tag import every row`() {
        val entries = parser.parse(vector(HLS_ENTRY))

        assertEquals(
            listOf(
                "https://example.com/news/index.m3u8",
                "https://example.com/sport/master.m3u8",
                "https://example.com/audio/live.mp3"
            ),
            entries.map { it.url }
        )
        assertEquals("Sport Live (mentions #EXT-X-VERSION in its title)", entries[1].title)
    }

    @Test
    fun `b03 the canonical extended playlist imports every row with its display name`() {
        val entries = parser.parse(vector(EXTENDED))

        assertEquals(
            listOf("Radio Paradise (Main Mix)", "Public News Stream", "Local Studio Camera"),
            entries.map { it.title }
        )
        assertEquals("https://example.com/news/live.m3u8", entries[1].url)
    }

    @Test
    fun `c01 a comma inside a quoted attribute does not start the title`() {
        val text = "#EXTM3U\n#EXTINF:-1 group-title=\"Rock, Pop\",Station Name\nhttp://radio.example/one\n"

        assertEquals("Station Name", parser.parse(text).single().title)
    }

    @Test
    fun `c02 a missing or blank title falls back to the host`() {
        val text = "#EXTINF:-1 group-title=\"A, B\"\nhttp://radio.example/one\n" +
            "#EXTINF:-1,  \nhttp://tv.example:8080/x\n"

        assertEquals(listOf("radio.example", "tv.example:8080"), parser.parse(text).map { it.title })
    }

    @Test
    fun `c03 an indented tag line still marks a manifest`() {
        assertTrue(parser.parse("#EXTM3U\n   #ext-x-targetduration:10\nsegment0.ts\n").isEmpty())
    }

    private fun vector(name: String) = String(resource(name).readBytes(), Charsets.UTF_8)

    private fun resource(name: String) = requireNotNull(javaClass.classLoader?.getResourceAsStream("$DIR/$name")) {
        "$DIR/$name is missing from the test resources"
    }

    private companion object {
        const val DIR = "user-playlist"
        const val PROVENANCE = "PROVENANCE.txt"
        const val PROVENANCE_ROW_FIELDS = 3
        const val HLS_MANIFEST = "hls-manifest-rejection.m3u8"
        const val HLS_ENTRY = "valid-extended-hls-entry.m3u8"
        const val EXTENDED = "valid-extended.m3u8"
        val VECTORS = setOf(HLS_MANIFEST, HLS_ENTRY, EXTENDED)
    }
}
