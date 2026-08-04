package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.domain.transfer.TempFileManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S1325: the cross-protocol tree walk. The source side is a local strategy, the destination side an
 * SMB one, which is exactly the pair the old dispatch refused outright.
 */
class DirectoryTreeTransferManagerTest {

    private val localStrategy: FileOperationStrategy = mockk(relaxed = true)
    private val smbStrategy: FileOperationStrategy = mockk(relaxed = true)
    private val tempFileManager: TempFileManager = mockk(relaxed = true)

    private lateinit var manager: DirectoryTreeTransferManager

    private val root = "/storage/Trip"
    private val destination = "smb://server/share/Trip"

    @Before
    fun setup() {
        every { localStrategy.getProtocolName() } returns "local"
        every { smbStrategy.getProtocolName() } returns "smb"
        manager = DirectoryTreeTransferManager(
            operationStrategies = mapOf("local" to localStrategy, "smb" to smbStrategy),
            tempFileManager = tempFileManager,
        )

        // /storage/Trip: one file plus one subdirectory holding a second file.
        coEvery { localStrategy.listEntries(root) } returns Result.success(
            listOf(
                DirectoryEntry("$root/a.jpg", "a.jpg", isDirectory = false, size = 10L),
                DirectoryEntry("$root/Day2", "Day2", isDirectory = true, size = 0L),
            )
        )
        coEvery { localStrategy.listEntries("$root/Day2") } returns Result.success(
            listOf(DirectoryEntry("$root/Day2/b.jpg", "b.jpg", isDirectory = false, size = 20L))
        )
        coEvery { smbStrategy.createDirectory(any()) } returns Result.success(Unit)
        coEvery { smbStrategy.copyFile(any(), any(), any(), any()) } returns Result.success("ok")
    }

    @Test
    fun `copyTree recreates the structure and copies every file`() = runTest {
        val result = manager.copyTree(root, destination)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow())
        coVerify(exactly = 1) { smbStrategy.createDirectory(destination) }
        coVerify(exactly = 1) { smbStrategy.createDirectory("$destination/Day2") }
        coVerify(exactly = 1) { smbStrategy.copyFile("$root/a.jpg", "$destination/a.jpg", true, any()) }
        coVerify(exactly = 1) { smbStrategy.copyFile("$root/Day2/b.jpg", "$destination/Day2/b.jpg", true, any()) }
    }

    @Test
    fun `copyTree reports the current entry through the progress callback`() = runTest {
        val seen = mutableListOf<String>()

        manager.copyTree(root, destination) { _, _, name -> seen += name }

        assertEquals(listOf("a.jpg", "b.jpg"), seen)
    }

    @Test
    fun `copyTree never deletes at the source`() = runTest {
        manager.copyTree(root, destination)

        coVerify(exactly = 0) { localStrategy.deleteFile(any()) }
        coVerify(exactly = 0) { localStrategy.deleteDirectory(any(), any()) }
    }

    @Test
    fun `moveTree deletes a source file only after its copy succeeded`() = runTest {
        val result = manager.moveTree(root, destination)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { localStrategy.deleteFile("$root/a.jpg") }
        coVerify(exactly = 1) { localStrategy.deleteFile("$root/Day2/b.jpg") }
        coVerify(exactly = 1) { localStrategy.deleteDirectory(root, null) }
    }

    @Test
    fun `moveTree keeps the source entry whose copy failed`() = runTest {
        coEvery {
            smbStrategy.copyFile("$root/Day2/b.jpg", "$destination/Day2/b.jpg", any(), any())
        } returns Result.failure(Exception("connection reset"))

        val result = manager.moveTree(root, destination)

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { localStrategy.deleteFile("$root/a.jpg") }
        coVerify(exactly = 0) { localStrategy.deleteFile("$root/Day2/b.jpg") }
        coVerify(exactly = 0) { localStrategy.deleteDirectory(root, null) }
    }

    /**
     * S1325 phase 04: the worker cancels a folder transfer through the same mechanism - the walk
     * checks the job per entry, so a cancelled job stops the tree instead of running to the end.
     */
    @Test
    fun `cancellation stops the walk and propagates`() = runTest {
        val job = Job()
        coEvery { smbStrategy.copyFile("$root/a.jpg", any(), any(), any()) } answers {
            job.cancel()
            Result.success("ok")
        }

        val thrown = runCatching {
            withContext(job) { manager.copyTree(root, destination) }
        }.exceptionOrNull()

        assertTrue("expected CancellationException, got $thrown", thrown is CancellationException)
        coVerify(exactly = 0) { smbStrategy.copyFile("$root/Day2/b.jpg", any(), any(), any()) }
    }

    @Test
    fun `listing failure aborts before anything is written at the destination`() = runTest {
        coEvery { localStrategy.listEntries(root) } returns Result.failure(Exception("unreadable"))

        val result = manager.copyTree(root, destination)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { smbStrategy.copyFile(any(), any(), any(), any()) }
    }
}
