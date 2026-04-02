package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.domain.transfer.FileOperationErrorHandler
import com.sza.fastmediasorter.domain.transfer.FileTransferProvider
import com.sza.fastmediasorter.domain.transfer.ProgressTracker
import com.sza.fastmediasorter.domain.transfer.TempFileManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UnifiedFileOperationHandlerDirectoryTest {

    private val localProvider: LocalTransferProvider = mockk(relaxed = true)
    private val tempFileManager: TempFileManager = mockk(relaxed = true)
    private val progressTracker: ProgressTracker = mockk(relaxed = true)
    private val errorHandler: FileOperationErrorHandler = mockk(relaxed = true)

    private val localStrategy: FileOperationStrategy = mockk()
    private val smbStrategy: FileOperationStrategy = mockk()

    private lateinit var handler: UnifiedFileOperationHandler

    @Before
    fun setup() {
        every { errorHandler.handleError(any(), any(), *anyVararg()) } answers {
            firstArg<Throwable>().message ?: "error"
        }
        handler = UnifiedFileOperationHandler(
            localProvider = localProvider,
            tempFileManager = tempFileManager,
            progressTracker = progressTracker,
            errorHandler = errorHandler,
            operationStrategies = mapOf("local" to localStrategy, "smb" to smbStrategy)
        )
    }

    // ---- deleteDirectory ----

    @Test
    fun `deleteDirectory delegates to local strategy`() = runTest {
        coEvery { localStrategy.deleteDirectory("/storage/Folder", any()) } returns Result.success(3)

        val result = handler.executeDeleteDirectory("/storage/Folder")
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrThrow())
    }

    @Test
    fun `deleteDirectory delegates to smb strategy`() = runTest {
        coEvery { smbStrategy.deleteDirectory("smb://server/share/dir", any()) } returns Result.success(10)

        val result = handler.executeDeleteDirectory("smb://server/share/dir")
        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrThrow())
    }

    // ---- renameDirectory ----

    @Test
    fun `renameDirectory computes new path and delegates`() = runTest {
        coEvery { localStrategy.renameDirectory("/storage/Old", "/storage/New") } returns Result.success("/storage/New")

        val result = handler.executeRenameDirectory("/storage/Old", "New")
        assertTrue(result.isSuccess)
        assertEquals("/storage/New", result.getOrThrow())
    }

    // ---- copyDirectory ----

    @Test
    fun `copyDirectory same protocol delegates to strategy`() = runTest {
        coEvery { localStrategy.copyDirectory("/a/src", "/a/dst/src", any()) } returns Result.success(5)

        val result = handler.executeCopyDirectory("/a/src", "/a/dst")
        assertTrue(result.isSuccess)
        assertEquals(5, result.getOrThrow())
        coVerify { localStrategy.copyDirectory("/a/src", "/a/dst/src", any()) }
    }

    @Test
    fun `copyDirectory cross-protocol returns UnsupportedOperationException`() = runTest {
        val result = handler.executeCopyDirectory("/local/dir", "smb://server/share")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }

    // ---- moveDirectory ----

    @Test
    fun `moveDirectory same protocol delegates to strategy`() = runTest {
        coEvery { smbStrategy.moveDirectory("smb://s/a", "smb://s/b/a", any()) } returns Result.success(7)

        val result = handler.executeMoveDirectory("smb://s/a", "smb://s/b")
        assertTrue(result.isSuccess)
        assertEquals(7, result.getOrThrow())
    }

    @Test
    fun `moveDirectory cross-protocol returns UnsupportedOperationException`() = runTest {
        val result = handler.executeMoveDirectory("smb://s/dir", "/local/dest")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }

    // ---- protocol key resolution ----

    @Test
    fun `getStrategy throws for unregistered protocol`() = runTest {
        // sftp strategy not registered
        val result = handler.executeDeleteDirectory("sftp://host/path")
        assertTrue(result.isFailure)
    }
}
