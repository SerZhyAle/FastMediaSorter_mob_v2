package com.sza.fastmediasorter.data.network

import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SmbFileMutationCoordinator] - rename/move decision logic over a mocked SMBJ
 * [DiskShare]. [SmbConnectionManager.withConnection] is stubbed to run the block in-process.
 */
class SmbFileMutationCoordinatorTest {

    private val info = SmbConnectionInfo(server = "srv", shareName = "share")

    private fun coordinatorFor(share: DiskShare): SmbFileMutationCoordinator {
        val manager = mockk<SmbConnectionManager>()
        val blockSlot = slot<suspend (DiskShare) -> SmbResult<Any?>>()
        coEvery { manager.withConnection(any(), any(), capture(blockSlot)) } coAnswers {
            blockSlot.captured.invoke(share)
        }
        return SmbFileMutationCoordinator(manager)
    }

    // ── rename ────────────────────────────────────────────────────────────────

    @Test
    fun `rename rejects invalid characters in new name`() = runTest {
        val share = mockk<DiskShare>(relaxed = true)
        val result = coordinatorFor(share).renameFile(info, "dir/old.txt", "ba:d?.txt")
        assertTrue((result as SmbResult.Error).message.contains("invalid characters"))
    }

    @Test
    fun `rename rejects when target already exists`() = runTest {
        val share = mockk<DiskShare>()
        every { share.fileExists("dir/new.txt") } returns true
        val result = coordinatorFor(share).renameFile(info, "dir/old.txt", "new.txt")
        assertTrue((result as SmbResult.Error).message.contains("already exists"))
    }

    @Test
    fun `rename keeps file in same directory for bare new name`() = runTest {
        val file = mockk<File>(relaxed = true)
        val share = mockk<DiskShare>()
        every { share.fileExists("dir/new.txt") } returns false
        every { share.openFile("dir/old.txt", any(), any(), any(), any(), any()) } returns file
        val result = coordinatorFor(share).renameFile(info, "/dir/old.txt", "new.txt")
        assertTrue(result is SmbResult.Success)
        verify { file.rename("dir/new.txt", false) }
    }

    @Test
    fun `rename treats slashed new name as full path from share root`() = runTest {
        val file = mockk<File>(relaxed = true)
        val share = mockk<DiskShare>()
        every { share.fileExists("other/moved.txt") } returns false
        every { share.openFile("dir/old.txt", any(), any(), any(), any(), any()) } returns file
        val result = coordinatorFor(share).renameFile(info, "dir/old.txt", "other/moved.txt")
        assertTrue(result is SmbResult.Success)
        verify { file.rename("other/moved.txt", false) }
    }

    @Test
    fun `rename reports error when source open fails`() = runTest {
        val share = mockk<DiskShare>()
        every { share.fileExists("dir/new.txt") } returns false
        every { share.openFile(any(), any(), any(), any(), any(), any()) } throws RuntimeException("no perm")
        val result = coordinatorFor(share).renameFile(info, "dir/old.txt", "new.txt")
        assertTrue((result as SmbResult.Error).message.contains("Failed to open source file"))
    }

    // ── move ──────────────────────────────────────────────────────────────────

    @Test
    fun `move fails when source missing`() = runTest {
        val share = mockk<DiskShare>()
        every { share.fileExists("src.txt") } returns false
        val result = coordinatorFor(share).moveFile(info, "src.txt", "dst/dst.txt")
        assertTrue((result as SmbResult.Error).message.contains("Source file does not exist"))
    }

    @Test
    fun `move fails when destination exists`() = runTest {
        val share = mockk<DiskShare>()
        every { share.fileExists("src.txt") } returns true
        every { share.fileExists("dst.txt") } returns true
        val result = coordinatorFor(share).moveFile(info, "src.txt", "dst.txt")
        assertTrue((result as SmbResult.Error).message.contains("Destination file already exists"))
    }

    @Test
    fun `move wraps outer exception`() = runTest {
        val manager = mockk<SmbConnectionManager>()
        coEvery { manager.withConnection(any(), any(), any<suspend (DiskShare) -> SmbResult<Any?>>()) } throws
            RuntimeException("connect fail")
        val result = SmbFileMutationCoordinator(manager).moveFile(info, "a", "b")
        assertTrue((result as SmbResult.Error).message.contains("Failed to move file"))
    }
}
