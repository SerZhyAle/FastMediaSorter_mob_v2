package com.sza.fastmediasorter.ui.player.dispatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaFamilyResolverTest {

    @Test
    fun testResolvePhotoVideo() {
        assertEquals(MediaFamily.PHOTO_VIDEO, MediaFamilyResolver.resolve("image/jpeg", "test.jpg"))
        assertEquals(MediaFamily.PHOTO_VIDEO, MediaFamilyResolver.resolve("image/gif", "test.gif"))
        assertEquals(MediaFamily.PHOTO_VIDEO, MediaFamilyResolver.resolve("video/mp4", "test.mp4"))
        assertEquals(MediaFamily.PHOTO_VIDEO, MediaFamilyResolver.resolve(null, "test.png"))
        assertEquals(MediaFamily.PHOTO_VIDEO, MediaFamilyResolver.resolve("video/*", null))
    }

    @Test
    fun testResolveAudio() {
        assertEquals(MediaFamily.AUDIO, MediaFamilyResolver.resolve("audio/mpeg", "test.mp3"))
        assertEquals(MediaFamily.AUDIO, MediaFamilyResolver.resolve(null, "test.flac"))
        assertEquals(MediaFamily.AUDIO, MediaFamilyResolver.resolve("audio/*", null))
    }

    @Test
    fun testResolveDocument() {
        // S1455: PDF and EPUB only. MediaTypeUtils recognises both from hardcoded literals, so they hold
        // on every flavor. The office formats moved to MediaFamilyResolverDocumentsTest in
        // src/testDocumentsEnabled - they come from OfficeDocumentFamilyCatalog, whose lite and photos
        // copies are empty, so asserting them here failed on exactly the builds that never ship them.
        assertEquals(MediaFamily.DOCUMENT, MediaFamilyResolver.resolve("application/pdf", "test.pdf"))
        assertEquals(MediaFamily.DOCUMENT, MediaFamilyResolver.resolve("application/epub+zip", "test.epub"))
    }

    @Test
    fun testResolveText() {
        assertEquals(MediaFamily.TEXT, MediaFamilyResolver.resolve("text/plain", "test.txt"))
        assertEquals(MediaFamily.TEXT, MediaFamilyResolver.resolve(null, "test.kt"))
        assertEquals(MediaFamily.TEXT, MediaFamilyResolver.resolve("application/json", "test.json"))
    }

    @Test
    fun testResolveUnsupported() {
        assertNull(MediaFamilyResolver.resolve("application/zip", "test.zip"))
        assertNull(MediaFamilyResolver.resolve("application/octet-stream", "test.bin"))
        assertNull(MediaFamilyResolver.resolve(null, "test.unknown"))
    }
}
