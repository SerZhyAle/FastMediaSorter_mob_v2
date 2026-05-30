package com.sza.fastmediasorter.data.network

import com.hierynomus.msdtyp.FileTime
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SmbShareDiscoveryHelper.performTestConnection] (share-path branch). The empty
 * shareName branch hits live socket enumeration and is out of scope. [DiskShare] is mocked.
 */
class SmbShareDiscoveryHelperTest {

    private fun item(name: String, isDir: Boolean): FileIdBothDirectoryInformation {
        val i = mockk<FileIdBothDirectoryInformation>()
        every { i.fileName } returns name
        every { i.fileAttributes } returns if (isDir) 0x10L else 0L
        every { i.endOfFile } returns 10L
        val ft = mockk<FileTime>()
        every { ft.toEpochMillis() } returns 1L
        every { i.lastWriteTime } returns ft
        return i
    }

    private fun helperFor(share: DiskShare): SmbShareDiscoveryHelper {
        val manager = mockk<SmbConnectionManager>()
        val blockSlot = slot<suspend (DiskShare) -> SmbResult<Any?>>()
        coEvery { manager.withConnection(any(), any(), capture(blockSlot)) } coAnswers {
            blockSlot.captured.invoke(share)
        }
        return SmbShareDiscoveryHelper(manager)
    }

    @Test
    fun `performTestConnection reports statistics for accessible share`() = runTest {
        val share = mockk<DiskShare>()
        every { share.folderExists("photos") } returns true
        every { share.fileExists("photos") } returns false
        every { share.list("photos") } returns listOf(
            item("a.jpg", false), item("clip.mp4", false), item("song.mp3", false),
            item("notes.txt", false), item("sub", true), item(".hidden", false)
        )
        val info = SmbConnectionInfo(server = "srv", shareName = "media")
        val result = helperFor(share).performTestConnection(info, "photos")
        val msg = (result as SmbResult.Success).data
        assertTrue(msg.contains("Subfolders: 1"))
        assertTrue(msg.contains("Media files: 3"))
        // five non-hidden items (a.jpg, clip.mp4, song.mp3, notes.txt, sub)
        assertTrue(msg.contains("Total items: 5"))
    }

    @Test
    fun `performTestConnection fails when requested subfolder is missing`() = runTest {
        val share = mockk<DiskShare>()
        every { share.folderExists("missing") } returns false
        every { share.fileExists("missing") } returns false
        val info = SmbConnectionInfo(server = "srv", shareName = "media")
        val result = helperFor(share).performTestConnection(info, "missing")
        assertTrue((result as SmbResult.Error).message.contains("does not exist"))
    }

    @Test
    fun `performTestConnection scans share root when no path given`() = runTest {
        val share = mockk<DiskShare>()
        every { share.list("") } returns listOf(item("v.mkv", false), item(".cache", false))
        val info = SmbConnectionInfo(server = "srv", shareName = "media")
        val result = helperFor(share).performTestConnection(info, "")
        val msg = (result as SmbResult.Success).data
        assertTrue(msg.contains("Media files: 1"))
        assertTrue(msg.contains("Total items: 1"))
    }

    @Test
    fun `performTestConnection warns when path verification throws but still scans root`() = runTest {
        val share = mockk<DiskShare>()
        every { share.folderExists("docs") } throws RuntimeException("acl error")
        every { share.list("") } returns listOf(item("x.jpg", false))
        val info = SmbConnectionInfo(server = "srv", shareName = "media")
        val result = helperFor(share).performTestConnection(info, "docs")
        val msg = (result as SmbResult.Success).data
        assertTrue(msg.contains("Warning"))
    }
}
