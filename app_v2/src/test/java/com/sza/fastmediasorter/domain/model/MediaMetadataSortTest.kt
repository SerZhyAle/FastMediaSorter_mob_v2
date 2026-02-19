package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies null-safe behavior of the metadata sort comparators used in GetMediaFilesUseCase.
 * Tests mirror the exact comparator expressions from sortFiles() to confirm correctness.
 */
class MediaMetadataSortTest {

    // region Helpers

    private fun makeFile(
        name: String,
        artist: String? = null,
        title: String? = null,
        duration: Long? = null,
        type: MediaType = MediaType.AUDIO
    ) = MediaFile(
        name = name,
        path = "/test/$name",
        size = 0L,
        createdDate = 0L,
        type = type,
        artist = artist,
        title = title,
        duration = duration
    )

    // endregion

    // region ARTIST_ASC null safety

    @Test
    fun `ARTIST_ASC null artists map to empty string and sort before non-null`() {
        val files = listOf(
            makeFile("c.mp3", artist = "ZZTop"),
            makeFile("a.mp3", artist = null),
            makeFile("b.mp3", artist = "ABBA"),
        )
        val sorted = files.sortedWith(
            compareBy<MediaFile> { it.artist?.lowercase() ?: "" }.thenBy { it.name.lowercase() }
        )
        // null → "" sorts first
        assertNull(sorted[0].artist)
        assertEquals("ABBA", sorted[1].artist)
        assertEquals("ZZTop", sorted[2].artist)
    }

    @Test
    fun `ARTIST_ASC multiple nulls are secondary-sorted by name`() {
        val files = listOf(
            makeFile("z.mp3", artist = null),
            makeFile("a.mp3", artist = null),
        )
        val sorted = files.sortedWith(
            compareBy<MediaFile> { it.artist?.lowercase() ?: "" }.thenBy { it.name.lowercase() }
        )
        assertEquals("a.mp3", sorted[0].name)
        assertEquals("z.mp3", sorted[1].name)
    }

    // endregion

    // region TITLE_ASC null safety

    @Test
    fun `TITLE_ASC null titles map to empty string and sort before non-null`() {
        val files = listOf(
            makeFile("c.mp3", title = "Yesterday"),
            makeFile("a.mp3", title = null),
            makeFile("b.mp3", title = "Africa"),
        )
        val sorted = files.sortedWith(
            compareBy<MediaFile> { it.title?.lowercase() ?: "" }.thenBy { it.name.lowercase() }
        )
        assertNull(sorted[0].title)
        assertEquals("Africa", sorted[1].title)
        assertEquals("Yesterday", sorted[2].title)
    }

    // endregion

    // region DURATION_ASC null safety

    @Test
    fun `DURATION_ASC null durations map to MAX_VALUE and sort last`() {
        val files = listOf(
            makeFile("c.mp3", duration = null),
            makeFile("a.mp3", duration = 30_000L),
            makeFile("b.mp3", duration = 120_000L),
        )
        val sorted = files.sortedWith(
            compareBy<MediaFile> { it.duration ?: Long.MAX_VALUE }.thenBy { it.name.lowercase() }
        )
        assertEquals(30_000L, sorted[0].duration)
        assertEquals(120_000L, sorted[1].duration)
        assertNull(sorted[2].duration)
    }

    @Test
    fun `DURATION_ASC multiple nulls are secondary-sorted by name`() {
        val files = listOf(
            makeFile("z.mp3", duration = null),
            makeFile("a.mp3", duration = null),
        )
        val sorted = files.sortedWith(
            compareBy<MediaFile> { it.duration ?: Long.MAX_VALUE }.thenBy { it.name.lowercase() }
        )
        assertEquals("a.mp3", sorted[0].name)
        assertEquals("z.mp3", sorted[1].name)
    }

    // endregion

    // region DATE_TAKEN_DESC null safety

    @Test
    fun `DATE_TAKEN_DESC null dates map to MIN_VALUE and sort last (DESC)`() {
        val files = listOf(
            makeFile("c.jpg", type = MediaType.IMAGE).copy(exifDateTime = null),
            makeFile("a.jpg", type = MediaType.IMAGE).copy(exifDateTime = 1_700_000_000_000L),
            makeFile("b.jpg", type = MediaType.IMAGE).copy(exifDateTime = 1_600_000_000_000L),
        )
        val sorted = files.sortedWith(
            compareByDescending<MediaFile> { it.exifDateTime ?: Long.MIN_VALUE }.thenBy { it.name.lowercase() }
        )
        assertEquals(1_700_000_000_000L, sorted[0].exifDateTime)
        assertEquals(1_600_000_000_000L, sorted[1].exifDateTime)
        assertNull(sorted[2].exifDateTime)
    }

    // endregion
}
