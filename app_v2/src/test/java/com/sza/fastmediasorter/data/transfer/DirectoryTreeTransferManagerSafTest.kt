package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.domain.transfer.TempFileManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1378: a removable volume as an endpoint of the cross-protocol tree transfer - strategic §2 goal
 * 3a, moving a folder from a network resource straight onto the card.
 *
 * These tests exist because the manager was **not** in fact generic over the new key, contrary to
 * what Phase 04 predicted. It carried its own private copy of the path->key mapping, so a
 * `content://` destination resolved to the local strategy, which declares it cannot address
 * `content:` at all. The mapping now lives once in [transferProtocolKeyFor]; the first test below is
 * what proves it, and would have gone red before that change.
 */
class DirectoryTreeTransferManagerSafTest {

    private val safStrategy: FileOperationStrategy = mockk(relaxed = true) {
        every { getProtocolName() } returns "saf"
    }
    private val smbStrategy: FileOperationStrategy = mockk(relaxed = true) {
        every { getProtocolName() } returns "smb"
    }
    private val localStrategy: FileOperationStrategy = mockk(relaxed = true) {
        every { getProtocolName() } returns "local"
    }
    private val tempFileManager: TempFileManager = mockk(relaxed = true)

    private val manager = DirectoryTreeTransferManager(
        operationStrategies = mapOf(
            "saf" to safStrategy,
            "smb" to smbStrategy,
            "local" to localStrategy,
        ),
        tempFileManager = tempFileManager,
    )

    private val destinationRoot = "content://com.android.externalstorage.documents/tree/primary%3ADCIM/Trip"

    private fun emptyListing(strategy: FileOperationStrategy, path: String) {
        coEvery { strategy.listEntries(path) } returns Result.success(emptyList())
    }

    @Test
    fun `a document-tree destination resolves the saf strategy, not the local one`() = runTest {
        coEvery { safStrategy.createDirectory(destinationRoot) } returns Result.success(Unit)
        emptyListing(smbStrategy, "smb://nas/Media/Trip")

        val result = manager.copyTree("smb://nas/Media/Trip", destinationRoot)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { safStrategy.createDirectory(destinationRoot) }
        coVerify(exactly = 0) { localStrategy.createDirectory(any()) }
    }

    @Test
    fun `a document-tree source resolves the saf strategy as the source`() = runTest {
        coEvery { smbStrategy.createDirectory("smb://nas/Media/Trip") } returns Result.success(Unit)
        emptyListing(safStrategy, destinationRoot)

        val result = manager.copyTree(destinationRoot, "smb://nas/Media/Trip")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { safStrategy.listEntries(destinationRoot) }
        coVerify(exactly = 0) { localStrategy.listEntries(any()) }
    }

    /**
     * The destination subtree has to exist before anything is written into it - a document provider
     * cannot create a file inside a directory that is not there yet.
     */
    @Test
    fun `the destination subtree is created level by level before its files are written`() = runTest {
        val subDirectory = "$destinationRoot/Sub"
        coEvery { safStrategy.createDirectory(any()) } returns Result.success(Unit)
        coEvery { smbStrategy.listEntries("smb://nas/Media/Trip") } returns Result.success(
            listOf(
                DirectoryEntry("smb://nas/Media/Trip/Sub", "Sub", isDirectory = true, size = 0L),
                DirectoryEntry("smb://nas/Media/Trip/a.jpg", "a.jpg", isDirectory = false, size = 5L),
            )
        )
        coEvery { smbStrategy.listEntries("smb://nas/Media/Trip/Sub") } returns Result.success(
            listOf(DirectoryEntry("smb://nas/Media/Trip/Sub/b.jpg", "b.jpg", isDirectory = false, size = 7L))
        )
        coEvery { smbStrategy.copyFile(any(), any(), any(), any()) } returns Result.success("ok")
        coEvery { safStrategy.copyFile(any(), any(), any(), any()) } returns Result.success("ok")

        val result = manager.copyTree("smb://nas/Media/Trip", destinationRoot)

        assertEquals(2, result.getOrThrow())
        coVerify(exactly = 1) { safStrategy.createDirectory(destinationRoot) }
        coVerify(exactly = 1) { safStrategy.createDirectory(subDirectory) }
    }
}
