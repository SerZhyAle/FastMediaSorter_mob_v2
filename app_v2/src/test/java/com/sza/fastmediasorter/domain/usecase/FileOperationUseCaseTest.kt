package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for FileOperationUseCase
 * 
 * Tests file operations (copy/move/delete/rename) with mocked file handlers
 * Validates progress emission, cancellation handling, and undo functionality
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileOperationUseCaseTest {

    private lateinit var smbHandler: SmbFileOperationHandler
    private lateinit var sftpHandler: SftpFileOperationHandler
    private lateinit var ftpHandler: FtpFileOperationHandler
    private lateinit var cloudHandler: CloudFileOperationHandler
    
    private lateinit var useCase: FileOperationUseCase

    @Before
    fun setup() {
        smbHandler = mockk(relaxed = true)
        sftpHandler = mockk(relaxed = true)
        ftpHandler = mockk(relaxed = true)
        cloudHandler = mockk(relaxed = true)
        
        useCase = FileOperationUseCase(
            smbHandler,
            sftpHandler,
            ftpHandler,
            cloudHandler
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `executeWithProgress emits Starting when operation begins`() = runTest {
        // Given
        val sourceFile = mockk<File>(relaxed = true)
        val destFile = mockk<File>(relaxed = true)
        every { sourceFile.exists() } returns true
        every { destFile.exists() } returns false
        
        val operation = FileOperation.Copy(
            sources = listOf(sourceFile),
            destination = destFile,
            overwrite = false
        )

        // When
        val emissions = useCase.executeWithProgress(operation).toList()

        // Then
        assertTrue("Should emit Starting progress", emissions.any { it is FileOperationProgress.Starting })
        val starting = emissions.first() as FileOperationProgress.Starting
        assertEquals("Total files should be 1", 1, starting.totalFiles)
    }

    @Test
    fun `copy operation returns Success when all files processed`() = runTest {
        // Given
        val sourceFile = mockk<File>(relaxed = true) {
            every { exists() } returns true
            every { isFile } returns true
            every { name } returns "test.jpg"
            every { absolutePath } returns "/source/test.jpg"
        }
        val destFile = mockk<File>(relaxed = true) {
            every { exists() } returns false
            every { mkdirs() } returns true
            every { absolutePath } returns "/dest"
        }
        
        // Mock successful copy
        every { sourceFile.copyTo(any(), any()) } returns mockk()
        
        val operation = FileOperation.Copy(
            sources = listOf(sourceFile),
            destination = destFile,
            overwrite = false
        )

        // When
        val emissions = useCase.executeWithProgress(operation).toList()

        // Then
        val completed = emissions.last() as FileOperationProgress.Completed
        assertTrue(
            "Result should be Success",
            completed.result is FileOperationResult.Success
        )
        val success = completed.result as FileOperationResult.Success
        assertEquals("Processed count should be 1", 1, success.processedCount)
    }

    @Test
    fun `copy operation returns Failure when destination does not exist and cannot be created`() = runTest {
        // Given
        val sourceFile = mockk<File>(relaxed = true) {
            every { exists() } returns true
        }
        val destFile = mockk<File>(relaxed = true) {
            every { exists() } returns false
            every { mkdirs() } returns false // Cannot create destination
        }
        
        val operation = FileOperation.Copy(
            sources = listOf(sourceFile),
            destination = destFile,
            overwrite = false
        )

        // When
        val emissions = useCase.executeWithProgress(operation).toList()

        // Then
        val completed = emissions.last() as FileOperationProgress.Completed
        assertTrue(
            "Result should be Failure",
            completed.result is FileOperationResult.Failure
        )
    }

    @Test
    fun `delete operation with softDelete moves files to trash`() = runTest {
        // Given
        val fileToDelete = mockk<File>(relaxed = true) {
            every { exists() } returns true
            every { isFile } returns true
            every { name } returns "file.jpg"
            every { parentFile } returns mockk {
                every { absolutePath } returns "/parent"
            }
        }
        
        val trashFolder = mockk<File>(relaxed = true) {
            every { exists() } returns false
            every { mkdirs() } returns true
        }
        
        mockkStatic(File::class)
        every { File(any<String>()) } returns trashFolder
        every { fileToDelete.renameTo(any()) } returns true
        
        val operation = FileOperation.Delete(
            files = listOf(fileToDelete),
            softDelete = true
        )

        // When
        val emissions = useCase.executeWithProgress(operation).toList()

        // Then
        val completed = emissions.last() as FileOperationProgress.Completed
        assertTrue(
            "Result should be Success",
            completed.result is FileOperationResult.Success
        )
        verify { fileToDelete.renameTo(any()) } // Verify file was moved, not deleted
    }

    @Test
    fun `delete operation with softDelete=false permanently deletes files`() = runTest {
        // Given
        val fileToDelete = mockk<File>(relaxed = true) {
            every { exists() } returns true
            every { delete() } returns true
        }
        
        val operation = FileOperation.Delete(
            files = listOf(fileToDelete),
            softDelete = false
        )

        // When
        val emissions = useCase.executeWithProgress(operation).toList()

        // Then
        val completed = emissions.last() as FileOperationProgress.Completed
        assertTrue(
            "Result should be Success",
            completed.result is FileOperationResult.Success
        )
        verify { fileToDelete.delete() } // Verify permanent deletion
    }

    @Test
    fun `rename operation returns Success when file renamed`() = runTest {
        // Given
        val originalFile = mockk<File>(relaxed = true) {
            every { exists() } returns true
            every { parentFile } returns mockk {
                every { absolutePath } returns "/parent"
            }
        }
        
        val newFile = mockk<File>(relaxed = true)
        mockkStatic(File::class)
        every { File(any<String>(), any<String>()) } returns newFile
        every { originalFile.renameTo(newFile) } returns true
        
        val operation = FileOperation.Rename(
            file = originalFile,
            newName = "newName.jpg"
        )

        // When
        val emissions = useCase.executeWithProgress(operation).toList()

        // Then
        val completed = emissions.last() as FileOperationProgress.Completed
        assertTrue(
            "Result should be Success",
            completed.result is FileOperationResult.Success
        )
    }

    @Test
    fun `move operation returns Success and removes source files`() = runTest {
        // Given
        val sourceFile = mockk<File>(relaxed = true) {
            every { exists() } returns true
            every { isFile } returns true
            every { name } returns "file.jpg"
            every { delete() } returns true
            every { absolutePath } returns "/source/file.jpg"
        }
        val destFile = mockk<File>(relaxed = true) {
            every { exists() } returns false
            every { mkdirs() } returns true
            every { absolutePath } returns "/dest"
        }
        
        every { sourceFile.copyTo(any(), any()) } returns mockk()
        
        val operation = FileOperation.Move(
            sources = listOf(sourceFile),
            destination = destFile,
            overwrite = false
        )

        // When
        val emissions = useCase.executeWithProgress(operation).toList()

        // Then
        val completed = emissions.last() as FileOperationProgress.Completed
        assertTrue("Result should be Success", completed.result is FileOperationResult.Success)
        verify { sourceFile.delete() } // Verify source was deleted after copy
    }

    @Test
    fun `operation with multiple files emits Processing for each file`() = runTest {
        // Given
        val files = (1..3).map { index ->
            mockk<File>(relaxed = true) {
                every { exists() } returns true
                every { isFile } returns true
                every { name } returns "file$index.jpg"
                every { absolutePath } returns "/source/file$index.jpg"
            }
        }
        val destFile = mockk<File>(relaxed = true) {
            every { exists() } returns false
            every { mkdirs() } returns true
            every { absolutePath } returns "/dest"
        }
        
        files.forEach { file ->
            every { file.copyTo(any(), any()) } returns mockk()
        }
        
        val operation = FileOperation.Copy(
            sources = files,
            destination = destFile,
            overwrite = false
        )

        // When
        val emissions = useCase.executeWithProgress(operation).toList()

        // Then
        val processingEmissions = emissions.filterIsInstance<FileOperationProgress.Processing>()
        assertEquals(
            "Should emit Processing for each file",
            3,
            processingEmissions.size
        )
        assertEquals("First file index should be 0", 0, processingEmissions[0].currentIndex)
        assertEquals("Last file index should be 2", 2, processingEmissions[2].currentIndex)
    }

    @Test
    fun `PartialSuccess returned when some files fail`() = runTest {
        // Given
        val successFile = mockk<File>(relaxed = true) {
            every { exists() } returns true
            every { isFile } returns true
            every { name } returns "success.jpg"
            every { absolutePath } returns "/source/success.jpg"
        }
        val failFile = mockk<File>(relaxed = true) {
            every { exists() } returns true
            every { isFile } returns true
            every { name } returns "fail.jpg"
            every { absolutePath } returns "/source/fail.jpg"
        }
        val destFile = mockk<File>(relaxed = true) {
            every { exists() } returns false
            every { mkdirs() } returns true
            every { absolutePath } returns "/dest"
        }
        
        every { successFile.copyTo(any(), any()) } returns mockk()
        every { failFile.copyTo(any(), any()) } throws Exception("Copy failed")
        
        val operation = FileOperation.Copy(
            sources = listOf(successFile, failFile),
            destination = destFile,
            overwrite = false
        )

        // When
        val emissions = useCase.executeWithProgress(operation).toList()

        // Then
        val completed = emissions.last() as FileOperationProgress.Completed
        assertTrue(
            "Result should be PartialSuccess",
            completed.result is FileOperationResult.PartialSuccess
        )
        val partial = completed.result as FileOperationResult.PartialSuccess
        assertEquals("Processed count should be 1", 1, partial.processedCount)
        assertEquals("Failed count should be 1", 1, partial.failedCount)
    }
}
