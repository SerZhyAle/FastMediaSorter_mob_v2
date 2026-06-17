package com.sza.fastmediasorter.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage for the S0493 scheme whitelist and share file-name sanitization. */
class MaterializeShareContentHelpersTest {

    @Test
    fun `network and cloud schemes are downloadable`() {
        assertTrue(MaterializeShareContentUseCase.isDownloadableScheme("smb://h/f"))
        assertTrue(MaterializeShareContentUseCase.isDownloadableScheme("sftp://h/f"))
        assertTrue(MaterializeShareContentUseCase.isDownloadableScheme("ftp://h/f"))
        assertTrue(MaterializeShareContentUseCase.isDownloadableScheme("cloud://drive/x"))
        assertTrue(MaterializeShareContentUseCase.isDownloadableScheme("cloud:/drive/x"))
    }

    @Test
    fun `http and local are not downloadable in this iteration`() {
        assertFalse(MaterializeShareContentUseCase.isDownloadableScheme("https://x/y"))
        assertFalse(MaterializeShareContentUseCase.isDownloadableScheme("http://x/y"))
        assertFalse(MaterializeShareContentUseCase.isDownloadableScheme("/local/path"))
    }

    @Test
    fun `sanitize keeps a readable name and replaces unsafe chars`() {
        assertEquals("song.mp3", MaterializeShareContentUseCase.sanitizeFileName("smb://host/dir/song.mp3"))
        assertEquals("a_b_c.mp3", MaterializeShareContentUseCase.sanitizeFileName("a b c.mp3"))
        assertEquals("shared_file", MaterializeShareContentUseCase.sanitizeFileName("///"))
    }
}
