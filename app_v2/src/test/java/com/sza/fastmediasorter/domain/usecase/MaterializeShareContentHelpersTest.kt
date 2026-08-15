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
    fun `direct http and https are downloadable, local paths are not`() {
        assertTrue(MaterializeShareContentUseCase.isDownloadableScheme("https://x/y"))
        assertTrue(MaterializeShareContentUseCase.isDownloadableScheme("http://x/y"))
        assertFalse(MaterializeShareContentUseCase.isDownloadableScheme("/local/path"))
    }

    @Test
    fun `streaming manifests stay on the failure path`() {
        assertFalse(MaterializeShareContentUseCase.isDownloadableScheme("https://x/live.m3u8"))
        assertFalse(MaterializeShareContentUseCase.isDownloadableScheme("https://x/stream.mpd"))
        assertFalse(MaterializeShareContentUseCase.isDownloadableScheme("http://x/smooth.ism"))
        assertFalse(MaterializeShareContentUseCase.isDownloadableScheme("https://x/LIVE.M3U8?token=1"))
        assertTrue(MaterializeShareContentUseCase.isDownloadableScheme("https://x/clip.mp4?m3u8=no"))
    }

    @Test
    fun `sanitize keeps a readable name and replaces unsafe chars`() {
        assertEquals("song.mp3", MaterializeShareContentUseCase.sanitizeFileName("smb://host/dir/song.mp3"))
        assertEquals("a_b_c.mp3", MaterializeShareContentUseCase.sanitizeFileName("a b c.mp3"))
        assertEquals("shared_file", MaterializeShareContentUseCase.sanitizeFileName("///"))
    }
}
