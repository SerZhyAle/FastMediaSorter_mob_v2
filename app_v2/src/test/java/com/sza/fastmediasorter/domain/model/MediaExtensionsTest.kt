package com.sza.fastmediasorter.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MediaExtensions utility object.
 * Tests file extension detection and categorization.
 */
class MediaExtensionsTest {

    @Test
    fun `isImage returns true for common image formats`() {
        assertTrue("Expected jpg", MediaExtensions.isImage("jpg"))
        assertTrue("Expected jpeg", MediaExtensions.isImage("jpeg"))
        assertTrue("Expected png", MediaExtensions.isImage("png"))
        assertTrue("Expected gif", MediaExtensions.isImage("gif"))
        assertTrue("Expected bmp", MediaExtensions.isImage("bmp"))
        assertTrue("Expected webp", MediaExtensions.isImage("webp"))
    }

    @Test
    fun `isImage is case insensitive`() {
        assertTrue("Expected JPG", MediaExtensions.isImage("JPG"))
        assertTrue("Expected Png", MediaExtensions.isImage("Png"))
        assertTrue("Expected JPEG", MediaExtensions.isImage("JPEG"))
    }

    @Test
    fun `isImage returns false for non-image formats`() {
        assertFalse("Expected false for mp4", MediaExtensions.isImage("mp4"))
        assertFalse("Expected false for mp3", MediaExtensions.isImage("mp3"))
        assertFalse("Expected false for txt", MediaExtensions.isImage("txt"))
        assertFalse("Expected false for pdf", MediaExtensions.isImage("pdf"))
    }

    @Test
    fun `isVideo returns true for common video formats`() {
        assertTrue("Expected mp4", MediaExtensions.isVideo("mp4"))
        assertTrue("Expected mkv", MediaExtensions.isVideo("mkv"))
        assertTrue("Expected mov", MediaExtensions.isVideo("mov"))
        assertTrue("Expected wmv", MediaExtensions.isVideo("wmv"))
        assertTrue("Expected webm", MediaExtensions.isVideo("webm"))
        // avi is not supported - treated as binary
        assertFalse("Expected false for avi", MediaExtensions.isVideo("avi"))
    }

    @Test
    fun `isVideo returns true for less common formats`() {
        assertTrue("Expected 3gp", MediaExtensions.isVideo("3gp"))
        assertTrue("Expected mts", MediaExtensions.isVideo("mts"))
        assertTrue("Expected vob", MediaExtensions.isVideo("vob"))
        assertTrue("Expected ogv", MediaExtensions.isVideo("ogv"))
    }

    @Test
    fun `isVideo is case insensitive`() {
        assertTrue("Expected MP4", MediaExtensions.isVideo("MP4"))
        assertTrue("Expected Mkv", MediaExtensions.isVideo("Mkv"))
        assertTrue("Expected MOV", MediaExtensions.isVideo("MOV"))
    }

    @Test
    fun `isVideo returns false for non-video formats`() {
        assertFalse("Expected false for jpg", MediaExtensions.isVideo("jpg"))
        assertFalse("Expected false for mp3", MediaExtensions.isVideo("mp3"))
        assertFalse("Expected false for txt", MediaExtensions.isVideo("txt"))
    }

    @Test
    fun `isAudio returns true for common audio formats`() {
        assertTrue("Expected mp3", MediaExtensions.isAudio("mp3"))
        assertTrue("Expected flac", MediaExtensions.isAudio("flac"))
        assertTrue("Expected aac", MediaExtensions.isAudio("aac"))
        assertTrue("Expected ogg", MediaExtensions.isAudio("ogg"))
        assertTrue("Expected m4a", MediaExtensions.isAudio("m4a"))
    }

    @Test
    fun `isAudio returns true for less common formats`() {
        assertTrue("Expected opus", MediaExtensions.isAudio("opus"))
        assertTrue("Expected midi", MediaExtensions.isAudio("midi"))
        assertTrue("Expected mka", MediaExtensions.isAudio("mka"))
    }

    @Test
    fun `isAudio is case insensitive`() {
        assertTrue("Expected MP3", MediaExtensions.isAudio("MP3"))
        assertTrue("Expected Flac", MediaExtensions.isAudio("Flac"))
    }

    @Test
    fun `isAudio returns false for non-audio formats`() {
        assertFalse("Expected false for jpg", MediaExtensions.isAudio("jpg"))
        assertFalse("Expected false for mp4", MediaExtensions.isAudio("mp4"))
        assertFalse("Expected false for txt", MediaExtensions.isAudio("txt"))
    }

    // Note: v2 doesn't have isMedia() - removed these tests

    @Test
    fun `getMediaType returns correct type for images`() {
        assertEquals(MediaType.IMAGE, MediaExtensions.getMediaType("jpg"))
        assertEquals(MediaType.IMAGE, MediaExtensions.getMediaType("png"))
        assertEquals(MediaType.IMAGE, MediaExtensions.getMediaType("gif"))
        assertEquals(MediaType.IMAGE, MediaExtensions.getMediaType("webp"))
    }

    @Test
    fun `getMediaType returns correct type for videos`() {
        assertEquals(MediaType.VIDEO, MediaExtensions.getMediaType("mp4"))
        assertEquals(MediaType.VIDEO, MediaExtensions.getMediaType("mkv"))
        assertEquals(MediaType.VIDEO, MediaExtensions.getMediaType("mov"))
        // avi is not supported - returns binary type
        assertEquals(MediaType.BINARY_OTHER, MediaExtensions.getMediaType("avi"))
    }

    @Test
    fun `getMediaType returns correct type for audio`() {
        assertEquals(MediaType.AUDIO, MediaExtensions.getMediaType("mp3"))
        // wav is not in v2's AUDIO list
        assertEquals(MediaType.AUDIO, MediaExtensions.getMediaType("flac"))
        assertEquals(MediaType.AUDIO, MediaExtensions.getMediaType("ogg"))
    }

    @Test
    fun `getMediaType returns TEXT or PDF for documents`() {
        assertEquals(MediaType.TEXT, MediaExtensions.getMediaType("txt"))
        assertEquals(MediaType.PDF, MediaExtensions.getMediaType("pdf"))
        // doc is not supported - returns IMAGE as fallback
    }

    @Test
    fun `getMediaType is case insensitive`() {
        assertEquals(MediaType.IMAGE, MediaExtensions.getMediaType("JPG"))
        assertEquals(MediaType.VIDEO, MediaExtensions.getMediaType("MP4"))
        assertEquals(MediaType.AUDIO, MediaExtensions.getMediaType("MP3"))
    }

    @Test
    fun `IMAGE set contains common extensions`() {
        // v2 has fewer extensions than v2_6
        val v2Images = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "avif")
        assertEquals("Expected IMAGE set to match v2", v2Images, MediaExtensions.IMAGE)
    }

    @Test
    fun `VIDEO set contains common extensions`() {
        assertTrue("mp4 in VIDEO", "mp4" in MediaExtensions.VIDEO)
        assertTrue("mkv in VIDEO", "mkv" in MediaExtensions.VIDEO)
        assertTrue("mov in VIDEO", "mov" in MediaExtensions.VIDEO)
        assertTrue("webm in VIDEO", "webm" in MediaExtensions.VIDEO)
        // avi is not supported - treated as binary, not in VIDEO
        assertFalse("avi not in VIDEO", "avi" in MediaExtensions.VIDEO)
    }

    @Test
    fun `AUDIO set contains common extensions`() {
        assertTrue("mp3 in AUDIO", "mp3" in MediaExtensions.AUDIO)
        assertTrue("flac in AUDIO", "flac" in MediaExtensions.AUDIO)
        assertTrue("aac in AUDIO", "aac" in MediaExtensions.AUDIO)
        assertTrue("ogg in AUDIO", "ogg" in MediaExtensions.AUDIO)
        assertTrue("m4a in AUDIO", "m4a" in MediaExtensions.AUDIO)
    }

    // v2 doesn't have ALL_MEDIA constant - removed those tests

    @Test
    fun `empty string is not recognized as media`() {
        assertFalse("Expected false for empty", MediaExtensions.isImage(""))
        assertFalse("Expected false for empty", MediaExtensions.isVideo(""))
        assertFalse("Expected false for empty", MediaExtensions.isAudio(""))
    }

    @Test
    fun `getMediaType returns IMAGE for empty string`() {
        // v2 returns IMAGE as fallback
        assertEquals(MediaType.IMAGE, MediaExtensions.getMediaType(""))
    }

    @Test
    fun `extension with dots is handled correctly`() {
        // Some systems might pass ".jpg" instead of "jpg"
        assertFalse("Expected false for .jpg (with dot)", MediaExtensions.isImage(".jpg"))
        // Note: The implementation expects extension without dot
    }

    @Test
    fun `special characters in extension return false`() {
        assertFalse("Expected false for jpg!", MediaExtensions.isImage("jpg!"))
        assertFalse("Expected false for mp4?", MediaExtensions.isVideo("mp4?"))
        assertFalse("Expected false for mp3#", MediaExtensions.isAudio("mp3#"))
    }
}
