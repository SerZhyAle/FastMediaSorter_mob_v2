package com.sza.fastmediasorter.domain.transfer

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [TempFileManager]. The injected [Context] only exposes a cache directory,
 * backed here by a [TemporaryFolder] so file logic runs on plain JVM.
 */
class TempFileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cacheDir: File
    private lateinit var manager: TempFileManager

    @Before
    fun setup() {
        cacheDir = tempFolder.newFolder("cache")
        val context = mockk<Context>(relaxed = true)
        every { context.cacheDir } returns cacheDir
        manager = TempFileManager(context)
    }

    @Test
    fun `createTempFile produces a tracked file in the cache directory with the prefix and suffix`() {
        val file = manager.createTempFile("download_", ".mp4")
        assertEquals(cacheDir, file.parentFile)
        assertTrue(file.name.startsWith("temp_"))
        assertTrue(file.name.endsWith("download_.mp4"))
        assertEquals(1, manager.getActiveTempFileCount())
    }

    @Test
    fun `createTempFileFromName preserves the extension and sanitizes the prefix`() {
        val file = manager.createTempFileFromName("My Vacation!.mp4")
        assertTrue("kept extension: ${file.name}", file.name.endsWith(".mp4"))
        // Spaces and punctuation in the stem are replaced with underscores.
        assertFalse(file.name.contains(" "))
        assertFalse(file.name.contains("!"))
    }

    @Test
    fun `createTempFileFromName tolerates a name without an extension`() {
        val file = manager.createTempFileFromName("README")
        assertTrue(file.name.startsWith("temp_"))
        // No extension dot is appended when the original had none.
        assertFalse(file.name.endsWith("."))
    }

    @Test
    fun `cleanupTempFile deletes the file and stops tracking it`() {
        val file = manager.createTempFile("x", ".dat")
        file.writeText("payload")
        assertTrue(file.exists())

        manager.cleanupTempFile(file)

        assertFalse(file.exists())
        assertEquals(0, manager.getActiveTempFileCount())
    }

    @Test
    fun `cleanupAllTempFiles removes every tracked file and returns the count`() {
        manager.createTempFile("a", ".dat").writeText("1")
        manager.createTempFile("b", ".dat").writeText("2")

        val deleted = manager.cleanupAllTempFiles()

        assertEquals(2, deleted)
        assertEquals(0, manager.getActiveTempFileCount())
    }

    @Test
    fun `cleanupOldTempFiles deletes only matching-prefix files older than the threshold`() {
        val oldTemp = File(cacheDir, "temp_old.dat").apply { writeText("x") }
        oldTemp.setLastModified(System.currentTimeMillis() - 48L * 60 * 60 * 1000)
        val freshTemp = File(cacheDir, "temp_fresh.dat").apply { writeText("x") }
        val unrelated = File(cacheDir, "keepme.dat").apply { writeText("x") }
        unrelated.setLastModified(System.currentTimeMillis() - 48L * 60 * 60 * 1000)

        val deleted = manager.cleanupOldTempFiles(maxAgeMs = 24L * 60 * 60 * 1000)

        assertEquals(1, deleted)
        assertFalse(oldTemp.exists())
        assertTrue(freshTemp.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `getTotalTempFileSize sums the sizes of existing tracked files`() {
        manager.createTempFile("a", ".dat").writeText("12345")
        manager.createTempFile("b", ".dat").writeText("123")
        assertEquals(8L, manager.getTotalTempFileSize())
    }

    @Test
    fun `hasAvailableSpace compares against the cache directory usable space`() {
        // A real directory reports a large usable space; 1 KB always fits.
        assertTrue(manager.hasAvailableSpace(1024))
        // Demanding more than Long-range usable space cannot be satisfied.
        assertFalse(manager.hasAvailableSpace(Long.MAX_VALUE))
    }
}
