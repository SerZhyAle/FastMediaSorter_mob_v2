package com.sza.fastmediasorter.data.network

import com.hierynomus.msdtyp.FileTime
import com.hierynomus.msfscc.fileinformation.FileAllInformation
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Unit tests for [SmbFileOperations]. [SmbConnectionManager.withConnection] is stubbed to invoke
 * the operation block with a mocked SMBJ [DiskShare]; no sockets are opened. Covers existence
 * guards, CRUD decision branches, read clamping/chunking, file-info mapping, and error wrapping.
 */
class SmbFileOperationsTest {

    private val info = SmbConnectionInfo(server = "srv", shareName = "share")

    private fun managerInvokingBlock(share: DiskShare): SmbConnectionManager {
        val manager = mockk<SmbConnectionManager>()
        val blockSlot = slot<suspend (DiskShare) -> SmbResult<Any?>>()
        coEvery { manager.withConnection(any(), any(), capture(blockSlot)) } coAnswers {
            blockSlot.captured.invoke(share)
        }
        return manager
    }

    private fun managerThrowing(t: Throwable): SmbConnectionManager {
        val manager = mockk<SmbConnectionManager>()
        coEvery { manager.withConnection(any(), any(), any<suspend (DiskShare) -> SmbResult<Any?>>()) } throws t
        return manager
    }

    private fun fileWithSize(size: Long, content: ByteArray = ByteArray(0)): File {
        val file = mockk<File>(relaxed = true)
        val standard = mockk<FileStandardInformation>()
        every { standard.endOfFile } returns size
        every { standard.isDirectory } returns false
        val basic = mockk<FileBasicInformation>()
        val ft = mockk<FileTime>()
        every { ft.toEpochMillis() } returns 123L
        every { basic.lastWriteTime } returns ft
        val all = mockk<FileAllInformation>()
        every { all.standardInformation } returns standard
        every { all.basicInformation } returns basic
        every { file.fileInformation } returns all
        every { file.inputStream } returns ByteArrayInputStream(content)
        return file
    }

    // ── download ──────────────────────────────────────────────────────────────

    @Test
    fun `downloadFile copies share content to output stream`() = runTest {
        val payload = "hello".toByteArray()
        val file = fileWithSize(payload.size.toLong(), payload)
        val share = mockk<DiskShare>()
        every { share.openFile(any(), any(), any(), any(), any(), any()) } returns file
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val out = ByteArrayOutputStream()
        val result = ops.downloadFile(info, "/dir/f.bin", out)
        assertTrue(result is SmbResult.Success)
        assertArrayEquals(payload, out.toByteArray())
    }

    @Test
    fun `downloadFile maps exception to error`() = runTest {
        val ops = SmbFileOperations(managerThrowing(RuntimeException("net down")))
        val result = ops.downloadFile(info, "/f", ByteArrayOutputStream())
        assertTrue(result is SmbResult.Error)
        assertTrue((result as SmbResult.Error).message.contains("net down"))
    }

    // ── readFileBytes ────────────────────────────────────────────────────────────

    @Test
    fun `readFileBytes full read returns all content`() = runTest {
        val payload = "0123456789".toByteArray()
        val file = fileWithSize(payload.size.toLong(), payload)
        val share = mockk<DiskShare>()
        every { share.openFile(any(), any(), any(), any(), any(), any()) } returns file
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.readFileBytes(info, "/f", maxBytes = Long.MAX_VALUE)
        assertArrayEquals(payload, (result as SmbResult.Success).data)
    }

    @Test
    fun `readFileBytes honours maxBytes cap`() = runTest {
        val payload = "0123456789".toByteArray()
        val file = fileWithSize(payload.size.toLong(), payload)
        val share = mockk<DiskShare>()
        every { share.openFile(any(), any(), any(), any(), any(), any()) } returns file
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.readFileBytes(info, "/f", maxBytes = 4)
        assertArrayEquals("0123".toByteArray(), (result as SmbResult.Success).data)
    }

    // ── readPartialFile ──────────────────────────────────────────────────────────

    @Test
    fun `readPartialFile clamps offset and length to file size`() = runTest {
        val payload = "abcdefghij".toByteArray()
        val file = fileWithSize(payload.size.toLong(), payload)
        val share = mockk<DiskShare>()
        every { share.openFile(any(), any(), any(), any(), any(), any()) } returns file
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.readPartialFile(info, "/f", offset = 2, length = 3)
        assertArrayEquals("cde".toByteArray(), (result as SmbResult.Success).data)
    }

    @Test
    fun `readPartialFile returns empty when offset at end`() = runTest {
        val payload = "abc".toByteArray()
        val file = fileWithSize(payload.size.toLong(), payload)
        val share = mockk<DiskShare>()
        every { share.openFile(any(), any(), any(), any(), any(), any()) } returns file
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.readPartialFile(info, "/f", offset = 3, length = 8)
        assertArrayEquals(ByteArray(0), (result as SmbResult.Success).data)
    }

    @Test
    fun `readFileBytesRange rejects range exceeding Int MAX_VALUE`() = runTest {
        val ops = SmbFileOperations(mockk())
        val result = ops.readFileBytesRange(info, "/f", start = 0, length = Int.MAX_VALUE.toLong() + 1)
        assertTrue(result is SmbResult.Error)
    }

    // ── upload ────────────────────────────────────────────────────────────────

    @Test
    fun `uploadFile writes stream content to share file`() = runTest {
        val captured = ByteArrayOutputStream()
        val file = mockk<File>(relaxed = true)
        every { file.outputStream } returns captured
        val share = mockk<DiskShare>(relaxed = true)
        every { share.folderExists(any()) } returns true
        every { share.openFile(any(), any(), any(), any(), any(), any()) } returns file
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val payload = "upload-me".toByteArray()
        val result = ops.uploadFile(info, "dir/out.bin", ByteArrayInputStream(payload))
        assertTrue(result is SmbResult.Success)
        assertArrayEquals(payload, captured.toByteArray())
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun `deleteFile fails when file does not exist`() = runTest {
        val share = mockk<DiskShare>()
        every { share.fileExists(any()) } returns false
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.deleteFile(info, "/missing")
        assertTrue(result is SmbResult.Error)
        assertTrue((result as SmbResult.Error).message.contains("File not found"))
    }

    @Test
    fun `deleteFile succeeds when file exists`() = runTest {
        val share = mockk<DiskShare>(relaxed = true)
        every { share.fileExists(any()) } returns true
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.deleteFile(info, "/f")
        assertTrue(result is SmbResult.Success)
    }

    @Test
    fun `deleteFile wraps rm exception`() = runTest {
        val share = mockk<DiskShare>()
        every { share.fileExists(any()) } returns true
        every { share.rm(any()) } throws RuntimeException("denied")
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.deleteFile(info, "/f")
        assertTrue(result is SmbResult.Error)
        assertTrue((result as SmbResult.Error).message.contains("denied"))
    }

    // ── deleteDirectory ──────────────────────────────────────────────────────────

    @Test
    fun `deleteDirectory treats missing directory as success`() = runTest {
        val share = mockk<DiskShare>()
        every { share.fileExists(any()) } returns false
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.deleteDirectory(info, "/dir")
        assertTrue(result is SmbResult.Success)
    }

    @Test
    fun `deleteDirectory removes existing directory recursively`() = runTest {
        val share = mockk<DiskShare>(relaxed = true)
        every { share.fileExists(any()) } returns true
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.deleteDirectory(info, "/dir")
        assertTrue(result is SmbResult.Success)
    }

    // ── rename ────────────────────────────────────────────────────────────────

    @Test
    fun `renameFile fails when source missing`() = runTest {
        val share = mockk<DiskShare>()
        every { share.fileExists("old") } returns false
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.renameFile(info, "old", "new")
        assertTrue((result as SmbResult.Error).message.contains("Source file not found"))
    }

    @Test
    fun `renameFile fails when destination exists`() = runTest {
        val share = mockk<DiskShare>()
        every { share.fileExists("old") } returns true
        every { share.fileExists("new") } returns true
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.renameFile(info, "old", "new")
        assertTrue((result as SmbResult.Error).message.contains("Destination already exists"))
    }

    @Test
    fun `renameFile succeeds`() = runTest {
        val file = mockk<File>(relaxed = true)
        val share = mockk<DiskShare>()
        every { share.fileExists("old") } returns true
        every { share.fileExists("new") } returns false
        every { share.openFile(any(), any(), any(), any(), any(), any()) } returns file
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.renameFile(info, "old", "new")
        assertTrue(result is SmbResult.Success)
    }

    // ── createDirectory / exists ────────────────────────────────────────────────

    @Test
    fun `createDirectory delegates to share mkdir`() = runTest {
        val share = mockk<DiskShare>(relaxed = true)
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.createDirectory(info, "/new")
        assertTrue(result is SmbResult.Success)
    }

    @Test
    fun `exists reports share fileExists result`() = runTest {
        val share = mockk<DiskShare>()
        every { share.fileExists(any()) } returns true
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.exists(info, "/f")
        assertTrue((result as SmbResult.Success).data)
    }

    // ── getFileInfo ──────────────────────────────────────────────────────────────

    @Test
    fun `getFileInfo maps standard and basic information`() = runTest {
        val file = fileWithSize(4096L)
        val share = mockk<DiskShare>()
        every { share.openFile(any(), any(), any(), any(), any(), any()) } returns file
        val ops = SmbFileOperations(managerInvokingBlock(share))
        val result = ops.getFileInfo(info, "dir/photo.jpg")
        val data = (result as SmbResult.Success).data
        assertEquals("photo.jpg", data.name)
        assertEquals(4096L, data.size)
        assertEquals(123L, data.lastModified)
        assertTrue(!data.isDirectory)
    }
}
