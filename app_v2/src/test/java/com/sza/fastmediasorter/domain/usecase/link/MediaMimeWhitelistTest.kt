package com.sza.fastmediasorter.domain.usecase.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMimeWhitelistTest {

    @Test
    fun `null mime is rejected`() {
        assertFalse(MediaMimeWhitelist.isAllowed(null))
    }

    @Test
    fun `octet-stream is explicitly rejected`() {
        assertFalse(MediaMimeWhitelist.isAllowed("application/octet-stream"))
    }

    @Test
    fun `whitelisted image video audio text are allowed`() {
        assertTrue(MediaMimeWhitelist.isAllowed("image/jpeg"))
        assertTrue(MediaMimeWhitelist.isAllowed("video/mp4"))
        assertTrue(MediaMimeWhitelist.isAllowed("audio/mpeg"))
        assertTrue(MediaMimeWhitelist.isAllowed("text/plain"))
    }

    @Test
    fun `application whitelist matches full type only`() {
        assertTrue(MediaMimeWhitelist.isAllowed("application/pdf"))
        assertTrue(MediaMimeWhitelist.isAllowed("application/epub+zip"))
        assertFalse(MediaMimeWhitelist.isAllowed("application/zip"))
    }

    @Test
    fun `parameters and case are normalised`() {
        assertTrue(MediaMimeWhitelist.isAllowed("IMAGE/JPEG; charset=binary"))
    }

    @Test
    fun `unknown subtype within known type is rejected`() {
        assertFalse(MediaMimeWhitelist.isAllowed("image/tiff"))
        assertFalse(MediaMimeWhitelist.isAllowed("video/x-flv"))
    }

    @Test
    fun `malformed mime without slash is rejected`() {
        assertFalse(MediaMimeWhitelist.isAllowed("image"))
    }

    @Test
    fun `extensionFor maps known mime back to an extension`() {
        assertEquals("png", MediaMimeWhitelist.extensionFor("image/png"))
        assertEquals("pdf", MediaMimeWhitelist.extensionFor("application/pdf"))
    }

    @Test
    fun `extensionFor returns null for unknown or null mime`() {
        assertNull(MediaMimeWhitelist.extensionFor(null))
        assertNull(MediaMimeWhitelist.extensionFor("image/tiff"))
    }

    @Test
    fun `mimeForExtension trims dot and lowercases`() {
        assertEquals("video/mp4", MediaMimeWhitelist.mimeForExtension(".MP4"))
        assertEquals("audio/mpeg", MediaMimeWhitelist.mimeForExtension("mp3"))
    }

    @Test
    fun `mimeForExtension returns null for blank or unknown`() {
        assertNull(MediaMimeWhitelist.mimeForExtension(null))
        assertNull(MediaMimeWhitelist.mimeForExtension("  "))
        assertNull(MediaMimeWhitelist.mimeForExtension("exe"))
    }
}
