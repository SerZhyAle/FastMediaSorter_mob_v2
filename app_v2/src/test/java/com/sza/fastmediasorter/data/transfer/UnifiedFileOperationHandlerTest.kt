package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.capability.RemoteSourceId
import com.sza.fastmediasorter.domain.transfer.FileOperationErrorHandler
import com.sza.fastmediasorter.testing.createMediaFile
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [UnifiedFileOperationHandler]: every protocol routes through the strategy map
 * (S3972 - the old provider registry held only "local", so every network path failed), single-file
 * copy goes through the tree transfer manager, and the source-availability gate still refuses.
 * Strategies / tree manager / errorHandler are mocked - no I/O.
 */
class UnifiedFileOperationHandlerTest {

    private val errorHandler = mockk<FileOperationErrorHandler>()
    private val localStrategy = mockk<FileOperationStrategy>(relaxed = true)
    private val smbStrategy = mockk<FileOperationStrategy>(relaxed = true)
    private val treeTransferManager = mockk<DirectoryTreeTransferManager>(relaxed = true)
    private var smbEnabled = true

    private lateinit var handler: UnifiedFileOperationHandler

    @Before
    fun setUp() {
        // S1378: a relaxed mock cannot stand in for a Result-returning call - it hands back a chained
        // mock where a value class is expected. Stubbing it explicitly also keeps the space pre-flight
        // on its "cannot measure, proceed" branch, leaving these protocol-routing assertions intact.
        coEvery { localStrategy.getDirectoryInfo(any()) } returns
            Result.failure(UnsupportedOperationException("not measured in this test"))
        coEvery { smbStrategy.getDirectoryInfo(any()) } returns
            Result.failure(UnsupportedOperationException("not measured in this test"))
        every { errorHandler.handleError(any(), any(), any(), any()) } returns "translated-error"
        handler = UnifiedFileOperationHandler(
            errorHandler = errorHandler,
            operationStrategies = mapOf("local" to localStrategy, "smb" to smbStrategy),
            remoteSourceGate = mockk<RemoteSourceAvailabilityGate> {
                every { isEnabled(RemoteSourceId.SMB) } answers { smbEnabled }
                every { isEnabled(neq(RemoteSourceId.SMB)) } returns true
                every { anyCloudEnabled() } returns true
            },
            directoryTreeTransferManager = treeTransferManager,
            // S1378: an unmeasurable destination is the "proceed" branch, so the fit check stays out
            // of the way of these assertions, which are about protocol routing, not capacity.
            getDestinationFreeSpace = mockk {
                coEvery { this@mockk(any()) } returns null
            },
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
        coVerify(exactly = 0) { treeTransferManager.copyFile(any(), any()) }
    }

    @Test
    fun `executeCopy to a network resource goes through the tree transfer manager`() = runBlocking {
        coEvery { treeTransferManager.copyFile("/cache/a.jpg", "smb://nas/share/cam/a.jpg") } returns
            Result.success(Unit)

        val result = handler.executeCopy(
            createMediaFile(name = "a.jpg", path = "/cache/a.jpg"),
            createMediaResource(),
            createMediaResource(path = "smb://nas/share/cam")
        )

        assertEquals("smb://nas/share/cam/a.jpg", result.getOrNull())
    }

    @Test
    fun `executeCopy surfaces a transfer failure`() = runBlocking {
        coEvery { treeTransferManager.copyFile(any(), any()) } returns
            Result.failure(RuntimeException("upload error"))

        val result = handler.executeCopy(
            createMediaFile(name = "a.jpg", path = "/src/a.jpg"),
            createMediaResource(),
            createMediaResource(path = "/dest")
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `executeCopy is refused when the destination source is disabled`() = runBlocking {
        smbEnabled = false

        val result = handler.executeCopy(
            createMediaFile(name = "a.jpg", path = "/cache/a.jpg"),
            createMediaResource(),
            createMediaResource(path = "smb://nas/share/cam")
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { treeTransferManager.copyFile(any(), any()) }
    }

    @Test
    fun `executeCreateDirectory on a network path reaches the smb strategy`() = runBlocking {
        coEvery { smbStrategy.createDirectory("smb://nas/share/new") } returns Result.success(Unit)

        assertEquals("smb://nas/share/new", handler.executeCreateDirectory("smb://nas/share/new").getOrNull())
    }

    @Test
    fun `executeCreateDirectory surfaces a strategy failure`() = runBlocking {
        coEvery { localStrategy.createDirectory("/dir/new") } returns Result.failure(RuntimeException("denied"))

        assertTrue(handler.executeCreateDirectory("/dir/new").isFailure)
    }

    @Test
    fun `executeCreateTextFile delegates to strategy`() = runBlocking {
        coEvery { localStrategy.createTextFile("/dir", "n.txt", "body", 9L) } returns Result.success("/dir/n.txt")
        val result = handler.executeCreateTextFile("/dir", "n.txt", 9L, "body")
        assertEquals("/dir/n.txt", result.getOrNull())
    }

    // S1325: cross-protocol directory work is routed to the tree transfer manager, not refused.
    @Test
    fun `executeCopyDirectory routes cross-protocol to the tree transfer manager`() = runBlocking {
        coEvery { treeTransferManager.copyTree("/local/dir", "smb://server/share/x/dir", any()) } returns
            Result.success(3)

        val result = handler.executeCopyDirectory("/local/dir", "smb://server/share/x")

        assertEquals(3, result.getOrNull())
    }

    @Test
    fun `executeMoveDirectory routes cross-protocol to the tree transfer manager`() = runBlocking {
        coEvery { treeTransferManager.moveTree("smb://s/share/d", "/local/dest/d", any()) } returns
            Result.success(6)

        val result = handler.executeMoveDirectory("smb://s/share/d", "/local/dest")

        assertEquals(6, result.getOrNull())
    }

    @Test
    fun `executeDeleteDirectory delegates to strategy`() = runBlocking {
        coEvery { localStrategy.deleteDirectory("/dir", any()) } returns Result.success(3)
        assertEquals(3, handler.executeDeleteDirectory("/dir").getOrNull())
    }
}
