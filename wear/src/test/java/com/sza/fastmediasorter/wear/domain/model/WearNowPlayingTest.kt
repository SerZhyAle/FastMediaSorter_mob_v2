package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearNowPlayingTest {

    @Test
    fun emptyRecordHasNoContent() {
        val empty = WearNowPlaying.EMPTY
        assertFalse(empty.hasContent)
        assertEquals("", empty.title)
        assertFalse(empty.isPlaying)
    }

    @Test
    fun blankTitleReportsNoContent() {
        val playing = WearNowPlaying(
            title = "   ",
            subtitle = "Artist",
            isPlaying = true,
            updatedAtEpochMs = 1000L
        )
        assertFalse(playing.hasContent)
    }

    @Test
    fun recordPreservesTitleWhenPlayingFlipsToFalse() {
        val playing = WearNowPlaying(
            title = "Track One",
            subtitle = "Artist Name",
            isPlaying = true,
            updatedAtEpochMs = 1000L
        )
        assertTrue(playing.hasContent)

        val stopped = playing.copy(isPlaying = false)
        assertTrue(stopped.hasContent)
        assertEquals("Track One", stopped.title)
        assertEquals("Artist Name", stopped.subtitle)
        assertFalse(stopped.isPlaying)
    }
}
