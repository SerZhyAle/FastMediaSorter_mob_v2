package com.sza.fastmediasorter.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryUtilsTest {

    @Test
    fun `prepareSearchQuery strips extension and separators`() {
        assertEquals("hello world", SearchQueryUtils.prepareSearchQuery("hello_world.mp3"))
    }

    @Test
    fun `prepareSearchQuery removes leading track number`() {
        assertEquals("song name", SearchQueryUtils.prepareSearchQuery("01 - song_name.flac"))
    }

    @Test
    fun `prepareSearchQuery removes bracket parenthesis and brace contents`() {
        assertEquals(
            "track",
            SearchQueryUtils.prepareSearchQuery("track [live](2011){remaster}.mp3")
        )
    }

    @Test
    fun `prepareSearchQuery keeps unicode letters`() {
        assertEquals("песня", SearchQueryUtils.prepareSearchQuery("песня.mp3"))
    }

    @Test
    fun `isPlaceholderValue true for null blank and known placeholders`() {
        assertTrue(SearchQueryUtils.isPlaceholderValue(null))
        assertTrue(SearchQueryUtils.isPlaceholderValue("  "))
        assertTrue(SearchQueryUtils.isPlaceholderValue("Unknown Artist"))
        assertTrue(SearchQueryUtils.isPlaceholderValue("VA"))
    }

    @Test
    fun `isPlaceholderValue false for real value`() {
        assertFalse(SearchQueryUtils.isPlaceholderValue("Pink Floyd"))
    }

    @Test
    fun `filterPlaceholder returns null for placeholder and trimmed value otherwise`() {
        assertNull(SearchQueryUtils.filterPlaceholder("unknown"))
        assertEquals("Pink Floyd", SearchQueryUtils.filterPlaceholder("  Pink Floyd  "))
    }

    @Test
    fun `cleanForSearch strips all bracket types and their contents`() {
        assertEquals("Breathe", SearchQueryUtils.cleanForSearch("Breathe (In the Air)"))
        assertEquals("Song", SearchQueryUtils.cleanForSearch("Song [Live Version]"))
        assertEquals("Track", SearchQueryUtils.cleanForSearch("Track {Remastered 2011}"))
        assertEquals("Pink Floyd", SearchQueryUtils.cleanForSearch("Pink Floyd"))
    }

    // ── S0347: structural parse ──────────────────────────────────────────────

    @Test
    fun `parseArtistTitle splits artist and title on spaced dash`() {
        val parsed = SearchQueryUtils.parseArtistTitle("Pink Floyd - Time.mp3")
        assertEquals("Pink Floyd", parsed.artist)
        assertEquals("Time", parsed.title)
    }

    @Test
    fun `parseArtistTitle strips leading track number before splitting`() {
        val parsed = SearchQueryUtils.parseArtistTitle("01 - Pink Floyd - Time.flac")
        assertEquals("Pink Floyd", parsed.artist)
        assertEquals("Time", parsed.title)
    }

    @Test
    fun `parseArtistTitle splits dash without surrounding spaces`() {
        val parsed = SearchQueryUtils.parseArtistTitle("Beatles-Help.mp3")
        assertEquals("Beatles", parsed.artist)
        assertEquals("Help", parsed.title)
    }

    @Test
    fun `parseArtistTitle returns null artist when no separator`() {
        val parsed = SearchQueryUtils.parseArtistTitle("Time.mp3")
        assertNull(parsed.artist)
        assertEquals("Time", parsed.title)
    }

    // ── S0347: confidence scoring ────────────────────────────────────────────

    @Test
    fun `matchConfidence is high when artist and title both match`() {
        val score = SearchQueryUtils.matchConfidence(
            queryArtist = "Pink Floyd", queryTitle = "Time",
            candidateArtist = "Pink Floyd", candidateTitle = "Time",
        )
        assertTrue("expected >= threshold, was $score", score >= SearchQueryUtils.COVER_MATCH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun `matchConfidence rejects same title by different artist`() {
        // The core false positive: title matches, artist does not.
        val score = SearchQueryUtils.matchConfidence(
            queryArtist = "Nirvana", queryTitle = "Come As You Are",
            candidateArtist = "Some Cover Band", candidateTitle = "Come As You Are",
        )
        assertTrue("expected < threshold, was $score", score < SearchQueryUtils.COVER_MATCH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun `matchConfidence uses dir artist as fallback signal`() {
        // Query artist missing, but the album-folder artist matches the candidate.
        val score = SearchQueryUtils.matchConfidence(
            queryArtist = null, queryTitle = "Time",
            candidateArtist = "Pink Floyd", candidateTitle = "Time",
            dirArtist = "Pink Floyd",
        )
        assertTrue("expected >= threshold, was $score", score >= SearchQueryUtils.COVER_MATCH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun `matchConfidence normalizes case and diacritics`() {
        val score = SearchQueryUtils.matchConfidence(
            queryArtist = "Beyonce", queryTitle = "Halo",
            candidateArtist = "Beyoncé", candidateTitle = "HALO",
        )
        assertTrue("expected >= threshold, was $score", score >= SearchQueryUtils.COVER_MATCH_CONFIDENCE_THRESHOLD)
    }
}
