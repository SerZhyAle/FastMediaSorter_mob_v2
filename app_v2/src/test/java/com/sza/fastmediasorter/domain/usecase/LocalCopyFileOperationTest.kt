package com.sza.fastmediasorter.domain.usecase

import io.mockk.every
import io.mockk.mockk
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM coverage for [LocalCopyFileOperation]. Only the plain-filesystem branches are exercised
 * (real [File] under [TemporaryFolder] + mocked [Context]); content:// SAF paths use Uri.parse and
 * are intentionally not covered here.
 */
class LocalCopyFileOperationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>()
    private val scanned = mutableListOf<String>()
    private lateinit var op: LocalCopyFileOperation

    @Before
    fun setup() {
        // Only the all-failed branch calls getString; return the joined error verbatim.
        every { context.getString(any(), *anyVararg()) } returns "all copy failed"
        op = LocalCopyFileOperation(context) { scanned.add(it) }
    }

    private fun copy(sources: List<File>, dest: File, overwrite: Boolean = false) =
        FileOperation.Copy(sources = sources, destination = dest, overwrite = overwrite)

    @Test
    fun `copies all files and records scan`() = runTest {
        val src = tempFolder.newFolder("src")
        val dest = tempFolder.newFolder("dest")
        val a = File(src, "a.txt").apply { writeText("aaa") }
        val b = File(src, "b.txt").apply { writeText("bbb") }

        val result = op.execute(copy(listOf(a, b), dest))

        val success = result as FileOperationResult.Success
        assertEquals(2, success.processedCount)
        assertTrue(File(dest, "a.txt").exists())
        assertTrue(File(dest, "b.txt").exists())
        assertEquals("aaa", File(dest, "a.txt").readText())
        assertEquals(2, scanned.size)
    }

    @Test
    fun `existing destination without overwrite is skipped`() = runTest {
        val src = tempFolder.newFolder("src")
        val dest = tempFolder.newFolder("dest")
        val a = File(src, "a.txt").apply { writeText("new") }
        File(dest, "a.txt").apply { writeText("old") }

        val result = op.execute(copy(listOf(a), dest, overwrite = false))

        val success = result as FileOperationResult.Success
        assertEquals(0, success.processedCount)
        assertEquals(1, success.skippedCount)
        // Existing content must be preserved when skipped.
        assertEquals("old", File(dest, "a.txt").readText())
    }

    @Test
    fun `existing destination with overwrite replaces content`() = runTest {
        val src = tempFolder.newFolder("src")
        val dest = tempFolder.newFolder("dest")
        val a = File(src, "a.txt").apply { writeText("new") }
        File(dest, "a.txt").apply { writeText("old") }

        val result = op.execute(copy(listOf(a), dest, overwrite = true))

        val success = result as FileOperationResult.Success
        assertEquals(1, success.processedCount)
        assertEquals("new", File(dest, "a.txt").readText())
    }

    @Test
    fun `missing source produces total failure`() = runTest {
        val src = tempFolder.newFolder("src")
        val dest = tempFolder.newFolder("dest")
        val missing = File(src, "ghost.txt")

        val result = op.execute(copy(listOf(missing), dest))

        assertTrue(result is FileOperationResult.Failure)
    }

    @Test
    fun `partial success when one source missing and one copies`() = runTest {
        val src = tempFolder.newFolder("src")
        val dest = tempFolder.newFolder("dest")
        val ok = File(src, "ok.txt").apply { writeText("x") }
        val missing = File(src, "ghost.txt")

        val result = op.execute(copy(listOf(ok, missing), dest))

        val partial = result as FileOperationResult.PartialSuccess
        assertEquals(1, partial.processedCount)
        assertEquals(1, partial.failedCount)
        assertTrue(File(dest, "ok.txt").exists())
    }
}
