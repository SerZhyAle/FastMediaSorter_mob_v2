package com.sza.fastmediasorter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.utils.SafHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/**
 * JVM coverage for [LocalMoveFileOperation]. Plain-filesystem branches, plus the SAF-source to
 * file-destination branch with Uri and SafHelper mocked. Under the unit-test runtime Build.VERSION.SDK_INT is 0, so the
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

    @Test
    fun `SAF source whose delete fails is an error, not a move`() = runTest {
        val dest = tempFolder.newFolder("dest")
        val resolver = mockk<ContentResolver>()
        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(byteArrayOf(7, 8, 9)) }
        mockkStatic(Uri::class)
        mockkObject(SafHelper)
        try {
            every { Uri.parse(any()) } returns mockk()
            every { Uri.decode(any()) } answers { firstArg() }
            every { SafHelper.deleteContentUri(any(), any(), any()) } returns false

            // A mocked File: on Windows File("content://..") would rewrite the separators the operation
            // relies on to recognise a content Uri.
            val source = mockk<File>(relaxed = true)
            every { source.path } returns "content://provider/doc/a.txt"
            every { source.absolutePath } returns "content://provider/doc/a.txt"
            every { source.name } returns "a.txt"

            val result = op.execute(move(listOf(source), dest))

            assertTrue(result is FileOperationResult.Failure)
            assertArrayEquals(byteArrayOf(7, 8, 9), File(dest, "a.txt").readBytes())
        } finally {
            unmockkObject(SafHelper)
            unmockkStatic(Uri::class)
        }
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) =
        org.junit.Assert.assertArrayEquals(expected, actual)
}
