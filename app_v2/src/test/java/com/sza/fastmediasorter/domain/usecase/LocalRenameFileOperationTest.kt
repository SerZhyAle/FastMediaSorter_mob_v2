package com.sza.fastmediasorter.domain.usecase

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

class LocalRenameFileOperationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>()
    private val scanned = mutableListOf<String>()
    private lateinit var op: LocalRenameFileOperation

    @Before
    fun setup() {
        every { context.getString(any(), any()) } returns "already exists"
        op = LocalRenameFileOperation(context) { scanned.add(it) }
    }

    @Test
    fun `missing file returns failure`() {
        val missing = File(tempFolder.root, "nope.txt")

        val result = op.execute(FileOperation.Rename(missing, "renamed.txt"))

        assertTrue(result is FileOperationResult.Failure)
    }

    @Test
    fun `successful local rename returns success and triggers scan`() {
        val file = tempFolder.newFile("orig.txt").apply { writeText("x") }

        val result = op.execute(FileOperation.Rename(file, "renamed.txt"))

        val success = result as FileOperationResult.Success
        assertEquals(1, success.processedCount)
        val newPath = File(tempFolder.root, "renamed.txt")
        assertTrue(newPath.exists())
        assertFalse(file.exists())
        assertEquals(listOf(newPath.absolutePath), scanned)
        assertEquals(listOf(newPath.absolutePath), success.copiedFilePaths)
    }

    @Test
    fun `rename to an existing target name fails`() {
        val file = tempFolder.newFile("orig.txt").apply { writeText("x") }
        tempFolder.newFile("taken.txt").apply { writeText("y") }

        val result = op.execute(FileOperation.Rename(file, "taken.txt"))

        assertTrue(result is FileOperationResult.Failure)
        // Original file must remain intact when the target already exists.
        assertTrue(file.exists())
        assertTrue(scanned.isEmpty())
    }
}
