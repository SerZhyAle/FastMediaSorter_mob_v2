package com.sza.fastmediasorter.domain.usecase

import io.mockk.every
import io.mockk.mockk
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM coverage for [LocalMoveFileOperation]. Plain-filesystem branches only - content:// SAF moves
 * use Uri.parse and are not covered. Under the unit-test runtime Build.VERSION.SDK_INT is 0, so the
 * shared-storage MediaStore delete path is never taken; moves fall back to File.delete().
 */
class LocalMoveFileOperationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>()
    private val scanned = mutableListOf<String>()
    private val mediaStoreDeleted = mutableListOf<String>()
    private lateinit var op: LocalMoveFileOperation

    @Before
    fun setup() {
        every { context.getString(any(), *anyVararg()) } returns "all move failed"
        op = LocalMoveFileOperation(
            context = context,
            scanNewFile = { scanned.add(it) },
            deleteViaMediaStore = { path -> mediaStoreDeleted.add(path); true },
            isSharedStorage = { false },
        )
    }

    private fun move(sources: List<File>, dest: File, overwrite: Boolean = false) =
        FileOperation.Move(sources = sources, destination = dest, overwrite = overwrite)

    @Test
    fun `moves file via rename and removes source`() = runTest {
        val src = tempFolder.newFolder("src")
        val dest = tempFolder.newFolder("dest")
        val a = File(src, "a.txt").apply { writeText("aaa") }

        val result = op.execute(move(listOf(a), dest))

        val success = result as FileOperationResult.Success
        assertEquals(1, success.processedCount)
        assertTrue(File(dest, "a.txt").exists())
        assertFalse(a.exists())
        assertEquals(1, scanned.size)
    }

    @Test
    fun `existing destination without overwrite is skipped and source kept`() = runTest {
        val src = tempFolder.newFolder("src")
        val dest = tempFolder.newFolder("dest")
        val a = File(src, "a.txt").apply { writeText("new") }
        File(dest, "a.txt").apply { writeText("old") }

        val result = op.execute(move(listOf(a), dest, overwrite = false))

        val success = result as FileOperationResult.Success
        assertEquals(0, success.processedCount)
        assertEquals(1, success.skippedCount)
        assertTrue(a.exists())
        assertEquals("old", File(dest, "a.txt").readText())
    }

    @Test
    fun `missing source produces total failure`() = runTest {
        val src = tempFolder.newFolder("src")
        val dest = tempFolder.newFolder("dest")

        val result = op.execute(move(listOf(File(src, "ghost.txt")), dest))

        assertTrue(result is FileOperationResult.Failure)
    }

    @Test
    fun `partial success when one source missing`() = runTest {
        val src = tempFolder.newFolder("src")
        val dest = tempFolder.newFolder("dest")
        val ok = File(src, "ok.txt").apply { writeText("x") }
        val missing = File(src, "ghost.txt")

        val result = op.execute(move(listOf(ok, missing), dest))

        val partial = result as FileOperationResult.PartialSuccess
        assertEquals(1, partial.processedCount)
        assertEquals(1, partial.failedCount)
        assertTrue(File(dest, "ok.txt").exists())
    }

    @Test
    fun `move keeps content intact`() = runTest {
        val src = tempFolder.newFolder("src")
        val dest = tempFolder.newFolder("dest")
        val a = File(src, "data.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }

        op.execute(move(listOf(a), dest))

        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), File(dest, "data.bin").readBytes())
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) =
        org.junit.Assert.assertArrayEquals(expected, actual)
}
