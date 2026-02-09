package com.sza.fastmediasorter.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        
        // Create test directory in app's cache
        testDir = File(context.cacheDir, "file_ops_test")
        testDir.mkdirs()
    }

    @After
    fun cleanup() {
        // Clean up all test files
        testFiles.forEach { it.delete() }
        testDir.deleteRecursively()
    }

    private fun createTestFile(name: String, content: String = "Test content"): File {
        val file = File(testDir, name)
        file.writeText(content)
        testFiles.add(file)
        return file
    }

    @Test
    fun testCopyFile_withinLocalStorage() {
        // Setup
        val sourceFile = createTestFile("source.txt", "Original content")
        val destDir = File(testDir, "dest")
        destDir.mkdirs()
        testFiles.add(destDir)

        // Execute
        val destFile = File(destDir, sourceFile.name)
        sourceFile.copyTo(destFile, overwrite = false)

        // Verify
        assertTrue("Destination file should exist", destFile.exists())
        assertEquals("Content should match", "Original content", destFile.readText())
        assertTrue("Source file should still exist", sourceFile.exists())
    }

    @Test
    fun testMoveFile_withinLocalStorage() {
        // Setup
        val sourceFile = createTestFile("moveme.txt", "Move this")
        val destDir = File(testDir, "dest")
        destDir.mkdirs()
        testFiles.add(destDir)

        // Execute - Copy then delete (simulating move)
        val destFile = File(destDir, sourceFile.name)
        sourceFile.copyTo(destFile, overwrite = false)
        val deleted = sourceFile.delete()

        // Verify
        assertTrue("Delete should succeed", deleted)
        assertTrue("Destination file should exist", destFile.exists())
        assertEquals("Content should match", "Move this", destFile.readText())
        assertFalse("Source file should not exist", sourceFile.exists())
    }

    @Test
    fun testDeleteFile_fromLocalStorage() {
        // Setup
        val fileToDelete = createTestFile("deleteme.txt", "Delete this")

        // Verify file exists before deletion
        assertTrue("File should exist before deletion", fileToDelete.exists())

        // Execute
        val deleted = fileToDelete.delete()

        // Verify
        assertTrue("Delete operation should succeed", deleted)
        assertFalse("File should not exist after deletion", fileToDelete.exists())
    }

    @Test
    fun testCopyFile_overwriteExisting() {
        // Setup
        val sourceFile = createTestFile("source.txt", "New content")
        val destDir = File(testDir, "dest")
        destDir.mkdirs()
        testFiles.add(destDir)
        
        // Create existing file with old content
        val existingFile = File(destDir, "source.txt")
        existingFile.writeText("Old content")
        testFiles.add(existingFile)

        // Execute
        sourceFile.copyTo(existingFile, overwrite = true)

        // Verify
        assertEquals("Content should be updated", "New content", existingFile.readText())
    }

    @Test
    fun testCopyFile_nonExistentSource() {
        // Setup
        val nonExistentFile = File(testDir, "nonexistent.txt")
        val destDir = File(testDir, "dest")
        destDir.mkdirs()
        testFiles.add(destDir)

        // Execute & Verify
        try {
            val destFile = File(destDir, nonExistentFile.name)
            nonExistentFile.copyTo(destFile, overwrite = false)
            fail("Should have thrown exception for non-existent file")
        } catch (e: Exception) {
            // Expected - file doesn't exist
            assertTrue("Exception should be thrown", true)
        }
    }

    @Test
    fun testCopyMultipleFiles() {
        // Setup
        val file1 = createTestFile("file1.txt", "Content 1")
        val file2 = createTestFile("file2.txt", "Content 2")
        val file3 = createTestFile("file3.txt", "Content 3")
        val destDir = File(testDir, "dest")
        destDir.mkdirs()
        testFiles.add(destDir)

        // Execute
        file1.copyTo(File(destDir, file1.name))
        file2.copyTo(File(destDir, file2.name))
        file3.copyTo(File(destDir, file3.name))

        // Verify
        assertTrue("File1 should exist in dest", File(destDir, "file1.txt").exists())
        assertTrue("File2 should exist in dest", File(destDir, "file2.txt").exists())
        assertTrue("File3 should exist in dest", File(destDir, "file3.txt").exists())
    }
}
