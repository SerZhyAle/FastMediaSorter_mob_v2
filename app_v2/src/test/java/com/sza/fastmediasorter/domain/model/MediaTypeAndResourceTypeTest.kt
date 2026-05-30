package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the classification helpers on [MediaType] and [ResourceType].
 */
class MediaTypeAndResourceTypeTest {

    @Test
    fun `isBinaryFile is true for every binary variant`() {
        assertTrue(MediaType.BINARY_ARCHIVE.isBinaryFile())
        assertTrue(MediaType.BINARY_DISK.isBinaryFile())
        assertTrue(MediaType.BINARY_EXECUTABLE.isBinaryFile())
        assertTrue(MediaType.BINARY_OTHER.isBinaryFile())
    }

    @Test
    fun `isBinaryFile is false for non-binary types`() {
        assertFalse(MediaType.IMAGE.isBinaryFile())
        assertFalse(MediaType.VIDEO.isBinaryFile())
        assertFalse(MediaType.PDF.isBinaryFile())
    }

    @Test
    fun `isDocumentFile is true for the document family`() {
        assertTrue(MediaType.TEXT.isDocumentFile())
        assertTrue(MediaType.PDF.isDocumentFile())
        assertTrue(MediaType.EPUB.isDocumentFile())
        assertTrue(MediaType.OFFICE_DOCUMENT.isDocumentFile())
    }

    @Test
    fun `isDocumentFile is false outside the document family`() {
        assertFalse(MediaType.IMAGE.isDocumentFile())
        assertFalse(MediaType.AUDIO.isDocumentFile())
        assertFalse(MediaType.BINARY_OTHER.isDocumentFile())
    }

    @Test
    fun `isNetworkResource is true for remote protocols`() {
        assertTrue(ResourceType.SMB.isNetworkResource)
        assertTrue(ResourceType.SFTP.isNetworkResource)
        assertTrue(ResourceType.FTP.isNetworkResource)
        assertTrue(ResourceType.CLOUD.isNetworkResource)
    }

    @Test
    fun `isNetworkResource is false for local`() {
        assertFalse(ResourceType.LOCAL.isNetworkResource)
    }
}
