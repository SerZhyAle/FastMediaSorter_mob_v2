package com.sza.fastmediasorter.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Pure-logic coverage for the S0493 scheme whitelist, share file-name sanitization and the cache prune. */
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
    fun `prune over cap keeps an in-flight directory and deletes the rest`() {
        val root = Files.createTempDirectory("send_to_share").toFile()
        try {
            val inFlight = File(root, "111").apply { mkdirs() }
            File(inFlight, "a.bin").writeBytes(ByteArray(64))
            val stale = File(root, "222").apply { mkdirs() }
            File(stale, "b.bin").writeBytes(ByteArray(64))

            val pruned = MaterializeShareContentUseCase.pruneOverCap(root, setOf("111"), capBytes = 100L)

            assertTrue(pruned)
            assertTrue(File(inFlight, "a.bin").exists())
            assertFalse(stale.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `prune under cap deletes nothing`() {
        val root = Files.createTempDirectory("send_to_share").toFile()
        try {
            val sub = File(root, "333").apply { mkdirs() }
            File(sub, "c.bin").writeBytes(ByteArray(8))

            assertFalse(MaterializeShareContentUseCase.pruneOverCap(root, emptySet(), capBytes = 100L))
            assertTrue(sub.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `sanitize keeps a readable name and replaces unsafe chars`() {
        assertEquals("song.mp3", MaterializeShareContentUseCase.sanitizeFileName("smb://host/dir/song.mp3"))
        assertEquals("a_b_c.mp3", MaterializeShareContentUseCase.sanitizeFileName("a b c.mp3"))
        assertEquals("shared_file", MaterializeShareContentUseCase.sanitizeFileName("///"))
    }
}
