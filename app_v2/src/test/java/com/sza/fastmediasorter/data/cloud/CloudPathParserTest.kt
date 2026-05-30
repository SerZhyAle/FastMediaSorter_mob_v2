package com.sza.fastmediasorter.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CloudPathParser]: provider-alias resolution, per-provider fileId encoding,
 * scheme normalization, and validation. Pure JVM - no Android types are touched.
 */
class CloudPathParserTest {

    private val parser = CloudPathParser()

    @Test
    fun `normalizePath upgrades single-slash scheme`() {
        assertEquals("cloud://x/y", parser.normalizePath("cloud:/x/y"))
    }

    @Test
    fun `normalizePath leaves double-slash scheme untouched`() {
        assertEquals("cloud://x/y", parser.normalizePath("cloud://x/y"))
    }

    @Test
    fun `normalizePath leaves non-cloud path untouched`() {
        assertEquals("/local/path", parser.normalizePath("/local/path"))
    }

    @Test
    fun `isCloudPath true for both scheme variants`() {
        assertTrue(parser.isCloudPath("cloud:/x"))
        assertTrue(parser.isCloudPath("cloud://x"))
    }

    @Test
    fun `isCloudPath false for non-cloud`() {
        assertFalse(parser.isCloudPath("smb://server/share"))
    }

    @Test
    fun `parseCloudPath returns null for non-cloud scheme`() {
        assertNull(parser.parseCloudPath("smb://server/share"))
    }

    @Test
    fun `parseCloudPath resolves google aliases`() {
        for (alias in listOf("google_drive", "gdrive", "google")) {
            val info = parser.parseCloudPath("cloud://$alias/folder/file")
            assertEquals(CloudProvider.GOOGLE_DRIVE, info!!.provider)
        }
    }

    @Test
    fun `parseCloudPath resolves dropbox and onedrive aliases`() {
        assertEquals(
            CloudProvider.DROPBOX,
            parser.parseCloudPath("cloud://dropbox/folder/file")!!.provider
        )
        assertEquals(
            CloudProvider.ONEDRIVE,
            parser.parseCloudPath("cloud://onedrive/folder/file")!!.provider
        )
        assertEquals(
            CloudProvider.ONEDRIVE,
            parser.parseCloudPath("cloud://msdrive/folder/file")!!.provider
        )
    }

    @Test
    fun `parseCloudPath returns null for unknown provider`() {
        assertNull(parser.parseCloudPath("cloud://unknownprovider/folder/file"))
    }

    @Test
    fun `parseCloudPath dropbox fileId is full slash-prefixed path`() {
        val info = parser.parseCloudPath("cloud://dropbox/folderId/sub/file.jpg")
        assertEquals(CloudProvider.DROPBOX, info!!.provider)
        assertEquals("folderId", info.folderId)
        // For Dropbox: "/" + parts.drop(1) joined => "/folderId/sub/file.jpg"
        assertEquals("/folderId/sub/file.jpg", info.fileId)
    }

    @Test
    fun `parseCloudPath google encodes folderId slash filename`() {
        val info = parser.parseCloudPath("cloud://google_drive/folderId/path/to/file.jpg")
        assertEquals("folderId", info!!.folderId)
        // filename taken via substringAfterLast('/') of parts[2] = "path/to/file.jpg"
        assertEquals("folderId/file.jpg", info.fileId)
    }

    @Test
    fun `parseCloudPath onedrive without folderId uses bare filename`() {
        // limit=3 split of "onedrive/file.jpg" => parts = [onedrive, file.jpg]; size==2 path
        val info = parser.parseCloudPath("cloud://onedrive/file.jpg")
        assertEquals(CloudProvider.ONEDRIVE, info!!.provider)
        assertEquals("file.jpg", info.folderId)
        // size == 2 → fileId falls to folderId
        assertEquals("file.jpg", info.fileId)
    }

    @Test
    fun `parseCloudPath provider-only path yields empty fileId`() {
        val info = parser.parseCloudPath("cloud://dropbox")
        assertEquals(CloudProvider.DROPBOX, info!!.provider)
        assertNull(info.folderId)
        assertEquals("", info.fileId)
    }

    @Test
    fun `parseCloudPath normalizes single-slash scheme before parsing`() {
        val info = parser.parseCloudPath("cloud:/dropbox/folder/file.jpg")
        assertEquals(CloudProvider.DROPBOX, info!!.provider)
    }
}
