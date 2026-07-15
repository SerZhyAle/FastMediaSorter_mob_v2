package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards S1024: the Draw editor "Save as.." naming must never emit a file whose name lacks a real
 * extension or ends in a bare dot. Covers the four failure/boundary cases named in the spec plus
 * the [ImageEditorFileNamer.buildName] blank-extension guard.
 */
class ImageEditorFileNamerTest {

    @Test
    fun `ensureExtension appends default jpg when no dot present`() {
        assertEquals("foo.jpg", ImageEditorFileNamer.ensureExtension("foo"))
    }

    @Test
    fun `ensureExtension replaces bare trailing dot with fallback`() {
        assertEquals("foo.png", ImageEditorFileNamer.ensureExtension("foo.", "png"))
    }

    @Test
    fun `ensureExtension keeps an existing valid extension`() {
        assertEquals("foo.png", ImageEditorFileNamer.ensureExtension("foo.png", "jpg"))
    }

    @Test
    fun `ensureExtension preserves a multi-dot real extension`() {
        assertEquals("archive.tar.gz", ImageEditorFileNamer.ensureExtension("archive.tar.gz"))
    }

    @Test
    fun `ensureExtension uses the custom fallback for a bare name`() {
        assertEquals("shot.png", ImageEditorFileNamer.ensureExtension("shot", "png"))
    }

    @Test
    fun `buildName guards a blank extension with jpg`() {
        val name = ImageEditorFileNamer.buildName("base", "", ImageEditorFileNamer.DRAW)
        assertTrue("expected .jpg suffix, was: $name", name.endsWith(".jpg"))
    }

    @Test
    fun `buildName keeps a provided extension`() {
        val name = ImageEditorFileNamer.buildName("base", "png", ImageEditorFileNamer.DRAW)
        assertTrue("expected .png suffix, was: $name", name.endsWith(".png"))
    }
}
