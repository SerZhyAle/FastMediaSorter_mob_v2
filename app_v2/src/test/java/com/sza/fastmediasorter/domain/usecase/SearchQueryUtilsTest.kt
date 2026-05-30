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
}
