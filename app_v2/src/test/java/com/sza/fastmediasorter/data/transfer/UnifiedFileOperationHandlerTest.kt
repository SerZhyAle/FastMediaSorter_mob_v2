package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.domain.transfer.FileOperationErrorHandler
import com.sza.fastmediasorter.domain.transfer.FileTransferProvider
import com.sza.fastmediasorter.domain.transfer.ProgressTracker
import com.sza.fastmediasorter.domain.transfer.TempFileManager
import com.sza.fastmediasorter.testing.createMediaFile
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for [UnifiedFileOperationHandler]: protocol routing by path prefix, cross-protocol
 * copy (download→upload via temp file + cleanup), move = copy + soft-delete with rollback, rename
 * and trash path construction, create-directory/text-file delegation, and the cross-protocol
 * directory guard. Providers / strategies / temp / progress / errorHandler are mocked - no I/O.
 */
class UnifiedFileOperationHandlerTest {

    private val localProvider = mockk<LocalTransferProvider>(relaxed = true)
    private val tempFileManager = mockk<TempFileManager>(relaxed = true)
    private val progressTracker = mockk<ProgressTracker>(relaxed = true)
    private val errorHandler = mockk<FileOperationErrorHandler>()
    private val localStrategy = mockk<FileOperationStrategy>(relaxed = true)

    private lateinit var handler: UnifiedFileOperationHandler

    @Before
    fun setUp() {
        every { errorHandler.handleError(any(), any(), any(), any()) } returns "translated-error"
        every { localProvider.protocolName } returns "Local"
        handler = UnifiedFileOperationHandler(
            localProvider = localProvider,
            tempFileManager = tempFileManager,
            progressTracker = progressTracker,
            errorHandler = errorHandler,
            operationStrategies = mapOf("local" to localStrategy)
        )
    }

    @Test
    fun `executeCopy aborts immediately when cancelFlag set`() = runBlocking {
        val result = handler.executeCopy(
            createMediaFile(name = "a.jpg", path = "/src/a.jpg"),
            createMediaResource(),
            createMediaResource(path = "/dest"),
            cancelFlag = { true }
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `executeCopy cross-protocol downloads to temp then uploads`() = runBlocking {
        val temp = File.createTempFile("ufoh", ".tmp")
        every { tempFileManager.createTempFileFromName(any()) } returns temp
        coEvery { localProvider.downloadFile(any(), any(), any()) } returns Result.success(Unit)
        coEvery { localProvider.uploadFile(any(), any(), any()) } returns Result.success(Unit)

        val result = handler.executeCopy(
            createMediaFile(name = "a.jpg", path = "/src/a.jpg"),
            createMediaResource(),
            createMediaResource(path = "/dest")
        )

        assertTrue(result.isSuccess)
        assertEquals("/dest/a.jpg", result.getOrNull())
        coVerify { tempFileManager.cleanupTempFile(temp) }
        temp.delete()
        Unit
    }

    @Test
    fun `executeCopy fails when download fails`() = runBlocking {
        every { tempFileManager.createTempFileFromName(any()) } returns File.createTempFile("ufoh", ".tmp")
        coEvery { localProvider.downloadFile(any(), any(), any()) } returns
            Result.failure(RuntimeException("download error"))

        val result = handler.executeCopy(
            createMediaFile(name = "a.jpg", path = "/src/a.jpg"),
            createMediaResource(),
            createMediaResource(path = "/dest")
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `executeMove copies then soft-deletes source`() = runBlocking {
        every { tempFileManager.createTempFileFromName(any()) } returns File.createTempFile("ufoh", ".tmp")
        coEvery { localProvider.downloadFile(any(), any(), any()) } returns Result.success(Unit)
        coEvery { localProvider.uploadFile(any(), any(), any()) } returns Result.success(Unit)
        coEvery { localProvider.createDirectory(any()) } returns Result.success("/dest/.trash")
        // soft-delete = moveFile to trash
        val trashSlot = slot<String>()
        coEvery { localProvider.moveFile(any(), capture(trashSlot)) } returns Result.success("/dest/.trash/a.jpg")

        val result = handler.executeMove(
            createMediaFile(name = "a.jpg", path = "/src/a.jpg"),
            createMediaResource(path = "/src"),
            createMediaResource(path = "/dest")
        )

        assertTrue(result.isSuccess)
        assertEquals("/dest/a.jpg", result.getOrNull()!!.destinationPath)
        assertEquals("/src/a.jpg", result.getOrNull()!!.originalPath)
        assertTrue(trashSlot.captured.endsWith("/.trash/a.jpg"))
    }

    @Test
    fun `executeMove rolls back copied file when soft-delete fails`() = runBlocking {
        every { tempFileManager.createTempFileFromName(any()) } returns File.createTempFile("ufoh", ".tmp")
        coEvery { localProvider.downloadFile(any(), any(), any()) } returns Result.success(Unit)
        coEvery { localProvider.uploadFile(any(), any(), any()) } returns Result.success(Unit)
        coEvery { localProvider.createDirectory(any()) } returns Result.success("/dest/.trash")
        coEvery { localProvider.moveFile(any(), any()) } returns Result.failure(RuntimeException("trash fail"))
        coEvery { localProvider.deleteFile(any()) } returns Result.success(Unit)

        val result = handler.executeMove(
            createMediaFile(name = "a.jpg", path = "/src/a.jpg"),
            createMediaResource(path = "/src"),
            createMediaResource(path = "/dest")
        )

        assertTrue(result.isFailure)
        // rollback delete of the copied destination
        coVerify { localProvider.deleteFile("/dest/a.jpg") }
    }

    @Test
    fun `executeRename builds new path in same directory`() = runBlocking {
        val newPathSlot = slot<String>()
        coEvery { localProvider.renameFile("/dir/old.jpg", capture(newPathSlot)) } returns Result.success("/dir/new.jpg")

        val result = handler.executeRename("/dir/old.jpg", "new.jpg", createMediaResource())

        assertTrue(result.isSuccess)
        assertEquals("/dir/new.jpg", newPathSlot.captured)
    }

    @Test
    fun `executeCreateDirectory delegates to provider`() = runBlocking {
        coEvery { localProvider.createDirectory("/dir/new") } returns Result.success("/dir/new")
        assertEquals("/dir/new", handler.executeCreateDirectory("/dir/new").getOrNull())
    }

    @Test
    fun `executeCreateTextFile delegates to strategy`() = runBlocking {
        coEvery { localStrategy.createTextFile("/dir", "n.txt", "body", 9L) } returns Result.success("/dir/n.txt")
        val result = handler.executeCreateTextFile("/dir", "n.txt", 9L, "body")
        assertEquals("/dir/n.txt", result.getOrNull())
    }

    @Test
    fun `executeCopyDirectory rejects cross-protocol`() = runBlocking {
        val result = handler.executeCopyDirectory("/local/dir", "smb://server/share/x")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }

    @Test
    fun `executeMoveDirectory rejects cross-protocol`() = runBlocking {
        val result = handler.executeMoveDirectory("smb://s/share/d", "/local/dest")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }

    @Test
    fun `executeDeleteDirectory delegates to strategy`() = runBlocking {
        coEvery { localStrategy.deleteDirectory("/dir", any()) } returns Result.success(3)
        assertEquals(3, handler.executeDeleteDirectory("/dir").getOrNull())
    }

    @Test
    fun `getProvider throws for unregistered protocol surfaces as failure`() = runBlocking {
        // smb provider not registered → executeRename routes to getProvider("smb://..") → throws → error result
        val result = handler.executeRename("smb://server/share/f.jpg", "g.jpg", createMediaResource())
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }
}
