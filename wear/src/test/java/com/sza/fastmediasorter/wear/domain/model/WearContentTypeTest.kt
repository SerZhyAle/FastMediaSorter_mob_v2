package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2129: the machine-checkable half of strategic criterion 3 - "an audio list shows the audio glyph,
 * an image list the image glyph, and neither shows the old generic sheet". Which glyph is legible on
 * a small round display stays with the on-device pass; which type a row resolves to is settled here.
 */
class WearContentTypeTest {

    @Test
    fun `audio mime resolves to music`() {
        assertEquals(
            WearContentType.MUSIC,
            contentTypeForEntry(mimeType = "audio/mpeg", isDirectory = false)
        )
    }

    @Test
    fun `image mime resolves to image`() {
        assertEquals(
            WearContentType.IMAGE,
            contentTypeForEntry(mimeType = "image/jpeg", isDirectory = false)
        )
    }

    @Test
    fun `video mime resolves to video`() {
        assertEquals(
            WearContentType.VIDEO,
            contentTypeForEntry(mimeType = "video/mp4", isDirectory = false)
        )
    }

    @Test
    fun `null mime on a file resolves to document`() {
        assertEquals(
            WearContentType.DOCUMENT,
            contentTypeForEntry(mimeType = null, isDirectory = false)
        )
    }

    @Test
    fun `unrecognised mime on a file resolves to document`() {
        assertEquals(
            WearContentType.DOCUMENT,
            contentTypeForEntry(mimeType = "application/x-made-up", isDirectory = false)
        )
    }

    @Test
    fun `directory resolves to folder`() {
        assertEquals(
            WearContentType.FOLDER,
            contentTypeForEntry(mimeType = null, isDirectory = true)
        )
    }

    /**
     * The branch order is the assertion: some providers hand a directory a mime type of its own, and
     * a folder drawn as an audio file would misreport what tapping it does.
     */
    @Test
    fun `directory wins over a mime that would resolve elsewhere`() {
        assertEquals(
            WearContentType.FOLDER,
            contentTypeForEntry(mimeType = "audio/mpeg", isDirectory = true)
        )
    }

    @Test
    fun `a bare type prefix without its slash does not resolve`() {
        assertEquals(
            WearContentType.DOCUMENT,
            contentTypeForEntry(mimeType = "imagevnd", isDirectory = false)
        )
    }
}
