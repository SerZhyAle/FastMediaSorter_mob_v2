package com.sza.fastmediasorter.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sza.fastmediasorter.TestFixtures
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumentation tests for file operations (Copy/Move/Delete).
 * These tests run on an Android device/emulator and verify actual file I/O.
 *
 * Note: These tests verify basic File API operations without network dependencies.
 */
@RunWith(AndroidJUnit4::class)
class FileOperationsInstrumentationTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private val testFiles = mutableListOf<File>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "file_ops_test")
        testDir.mkdirs()
    }

    @After
    fun cleanup() {
        testFiles.forEach { it.delete() }
        testDir.deleteRecursively()
    }

    @Test
    fun testCopyFile_withinLocalStorage() {
        val sourceFile = TestFixtures.createTempFile(testDir, "source.txt", "Original content")
        val destDir = File(testDir, "dest")
        destDir.mkdirs()
        testFiles.add(destDir)

        val destFile = File(destDir, sourceFile.name)
        sourceFile.copyTo(destFile, overwrite = false)

        assertTrue("Destination file should exist", destFile.exists())
        assertEquals("Content should match", "Original content", destFile.readText())
        assertTrue("Source file should still exist", sourceFile.exists())
    }

    @Test
    fun testMoveFile_withinLocalStorage() {
        val sourceFile = TestFixtures.createTempFile(testDir, "moveme.txt", "Move this")
        val destDir = File(testDir, "dest")
        destDir.mkdirs()
        testFiles.add(destDir)

        val destFile = File(destDir, sourceFile.name)
        sourceFile.copyTo(destFile, overwrite = false)
        val deleted = sourceFile.delete()

        assertTrue("Delete should succeed", deleted)
        assertTrue("Destination file should exist", destFile.exists())
        assertEquals("Content should match", "Move this", destFile.readText())
        assertFalse("Source file should not exist", sourceFile.exists())
    }

    @Test
    fun testDeleteFile_fromLocalStorage() {
        val fileToDelete = TestFixtures.createTempFile(testDir, "deleteme.txt", "Delete this")

        assertTrue("File should exist before deletion", fileToDelete.exists())

        val deleted = fileToDelete.delete()

        assertTrue("Delete operation should succeed", deleted)
        assertFalse("File should not exist after deletion", fileToDelete.exists())
    }

    @Test
    fun testCopyFile_overwriteExisting() {
        val sourceFile = TestFixtures.createTempFile(testDir, "source.txt", "New content")
        val destDir = File(testDir, "dest")
        destDir.mkdirs()
        testFiles.add(destDir)

        val existingFile = File(destDir, "source.txt")
        existingFile.writeText("Old content")
        testFiles.add(existingFile)

        sourceFile.copyTo(existingFile, overwrite = true)

        assertEquals("Content should be updated", "New content", existingFile.readText())
    }

    @Test
    fun testCopyFile_nonExistentSource() {
        val nonExistentFile = File(testDir, "nonexistent.txt")
        val destDir = File(testDir, "dest")
        destDir.mkdirs()
        testFiles.add(destDir)

        try {
            val destFile = File(destDir, nonExistentFile.name)
            nonExistentFile.copyTo(destFile, overwrite = false)
            fail("Should have thrown exception for non-existent file")
        } catch (e: Exception) {
            assertTrue("Exception should be thrown", true)
        }
    }

    @Test
    fun testCopyMultipleFiles() {
        val file1 = TestFixtures.createTempFile(testDir, "file1.txt", "Content 1")
        val file2 = TestFixtures.createTempFile(testDir, "file2.txt", "Content 2")
        val file3 = TestFixtures.createTempFile(testDir, "file3.txt", "Content 3")
        val destDir = File(testDir, "dest")
        destDir.mkdirs()
        testFiles.add(destDir)

        file1.copyTo(File(destDir, file1.name))
        file2.copyTo(File(destDir, file2.name))
        file3.copyTo(File(destDir, file3.name))

        assertTrue("File1 should exist in dest", File(destDir, "file1.txt").exists())
        assertTrue("File2 should exist in dest", File(destDir, "file2.txt").exists())
        assertTrue("File3 should exist in dest", File(destDir, "file3.txt").exists())
    }
}
