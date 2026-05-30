package com.sza.fastmediasorter.data.network.helpers

import com.hierynomus.msdtyp.FileTime
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.smbj.share.DiskShare
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SmbDirectoryScanner]. The SMBJ [DiskShare] is mocked - no sockets are opened.
 * Covers extension filtering, directory traversal, trash-segment skipping, dot-entry skipping,
 * pagination (offset/limit), and counting.
 */
class SmbDirectoryScannerTest {

    private val scanner = SmbDirectoryScanner(Dispatchers.Unconfined)

    private fun entry(name: String, isDir: Boolean, size: Long = 10L): FileIdBothDirectoryInformation {
        val item = mockk<FileIdBothDirectoryInformation>()
        every { item.fileName } returns name
        // FILE_ATTRIBUTE_DIRECTORY = 0x10
        every { item.fileAttributes } returns if (isDir) 0x10L else 0L
        every { item.endOfFile } returns size
        val ft = mockk<FileTime>()
        every { ft.toEpochMillis() } returns 1_000L
        every { item.lastWriteTime } returns ft
        return item
    }

    private fun file(name: String, size: Long = 10L) = entry(name, isDir = false, size = size)
    private fun dir(name: String) = entry(name, isDir = true)

    // ── non-recursive ──────────────────────────────────────────────────────────

    @Test
    fun `non-recursive keeps matching files and drops dirs and dot entries`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("root") } returns listOf(
            file("a.mp4"), dir("sub"), file("b.jpg"), file("note.txt"), entry(".", true), entry("..", true)
        )
        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
        scanner.scanDirectoryNonRecursive(share, "root", setOf("mp4", "jpg"), results, Int.MAX_VALUE)
        assertEquals(listOf("a.mp4", "b.jpg"), results.map { it.name })
    }

    @Test
    fun `non-recursive null extensions keeps all files`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("") } returns listOf(file("a.mp4"), file("b.unknown"))
        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
        scanner.scanDirectoryNonRecursive(share, "", null, results, Int.MAX_VALUE)
        assertEquals(listOf("a.mp4", "b.unknown"), results.map { it.name })
    }

    @Test
    fun `non-recursive includeDirectories adds folders`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("root") } returns listOf(dir("sub"), file("a.mp4"))
        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
        scanner.scanDirectoryNonRecursive(share, "root", setOf("mp4"), results, Int.MAX_VALUE, includeDirectories = true)
        assertEquals(setOf("sub" to true, "a.mp4" to false), results.map { it.name to it.isDirectory }.toSet())
    }

    @Test
    fun `non-recursive respects maxFiles`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("root") } returns listOf(file("a.mp4"), file("b.mp4"), file("c.mp4"))
        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
        scanner.scanDirectoryNonRecursive(share, "root", setOf("mp4"), results, maxFiles = 2)
        assertEquals(2, results.size)
    }

    @Test
    fun `non-recursive builds full path with parent prefix`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("dir") } returns listOf(file("a.mp4"))
        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
        scanner.scanDirectoryNonRecursive(share, "/dir/", setOf("mp4"), results, Int.MAX_VALUE)
        assertEquals("dir/a.mp4", results.single().path)
    }

    // ── recursive with limit ────────────────────────────────────────────────────

    @Test
    fun `recursive with limit descends into subdirectories and skips trash`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("root") } returns listOf(file("top.mp4"), dir("sub"), dir(".trash_123"))
        every { share.list("root/sub") } returns listOf(file("nested.mp4"))
        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
        val reached = scanner.scanDirectoryRecursiveWithLimit(share, "root", setOf("mp4"), results, maxFiles = 100)
        assertEquals(setOf("top.mp4", "nested.mp4"), results.map { it.name }.toSet())
        assertTrue(!reached)
    }

    @Test
    fun `recursive with limit stops at maxFiles`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("root") } returns listOf(file("a.mp4"), file("b.mp4"), file("c.mp4"))
        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
        val reached = scanner.scanDirectoryRecursiveWithLimit(share, "root", setOf("mp4"), results, maxFiles = 2)
        assertEquals(2, results.size)
        assertTrue(reached)
    }

    // ── offset / limit pagination ─────────────────────────────────────────────────

    @Test
    fun `offset-limit skips offset files then collects up to limit`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("root") } returns listOf(
            file("f0.mp4"), file("f1.mp4"), file("f2.mp4"), file("f3.mp4"), file("f4.mp4")
        )
        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
        scanner.scanDirectoryWithOffsetLimit(share, "root", setOf("mp4"), results, offset = 1, limit = 2, skippedSoFar = 0)
        // files sorted alphabetically, offset 1 → skip f0, take f1,f2
        assertEquals(listOf("f1.mp4", "f2.mp4"), results.map { it.name })
    }

    @Test
    fun `offset-limit early-exits when limit already reached`() = runTest {
        val share = mockk<DiskShare>()
        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>(
            SmbDirectoryScanner.SmbFileInfo("x", "x", false, 1, 1),
            SmbDirectoryScanner.SmbFileInfo("y", "y", false, 1, 1)
        )
        val skipped = scanner.scanDirectoryWithOffsetLimit(share, "root", null, results, offset = 0, limit = 2, skippedSoFar = 7)
        assertEquals(7, skipped)
    }

    @Test
    fun `non-recursive with offset skips first offset matches`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("root") } returns listOf(
            file("a.mp4"), file("b.mp4"), file("c.mp4"), dir("sub")
        )
        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
        scanner.scanDirectoryNonRecursiveWithOffset(share, "root", setOf("mp4"), results, offset = 1, limit = 10)
        assertEquals(listOf("b.mp4", "c.mp4"), results.map { it.name })
    }

    // ── counting ────────────────────────────────────────────────────────────────

    @Test
    fun `count recursive counts matching files across subdirectories`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("root") } returns listOf(file("a.mp4"), file("skip.txt"), dir("sub"))
        every { share.list("root/sub") } returns listOf(file("b.mp4"))
        val count = scanner.countDirectoryRecursive(share, "root", setOf("mp4"), maxCount = 1000)
        assertEquals(2, count)
    }

    @Test
    fun `count recursive honours maxCount`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("root") } returns listOf(file("a.mp4"), file("b.mp4"), file("c.mp4"))
        val count = scanner.countDirectoryRecursive(share, "root", setOf("mp4"), maxCount = 2)
        assertEquals(2, count)
    }

    @Test
    fun `count non-recursive skips subdirectories`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("root") } returns listOf(file("a.mp4"), dir("sub"), file("b.mp4"))
        val count = scanner.countDirectoryNonRecursive(share, "root", setOf("mp4"), maxCount = 1000)
        assertEquals(2, count)
    }

    @Test
    fun `count non-recursive returns zero on share error`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list(any<String>()) } throws RuntimeException("boom")
        val count = scanner.countDirectoryNonRecursive(share, "root", null, maxCount = 1000)
        assertEquals(0, count)
    }
}
