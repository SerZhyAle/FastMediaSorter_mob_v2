package com.sza.fastmediasorter.data.network

import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.data.network.helpers.SmbDirectoryScanner
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SmbMediaScanCoordinator]. The directory traversal ([SmbDirectoryScanner]) and the
 * connection manager are mocked; the coordinator's mapping, error wrapping, and progress callbacks
 * are exercised. No sockets are opened.
 */
class SmbMediaScanCoordinatorTest {

    private val info = SmbConnectionInfo(server = "srv", shareName = "share")

    private fun managerInvokingBlock(): SmbConnectionManager {
        val manager = mockk<SmbConnectionManager>()
        val share = mockk<DiskShare>(relaxed = true)
        val blockSlot = slot<suspend (DiskShare) -> SmbResult<Any?>>()
        coEvery { manager.withConnection(any(), any(), capture(blockSlot)) } coAnswers {
            blockSlot.captured.invoke(share)
        }
        return manager
    }

    private fun scanner(): SmbDirectoryScanner = mockk(relaxed = true)

    @Test
    fun `scanMediaFiles maps scanner results to public model`() = runTest {
        val scanner = scanner()
        coEvery {
            scanner.scanDirectoryRecursive(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val out = arg<MutableList<SmbDirectoryScanner.SmbFileInfo>>(3)
            out.add(SmbDirectoryScanner.SmbFileInfo("a.mp4", "dir/a.mp4", false, 100L, 5L))
        }
        val coordinator = SmbMediaScanCoordinator(managerInvokingBlock(), scanner)
        val result = coordinator.scanMediaFiles(info, "dir", scanSubdirectories = true)
        val data = (result as SmbResult.Success).data
        assertEquals(1, data.size)
        assertEquals("a.mp4", data.single().name)
        assertEquals("dir/a.mp4", data.single().path)
        assertEquals(100L, data.single().size)
    }

    @Test
    fun `scanMediaFiles reports completion progress on success`() = runTest {
        val scanner = scanner()
        val progress = mockk<ScanProgressCallback>(relaxed = true)
        val coordinator = SmbMediaScanCoordinator(managerInvokingBlock(), scanner)
        coordinator.scanMediaFiles(info, "dir", progressCallback = progress)
        coVerify { progress.onComplete(any(), any()) }
    }

    @Test
    fun `scanMediaFiles non-recursive uses non-recursive scanner`() = runTest {
        val scanner = scanner()
        val coordinator = SmbMediaScanCoordinator(managerInvokingBlock(), scanner)
        coordinator.scanMediaFiles(info, "dir", scanSubdirectories = false)
        coVerify { scanner.scanDirectoryNonRecursive(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `scanMediaFiles wraps scanner exception into error`() = runTest {
        val scanner = scanner()
        coEvery {
            scanner.scanDirectoryRecursive(any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("scan boom")
        val coordinator = SmbMediaScanCoordinator(managerInvokingBlock(), scanner)
        val result = coordinator.scanMediaFiles(info, "dir")
        assertTrue((result as SmbResult.Error).message.contains("Failed to scan media files"))
    }

    @Test
    fun `scanMediaFilesChunked maps results`() = runTest {
        val scanner = scanner()
        coEvery {
            scanner.scanDirectoryRecursiveWithLimit(any(), any(), any(), any(), any())
        } coAnswers {
            val out = arg<MutableList<SmbDirectoryScanner.SmbFileInfo>>(3)
            out.add(SmbDirectoryScanner.SmbFileInfo("c.jpg", "c.jpg", false, 1L, 1L))
            true
        }
        val coordinator = SmbMediaScanCoordinator(managerInvokingBlock(), scanner)
        val result = coordinator.scanMediaFilesChunked(info, "", maxFiles = 10)
        assertEquals(listOf("c.jpg"), (result as SmbResult.Success).data.map { it.name })
    }

    @Test
    fun `scanMediaFilesPaged delegates to offset-limit scanner`() = runTest {
        val scanner = scanner()
        val coordinator = SmbMediaScanCoordinator(managerInvokingBlock(), scanner)
        coordinator.scanMediaFilesPaged(info, "dir", offset = 5, limit = 20)
        coVerify { scanner.scanDirectoryWithOffsetLimit(any(), any(), any(), any(), 5, 20, 0) }
    }

    @Test
    fun `countMediaFiles returns scanner count`() = runTest {
        val scanner = scanner()
        every { scanner.countDirectoryRecursive(any(), any(), any(), any(), any()) } returns 42
        val coordinator = SmbMediaScanCoordinator(managerInvokingBlock(), scanner)
        val result = coordinator.countMediaFiles(info, "dir")
        assertEquals(42, (result as SmbResult.Success).data)
    }

    @Test
    fun `countMediaFiles wraps exception`() = runTest {
        val manager = mockk<SmbConnectionManager>()
        coEvery { manager.withConnection(any(), any(), any<suspend (DiskShare) -> SmbResult<Any?>>()) } throws
            RuntimeException("down")
        val coordinator = SmbMediaScanCoordinator(manager, scanner())
        val result = coordinator.countMediaFiles(info, "dir")
        assertTrue((result as SmbResult.Error).message.contains("Failed to count media files"))
    }
}
