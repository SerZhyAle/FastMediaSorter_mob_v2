package com.sza.fastmediasorter.core.util

import com.sza.fastmediasorter.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S3842 - one size table for SMB, SFTP and FTP partial reads, and the SAF URI normalisation shared
 * by every [SafUriExtractor] method.
 */
class NetworkFileDownloaderPartialSizeTest {

    @Test
    fun `partial size follows the file type`() {
        assertEquals(64 * 1024L, NetworkFileDownloader.partialSizeFor(MediaType.IMAGE, false))
        assertEquals(5 * 1024 * 1024L, NetworkFileDownloader.partialSizeFor(MediaType.GIF, false))
        assertEquals(
            NetworkFileDownloader.VIDEO_INITIAL_SIZE,
            NetworkFileDownloader.partialSizeFor(MediaType.VIDEO, false)
        )
        assertEquals(5 * 1024 * 1024L, NetworkFileDownloader.partialSizeFor(MediaType.AUDIO, true))
        assertNull(NetworkFileDownloader.partialSizeFor(MediaType.PDF, false))
    }

    @Test
    fun `content uri is normalised from both forms`() {
        assertEquals("content://a/b", SafUriExtractor.toContentUri("content:/a/b"))
        assertEquals("content://a/b", SafUriExtractor.toContentUri("content://a/b"))
    }
}
