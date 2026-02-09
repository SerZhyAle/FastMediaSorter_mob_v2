package com.sza.fastmediasorter.data.network.smb

import com.hierynomus.msdtyp.FileTime
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.data.network.helpers.SmbDirectoryScanner
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.StandardTestDispatcher

@ExperimentalCoroutinesApi
class SmbDirectoryScannerTest {

    private lateinit var scanner: SmbDirectoryScanner
    private lateinit var mockShare: DiskShare
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        // Use UnconfinedTestDispatcher to run coroutines immediately in tests
        scanner = SmbDirectoryScanner(testDispatcher)
        mockShare = mockk(relaxed = true)
    }

    private fun createMockFile(name: String, isDirectory: Boolean, size: Long = 1000L): FileIdBothDirectoryInformation {
        val mockFile = mockk<FileIdBothDirectoryInformation>()
        every { mockFile.fileName } returns name
        
        // 0x10 is FILE_ATTRIBUTE_DIRECTORY
        val attributes = if (isDirectory) 0x10L else 0x20L // 0x20 is FILE_ATTRIBUTE_ARCHIVE (normal file)
        every { mockFile.fileAttributes } returns attributes
        
        every { mockFile.endOfFile } returns size
        
        val mockTime = mockk<FileTime>()
        every { mockTime.toEpochMillis() } returns 1234567890L
        every { mockFile.lastWriteTime } returns mockTime
        
        return mockFile
    }

    @Test
    fun `scanDirectoryRecursive returns files from root`() = runTest(testDispatcher) {
        // Setup
        val file1 = createMockFile("file1.jpg", false)
        val file2 = createMockFile("file2.mp4", false)
        
        every { mockShare.list("") } returns listOf(file1, file2)

        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()

        // Execute
        scanner.scanDirectoryRecursive(mockShare, "", null, results)

        // Verify
        assertEquals(2, results.size)
        assertTrue(results.any { it.name == "file1.jpg" && !it.isDirectory })
        assertTrue(results.any { it.name == "file2.mp4" && !it.isDirectory })
    }

    @Test
    fun `scanDirectoryRecursive filters by extension`() = runTest(testDispatcher) {
        // Setup
        val file1 = createMockFile("image.jpg", false)
        val file2 = createMockFile("video.mp4", false)
        val file3 = createMockFile("doc.pdf", false)
        
        every { mockShare.list("") } returns listOf(file1, file2, file3)

        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()
        val extensions = setOf("jpg", "mp4")

        // Execute
        scanner.scanDirectoryRecursive(mockShare, "", extensions, results)

        // Verify
        assertEquals(2, results.size)
        assertTrue(results.any { it.name == "image.jpg" })
        assertTrue(results.any { it.name == "video.mp4" })
        assertTrue(results.none { it.name == "doc.pdf" })
    }

    @Test
    fun `scanDirectoryRecursive traverses subdirectories but does not add them to results`() = runTest(testDispatcher) {
        // Setup
        val rootFile = createMockFile("rootFile.jpg", false)
        val subDir = createMockFile("SubFolder", true)
        
        val subFile = createMockFile("subFile.jpg", false)

        // Mock list behavior for root and subdirectory
        every { mockShare.list("") } returns listOf(rootFile, subDir)
        every { mockShare.list("SubFolder") } returns listOf(subFile)

        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()

        // Execute
        scanner.scanDirectoryRecursive(mockShare, "", null, results)

        // Verify
        // Should contain rootFile.jpg and subFile.jpg
        // Should NOT contain "SubFolder" itself (based on current implementation)
        assertEquals(2, results.size)
        assertTrue(results.any { it.name == "rootFile.jpg" })
        assertTrue(results.any { it.name == "subFile.jpg" && it.path.contains("SubFolder") })
        assertTrue(results.none { it.name == "SubFolder" })
        
        // Verify recursion happened
        verify(exactly = 1) { mockShare.list("SubFolder") }
    }

    @Test
    fun `scanDirectoryNonRecursive ignores subdirectories`() = runTest(testDispatcher) {
        // Setup
        val rootFile = createMockFile("rootFile.jpg", false)
        val subDir = createMockFile("SubFolder", true)
        
        every { mockShare.list("") } returns listOf(rootFile, subDir)
        // Ensure we don't call list on subDir
        every { mockShare.list("SubFolder") } throws RuntimeException("Should not be called")

        val results = mutableListOf<SmbDirectoryScanner.SmbFileInfo>()

        // Execute
        scanner.scanDirectoryNonRecursive(mockShare, "", null, results, 100)

        // Verify
        assertEquals(1, results.size)
        assertTrue(results.any { it.name == "rootFile.jpg" })
    }
}
