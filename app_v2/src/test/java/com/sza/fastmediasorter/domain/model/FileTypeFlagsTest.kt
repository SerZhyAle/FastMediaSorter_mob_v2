package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FileTypeFlags] bitmask helpers, derived constants, and legacy-name mapping.
 */
class FileTypeFlagsTest {

    @Test
    fun `constants have the documented bit values`() {
        assertEquals(1, FileTypeFlags.ALL_FILES)
        assertEquals(2, FileTypeFlags.IMAGES)
        assertEquals(4, FileTypeFlags.AUDIO)
        assertEquals(8, FileTypeFlags.VIDEO)
        assertEquals(16, FileTypeFlags.DOCUMENTS)
    }

    @Test
    fun `ALL_MEDIA combines every media flag without ALL_FILES`() {
        assertEquals(30, FileTypeFlags.ALL_MEDIA)
        assertFalse(FileTypeFlags.isAllFiles(FileTypeFlags.ALL_MEDIA))
    }

    @Test
    fun `DEFAULT is ALL_FILES plus ALL_MEDIA`() {
        assertEquals(31, FileTypeFlags.DEFAULT)
    }

    @Test
    fun `per-flag predicates detect their own bit`() {
        assertTrue(FileTypeFlags.isAllFiles(FileTypeFlags.ALL_FILES))
        assertTrue(FileTypeFlags.hasImages(FileTypeFlags.IMAGES))
        assertTrue(FileTypeFlags.hasAudio(FileTypeFlags.AUDIO))
        assertTrue(FileTypeFlags.hasVideo(FileTypeFlags.VIDEO))
        assertTrue(FileTypeFlags.hasDocuments(FileTypeFlags.DOCUMENTS))
    }

    @Test
    fun `predicates return false for an unset bit`() {
        assertFalse(FileTypeFlags.hasAudio(FileTypeFlags.IMAGES))
        assertFalse(FileTypeFlags.hasVideo(FileTypeFlags.IMAGES or FileTypeFlags.AUDIO))
        assertFalse(FileTypeFlags.isAllFiles(0))
    }

    @Test
    fun `combined mask reports every contained flag`() {
        val mask = FileTypeFlags.IMAGES or FileTypeFlags.VIDEO
        assertTrue(FileTypeFlags.hasImages(mask))
        assertTrue(FileTypeFlags.hasVideo(mask))
        assertFalse(FileTypeFlags.hasAudio(mask))
        assertFalse(FileTypeFlags.hasDocuments(mask))
    }

    @Test
    fun `fromLegacyName maps the recognised enum names`() {
        assertEquals(FileTypeFlags.ALL_MEDIA, FileTypeFlags.fromLegacyName("ALL"))
        assertEquals(FileTypeFlags.IMAGES, FileTypeFlags.fromLegacyName("IMAGES"))
        assertEquals(FileTypeFlags.AUDIO, FileTypeFlags.fromLegacyName("AUDIO"))
        assertEquals(FileTypeFlags.VIDEO, FileTypeFlags.fromLegacyName("VIDEO"))
        assertEquals(FileTypeFlags.DOCUMENTS, FileTypeFlags.fromLegacyName("DOCUMENTS"))
    }

    @Test
    fun `fromLegacyName is case-insensitive`() {
        assertEquals(FileTypeFlags.IMAGES, FileTypeFlags.fromLegacyName("images"))
        assertEquals(FileTypeFlags.VIDEO, FileTypeFlags.fromLegacyName("Video"))
    }

    @Test
    fun `fromLegacyName falls back to ALL_MEDIA for unknown names`() {
        assertEquals(FileTypeFlags.ALL_MEDIA, FileTypeFlags.fromLegacyName("garbage"))
        assertEquals(FileTypeFlags.ALL_MEDIA, FileTypeFlags.fromLegacyName(""))
    }
}
